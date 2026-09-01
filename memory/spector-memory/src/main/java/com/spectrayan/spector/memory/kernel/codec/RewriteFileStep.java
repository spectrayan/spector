/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.kernel.codec;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.READ;

/**
 * Template for full-rewrite migrations using temp-file + fsync + ATOMIC_MOVE protocol.
 */
public abstract non-sealed class RewriteFileStep implements CodecStep {

    @Override
    public final Kind kind() {
        return Kind.REWRITE;
    }

    @Override
    public final void apply(MigrationContext ctx) throws IOException {
        Path src = ctx.sourcePath();
        Path tmp = src.resolveSibling(src.getFileName() + ".migrate.tmp");
        Files.deleteIfExists(tmp);

        try {
            rewrite(src, tmp, ctx);
            fsync(tmp);
            if (ctx.keepBackup()) {
                Path bak = src.resolveSibling(src.getFileName() + ".bak.v" + from().version());
                Files.copy(src, bak, REPLACE_EXISTING);
                fsync(bak);
            }
            Files.move(tmp, src, ATOMIC_MOVE, REPLACE_EXISTING);
            Path parent = src.getParent();
            if (parent != null) {
                fsync(parent);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    protected abstract void rewrite(Path source, Path target, MigrationContext ctx) throws IOException;

    private static void fsync(Path file) throws IOException {
        if (Files.exists(file) && !Files.isDirectory(file)) {
            try (FileChannel ch = FileChannel.open(file, READ)) {
                ch.force(true);
            }
        }
    }
}
