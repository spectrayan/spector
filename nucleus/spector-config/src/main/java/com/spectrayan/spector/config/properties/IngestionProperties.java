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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import com.spectrayan.spector.commons.chunker.ChunkConfig;
import java.io.Serializable;
import java.nio.file.Path;

/**
 * Configuration properties POJO for file ingestion.
 *
 * <p>Maps to {@code spector.ingestion.*} namespace.</p>
 */
public class IngestionProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private Path rootDirectory = DEFAULT_INGESTION_ROOT_DIRECTORY;
    private String filePattern = DEFAULT_INGESTION_FILE_PATTERN;
    private String skipDirs = DEFAULT_INGESTION_SKIP_DIRS;
    private int chunkSize = DEFAULT_INGESTION_CHUNK_SIZE;
    private int chunkOverlap = DEFAULT_INGESTION_CHUNK_OVERLAP;
    private int parallelism = DEFAULT_INGESTION_PARALLELISM;
    private int maxRetries = DEFAULT_INGESTION_MAX_RETRIES;
    private int retryDelayMs = DEFAULT_INGESTION_RETRY_DELAY_MS;

    public IngestionProperties() {}

    public Path getRootDirectory() { return rootDirectory; }
    public void setRootDirectory(Path rootDirectory) {
        if (rootDirectory != null) this.rootDirectory = rootDirectory;
    }

    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) {
        if (filePattern != null && !filePattern.isBlank()) this.filePattern = filePattern;
    }

    public String getSkipDirs() { return skipDirs; }
    public void setSkipDirs(String skipDirs) {
        if (skipDirs != null) this.skipDirs = skipDirs;
    }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) {
        if (chunkSize > 0) this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) {
        if (chunkOverlap >= 0) this.chunkOverlap = chunkOverlap;
    }

    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) {
        if (parallelism > 0) this.parallelism = parallelism;
    }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) {
        if (maxRetries >= 0) this.maxRetries = maxRetries;
    }

    public int getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(int retryDelayMs) {
        if (retryDelayMs >= 0) this.retryDelayMs = retryDelayMs;
    }

    /**
     * Converts ingestion chunking properties to SPI {@link ChunkConfig}.
     */
    public ChunkConfig toChunkConfig() {
        return ChunkConfig.markdown(chunkSize, chunkOverlap);
    }

    public Path rootDirectory() { return getRootDirectory(); }
    public String filePattern() { return getFilePattern(); }
    public String skipDirs() { return getSkipDirs(); }
    public int chunkSize() { return getChunkSize(); }
    public int chunkOverlap() { return getChunkOverlap(); }
    public int parallelism() { return getParallelism(); }
    public int maxRetries() { return getMaxRetries(); }
    public int retryDelayMs() { return getRetryDelayMs(); }
}
