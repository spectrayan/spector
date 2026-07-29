/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.temporal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for TemporalChainMemory: linking, traversal, and persistence.
 */
class TemporalChainMemoryTest {

    @TempDir
    Path tempDir;

    private TemporalChainMemory chain;

    @BeforeEach
    void setUp() {
        chain = new TemporalChainMemory(100);
    }

    @AfterEach
    void tearDown() {
        if (chain != null) {
            chain.close();
        }
    }

    @Test
    void initialStateIsUnlinked() {
        assertThat(chain.isLinked(0)).isFalse();
        assertThat(chain.isLinked(99)).isFalse();
    }

    @Test
    void linkNodesEstablishesBidirectionalPointer() {
        chain.linkNodes(0, 1, 42, 1000);

        assertThat(chain.getNextIndex(0)).isEqualTo(1);
        assertThat(chain.getPrevIndex(1)).isEqualTo(0);
        assertThat(chain.getSessionId(0)).isEqualTo(42);
        assertThat(chain.getEpochSec(0)).isEqualTo(1000);
        assertThat(chain.getSessionId(1)).isEqualTo(42);

        assertThat(chain.isLinked(0)).isTrue();
        assertThat(chain.isLinked(1)).isTrue();
    }

    @Test
    void linkThreeNodesInSequence() {
        chain.linkNodes(0, 1, 100, 1000);
        chain.linkNodes(1, 2, 100, 1005);

        assertThat(chain.getNextIndex(0)).isEqualTo(1);
        assertThat(chain.getNextIndex(1)).isEqualTo(2);
        assertThat(chain.getPrevIndex(2)).isEqualTo(1);
        assertThat(chain.getPrevIndex(1)).isEqualTo(0);
    }

    @Test
    void boundsCheckThrowsException() {
        assertThatThrownBy(() -> chain.linkNodes(-1, 0, 1, 100))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> chain.linkNodes(0, 100, 1, 100))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void filePersistenceRoundTrip() {
        Path filePath = tempDir.resolve("temporal-chain.dat");

        try (TemporalChainMemory writer = new TemporalChainMemory(filePath, 50)) {
            writer.linkNodes(5, 10, 77, 2000);
            writer.flush();
        }

        try (TemporalChainMemory reader = new TemporalChainMemory(filePath, 50)) {
            assertThat(reader.getNextIndex(5)).isEqualTo(10);
            assertThat(reader.getPrevIndex(10)).isEqualTo(5);
            assertThat(reader.getSessionId(5)).isEqualTo(77);
            assertThat(reader.getEpochSec(5)).isEqualTo(2000);
        }
    }
}
