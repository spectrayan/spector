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

import com.spectrayan.spector.commons.concurrent.spi.SpectorExecutorProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Supervises long-running background daemon loops with restart policies,
 * failure backoff, and watchdog health tracking.
 *
 * <p>Adheres to ADR-0026 Model B: daemons are scheduled onto host-injected executors
 * for the declared {@link ThreadPlane} rather than creating raw threads directly.</p>
 *
 * <h3>Key Capabilities</h3>
 * <ul>
 *   <li><b>Supervised Execution:</b> Each daemon runs on a designated executor plane with cycle interval timing.</li>
 *   <li><b>Restart on Failure:</b> Automatic recovery per {@link DaemonPolicy#maxRestarts()} and backoff.</li>
 *   <li><b>Watchdog:</b> Warns when a cycle exceeds {@link DaemonPolicy#watchdogTimeout()}.</li>
 *   <li><b>Status Visibility:</b> Live {@link DaemonStatus} snapshots for health endpoints and telemetry.</li>
 *   <li><b>Coordinated Stop:</b> Graceful interruption and cycle completion join on shutdown.</li>
 * </ul>
 *
 * @see DaemonPolicy
 * @see DaemonStatus
 * @see SpectorExecutors
 */
public final class DaemonSupervisor implements AutoCloseable {

    private static final System.Logger log = System.getLogger(DaemonSupervisor.class.getName());

    private final String prefix;
    private final SpectorExecutorProvider executorProvider;
    private final CopyOnWriteArrayList<ManagedDaemon> daemons = new CopyOnWriteArrayList<>();
    private volatile boolean closed = false;

    /**
     * Creates a supervisor with a name prefix defaulting to {@link SpectorExecutors#current()}.
     *
     * @param prefix thread/pool name prefix (e.g., "memory" → "spector-daemon-memory-checkpoint")
     */
    public DaemonSupervisor(String prefix) {
        this(prefix, null);
    }

    /**
     * Creates a supervisor with a name prefix and explicit {@link SpectorExecutorProvider}.
     *
     * @param prefix           thread/pool name prefix
     * @param executorProvider provider for resolving executors (nullable, falls back to locator)
     */
    public DaemonSupervisor(String prefix, SpectorExecutorProvider executorProvider) {
        this.prefix = prefix != null ? prefix : "default";
        this.executorProvider = executorProvider;
    }

    private SpectorExecutorProvider activeProvider() {
        return executorProvider != null ? executorProvider : SpectorExecutors.current();
    }

    /**
     * Registers and starts a periodic daemon defaulting to {@link ThreadPlane#PLATFORM_WRITER}.
     *
     * @param name     daemon name
     * @param task     the work to execute each cycle
     * @param interval sleep duration between cycles
     * @param policy   restart and watchdog policy
     */
    public void schedule(String name, Runnable task, Duration interval, DaemonPolicy policy) {
        schedule(name, task, interval, policy, ThreadPlane.PLATFORM_WRITER);
    }

    /**
     * Registers and starts a periodic daemon on the declared {@link ThreadPlane}.
     *
     * @param name     daemon name (must be unique within this supervisor)
     * @param task     the work to execute each cycle
     * @param interval sleep duration between cycles
     * @param policy   restart and watchdog policy
     * @param plane    target thread execution plane
     * @throws IllegalStateException if the supervisor is closed
     * @throws IllegalArgumentException if a daemon with this name already exists
     */
    public void schedule(String name, Runnable task, Duration interval, DaemonPolicy policy, ThreadPlane plane) {
        if (closed) throw new IllegalStateException("Supervisor is closed");
        for (ManagedDaemon d : daemons) {
            if (d.name.equals(name)) {
                throw new IllegalArgumentException("Daemon '" + name + "' already registered");
            }
        }

        ManagedDaemon daemon = new ManagedDaemon(name, task, interval, policy, plane != null ? plane : ThreadPlane.PLATFORM_WRITER);
        daemons.add(daemon);
        daemon.start();
    }

    /**
     * Returns live status snapshots of all managed daemons.
     *
     * @return unmodifiable list of daemon statuses
     */
    public List<DaemonStatus> status() {
        return daemons.stream()
                .map(ManagedDaemon::snapshot)
                .toList();
    }

    /**
     * Returns the status of a specific daemon by name.
     *
     * @param name the daemon name
     * @return the status, or null if not found
     */
    public DaemonStatus status(String name) {
        for (ManagedDaemon d : daemons) {
            if (d.name.equals(name)) return d.snapshot();
        }
        return null;
    }

    /**
     * Returns the number of registered daemons.
     */
    public int size() {
        return daemons.size();
    }

    /**
     * Shuts down all managed daemons: signals stop, interrupts sleep, and waits for current cycles.
     */
    @Override
    public void close() {
        closed = true;
        for (ManagedDaemon d : daemons) {
            d.stop();
        }
        for (ManagedDaemon d : daemons) {
            d.join(5000);
        }
        daemons.clear();
        log.log(System.Logger.Level.INFO, "DaemonSupervisor[{0}] closed", prefix);
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal managed daemon instance
    // ─────────────────────────────────────────────────────────────────

    private final class ManagedDaemon {

        final String name;
        final Runnable task;
        final Duration interval;
        final DaemonPolicy policy;
        final ThreadPlane plane;

        // ── Mutable state (accessed from daemon thread + status() calls) ──
        volatile DaemonStatus.State state = DaemonStatus.State.IDLE;
        volatile int restartCount = 0;
        volatile Instant lastCycleStart;
        volatile Duration lastCycleDuration;
        volatile Throwable lastError;
        volatile Thread thread;
        volatile boolean running = true;

        ManagedDaemon(String name, Runnable task, Duration interval, DaemonPolicy policy, ThreadPlane plane) {
            this.name = name;
            this.task = task;
            this.interval = interval;
            this.policy = policy;
            this.plane = plane;
        }

        void start() {
            String poolName = "daemon-" + prefix + "-" + name;
            Executor executor = activeProvider().executor(plane, poolName);
            executor.execute(this::loop);

            log.log(System.Logger.Level.INFO,
                    "Daemon[{0}] scheduled on plane [{1}]: interval={2}s, maxRestarts={3}, watchdog={4}s",
                    poolName, plane, interval.toSeconds(), policy.maxRestarts(),
                    policy.watchdogTimeout().toSeconds());
        }

        void stop() {
            running = false;
            state = DaemonStatus.State.STOPPED;
            Thread t = thread;
            if (t != null) {
                t.interrupt();
            }
        }

        void join(long timeoutMs) {
            Thread t = thread;
            if (t != null) {
                try {
                    t.join(timeoutMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        DaemonStatus snapshot() {
            return new DaemonStatus(name, state, restartCount,
                    lastCycleStart, lastCycleDuration, lastError);
        }

        /**
         * Main supervised loop: sleep → execute → watchdog check → restart on failure.
         */
        private void loop() {
            thread = Thread.currentThread();
            while (running) {
                // ── Sleep phase ──
                state = DaemonStatus.State.SLEEPING;
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!running) break;

                // ── Execute phase ──
                state = DaemonStatus.State.RUNNING;
                lastCycleStart = Instant.now();

                try {
                    task.run();

                    // Successful cycle — reset restart counter
                    lastCycleDuration = Duration.between(lastCycleStart, Instant.now());
                    restartCount = 0;
                    lastError = null;

                    // Watchdog: warn if cycle took too long
                    if (lastCycleDuration.compareTo(policy.watchdogTimeout()) > 0) {
                        log.log(System.Logger.Level.WARNING,
                                "Daemon[{0}] cycle exceeded watchdog: {1}ms > {2}ms",
                                name, lastCycleDuration.toMillis(),
                                policy.watchdogTimeout().toMillis());
                    }

                } catch (Exception e) {
                    lastError = e;
                    lastCycleDuration = Duration.between(lastCycleStart, Instant.now());
                    log.log(System.Logger.Level.ERROR,
                            "Daemon[{0}] cycle failed (attempt {1}/{2}): {3}",
                            name, restartCount + 1, policy.maxRestarts(), e.getMessage());

                    if (!handleFailure()) break;

                } catch (Error e) {
                    lastError = e;
                    lastCycleDuration = Duration.between(lastCycleStart, Instant.now());
                    log.log(System.Logger.Level.ERROR,
                            "Daemon[{0}] hit Error: {1}", name, e.getMessage());

                    if (policy.restartOnError()) {
                        if (!handleFailure()) break;
                    } else {
                        state = DaemonStatus.State.DEAD;
                        log.log(System.Logger.Level.ERROR,
                                "Daemon[{0}] DEAD — Error not restartable per policy", name);
                        break;
                    }
                }
            }

            if (state != DaemonStatus.State.DEAD) {
                state = DaemonStatus.State.STOPPED;
            }
            log.log(System.Logger.Level.INFO, "Daemon[{0}] exited: state={1}", name, state);
        }

        private boolean handleFailure() {
            restartCount++;
            if (restartCount > policy.maxRestarts()) {
                state = DaemonStatus.State.DEAD;
                log.log(System.Logger.Level.ERROR,
                        "Daemon[{0}] DEAD — exceeded max restarts ({1})", name, policy.maxRestarts());
                return false;
            }

            state = DaemonStatus.State.RESTARTING;
            long backoffMs = policy.backoffFor(restartCount).toMillis();
            log.log(System.Logger.Level.INFO,
                    "Daemon[{0}] restarting in {1}ms (attempt {2}/{3})",
                    name, backoffMs, restartCount, policy.maxRestarts());
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                state = DaemonStatus.State.STOPPED;
                return false;
            }
            return running;
        }
    }
}
