/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.migration;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Holds the advisory {@link RemembererRootLock} for the lifetime of a running Synapse, so that an
 * out-of-process {@code spectorctl migrate-namespaces} can tell a live server is serving this root
 * and refuse to relocate directories it has memory-mapped (Req R5.4).
 *
 * <p>Failure to acquire is logged, not fatal. Several Spector processes sharing one data directory is
 * a misconfiguration rather than something to crash on, and a hard failure here would turn a
 * previously working setup into a boot failure on upgrade. The migrator is the component that
 * actually refuses to proceed.</p>
 */
@Component
public class RemembererRootLockHolder {

    private static final Logger log = LoggerFactory.getLogger(RemembererRootLockHolder.class);

    private final Path remembererRoot;
    private volatile RemembererRootLock lock;

    public RemembererRootLockHolder(SynapseProperties synapseProps) {
        Objects.requireNonNull(synapseProps, "synapseProps");
        this.remembererRoot = synapseProps.remembererRoot();
    }

    @PostConstruct
    void acquire() {
        RemembererRootLock.Attempt attempt = RemembererRootLock.tryAcquire(remembererRoot);
        switch (attempt.outcome()) {
            case ACQUIRED -> {
                this.lock = attempt.lock();
                log.info("[RemembererRootLockHolder] holding rememberer root lock at {}", attempt.lock().lockFile());
            }
            case HELD_BY_OTHER_PROCESS -> log.warn(
                    "[RemembererRootLockHolder] another process already holds the rememberer root {}. "
                            + "Running two Spector instances against one data directory is unsupported; "
                            + "namespace migration will refuse to run while this is the case.", remembererRoot);
            case HELD_BY_THIS_JVM -> log.debug(
                    "[RemembererRootLockHolder] rememberer root {} already locked by this JVM", remembererRoot);
            case UNAVAILABLE -> log.warn(
                    "[RemembererRootLockHolder] could not evaluate the rememberer root lock at {}; "
                            + "migration will not be able to prove no server is running.", remembererRoot);
        }
    }

    @PreDestroy
    void release() {
        RemembererRootLock held = this.lock;
        if (held != null) {
            held.close();
            this.lock = null;
            log.info("[RemembererRootLockHolder] released rememberer root lock at {}", remembererRoot);
        }
    }

    /** @return whether this process currently holds the lock. */
    public boolean isHeld() {
        return lock != null;
    }
}
