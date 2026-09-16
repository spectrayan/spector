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
/**
 * Provides the filesystem contract for the memory system: where every file lives, how a
 * namespace directory is resolved, and how payload bytes are protected at rest.
 *
 * <p>{@link StoragePaths} is the single definition of every file name, directory name, and
 * extension beneath the configured {@code persistence-path} — manifest, {@code runtime/},
 * {@code wal/}, and the numbered {@code partitions/} directories. No path fragment is
 * hardcoded anywhere else, so the user configures one root and the layout follows.</p>
 *
 * <p>{@link NamespacePathResolver} is the only sanctioned source of a rememberer directory
 * (ADR-0034 §9.2, requirements R1.1, R1.2, R4, R9); resolving a namespace path by hand
 * anywhere else is a defect, because it bypasses the validation that keeps one tenant's files
 * out of another's tree. {@link PayloadEncryptor} is the SPI a deployment implements to encrypt
 * payload bytes before they reach the mmap'd slab.</p>
 *
 * <p>Key components include {@link StoragePaths}, {@link NamespacePathResolver}, and
 * {@link PayloadEncryptor}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.storage;
