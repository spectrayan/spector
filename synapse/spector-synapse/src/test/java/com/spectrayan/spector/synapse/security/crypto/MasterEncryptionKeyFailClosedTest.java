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
package com.spectrayan.spector.synapse.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Spector fails closed at boot time when {@code SPECTOR_MASTER_ENCRYPTION_KEY}
 * is unset outside {@code dev} and {@code test} profiles (Issue #1052, ADR-0070).
 */
@DisplayName("Master Encryption Key Fail-Closed Boot Contract Tests (#1052)")
class MasterEncryptionKeyFailClosedTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AesGcmCipher.class);

    @Test
    @DisplayName("Boot fails in prod profile when master encryption key is unset")
    void bootFailsInProdProfileWithoutKey() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage(
                                    "[SPE-820-002 / SPE-SEC-002] Master encryption key is required outside dev/test profiles (set SPECTOR_MASTER_ENCRYPTION_KEY or spector.security.master-key)");
                });
    }

    @Test
    @DisplayName("Boot fails in production profile when master encryption key is empty string")
    void bootFailsInProductionProfileWithBlankKey() {
        runner.withPropertyValues(
                        "spring.profiles.active=production",
                        "spector.security.master-key=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage(
                                    "[SPE-820-002 / SPE-SEC-002] Master encryption key is required outside dev/test profiles (set SPECTOR_MASTER_ENCRYPTION_KEY or spector.security.master-key)");
                });
    }

    @Test
    @DisplayName("Boot fails under default profile (no active profiles) when master encryption key is unset")
    void bootFailsUnderDefaultProfileWithoutKey() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                            "[SPE-820-002 / SPE-SEC-002] Master encryption key is required outside dev/test profiles (set SPECTOR_MASTER_ENCRYPTION_KEY or spector.security.master-key)");
        });
    }

    @Test
    @DisplayName("Boot succeeds in dev profile without key using deterministic fallback")
    void bootSucceedsInDevProfileWithoutKey() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AesGcmCipher.class);

                    AesGcmCipher cipher = context.getBean(AesGcmCipher.class);
                    var encrypted = cipher.encrypt("dev-secret", "dev-tenant");
                    assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "dev-tenant"))
                            .isEqualTo("dev-secret");
                });
    }

    @Test
    @DisplayName("Boot succeeds in test profile without key using deterministic fallback")
    void bootSucceedsInTestProfileWithoutKey() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AesGcmCipher.class);

                    AesGcmCipher cipher = context.getBean(AesGcmCipher.class);
                    var encrypted = cipher.encrypt("test-secret", "test-tenant");
                    assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "test-tenant"))
                            .isEqualTo("test-secret");
                });
    }

    @Test
    @DisplayName("Boot succeeds in prod profile when spector.security.master-key property is provided")
    void bootSucceedsInProdProfileWithPropertyKey() {
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "spector.security.master-key=my-production-master-secret-key-32b")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AesGcmCipher.class);

                    AesGcmCipher cipher = context.getBean(AesGcmCipher.class);
                    var encrypted = cipher.encrypt("confidential-data", "tenant-100");
                    assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "tenant-100"))
                            .isEqualTo("confidential-data");
                });
    }

    @Test
    @DisplayName("Boot succeeds in prod profile when SPECTOR_MASTER_ENCRYPTION_KEY env var is provided")
    void bootSucceedsInProdProfileWithEnvVarKey() {
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "SPECTOR_MASTER_ENCRYPTION_KEY=my-env-var-production-master-key-32b")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AesGcmCipher.class);

                    AesGcmCipher cipher = context.getBean(AesGcmCipher.class);
                    var encrypted = cipher.encrypt("vault-secret", "tenant-200");
                    assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), "tenant-200"))
                            .isEqualTo("vault-secret");
                });
    }
}
