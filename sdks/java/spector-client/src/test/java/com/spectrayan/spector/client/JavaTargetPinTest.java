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
 * Pins the client SDK's bytecode level at Java 25 (class file major version 69).
 *
 * <p>The client SDK standardizes on Java 25 aligned with the core reactor standard.</p>
 */
@DisplayName("Client SDK bytecode level is pinned at Java 25")
class JavaTargetPinTest {

    /** Java 25 = class file major version 69. */
    private static final int JAVA_25_MAJOR = 69;

    @Test
    @DisplayName("SpectorClient.class is compiled at major version 69 (Java 25)")
    void clientClassFileMajorVersionIs25() throws Exception {
        String classResource = SpectorClient.class.getName().replace('.', '/') + ".class";
        try (InputStream is = SpectorClient.class.getClassLoader().getResourceAsStream(classResource)) {
            assertThat(is).as("SpectorClient.class must be loadable as a resource").isNotNull();

            DataInputStream dis = new DataInputStream(is);
            int magic = dis.readInt();
            assertThat(magic).as("class file magic number").isEqualTo(0xCAFEBABE);

            dis.readUnsignedShort(); // minor version
            int major = dis.readUnsignedShort();

            assertThat(major)
                    .as("bytecode major version — expected 69 (Java 25)")
                    .isEqualTo(JAVA_25_MAJOR);
        }
    }
}
