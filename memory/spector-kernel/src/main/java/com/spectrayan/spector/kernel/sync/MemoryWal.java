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
package com.spectrayan.spector.kernel.sync;

public interface MemoryWal {
    Object appendAppend(String memoryId, byte[] bytes);
    Object appendRecordWrite(String memoryId, long recordId, byte[] recordBytes);
    Object appendAdjAddEdge(String memoryId, int fromNode, int toNode, byte[] edgeBytes);
    Object appendRegistryIntern(String memoryId, int id, String name);
    Object appendHyperedgeAdd(String memoryId, int[] vertexEntities, int[] vertexRoles,
                              int type, float weight, int memoryIdx, long timestamp);
    Object appendChainLink(String memoryId, int fromIdx, int toIdx, int sessionId);
    default Object appendGraphAddNode(String memoryId, int entityId, String normalized, String type) { return null; }
    default Object appendGraphLinkMemory(String memoryId, int entityId, int memoryIdx) { return null; }

    default void replay(WalVisitor visitor) {}
    default void replay(long fromSeq, WalVisitor visitor) {}
}
