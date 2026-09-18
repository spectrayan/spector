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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.aisme.privacy.EdgeAnonymizer;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synaptic pathway relay executing edge-local text and tag PII sanitization and deterministic pseudonymization.
 *
 * <h3>Biological Analog: Prefrontal De-identification & Semantic Categorization</h3>
 * <p>Scrubs and pseudonymizes raw personal identifiers before sensory consolidation into cortical storage.</p>
 */
public final class EdgeAnonymizationRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(EdgeAnonymizationRelay.class);

    private final AismeProperties config;
    private final EdgeAnonymizer anonymizer;

    public EdgeAnonymizationRelay(AismeProperties config, EdgeAnonymizer anonymizer) {
        this.config = config;
        this.anonymizer = anonymizer;
    }

    @Override
    public String relayName() {
        return RelayNames.EDGE_ANONYMIZATION;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal == null || config == null || anonymizer == null) {
            return true;
        }

        if (config.enablePrivacy() && config.privacyAnonymizePii()) {
            // Anonymize text
            String rawText = signal.rawText();
            if (rawText != null && !rawText.isEmpty()) {
                String sanitized = anonymizer.anonymize(rawText);
                signal.sanitizedText(sanitized);
            }

            // Anonymize tags
            String[] tags = signal.tags();
            if (tags != null && tags.length > 0) {
                String[] sanitizedTags = new String[tags.length];
                for (int i = 0; i < tags.length; i++) {
                    sanitizedTags[i] = anonymizer.anonymize(tags[i]);
                }
                signal.tags(sanitizedTags);
            }

            log.debug("EdgeAnonymization: sanitized text and tags for signal id={}", signal.id());
        }

        return true;
    }
}
