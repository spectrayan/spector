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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.RememberResult;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideReport;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressReport;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain Reports ConductionOutcome Telemetry Tests")
class DomainReportsOutcomeTest {

    private final ConductionOutcome dummyOutcome = new ConductionOutcome();

    @Test
    @DisplayName("DreamReport surfaces ConductionOutcome and maintains backward compatibility")
    void testDreamReportOutcome() {
        DreamReport emptyReport = DreamReport.empty();
        assertThat(emptyReport.outcome()).isNull();

        DreamReport shortCircuited = DreamReport.empty(dummyOutcome);
        assertThat(shortCircuited.outcome()).isSameAs(dummyOutcome);

        DreamReport legacyReport = new DreamReport(1, 2, 3, 4, 5, 6, Duration.ofMillis(10), DreamMode.REM);
        assertThat(legacyReport.outcome()).isNull();

        DreamReport fullReport = new DreamReport(1, 2, 3, 4, 5, 6, Duration.ofMillis(10), DreamMode.REM, dummyOutcome);
        assertThat(fullReport.outcome()).isSameAs(dummyOutcome);
    }

    @Test
    @DisplayName("DecideReport surfaces ConductionOutcome and maintains backward compatibility")
    void testDecideReportOutcome() {
        DecideReport emptyReport = DecideReport.empty();
        assertThat(emptyReport.outcome()).isNull();

        DecideReport shortCircuited = DecideReport.empty(dummyOutcome);
        assertThat(shortCircuited.outcome()).isSameAs(dummyOutcome);

        DecideReport legacyReport = new DecideReport(PolicyDecisionReport.empty(), 15L, true);
        assertThat(legacyReport.outcome()).isNull();

        DecideReport fullReport = new DecideReport(PolicyDecisionReport.empty(), 15L, true, dummyOutcome);
        assertThat(fullReport.outcome()).isSameAs(dummyOutcome);
    }

    @Test
    @DisplayName("ExpressReport surfaces ConductionOutcome and maintains backward compatibility")
    void testExpressReportOutcome() {
        ExpressReport emptyReport = ExpressReport.empty();
        assertThat(emptyReport.outcome()).isNull();

        ExpressReport shortCircuited = ExpressReport.empty(dummyOutcome);
        assertThat(shortCircuited.outcome()).isSameAs(dummyOutcome);

        ExpressReport legacyReport = new ExpressReport(null, null, null, null, "prompt", "thought", "ssml", Duration.ofMillis(20), 4);
        assertThat(legacyReport.outcome()).isNull();

        ExpressReport fullReport = new ExpressReport(null, null, null, null, "prompt", "thought", "ssml", Duration.ofMillis(20), 4, dummyOutcome);
        assertThat(fullReport.outcome()).isSameAs(dummyOutcome);
    }

    @Test
    @DisplayName("WanderReport surfaces ConductionOutcome and maintains backward compatibility")
    void testWanderReportOutcome() {
        WanderReport emptyReport = WanderReport.empty();
        assertThat(emptyReport.outcome()).isNull();

        WanderReport shortCircuited = WanderReport.empty(dummyOutcome);
        assertThat(shortCircuited.outcome()).isSameAs(dummyOutcome);

        WanderReport legacyReport = new WanderReport(10, 2, 0.5f, true, Duration.ofMillis(50), List.of());
        assertThat(legacyReport.outcome()).isNull();

        WanderReport fullReport = new WanderReport(10, 2, 0.5f, true, Duration.ofMillis(50), List.of(), dummyOutcome);
        assertThat(fullReport.outcome()).isSameAs(dummyOutcome);
    }

    @Test
    @DisplayName("ReflectReport surfaces ConductionOutcome and maintains backward compatibility")
    void testReflectReportOutcome() {
        ReflectReport emptyReport = ReflectReport.empty();
        assertThat(emptyReport.outcome()).isNull();

        ReflectReport shortCircuited = ReflectReport.empty(dummyOutcome);
        assertThat(shortCircuited.outcome()).isSameAs(dummyOutcome);

        ReflectReport legacyReport10 = new ReflectReport(1, 2, 3, 4, Duration.ofMillis(100), null, 5, 6, 0.1f, 7);
        assertThat(legacyReport10.outcome()).isNull();

        ReflectReport legacyReport6 = new ReflectReport(1, 2, 3, 4, Duration.ofMillis(100), null);
        assertThat(legacyReport6.outcome()).isNull();

        ReflectReport fullReport = new ReflectReport(1, 2, 3, 4, Duration.ofMillis(100), null, 5, 6, 0.1f, 7, dummyOutcome);
        assertThat(fullReport.outcome()).isSameAs(dummyOutcome);
    }

    @Test
    @DisplayName("RememberResult surfaces ConductionOutcome and maintains backward compatibility")
    void testRememberResultOutcome() {
        RememberResult skippedReport = RememberResult.skipped();
        assertThat(skippedReport.outcome()).isNull();

        RememberResult shortCircuited = RememberResult.skipped(dummyOutcome);
        assertThat(shortCircuited.outcome()).isSameAs(dummyOutcome);

        RememberResult legacyResult = new RememberResult("mem-1", 10, false, MemoryType.EPISODIC, MemorySource.USER_STATED);
        assertThat(legacyResult.outcome()).isNull();

        RememberResult fullResult = new RememberResult("mem-1", 10, false, MemoryType.EPISODIC, MemorySource.USER_STATED, dummyOutcome);
        assertThat(fullResult.outcome()).isSameAs(dummyOutcome);
    }
}
