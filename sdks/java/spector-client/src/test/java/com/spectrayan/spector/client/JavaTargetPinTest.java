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
package com.spectrayan.spector.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the client SDK's bytecode level at Java 21 (class file major version 65).
 *
 * <p>The root reactor compiles at Java 25 and adds {@code jdk.incubator.vector} + {@code --enable-preview}.
 * The client SDK is a thin HTTP client used by external integrators, so it targets 21 via an explicit
 * compiler-plugin override in its POM. Without this test, a POM merge that drops the override or an
 * accidental import from an engine module would silently raise the floor to 25 — and the next consumer on
 * JDK 21 would get an {@code UnsupportedClassVersionError} rather than a compilation failure we control.</p>
 *
 * <p>The test reads the raw class file header rather than relying on reflection, because
 * {@code Class.getModule().getDescriptor()} and {@code ProcessHandle} APIs exist in JDK 9+ but do not
 * report the bytecode version of the calling class.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/986">spectrayan/spector#986</a>
 */
@DisplayName("Client SDK bytecode level is pinned at Java 21 (#986)")
class JavaTargetPinTest {

    /** Java 21 = class file major version 65. */
    private static final int JAVA_21_MAJOR = 65;

    @Test
    @DisplayName("SpectorClient.class is compiled at major version 65 (Java 21), not 69 (Java 25)")
    void clientClassFileMajorVersionIs21() throws Exception {
        String classResource = SpectorClient.class.getName().replace('.', '/') + ".class";
        try (InputStream is = SpectorClient.class.getClassLoader().getResourceAsStream(classResource)) {
            assertThat(is).as("SpectorClient.class must be loadable as a resource").isNotNull();

            DataInputStream dis = new DataInputStream(is);
            int magic = dis.readInt();
            assertThat(magic).as("class file magic number").isEqualTo(0xCAFEBABE);

            dis.readUnsignedShort(); // minor version
            int major = dis.readUnsignedShort();

            assertThat(major)
                    .as("bytecode major version — if this is 69 (Java 25), the compiler override in "
                            + "spector-client/pom.xml has been dropped or an engine type was imported")
                    .isEqualTo(JAVA_21_MAJOR);
        }
    }
}
