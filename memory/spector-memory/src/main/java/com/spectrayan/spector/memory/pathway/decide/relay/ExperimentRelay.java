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
package com.spectrayan.spector.memory.pathway.decide.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage relay in {@link com.spectrayan.spector.memory.pathway.decide.DecidePathway}.
 *
 * <h3>Biological Analog: Deliberate Waking Thought Experimentation (Constructive Prospection)</h3>
 * <p>Executes tight-constraint, low-temperature counterfactual simulations over candidate decision
 * policies, evaluating Expected Free Energy \(G(\pi) = \text{PragmaticRisk}(\pi) - \text{EpistemicGain}(\pi)\)
 * to rank strategic options prior to action selection.</p>
 *
 * @since 1.4.0
 */
public final class ExperimentRelay implements SynapticRelay<DecideSignal> {

    private static final Logger log = LoggerFactory.getLogger(ExperimentRelay.class);

    @Override
    public boolean transmit(final DecideSignal signal) {
        if (signal == null || signal.candidatePolicies().isEmpty()) {
            return true;
        }

        PolicyInferenceEngine engine = signal.policyInferenceEngine();
        if (engine != null) {
            PolicyDecisionReport report = engine.evaluate(signal.candidatePolicies(), signal.soulContexts());
            signal.setReport(report);

            if (log.isDebugEnabled()) {
                log.debug("ExperimentRelay: counterfactual decision evaluation complete — selected={} (precision={:.3f}, evaluated={})",
                        report.selectedPolicy() != null ? report.selectedPolicy().policyType() : "none",
                        report.precision(),
                        report.rankedPolicies().size());
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return "experiment_relay";
    }
}
