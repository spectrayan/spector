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
package com.spectrayan.spector.test.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SealRulesTest {

    @Test
    @DisplayName("importedOrFail MUST fail when pointed at a non-existent or empty package (guards against #734)")
    void importedOrFailFailsOnEmptyPackage() {
        assertThatThrownBy(() -> SealRules.importedOrFail("com.spectrayan.spector.nonexistent.empty.pkg", 0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("a zero import passes every noClasses() rule vacuously and is the exact failure recorded in #734");
    }

    @Test
    @DisplayName("importedOrFail succeeds and returns classes when importing populated package")
    void importedOrFailSucceedsOnPopulatedPackage() {
        JavaClasses classes = SealRules.importedOrFail("com.spectrayan.spector.test.arch", 0);
        assertThat(classes).isNotEmpty();
        assertThat(classes.contain(SealRules.class)).isTrue();
    }
}
