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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REM Sleep Proactive Interference Relay.
 *
 * <p>Legacy fixed-stride episodic partitions have been replaced by {@code EpisodicLayout} append logs.
 * Episodic reflection and consolidation are handled via {@link EpisodicLogConsolidationRelay}.</p>
 */
public final class ProactiveInterferenceRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(ProactiveInterferenceRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        return true;
    }
}
