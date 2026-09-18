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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.events.SpectorEvent;

/**
 * Sealed sub-hierarchy for memory system lifecycle events.
 *
 * <p>These events are published by the memory subsystem when significant
 * lifecycle milestones occur — checkpoint completion, compaction, tier
 * migration, etc. They drive event-driven operations such as:</p>
 * <ul>
 *   <li><b>Replication</b>: Enterprise {@code ReplicationCoordinator} subscribes
 *       to trigger incremental snapshot sync after checkpoints.</li>
 *   <li><b>Backup</b>: Backup services subscribe to schedule incremental
 *       backups after checkpoints.</li>
 *   <li><b>Analytics</b>: Tenant-specific analytics DBs receive lifecycle
 *       metrics for capacity planning.</li>
 * </ul>
 *
 * <h3>Identity via Context</h3>
 * <p>Lifecycle events use {@link SpectorEvent#context()} for generic instance
 * identity — the core module is tenant/user agnostic. Enterprise layers
 * enrich the context with {@link SpectorEvent.ContextKeys#TENANT} and
 * {@link SpectorEvent.ContextKeys#NAMESPACE} at construction time.</p>
 *
 * @see SpectorEvent
 * @see CheckpointCompletedEvent
 */
public sealed interface SpectorLifecycleEvent extends SpectorEvent permits
        CheckpointCompletedEvent {
    // Uses context() from SpectorEvent for instance/partition identity.
    // No enterprise-specific fields (tenantId, namespace) — core is agnostic.
}
