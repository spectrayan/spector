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
package com.spectrayan.spector.kernel.storage;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit and contract tests for {@link NamespacePathResolver} (Task 2.7, Spec §4.1, Req R1, R4, R9).
 */
@DisplayName("NamespacePathResolver Specifications")
class NamespacePathResolverTest {

    private static final Path ROOT = Path.of("/data/spector/cognitive");

    @Nested
    @DisplayName("Untenanted resolution (Layout FLAT_SHA256)")
    class UntenantedTests {

        @Test
        @DisplayName("tenantId == null produces FLAT_SHA256 layout matching StoragePaths.namespaceDirSharded")
        void resolveUntenanted() {
            var placement = NamespacePathResolver.resolve(ROOT, null, "ns-project-1");

            assertThat(placement.layout()).isEqualTo(NamespacePathResolver.Layout.FLAT_SHA256);
            assertThat(placement.tenantId()).isNull();
            assertThat(placement.namespaceId()).isEqualTo("ns-project-1");
            assertThat(placement.dir()).isEqualTo(StoragePaths.namespaceDirSharded(ROOT, "ns-project-1"));
        }
    }

    @Nested
    @DisplayName("Tenanted resolution (Layout TENANT_SHA256)")
    class TenantedTests {

        @Test
        @DisplayName("tenantId != null produces TENANT_SHA256 layout matching StoragePaths.tenantRootedNamespaceDir")
        void resolveTenanted() {
            var placement = NamespacePathResolver.resolve(ROOT, "acme", "ns-project-1");

            assertThat(placement.layout()).isEqualTo(NamespacePathResolver.Layout.TENANT_SHA256);
            assertThat(placement.tenantId()).isEqualTo("acme");
            assertThat(placement.namespaceId()).isEqualTo("ns-project-1");
            assertThat(placement.dir()).isEqualTo(StoragePaths.tenantRootedNamespaceDir(ROOT, "acme", "ns-project-1"));
        }

        @Test
        @DisplayName("Tenanted hierarchy matches ADR-0033 §9.2 structure")
        void verifyPathStructure() {
            var placement = NamespacePathResolver.resolve(ROOT, "acme", "ns-project-1");
            Path relative = ROOT.relativize(placement.dir());

            // Structure: tenants/XX/YY/acme/namespaces/ZZ/WW/ns-project-1 (8 components)
            assertThat(relative.getNameCount()).isEqualTo(8);
            assertThat(relative.getName(0).toString()).isEqualTo("tenants");
            assertThat(relative.getName(1).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(2).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(3).toString()).isEqualTo("acme");
            assertThat(relative.getName(4).toString()).isEqualTo("namespaces");
            assertThat(relative.getName(5).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(6).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(7).toString()).isEqualTo("ns-project-1");
        }
    }

    @Nested
    @DisplayName("Security & Validation (Req R4)")
    class ValidationTests {

