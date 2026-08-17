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
package com.spectrayan.spector.commons.pathway;

import java.util.function.Predicate;

/**
 * A specification encapsulates a business rule that can be evaluated against
 * a candidate object, extending {@link Predicate} for full compatibility with
 * {@link GatedRelay} and standard Java functional APIs.
 *
 * <p>Unlike a bare {@code Predicate}, a {@code Specification} carries a
 * human-readable {@link #unsatisfiedReason(Object)} explaining <em>why</em>
 * the rule failed — invaluable for debug logging and recall trace diagnostics.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   Specification<RecallSignal> TEXT_SEARCH = Specification.of(
 *       "text search not enabled or mode is VECTOR_ONLY",
 *       s -> s.options().enableTextSearch());
 *
 *   // Works anywhere a Predicate is expected:
 *   pathway.gated("bm25", TEXT_SEARCH, relay, DEGRADE_GRACEFULLY);
 * }</pre>
 *
 * @param <T> the type of object this specification evaluates
 */
@FunctionalInterface
public interface Specification<T> extends Predicate<T> {

    /**
     * Evaluates whether the candidate satisfies this specification.
     *
     * @param candidate the object to evaluate
     * @return {@code true} if the candidate satisfies this specification
     */
    boolean isSatisfiedBy(T candidate);

    /**
     * Bridge to {@link Predicate#test(Object)} — delegates to
     * {@link #isSatisfiedBy(Object)}.
     */
    @Override
    default boolean test(T t) {
        return isSatisfiedBy(t);
    }

    /**
     * Returns a human-readable reason why the specification was not satisfied.
     *
     * <p>Called only when {@link #isSatisfiedBy(Object)} returns {@code false}.
     * Useful for debug logging, recall traces, and diagnostics.</p>
     *
     * @param candidate the object that did not satisfy this specification
     * @return a descriptive reason string
     */
    default String unsatisfiedReason(T candidate) {
        return getClass().getSimpleName() + " not satisfied";
    }

    /**
     * Creates a named specification with a fixed reason string.
     *
     * @param reason    the reason returned when the specification is not satisfied
     * @param predicate the evaluation logic
     * @param <T>       the type of object this specification evaluates
     * @return a new specification wrapping the predicate with the given reason
     */
    static <T> Specification<T> of(String reason, Predicate<T> predicate) {
        return new Specification<>() {
            @Override
            public boolean isSatisfiedBy(T candidate) {
                return predicate.test(candidate);
            }

            @Override
            public String unsatisfiedReason(T candidate) {
                return reason;
            }

            @Override
            public String toString() {
                return "Specification[" + reason + "]";
            }
        };
    }

    /**
     * Combines this specification with another using logical AND.
     * The resulting specification reports the first unsatisfied reason.
     */
    default Specification<T> and(Specification<T> other) {
        Specification<T> self = this;
        return new Specification<>() {
            @Override
            public boolean isSatisfiedBy(T candidate) {
                return self.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
            }

            @Override
            public String unsatisfiedReason(T candidate) {
                if (!self.isSatisfiedBy(candidate)) {
                    return self.unsatisfiedReason(candidate);
                }
                return other.unsatisfiedReason(candidate);
            }
        };
    }

    /**
     * Combines this specification with another using logical OR.
     */
    default Specification<T> or(Specification<T> other) {
        Specification<T> self = this;
        return candidate -> self.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    /**
     * Negates this specification.
     */
    default Specification<T> not() {
        Specification<T> self = this;
        return Specification.of("NOT(" + self + ")", candidate -> !self.isSatisfiedBy(candidate));
    }
}
