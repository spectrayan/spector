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
package com.spectrayan.spector.synapse.agent.chat.service;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.Optional;

/**
 * Port interface extending LangGraph4j's {@link BaseCheckpointSaver} for durable
 * thread checkpointing in the operational plane.
 */
public interface GraphCheckpointPort extends BaseCheckpointSaver {

    /**
     * Directly persists raw serialized checkpoint bytes for a thread.
     */
    void saveCheckpoint(String threadId, String checkpointId, byte[] stateBytes);

    /**
     * Directly loads raw serialized checkpoint bytes for a thread.
     */
    Optional<byte[]> loadCheckpoint(String threadId);

    /**
     * Deletes checkpoint state for a thread.
     */
    void pruneCheckpoint(String threadId);
}
