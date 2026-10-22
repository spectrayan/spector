/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.cli;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.memory.persist.StoreDirectoryLock;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.synapse.security.AgentTokenService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Moves a flat on-disk store into {@code <data-dir>/namespaces/<slug>}.
 */
@Component
@Command(
        name = "adopt-store",
        description = "Move an existing flat store into namespaces/<slug>, holding the store lock.",
        mixinStandardHelpOptions = true
)
public class AdoptStoreCommand implements Runnable {

    @Option(names = "--data-dir", required = true, description = "Flat persistence directory to adopt.")
    Path dataDir;

    @Option(names = "--namespace", required = true, description = "Namespace slug that will own the store.")
    String namespace;

    @Option(names = "--dry-run", description = "Report the plan from a temporary copy. Does not modify the store.")
    boolean dryRun;

    @Option(names = "--count", description = "With --dry-run, count live and forgotten records from the copy.")
    boolean count;

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        Adoption plan = plan(dataDir, namespace);
        var out = spec.commandLine().getOut();
        out.println("data-dir=" + plan.dataDir());
        out.println("target=" + plan.target());
        out.println("backup=" + plan.backup());
        if (dryRun) {
            Counts counts = count ? countCopy(plan.dataDir()) : new Counts(0, 0);
            out.println("dry-run=true");
            out.println("live=" + counts.live());
            out.println("forgotten=" + counts.forgotten());
            out.println("action=none");
            return;
        }
        execute(plan);
        out.println("dry-run=false");
        out.println("action=moved");
    }

    static Adoption plan(Path dataDir, String namespace) {
        if (dataDir == null || !Files.isDirectory(dataDir)) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "--data-dir", dataDir);
        }
        if (!AgentTokenService.isSlug(namespace)) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "--namespace", namespace);
        }
        Path root = dataDir.toAbsolutePath().normalize();
        Path target = root.resolve("namespaces").resolve(namespace);
        if (Files.isDirectory(target) && !isEmptyDir(target)) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "target",
                    target + " already holds a store");
        }
        refuseIfLocked(root);
        Path parent = root.getParent() == null ? root : root.getParent();
        Path backup = parent.resolve(root.getFileName().toString() + ".adopt-backup-" + System.currentTimeMillis());
        return new Adoption(root, target, backup);
    }

    static void execute(Adoption plan) {
        StoreDirectoryLock.acquire(plan.dataDir());
        try {
            copyTree(plan.dataDir(), plan.backup());
            Files.createDirectories(plan.target());
            try (Stream<Path> children = Files.list(plan.dataDir())) {
                for (Path child : children.toList()) {
                    String name = child.getFileName().toString();
                    if ("namespaces".equals(name) || StoreDirectoryLock.LOCK_FILE.equals(name)) {
                        continue;
                    }
                    Files.move(child, plan.target().resolve(name), StandardCopyOption.ATOMIC_MOVE);
                }
            }
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, e,
                    "adopt-store", e.getMessage());
        } finally {
            StoreDirectoryLock.release(plan.dataDir());
        }
    }

    static Counts countCopy(Path dataDir) {
        Path temp;
        try {
            temp = Files.createTempDirectory("spector-adopt-");
            copyTree(dataDir, temp);
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, e, "adopt-store", e.getMessage());
        }
        try {
            return countWal(temp.resolve("wal"));
        } finally {
            deleteTree(temp);
        }
    }

    static Counts countWal(Path walDir) {
        if (!Files.isDirectory(walDir)) {
            return new Counts(0, 0);
        }
        Set<String> live = new HashSet<>();
        Set<String> forgotten = new HashSet<>();
        try (MemoryWal wal = new MemoryWal(walDir)) {
            for (WalEvent event : wal.replayFromDisk()) {
                String id = event.memoryId();
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (event.type() == WalEvent.EventType.REMEMBER || event.type() == WalEvent.EventType.RECORD_WRITE) {
                    live.add(id);
                    forgotten.remove(id);
                } else if (event.type() == WalEvent.EventType.FORGET || event.type() == WalEvent.EventType.PURGE) {
                    live.remove(id);
                    forgotten.add(id);
                }
            }
        } catch (RuntimeException e) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, e, "wal", e.getMessage());
        }
        return new Counts(live.size(), forgotten.size());
    }

    private static void refuseIfLocked(Path dir) {
        Path lockFile = dir.resolve(StoreDirectoryLock.LOCK_FILE);
        if (!Files.exists(lockFile)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new SpectorConfigException(ErrorCode.STORE_LOCKED, dir, "another process");
            }
            lock.release();
        } catch (OverlappingFileLockException e) {
            throw new SpectorConfigException(ErrorCode.STORE_LOCKED, dir, "this process");
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.STORE_LOCKED, e, dir, e.getMessage());
        }
    }

    private static boolean isEmptyDir(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    static void copyTree(Path source, Path dest) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(source.relativize(file));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup of the temporary copy
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public record Adoption(Path dataDir, Path target, Path backup) {
    }

    public record Counts(int live, int forgotten) {
    }
}
