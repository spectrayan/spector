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
package com.spectrayan.spector.commons.valhalla;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Architectural validation utility ensuring candidate types comply with
 * Project Valhalla (JEP 401) and Value-Based Class (JEP 390) structural constraints:
 * <ul>
 *   <li>Annotated with {@link ValueCandidate}</li>
 *   <li>Is a Java {@code record} or {@code final} class</li>
 *   <li>All instance fields are {@code final} (immutable state)</li>
 *   <li>No {@code synchronized} methods or monitor locks</li>
 *   <li>Implements value-based {@code equals} and {@code hashCode}</li>
 * </ul>
 */
public final class ValueClassValidator {

    private ValueClassValidator() {}

    /**
     * Asserts that the given class strictly adheres to JEP 390 and JEP 401 value class rules.
     *
     * @param type the class to inspect
     * @throws AssertionError if any value class constraint is violated
     */
    public static void assertValueClassCompliant(Class<?> type) {
        if (type == null) {
            throw new AssertionError("Type to validate cannot be null");
        }

        // 1. Must be annotated with @ValueCandidate
        if (!type.isAnnotationPresent(ValueCandidate.class)) {
            throw new AssertionError(type.getName() + " must be annotated with @" + ValueCandidate.class.getSimpleName());
        }

        // 2. Must be record or final class (no polymorphism or object slicing)
        if (!type.isRecord() && !Modifier.isFinal(type.getModifiers())) {
            throw new AssertionError(type.getName() + " must be a Java record or final class");
        }

        // 3. All instance fields must be strictly final
        Class<?> curr = type;
        while (curr != null && curr != Object.class && curr != Record.class) {
            for (Field field : curr.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    if (!Modifier.isFinal(field.getModifiers())) {
                        throw new AssertionError("Field " + field.getName() + " in " + type.getName()
                                + " is not final; value classes require all instance fields to be final");
                    }
                }
            }
            curr = curr.getSuperclass();
        }

        // 4. No synchronized methods (value classes have no object monitors)
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isSynchronized(method.getModifiers())) {
                throw new AssertionError("Method " + method.getName() + " in " + type.getName()
                        + " is synchronized; value classes do not possess object monitors");
            }
        }

        // 5. Must implement equals and hashCode (records provide these automatically)
        boolean hasEquals = false;
        boolean hasHashCode = false;
        try {
            Method equalsMethod = type.getMethod("equals", Object.class);
            hasEquals = equalsMethod.getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException ignored) {}

        try {
            Method hashMethod = type.getMethod("hashCode");
            hasHashCode = hashMethod.getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException ignored) {}

        if (!hasEquals || !hasHashCode) {
            throw new AssertionError(type.getName() + " must provide value-based equals() and hashCode() implementations");
        }
    }
}
