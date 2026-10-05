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

    public static boolean isSessionActive() {
        return SESSION_ID.isBound();
    }

    public static boolean isNamespaceActive() {
        return NAMESPACE_ID.isBound();
    }

    public static boolean isActive() {
        return isSessionActive();
    }

    public static void runWithScope(String sessionId, String namespaceId, Runnable task) {
        runWithScope(null, sessionId, namespaceId, task);
    }

    public static void runWithScope(String tenantId, String sessionId, String namespaceId, Runnable task) {
        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        boolean hasNamespace = namespaceId != null && !namespaceId.isBlank();

        if (hasTenant) {
            var carrier = ScopedValue.where(TENANT_ID, tenantId);
            if (hasSession) {
                carrier = carrier.where(SESSION_ID, sessionId);
            }
            if (hasNamespace) {
                carrier = carrier.where(NAMESPACE_ID, namespaceId);
            }
            carrier.run(task);
        } else if (hasSession && hasNamespace) {
            ScopedValue.where(SESSION_ID, sessionId)
                    .where(NAMESPACE_ID, namespaceId)
                    .run(task);
        } else if (hasSession) {
            ScopedValue.where(SESSION_ID, sessionId).run(task);
        } else if (hasNamespace) {
            ScopedValue.where(NAMESPACE_ID, namespaceId).run(task);
        } else {
            task.run();
        }
    }

    public static <T> T callWithScope(String sessionId, String namespaceId, Callable<T> task) throws Exception {
        return callWithScope(null, sessionId, namespaceId, task);
    }

    public static <T> T callWithScope(String tenantId, String sessionId, String namespaceId, Callable<T> task) throws Exception {
        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        boolean hasNamespace = namespaceId != null && !namespaceId.isBlank();

        if (hasTenant) {
            var carrier = ScopedValue.where(TENANT_ID, tenantId);
            if (hasSession) {
                carrier = carrier.where(SESSION_ID, sessionId);
            }
            if (hasNamespace) {
                carrier = carrier.where(NAMESPACE_ID, namespaceId);
            }
            return carrier.call(task::call);
        } else if (hasSession && hasNamespace) {
            return ScopedValue.where(SESSION_ID, sessionId)
                    .where(NAMESPACE_ID, namespaceId)
                    .call(task::call);
        } else if (hasSession) {
            return ScopedValue.where(SESSION_ID, sessionId).call(task::call);
        } else if (hasNamespace) {
            return ScopedValue.where(NAMESPACE_ID, namespaceId).call(task::call);
        } else {
            return task.call();
        }
    }
}
