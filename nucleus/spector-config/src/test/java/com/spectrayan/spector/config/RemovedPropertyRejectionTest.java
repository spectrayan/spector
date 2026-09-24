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
package com.spectrayan.spector.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * Verifies that a configuration setting a removed property is refused at load, not ignored.
 *
 * <p>Silently ignoring a withdrawn property is worse than failing on it: the operator who set it
 * believes it took effect, and the consequence surfaces later as behaviour nothing points back at.
 * These tests pin the refusal and the content of the message, because a refusal that does not name
 * the replacement just moves the confusion.</p>
 */
@DisplayName("Removed Property Rejection")
class RemovedPropertyRejectionTest {

    @Test
    @DisplayName("setting spector.memory.dimensions fails the load")
    void removedDimensionsPropertyIsRefused() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override(SpectorPropertyConstants.REMOVED_MEMORY_DIMENSIONS, "512")
                .build();

        assertThatThrownBy(() -> SpectorConfigFactory.spectorProperties(source))
                .isInstanceOf(SpectorConfigException.class)
                .satisfies(e -> assertThat(((SpectorConfigException) e).errorCode())
                        .isEqualTo(ErrorCode.CONFIG_VALUE_INVALID));
    }

    @Test
    @DisplayName("the refusal names the removed key, the value set, and the replacement")
    void refusalMessageNamesTheReplacement() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override(SpectorPropertyConstants.REMOVED_MEMORY_DIMENSIONS, "512")
                .build();

        assertThatThrownBy(() -> SpectorConfigFactory.spectorProperties(source))
                .hasMessageContaining("spector.memory.dimensions")
                .hasMessageContaining("512")
                .hasMessageContaining(SpectorPropertyConstants.PROVIDER_EMBEDDING_DIMENSIONS)
                // The reason matters as much as the replacement: an operator who set this believed they were
                // sizing the engine independently of the embedder.
                .hasMessageContaining("single source");
    }

    @Test
    @DisplayName("a configuration that does not set it loads normally")
    void absentRemovedPropertyLoadsFine() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override(SpectorPropertyConstants.PROVIDER_EMBEDDING_DIMENSIONS, "1024")
                .build();

        assertThatCode(() -> SpectorConfigFactory.spectorProperties(source)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the engine's width is derived from the embedding property")
    void engineWidthFollowsTheEmbeddingProperty() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override(SpectorPropertyConstants.PROVIDER_EMBEDDING_DIMENSIONS, "1024")
                .build();

        SpectorProperties props = SpectorConfigFactory.spectorProperties(source);

        // Both sides of the former duplication now report the same value because there is only one.
        assertThat(props.provider().getEmbedding().getDimensions()).isEqualTo(1024);
        assertThat(props.memory().getDimensions()).isEqualTo(1024);
    }

    @Test
    @DisplayName("every removal states a reason, and any replacement it names is a real property")
    void everyRemovalIsSelfExplaining() {
        assertThat(SpectorPropertyConstants.REMOVED_PROPERTIES).isNotEmpty();
        SpectorPropertyConstants.REMOVED_PROPERTIES.forEach((removed, removal) -> {
            assertThat(removed).startsWith("spector.");
            // A refusal that cannot say why is just an obstacle.
            assertThat(removal.reason()).isNotBlank();
            if (removal.replacement() != null) {
                assertThat(removal.replacement()).startsWith("spector.").isNotEqualTo(removed);
            }
        });
    }

    @Test
    @DisplayName("a property removed with no replacement is refused, and says nothing is lost")
    void removalWithoutReplacementIsRefused() {
        SpectorConfigSource source = SpectorConfigSource.builder()
                .override(SpectorPropertyConstants.REMOVED_PROVIDER_EMBEDDING_INTRA_OP_THREADS, "4")
                .build();

        assertThatThrownBy(() -> SpectorConfigFactory.spectorProperties(source))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("intra-op-threads")
                .hasMessageContaining("no replacement")
                .hasMessageContaining("had no effect");
    }
}
