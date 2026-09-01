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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;

public final class PolicyInferenceRelay implements SynapticRelay<DecideSignal> {
    @Override
    public boolean transmit(DecideSignal signal) {
        if (signal == null || signal.policyInferenceEngine() == null) return false;
        var report = signal.policyInferenceEngine().evaluate(signal.candidatePolicies(), signal.soulContexts());
        signal.setReport(report);
        return report.selectedPolicy() != null;
    }
    
    @Override
    public String relayName() { 
        return "policy_inference"; 
    }
}
