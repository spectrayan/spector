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
package com.spectrayan.spector.synapse.replication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReplicationFrame JSON serialization (G58) and wire framing.
 */
@DisplayName("ReplicationFrame Protocol & JSON Tests")
class ReplicationFrameTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("G58: ack() properly escapes quotes, backslashes, and control characters in JSON")
    void testAckJsonEscaping() throws IOException {
        String trickyTenant = "tenant-\"alpha\"\\test";
        String trickyNamespace = "ns/with/\"quotes\"\\and\\slashes\nnewline";
        String trickyMessage = "Message with \"quotes\" and \\escapes\\";

        ReplicationFrame frame = ReplicationFrame.ack(
                trickyTenant,
                trickyNamespace,
                100L,
                "SUCCESS",
                trickyMessage
        );

        assertThat(frame.type()).isEqualTo(ReplicationFrame.TYPE_ACK);

        JsonNode root = mapper.readTree(frame.payload());
        assertThat(root.get("tenantId").asText()).isEqualTo(trickyTenant);
        assertThat(root.get("namespaceId").asText()).isEqualTo(trickyNamespace);
        assertThat(root.get("appliedHwm").asLong()).isEqualTo(100L);
        assertThat(root.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(root.get("message").asText()).isEqualTo(trickyMessage);
    }

    @Test
    @DisplayName("G58: error() properly escapes quotes and backslashes in sanitized JSON")
    void testErrorJsonEscaping() throws IOException {
        String trickyError = "Error with \"quotes\" and \\backslashes\\";
        ReplicationFrame frame = ReplicationFrame.error(trickyError);

        assertThat(frame.type()).isEqualTo(ReplicationFrame.TYPE_ERROR);

        JsonNode root = mapper.readTree(frame.payload());
        assertThat(root.get("error").asText()).isEqualTo(trickyError);
    }

    @Test
    @DisplayName("writeTo and readFrom round-trip preserves frame type and payload")
    void testWireRoundTrip() throws IOException {
        byte[] payload = "test-payload-bytes".getBytes();
        ReplicationFrame original = new ReplicationFrame(ReplicationFrame.TYPE_WAL_TAIL, payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeTo(out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ReplicationFrame decoded = ReplicationFrame.readFrom(in);

        assertThat(decoded.type()).isEqualTo(original.type());
        assertThat(decoded.payload()).isEqualTo(original.payload());
    }
}
