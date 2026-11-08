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
package com.spectrayan.spector.commons.concurrent;

import java.util.concurrent.Callable;

public final class MemoryScope {

    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> NAMESPACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<Long> FENCE_EPOCH = ScopedValue.newInstance();

    private MemoryScope() {}

    public static String tenantId() {
        return TENANT_ID.isBound() ? TENANT_ID.get() : null;
    }

    public static boolean isTenantActive() {
        return TENANT_ID.isBound();
    }

    public static String sessionId() {
        return SESSION_ID.isBound() ? SESSION_ID.get() : null;
    }

    public static String namespaceId() {
        return NAMESPACE_ID.isBound() ? NAMESPACE_ID.get() : null;
    }

    public static long fenceEpoch() {
        return FENCE_EPOCH.isBound() ? FENCE_EPOCH.get() : -1L;
    }

    public static boolean isFenceEpochActive() {
        return FENCE_EPOCH.isBound();
    }

    public static boolean isSessionActive() {
        return SESSION_ID.isBound();
    }

    public static boolean isNamespaceActive() {
        return NAMESPACE_ID.isBound();
    }

    public static boolean isActive() {
        return isSessionActive();
    }

    public static void runWithFenceEpoch(long fenceEpoch, Runnable task) {
        if (fenceEpoch >= 0) {
            ScopedValue.where(FENCE_EPOCH, fenceEpoch).run(task);
        } else {
            task.run();
        }
    }

    public static <T> T callWithFenceEpoch(long fenceEpoch, Callable<T> task) throws Exception {
        if (fenceEpoch >= 0) {
            return ScopedValue.where(FENCE_EPOCH, fenceEpoch).call(task::call);
        }
        return task.call();
    }

    public static void runWithScope(String sessionId, String namespaceId, Runnable task) {
        runWithScope(null, sessionId, namespaceId, fenceEpoch(), task);
    }

    public static void runWithScope(String sessionId, String namespaceId, long fenceEpoch, Runnable task) {
        runWithScope(null, sessionId, namespaceId, fenceEpoch, task);
    }

    public static void runWithScope(String tenantId, String sessionId, String namespaceId, Runnable task) {
        runWithScope(tenantId, sessionId, namespaceId, fenceEpoch(), task);
    }

    public static void runWithScope(String tenantId, String sessionId, String namespaceId, long fenceEpoch, Runnable task) {
        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        boolean hasNamespace = namespaceId != null && !namespaceId.isBlank();
        boolean hasFence = fenceEpoch >= 0;

        ScopedValue.Carrier carrier = null;
        if (hasTenant) {
            carrier = ScopedValue.where(TENANT_ID, tenantId);
        }
        if (hasSession) {
            carrier = carrier != null ? carrier.where(SESSION_ID, sessionId) : ScopedValue.where(SESSION_ID, sessionId);
        }
        if (hasNamespace) {
            carrier = carrier != null ? carrier.where(NAMESPACE_ID, namespaceId) : ScopedValue.where(NAMESPACE_ID, namespaceId);
        }
        if (hasFence) {
            carrier = carrier != null ? carrier.where(FENCE_EPOCH, fenceEpoch) : ScopedValue.where(FENCE_EPOCH, fenceEpoch);
        }

        if (carrier != null) {
            carrier.run(task);
        } else {
            task.run();
        }
    }

    public static <T> T callWithScope(String sessionId, String namespaceId, Callable<T> task) throws Exception {
        return callWithScope(null, sessionId, namespaceId, fenceEpoch(), task);
    }

    public static <T> T callWithScope(String sessionId, String namespaceId, long fenceEpoch, Callable<T> task) throws Exception {
        return callWithScope(null, sessionId, namespaceId, fenceEpoch, task);
    }

    public static <T> T callWithScope(String tenantId, String sessionId, String namespaceId, Callable<T> task) throws Exception {
        return callWithScope(tenantId, sessionId, namespaceId, fenceEpoch(), task);
    }

    public static <T> T callWithScope(String tenantId, String sessionId, String namespaceId, long fenceEpoch, Callable<T> task) throws Exception {
        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        boolean hasNamespace = namespaceId != null && !namespaceId.isBlank();
        boolean hasFence = fenceEpoch >= 0;

        ScopedValue.Carrier carrier = null;
        if (hasTenant) {
            carrier = ScopedValue.where(TENANT_ID, tenantId);
        }
        if (hasSession) {
            carrier = carrier != null ? carrier.where(SESSION_ID, sessionId) : ScopedValue.where(SESSION_ID, sessionId);
        }
        if (hasNamespace) {
            carrier = carrier != null ? carrier.where(NAMESPACE_ID, namespaceId) : ScopedValue.where(NAMESPACE_ID, namespaceId);
        }
        if (hasFence) {
            carrier = carrier != null ? carrier.where(FENCE_EPOCH, fenceEpoch) : ScopedValue.where(FENCE_EPOCH, fenceEpoch);
        }

        if (carrier != null) {
            return carrier.call(task::call);
        }
        return task.call();
    }
}
