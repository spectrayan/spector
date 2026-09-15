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

import com.spectrayan.spector.commons.error.SpectorException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Conduction outcome metadata tracking completion state, degraded stages, and bypassed relays.
 */
public final class ConductionOutcome {

    /**
     * Final disposition of the pathway conduction.
     */
    public enum Finish {
        /** Conduction finished all planned relays successfully. */
        COMPLETED,
        /** Conduction stopped early without failure (short-circuit or abort). */
        SHORT_CIRCUITED,
        /** Conduction encountered an unrecoverable failure. */
        FAILED
    }

    /**
     * Detailed mark describing a degraded or bypassed execution point.
     *
     * @param scope     pathway or stage scope identifier (e.g. "dream/dream_ingest")
     * @param kind      classified fault kind
     * @param message   descriptive message or error cause
     * @param errorCode error code string if applicable, or null
     */
    public record Mark(
            String scope,
            FaultKind kind,
            String message,
            String errorCode
    ) {
        public Mark {
            Objects.requireNonNull(scope, "scope cannot be null");
            Objects.requireNonNull(kind, "kind cannot be null");
            if (message == null) {
                message = "";
            }
        }
    }

    private volatile Finish finish = Finish.COMPLETED;
    private final List<Mark> degraded = new CopyOnWriteArrayList<>();
    private final List<Mark> bypassed = new CopyOnWriteArrayList<>();

    /**
     * Records a degraded stage with an exception cause.
     *
     * @param scope stage or pathway scope
     * @param kind  fault kind
     * @param cause the exception causing degradation
     */
    public void markDegraded(String scope, FaultKind kind, Throwable cause) {
        String msg = cause != null ? cause.getMessage() : "unknown";
        String code = (cause instanceof SpectorException se) ? se.errorCode().id() : null;
        degraded.add(new Mark(scope, kind, msg, code));
    }

    /**
     * Records a degraded stage with an explicit message and error code.
     *
     * @param scope     stage or pathway scope
     * @param kind      fault kind
     * @param message   error message
     * @param errorCode error code string
     */
    public void markDegraded(String scope, FaultKind kind, String message, String errorCode) {
        degraded.add(new Mark(scope, kind, message, errorCode));
    }

    /**
     * Records a bypassed stage.
     *
     * @param scope  stage or pathway scope
     * @param reason reason for bypass
     */
    public void markBypassed(String scope, String reason) {
        bypassed.add(new Mark(scope, FaultKind.CONTROL, reason, null));
    }

    /**
     * Sets the final finish state.
     *
     * @param finish finish state
     */
    public void finish(Finish finish) {
        this.finish = Objects.requireNonNull(finish, "finish cannot be null");
    }

    /**
     * Returns true if any stage was marked as degraded.
     *
     * @return true if degraded
     */
    public boolean degraded() {
        return !degraded.isEmpty();
    }

    /**
     * Returns the final finish state.
     *
     * @return finish state
     */
    public Finish finish() {
        return finish;
    }

    /**
     * Returns an immutable list of degraded marks.
     *
     * @return degraded marks
     */
    public List<Mark> degradedMarks() {
        return List.copyOf(degraded);
    }

    /**
     * Returns an immutable list of bypassed marks.
     *
     * @return bypassed marks
     */
    public List<Mark> bypassedMarks() {
        return List.copyOf(bypassed);
    }

    /**
     * Returns true if the specified scope or relay was marked as degraded.
     *
     * @param scope relay or scope identifier
     * @return true if degraded
     */
    public boolean isDegraded(String scope) {
        if (scope == null) return false;
        for (Mark m : degraded) {
            if (scope.equals(m.scope()) || m.scope().endsWith("/" + scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the specified scope or relay was marked as bypassed.
     *
     * @param scope relay or scope identifier
     * @return true if bypassed
     */
    public boolean isBypassed(String scope) {
        if (scope == null) return false;
        for (Mark m : bypassed) {
            if (scope.equals(m.scope()) || m.scope().endsWith("/" + scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Imports outcome marks from a child/nested conduction outcome, prefixing the scopes.
     *
     * @param child  child outcome to import from
     * @param prefix scope prefix (e.g. stage name)
     */
    public void importFrom(ConductionOutcome child, String prefix) {
        if (child == null) {
            return;
        }
        String pfx = (prefix != null && !prefix.isBlank()) ? prefix + "/" : "";
        for (Mark m : child.degradedMarks()) {
            degraded.add(new Mark(pfx + m.scope(), m.kind(), m.message(), m.errorCode()));
        }
        for (Mark m : child.bypassedMarks()) {
            bypassed.add(new Mark(pfx + m.scope(), m.kind(), m.message(), m.errorCode()));
        }
    }
}
