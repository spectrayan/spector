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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record or class as a candidate for migration to a
 * <a href="https://openjdk.org/jeps/401">JEP 401 Value Class</a>
 * when Project Valhalla lands in a GA JDK release or preview.
 *
 * <h3>Requirements for Value Class Migration (JEP 390 / JEP 401)</h3>
 * <ul>
 *   <li>All instance fields must be {@code final} (Java records satisfy this implicitly)</li>
 *   <li>No {@code synchronized} methods or blocks, and never used as a monitor lock</li>
 *   <li>No identity-sensitive operations (avoid {@code ==} reference comparison and {@code System.identityHashCode})</li>
 *   <li>No subclasses (records and {@code final} classes satisfy this)</li>
 *   <li>Must implement value-based {@code equals}, {@code hashCode}, and {@code toString}</li>
 * </ul>
 *
 * <h3>Performance &amp; Hardware Benefits</h3>
 * <ul>
 *   <li><b>Heap flattening</b> — arrays of value records (e.g. {@code ScoredResult[]}) store components contiguously,
 *       completely eliminating 8-byte object headers and reference pointer arrays.</li>
 *   <li><b>Register scalarization</b> — the JIT compiler decomposes value objects directly into CPU registers,
 *       avoiding heap and stack frame allocations altogether.</li>
 *   <li><b>Zero GC pause overhead</b> — value objects on hot search/routing paths never trigger garbage collection.</li>
 *   <li><b>L1/L2 Cache Locality</b> — linear contiguous memory traversal eliminates pointer chasing and TLB misses.</li>
 * </ul>
 *
 * <p>On the {@code labs/valhalla} branch, annotated types are converted to {@code value record}
 * and compiled with Valhalla EA javac. On mainline ({@code epic/802-jdk27-upgrade} and {@code main}),
 * this annotation provides formal structural validation and guarantees drop-in compatibility.</p>
 *
 * @see <a href="https://openjdk.org/jeps/401">JEP 401: Value Classes and Objects (Preview)</a>
 * @see <a href="https://openjdk.org/jeps/390">JEP 390: Warnings Upon Identity-Sensitive Operations on Value-Based Classes</a>
 * @see <a href="https://openjdk.org/projects/valhalla/">Project Valhalla</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValueCandidate {

    /**
     * Brief architectural rationale for why this type is a value class candidate.
     */
    String reason() default "";

    /**
     * Estimated allocation frequency on the hot execution path.
     */
    Frequency hotPathFrequency() default Frequency.HIGH;

    /**
     * Allocation frequency categories.
     */
    enum Frequency {
        /** Millions of allocations per search or iteration (e.g. HNSW candidate queues, batch GPU similarity). */
        CRITICAL,
        /** Thousands of allocations per query or request (e.g. tokenizers, routing keys, fact entries). */
        HIGH,
        /** Tens of allocations per request (e.g. metrics snapshots, telemetry wrappers). */
        MEDIUM,
        /** Rarely allocated on the hot path. */
        LOW
    }
}
