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

import com.spectrayan.spector.commons.error.ErrorCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Conduction scope tracking the execution stack of cognitive pathways during a single root conduction.
 *
 * <p>A single {@code ConductionScope} instance is created per root conduction and shared by reference
 * across all nested pathway invocations. It enforces thread confinement and detects recursion cycles.</p>
 */
public final class ConductionScope {

    /**
     * A frame in the conduction stack representing an active pathway or segment.
     */
    public static final class Frame {
        private final String pathwayName;
        private final String segment;
        private boolean shortCircuited;

        public Frame(final String pathwayName, final String segment) {
            this.pathwayName = Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
            this.segment = segment != null ? segment : pathwayName;
            this.shortCircuited = false;
        }

        public String pathwayName() {
            return pathwayName;
        }

        public String segment() {
            return segment;
        }

        public boolean isShortCircuited() {
            return shortCircuited;
        }

        public void markShortCircuited() {
            this.shortCircuited = true;
        }

        @Override
        public String toString() {
            return pathwayName + (segment != null && !segment.equals(pathwayName) ? "(" + segment + ")" : "");
        }
    }

    private final Thread ownerThread;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<String> pendingSegments = new ArrayDeque<>();

    public ConductionScope() {
        this(Thread.currentThread());
    }

    public ConductionScope(final Thread ownerThread) {
        this.ownerThread = Objects.requireNonNull(ownerThread, "ownerThread cannot be null");
    }

    private void checkThreadConfinement() {
        final Thread current = Thread.currentThread();
        if (current != ownerThread) {
            throw new IllegalStateException("ConductionScope is thread-confined to "
                    + ownerThread.getName() + " [id=" + ownerThread.threadId() + "] but accessed by "
                    + current.getName() + " [id=" + current.threadId() + "]");
        }
    }

    /**
     * Asserts that the specified pathway is not already active on the conduction stack.
     *
     * @param pathwayName the name of the pathway to check
     * @throws CognitivePathwayException with {@link ErrorCode#PATHWAY_CYCLE} and {@link FaultKind#CONTRACT} if cycle is detected
     */
    public void assertNotOnStack(final String pathwayName) {
        checkThreadConfinement();
        Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
        for (final Frame frame : frames) {
            if (pathwayName.equals(frame.pathwayName())) {
                throw new CognitivePathwayException(
                        ErrorCode.PATHWAY_CYCLE,
                        pathwayName,
                        "<cycle>",
                        FaultKind.CONTRACT,
                        false,
                        new IllegalStateException("Pathway cycle detected: '" + pathwayName
                                + "' is already executing on conduction stack: " + stackSummary()));
            }
        }
    }

    /**
     * Pushes a pending segment to be associated with the next {@link #enter(String)} call.
     *
     * @param segment segment name
     */
    public void pushSegment(final String segment) {
        checkThreadConfinement();
        if (segment != null && !segment.isBlank()) {
            pendingSegments.push(segment);
        }
    }

    /**
     * Enters a pathway, pushing a new frame onto the conduction stack.
     *
     * @param pathwayName the pathway name
     */
    public void enter(final String pathwayName) {
        checkThreadConfinement();
        Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
        final String segment = !pendingSegments.isEmpty() ? pendingSegments.pop() : pathwayName;
        frames.push(new Frame(pathwayName, segment));
    }

    /**
     * Leaves a pathway, popping its frame from the conduction stack.
     *
     * @param pathwayName the pathway name
     */
    public void leave(final String pathwayName) {
        checkThreadConfinement();
        Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
        if (frames.isEmpty()) {
            throw new IllegalStateException("Cannot leave pathway '" + pathwayName + "': stack is empty");
        }
        final Frame top = frames.pop();
        if (!pathwayName.equals(top.pathwayName())) {
            throw new IllegalStateException("Stack mismatch: expected to leave '" + top.pathwayName()
                    + "' but got '" + pathwayName + "'");
        }
    }

    /**
     * Returns the name of the currently active pathway (innermost frame), or "root" if empty.
     *
     * @return current pathway name
     */
    public String pathwayName() {
        checkThreadConfinement();
        return !frames.isEmpty() ? frames.peek().pathwayName() : "root";
    }

    /**
     * Returns the current full segment path (e.g. "dream/dream_ingest").
     *
     * @return segment path
     */
    public String segment() {
        checkThreadConfinement();
        if (frames.isEmpty()) {
            return "root";
        }
        final List<String> segments = new ArrayList<>();
        for (final Frame frame : frames) {
            segments.add(0, frame.segment());
        }
        return String.join("/", segments);
    }

    /**
     * Marks the innermost frame for the specified pathway as short-circuited.
     *
     * @param pathwayName pathway name
     */
    public void markShortCircuited(final String pathwayName) {
        checkThreadConfinement();
        Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
        for (final Frame frame : frames) {
            if (frame.pathwayName().equals(pathwayName)) {
                frame.markShortCircuited();
                return;
            }
        }
        if (!frames.isEmpty()) {
            frames.peek().markShortCircuited();
        }
    }

    /**
     * Returns whether the specified pathway short-circuited.
     *
     * @param pathwayName pathway name
     * @return true if short-circuited
     */
    public boolean shortCircuited(final String pathwayName) {
        checkThreadConfinement();
        Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
        for (final Frame frame : frames) {
            if (frame.pathwayName().equals(pathwayName)) {
                return frame.isShortCircuited();
            }
        }
        return false;
    }

    /**
     * Returns a human-readable summary of the current stack.
     *
     * @return stack summary string
     */
    public String stackSummary() {
        checkThreadConfinement();
        final List<String> list = new ArrayList<>();
        for (final Frame frame : frames) {
            list.add(0, frame.toString());
        }
        return "[" + String.join(" -> ", list) + "]";
    }

    /**
     * Returns the owner thread of this conduction scope.
     *
     * @return owner thread
     */
    public Thread ownerThread() {
        return ownerThread;
    }

    /**
     * Returns the current depth of the frame stack.
     *
     * @return frame depth
     */
    public int depth() {
        checkThreadConfinement();
        return frames.size();
    }
}
