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

import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Callback visitor for replaying decoded WAL mutation events (R9.1, R9.3, R9.4).
 *
 * <p>Receives fully decoded record payloads without exposing raw Panama {@code MemorySegment}
 * references to above-the-line consumers.</p>
 */
public interface WalVisitor {

    default void onRemember(String id, MemoryType tier, byte[] payload, long seq) {}

    default void onReinforce(String id, float delta, long seq) {}

    default void onTombstone(String id, long seq) {}

    default void onRecordWrite(String id, long slot, byte[] bytes, long seq) {}

    default void onAppend(String id, byte[] bytes, long seq) {}

    default void onRegistryIntern(String id, int code, String name, long seq) {}

    default void onAdjAddEdge(String id, int from, int to, float weightDelta, long seq) {}

    default void onGraphAddNode(String id, int nodeId, String name, String type, long seq) {}

    default void onGraphLinkMemory(String id, int entityId, int memoryIdx, long seq) {}

    default void onChainLink(String id, int fromIdx, int toIdx, int sessionId, long seq) {}

    default void onHyperedgeAdd(String id, int type, float weight, int memIdx, long ts, int[] verts, int[] roles, long seq) {}

    default void onSnapshotMark(long seq) {}
}
