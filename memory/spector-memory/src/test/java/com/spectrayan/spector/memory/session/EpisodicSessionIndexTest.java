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
package com.spectrayan.spector.memory.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EpisodicSessionIndex Tests")
class EpisodicSessionIndexTest {

    private EpisodicSessionIndex index;
    private static final long SESSION_1 = 111L;
    private static final long SESSION_2 = 222L;

    @BeforeEach
    void setUp() {
        index = new EpisodicSessionIndex();
    }

    @Test
    @DisplayName("Should append turn and get session turns correctly")
    void shouldAppendAndGetTurns_whenTurnsAdded() {
        index.appendTurn(SESSION_1, 100L);
        index.appendTurn(SESSION_1, 200L);

        List<Long> turns = index.getSessionTurns(SESSION_1);
        assertEquals(2, turns.size());
        assertEquals(100L, turns.get(0));
        assertEquals(200L, turns.get(1));
    }

    @Test
    @DisplayName("Should paginate correctly with various offsets and limits")
    void shouldPaginate_whenGivenOffsetsAndLimits() {
        for (long i = 0; i < 10; i++) {
            index.appendTurn(SESSION_1, i * 10);
        }

        List<Long> page1 = index.paginate(SESSION_1, 0, 3);
        assertEquals(List.of(0L, 10L, 20L), page1);

        List<Long> page2 = index.paginate(SESSION_1, 3, 4);
        assertEquals(List.of(30L, 40L, 50L, 60L), page2);

        List<Long> page3 = index.paginate(SESSION_1, 8, 5);
        assertEquals(List.of(80L, 90L), page3); // Limit goes past end
        
        List<Long> page4 = index.paginate(SESSION_1, 10, 5);
        assertTrue(page4.isEmpty()); // Offset beyond size

        List<Long> page5 = index.paginate(SESSION_1, 0, 0);
        assertTrue(page5.isEmpty()); // Limit of 0
    }

    @Test
    @DisplayName("Should tail turns and return correct last N")
    void shouldTailTurns_whenCountRequested() {
        for (long i = 0; i < 5; i++) {
            index.appendTurn(SESSION_1, i * 10);
        }

        assertEquals(List.of(30L, 40L), index.tailTurns(SESSION_1, 2));
        assertEquals(List.of(0L, 10L, 20L, 30L, 40L), index.tailTurns(SESSION_1, 10)); // Request more than exists
        assertTrue(index.tailTurns(SESSION_1, 0).isEmpty());
        assertTrue(index.tailTurns(SESSION_2, 5).isEmpty()); // Unknown session
    }

    @Test
    @DisplayName("Should return correct turn count, session count, and total count")
    void shouldReturnCorrectCounts_whenPopulated() {
        index.appendTurn(SESSION_1, 10L);
        index.appendTurn(SESSION_1, 20L);
        index.appendTurn(SESSION_2, 30L);

        assertEquals(2, index.turnCount(SESSION_1));
        assertEquals(1, index.turnCount(SESSION_2));
        assertEquals(0, index.turnCount(999L));

        assertEquals(2, index.sessionCount());
        assertEquals(3, index.totalTurnCount());
    }

    @Test
    @DisplayName("Should return all session IDs when listSessions is called")
    void shouldListSessions_whenSessionsExist() {
        index.appendTurn(SESSION_1, 10L);
        index.appendTurn(SESSION_2, 20L);

        Set<Long> sessions = index.listSessions();
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains(SESSION_1));
        assertTrue(sessions.contains(SESSION_2));
    }

    @Test
    @DisplayName("Should clear session correctly when removeSession is called")
    void shouldRemoveSession_whenRequested() {
        index.appendTurn(SESSION_1, 10L);
        index.appendTurn(SESSION_2, 20L);

        List<Long> removed = index.removeSession(SESSION_1);
        assertEquals(List.of(10L, 20L), removed);
        assertEquals(0, index.turnCount(SESSION_1));
        assertEquals(1, index.sessionCount());
        
        assertNull(index.removeSession(999L));
    }

    @Test
    @DisplayName("Should clear everything when clear is called")
    void shouldClearEverything_whenClearIsCalled() {
        index.appendTurn(SESSION_1, 10L);
        index.appendTurn(SESSION_2, 20L);

        index.clear();

        assertEquals(0, index.sessionCount());
        assertEquals(0, index.totalTurnCount());
        assertTrue(index.listSessions().isEmpty());
    }

    @Test
    @DisplayName("Should handle interleaved turns from multiple sessions correctly")
    void shouldHandleInterleavedTurns_whenMultipleSessions() {
        index.appendTurn(SESSION_1, 100L);
        index.appendTurn(SESSION_2, 150L);
        index.appendTurn(SESSION_1, 200L);
        index.appendTurn(SESSION_2, 250L);

        assertEquals(List.of(100L, 200L), index.getSessionTurns(SESSION_1));
        assertEquals(List.of(150L, 250L), index.getSessionTurns(SESSION_2));
    }

    @Test
    @DisplayName("Should handle edge cases correctly")
    void shouldHandleEdgeCases_properly() {
        assertTrue(index.getSessionTurns(SESSION_1).isEmpty());
        assertTrue(index.paginate(SESSION_1, 0, 10).isEmpty());
        assertTrue(index.tailTurns(SESSION_1, 10).isEmpty());
        assertEquals(0, index.turnCount(SESSION_1));
    }
}
