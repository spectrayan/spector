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

import com.spectrayan.spector.config.SpectorProperties;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * File names used for persistence layout.
 */
public class PersistenceFiles implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_INDEX_FILE = DEFAULT_PERSISTENCE_FILES_INDEX;
    public static final String DEFAULT_VECTORS_FILE = DEFAULT_PERSISTENCE_FILES_VECTORS;
    public static final String DEFAULT_DOCUMENTS_FILE = DEFAULT_PERSISTENCE_FILES_DOCUMENTS;
    public static final String DEFAULT_ID_MAPPINGS_FILE = DEFAULT_PERSISTENCE_FILES_ID_MAPPINGS;
    public static final String DEFAULT_SHARD_DIR_NAME = DEFAULT_PERSISTENCE_FILES_SHARD_DIR;

    public static final PersistenceFiles DEFAULTS = new PersistenceFiles(
            DEFAULT_INDEX_FILE, DEFAULT_VECTORS_FILE,
            DEFAULT_DOCUMENTS_FILE, DEFAULT_ID_MAPPINGS_FILE, DEFAULT_SHARD_DIR_NAME
    );

    private String indexFile;
    private String vectorsFile;
    private String documentsFile;
    private String idMappingsFile;
    private String shardDirName;

    public PersistenceFiles() {
        this(DEFAULTS.indexFile, DEFAULTS.vectorsFile, DEFAULTS.documentsFile, DEFAULTS.idMappingsFile, DEFAULTS.shardDirName);
    }

    public PersistenceFiles(String indexFile, String vectorsFile,
                            String documentsFile, String idMappingsFile) {
        this(indexFile, vectorsFile, documentsFile, idMappingsFile, DEFAULT_SHARD_DIR_NAME);
    }

    public PersistenceFiles(String indexFile, String vectorsFile,
                            String documentsFile, String idMappingsFile,
                            String shardDirName) {
        this.indexFile = requireNonBlank(indexFile, "indexFile");
        this.vectorsFile = requireNonBlank(vectorsFile, "vectorsFile");
        this.documentsFile = requireNonBlank(documentsFile, "documentsFile");
        this.idMappingsFile = requireNonBlank(idMappingsFile, "idMappingsFile");
        this.shardDirName = requireNonBlank(shardDirName, "shardDirName");
    }

    private static String requireNonBlank(String val, String name) {
        if (val == null || val.isBlank()) {
            throw new com.spectrayan.spector.config.error.SpectorConfigValueException(
                    "spector.persistence.files." + name, val);
        }
        return val;
    }

    public static PersistenceFiles from(SpectorProperties props) {
        return fromProperties(props);
    }

    public static PersistenceFiles fromProperties(SpectorProperties props) {
        if (props == null) return DEFAULTS;
        return new PersistenceFiles(
                props.getString(PERSISTENCE_FILES_INDEX, DEFAULTS.indexFile),
                props.getString(PERSISTENCE_FILES_VECTORS, DEFAULTS.vectorsFile),
                props.getString(PERSISTENCE_FILES_DOCUMENTS, DEFAULTS.documentsFile),
                props.getString(PERSISTENCE_FILES_ID_MAPPINGS, DEFAULTS.idMappingsFile),
                props.getString(PERSISTENCE_FILES_SHARD_DIR, DEFAULTS.shardDirName)
        );
    }

    public Path resolveIndex(Path baseDir) { return baseDir.resolve(indexFile); }
    public Path resolveVectors(Path baseDir) { return baseDir.resolve(vectorsFile); }
    public Path resolveDocuments(Path baseDir) { return baseDir.resolve(documentsFile); }
    public Path resolveIdMappings(Path baseDir) { return baseDir.resolve(idMappingsFile); }
    public Path resolveShardDir(Path baseDir) { return baseDir.resolve(shardDirName); }

    public String getIndexFile() { return indexFile; }
    public void setIndexFile(String indexFile) { this.indexFile = requireNonBlank(indexFile, "indexFile"); }
    public String indexFile() { return indexFile; }

    public String getVectorsFile() { return vectorsFile; }
    public void setVectorsFile(String vectorsFile) { this.vectorsFile = requireNonBlank(vectorsFile, "vectorsFile"); }
    public String vectorsFile() { return vectorsFile; }

    public String getDocumentsFile() { return documentsFile; }
    public void setDocumentsFile(String documentsFile) { this.documentsFile = requireNonBlank(documentsFile, "documentsFile"); }
    public String documentsFile() { return documentsFile; }

    public String getIdMappingsFile() { return idMappingsFile; }
    public void setIdMappingsFile(String idMappingsFile) { this.idMappingsFile = requireNonBlank(idMappingsFile, "idMappingsFile"); }
    public String idMappingsFile() { return idMappingsFile; }

    public String getShardDirName() { return shardDirName; }
    public void setShardDirName(String shardDirName) { this.shardDirName = requireNonBlank(shardDirName, "shardDirName"); }
    public String shardDirName() { return shardDirName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersistenceFiles that = (PersistenceFiles) o;
        return Objects.equals(indexFile, that.indexFile) &&
                Objects.equals(vectorsFile, that.vectorsFile) &&
                Objects.equals(documentsFile, that.documentsFile) &&
                Objects.equals(idMappingsFile, that.idMappingsFile) &&
                Objects.equals(shardDirName, that.shardDirName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexFile, vectorsFile, documentsFile, idMappingsFile, shardDirName);
    }

    @Override
    public String toString() {
        return "PersistenceFiles{" +
                "indexFile='" + indexFile + '\'' +
                ", vectorsFile='" + vectorsFile + '\'' +
                ", documentsFile='" + documentsFile + '\'' +
                ", idMappingsFile='" + idMappingsFile + '\'' +
                ", shardDirName='" + shardDirName + '\'' +
                '}';
    }
}
