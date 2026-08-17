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

/**
 * {@link ScopedValue}-based carrier for memory session and namespace context in Java 25.
 *
 * <p>Allows the session ID and namespace ID to be propagated anywhere in the call stack
 * and across virtual thread boundaries without constructor injection or manual carrier objects.</p>
 *
 * @see com.spectrayan.spector.commons.concurrent.ConcurrentTasks
 */
public final class MemoryScope {

    public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> NAMESPACE_ID = ScopedValue.newInstance();

    private MemoryScope() {}

    /**
     * Returns the session ID if bound in the current scope, null otherwise.
     *
     * @return the session ID or null
     */
    public static String sessionId() {
        if (SESSION_ID.isBound()) {
            return SESSION_ID.get();
        }
        return null;
    }

    /**
     * Returns the namespace ID if bound in the current scope, null otherwise.
     *
     * @return the namespace ID or null
     */
    public static String namespaceId() {
        if (NAMESPACE_ID.isBound()) {
            return NAMESPACE_ID.get();
        }
        return null;
    }

    /**
     * Returns true if a session ID is bound in the current scope.
     *
     * @return true if session ID is active in this call stack
     */
    public static boolean isSessionActive() {
        return SESSION_ID.isBound();
    }

    /**
     * Returns true if a namespace ID is bound in the current scope.
     *
     * @return true if namespace ID is active in this call stack
     */
    public static boolean isNamespaceActive() {
        return NAMESPACE_ID.isBound();
    }

    /**
     * Backward-compatible alias for {@link #isSessionActive()}.
     *
     * @return true if session ID is active in this call stack
     */
    public static boolean isActive() {
        return isSessionActive();
    }

    /**
     * Executes a {@link Runnable} within a bound session and namespace scope.
     *
     * @param sessionId   session ID to bind (optional)
     * @param namespaceId namespace ID to bind (optional)
     * @param task        the runnable task to execute
     */
    public static void runWithScope(String sessionId, String namespaceId, Runnable task) {
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        boolean hasNamespace = namespaceId != null && !namespaceId.isBlank();

        if (hasSession && hasNamespace) {
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

    /**
     * Executes a {@link Callable} within a bound session and namespace scope.
     *
     * @param sessionId   session ID to bind (optional)
     * @param namespaceId namespace ID to bind (optional)
     * @param task        the callable task to execute
     * @param <T>         return type
     * @return result of the task
     * @throws Exception if task throws
     */
    public static <T> T callWithScope(String sessionId, String namespaceId, Callable<T> task) throws Exception {
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        boolean hasNamespace = namespaceId != null && !namespaceId.isBlank();

        if (hasSession && hasNamespace) {
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
