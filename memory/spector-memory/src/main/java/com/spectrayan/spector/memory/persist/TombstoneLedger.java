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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable record of forgotten-but-not-purged memory locations for a disk-backed store.
 *
 * <p>{@code forget} removes an id from the live index while its payload and text bytes stay on disk, and the
 * index snapshot only persists live ids. Without this ledger a restart loses the only pointer to those bytes,
 * so a later {@code purge} could not find them. Each line holds one id (Base64url, so any id is safe) and its
 * location; no content is ever written here.</p>
 *
 * <p>The file is rewritten atomically on every change. Tombstones are few and short-lived compared with live
 * records, so a full rewrite is cheap and avoids an append log that needs its own compaction.</p>
 */
public final class TombstoneLedger {

    private static final Logger log = LoggerFactory.getLogger(TombstoneLedger.class);

    /** File name of the ledger inside the store's persistence root. */
    public static final String FILE_NAME = "tombstones.ledger";

    private static final String HEADER = "# spector tombstone ledger v1: id-b64 type offset graphSlot partition textOffset textLength";

    private final Path file;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a ledger stored in {@code baseDir}.
     *
     * @param baseDir the store's persistence root
     */
    public TombstoneLedger(Path baseDir) {
        this.file = baseDir.resolve(FILE_NAME);
    }

    /**
     * Returns the ledger file path.
     *
     * @return the path of the ledger file
     */
    public Path file() {
        return file;
    }

    /**
     * Reads every entry. A missing file is an empty ledger; a malformed line is skipped and logged.
     *
     * @return id to retired location, in file order
     */
    public Map<String, MemoryLocation> load() {
        lock.lock();
        try {
            Map<String, MemoryLocation> out = new LinkedHashMap<>();
            if (!Files.exists(file)) {
                return out;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                Entry entry = Entry.parse(line);
                if (entry == null) {
                    log.warn("[TombstoneLedger] Skipping malformed line in {}", file);
                    continue;
                }
                out.put(entry.id(), entry.location());
            }
            return out;
        } catch (IOException e) {
            log.error("[TombstoneLedger] Failed to read {}: {}", file, e.getMessage(), e);
            return new LinkedHashMap<>();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replaces the ledger with {@code entries}. An empty map deletes the file.
     *
     * @param entries id to retired location
     * @throws IOException when the ledger cannot be written
     */
    public void save(Map<String, MemoryLocation> entries) throws IOException {
        lock.lock();
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            List<String> lines = new ArrayList<>(entries.size() + 1);
            lines.add(HEADER);
            entries.forEach((id, loc) -> lines.add(new Entry(id, loc).format()));
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            lock.unlock();
        }
    }

    /** One ledger line. */
    record Entry(String id, MemoryLocation location) {

        String format() {
            String encodedId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(id.getBytes(StandardCharsets.UTF_8));
            return String.join(" ",
                    encodedId,
                    location.type().name(),
                    Long.toString(location.offset()),
                    Integer.toString(location.graphSlot()),
                    Integer.toString(location.colocatedPartition()),
                    Long.toString(location.textOffset()),
                    Integer.toString(location.textLength()));
        }

        static Entry parse(String line) {
            String[] f = line.trim().split(" ");
            if (f.length != 7) {
                return null;
            }
            try {
                String id = new String(Base64.getUrlDecoder().decode(f[0]), StandardCharsets.UTF_8);
                MemoryLocation loc = new MemoryLocation(
                        MemoryType.valueOf(f[1]),
                        Long.parseLong(f[2]),
                        Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]),
                        Long.parseLong(f[5]),
                        Integer.parseInt(f[6]));
                return new Entry(id, loc);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