        @Test
        @DisplayName("null namespaceId is rejected")
        void nullNamespaceId() {
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, "acme", null))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        @DisplayName("blank namespaceId is rejected")
        void blankNamespaceId(String invalid) {
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, "acme", invalid))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        @DisplayName("blank tenantId is rejected")
        void blankTenantId(String invalid) {
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, invalid, "ns-1"))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier");
        }

        @Test
        @DisplayName("over-length namespaceId (> 256 chars) is rejected")
        void overLengthNamespaceId() {
            String longId = "a".repeat(257);
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, "acme", longId))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier");
        }

        @Test
        @DisplayName("over-length tenantId (> 256 chars) is rejected")
        void overLengthTenantId() {
            String longId = "a".repeat(257);
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, longId, "ns-1"))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "foo/bar", "foo\\bar", "foo..bar", "..", "../secret",
                "ns\u0000bad", "ns\u001Fbad"
        })
        @DisplayName("illegal characters in namespaceId are rejected without echoing raw value")
        void illegalCharsNamespaceId(String invalid) {
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, "acme", invalid))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier")
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(invalid));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "foo/bar", "foo\\bar", "foo..bar", "..", "../secret",
                "ten\u0000bad", "ten\u001Fbad"
        })
        @DisplayName("illegal characters in tenantId are rejected without echoing raw value")
        void illegalCharsTenantId(String invalid) {
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, invalid, "ns-1"))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier")
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(invalid));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Acme", "ACME", "Tenant-One", "tenantA"})
        @DisplayName("uppercase and mixed-case tenant IDs are rejected (APFS case-collision protection, Req R4.6)")
        void mixedCaseTenantIdRejected(String mixedCase) {
            assertThatThrownBy(() -> NamespacePathResolver.resolve(ROOT, mixedCase, "ns-1"))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID)
                    .hasMessageContaining("namespace identifier")
                    .hasMessageContaining("lowercase");
        }
    }

    @Nested
    @DisplayName("Tenant Wipe Prefix & Containment (Req R9)")
    class TenantPrefixTests {

        @Test
        @DisplayName("tenantPrefix derives root/tenants/XX/YY/tenantId without catalog")
        void tenantPrefixDerivation() {
            Path prefix = NamespacePathResolver.tenantPrefix(ROOT, "acme");

            Path relative = ROOT.relativize(prefix);
            assertThat(relative.getNameCount()).isEqualTo(4);
            assertThat(relative.getName(0).toString()).isEqualTo("tenants");
            assertThat(relative.getName(1).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(2).toString()).matches("[0-9a-f]{2}");
            assertThat(relative.getName(3).toString()).isEqualTo("acme");
        }

        @Test
        @DisplayName("All namespaces of a tenant reside within tenantPrefix (Containment)")
        void tenantNamespaceContainment() {
            Path prefix = NamespacePathResolver.tenantPrefix(ROOT, "acme");
            var ns1 = NamespacePathResolver.resolve(ROOT, "acme", "ns-jira");
            var ns2 = NamespacePathResolver.resolve(ROOT, "acme", "ns-docs");
            var otherTenantNs = NamespacePathResolver.resolve(ROOT, "globex", "ns-jira");

            assertThat(ns1.dir().startsWith(prefix)).isTrue();
            assertThat(ns2.dir().startsWith(prefix)).isTrue();
            assertThat(otherTenantNs.dir().startsWith(prefix)).isFalse();
        }

        @Test
        @DisplayName("tenantPrefix rejects invalid and mixed-case tenant IDs")
        void tenantPrefixValidation() {
            assertThatThrownBy(() -> NamespacePathResolver.tenantPrefix(ROOT, "Acme"))
                    .isInstanceOf(SpectorValidationException.class);
            assertThatThrownBy(() -> NamespacePathResolver.tenantPrefix(ROOT, "../escape"))
                    .isInstanceOf(SpectorValidationException.class);
        }
    }

    @Nested
    @DisplayName("Determinism & Injectivity")
    class MathematicalPropertiesTests {

        @Test
        @DisplayName("Resolution is deterministic across multiple calls")
        void determinism() {
            var p1 = NamespacePathResolver.resolve(ROOT, "acme", "ns-1");
            var p2 = NamespacePathResolver.resolve(ROOT, "acme", "ns-1");

            assertThat(p1).isEqualTo(p2);
            assertThat(p1.dir()).isEqualTo(p2.dir());
        }

        @Test
        @DisplayName("Distinct (tenantId, namespaceId) pairs never collide")
        void injectivity() {
            Set<Path> seen = new HashSet<>();
            String[] tenants = {"tenant-a", "tenant-b", "tenant-c", null};
            String[] namespaces = {"ns-alpha", "ns-beta", "ns-gamma", "ns-delta"};

            for (String t : tenants) {
                for (String ns : namespaces) {
                    var placement = NamespacePathResolver.resolve(ROOT, t, ns);
                    assertThat(seen.add(placement.dir()))
                            .as("Duplicate path resolved for tenant=%s, ns=%s", t, ns)
                            .isTrue();
                }
            }
        }
    }

    @Nested
    @DisplayName("Layout Enum & Metadata (Req R8.3)")
    class LayoutTests {

        @Test
        @DisplayName("Layout IDs round-trip and match specification")
        void layoutRoundTrip() {
            assertThat(NamespacePathResolver.Layout.FLAT_SHA256.id())
                    .isEqualTo("StoragePaths.namespaceDirSharded");
            assertThat(NamespacePathResolver.Layout.TENANT_SHA256.id())
                    .isEqualTo("StoragePaths.tenantRootedNamespaceDir");

            assertThat(NamespacePathResolver.Layout.fromId("StoragePaths.namespaceDirSharded"))
                    .isEqualTo(NamespacePathResolver.Layout.FLAT_SHA256);
            assertThat(NamespacePathResolver.Layout.fromId("StoragePaths.tenantRootedNamespaceDir"))
                    .isEqualTo(NamespacePathResolver.Layout.TENANT_SHA256);

            assertThatThrownBy(() -> NamespacePathResolver.Layout.fromId("unknown.layout"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown layout id");
        }
    }
}
