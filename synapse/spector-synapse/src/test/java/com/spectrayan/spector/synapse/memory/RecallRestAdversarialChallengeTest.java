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
package com.spectrayan.spector.synapse.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser
@DisplayName("RecallRestAdversarialChallengeTest — M3 REST Truncation Headers and Flags")
class RecallRestAdversarialChallengeTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    MemoryService memoryService;

    @MockitoBean
    FederatedRecallService federatedRecallService;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private RecallResult createRecallResult(String id, boolean truncated) {
        return new RecallResult(
                id,
                "Memory content for " + id,
                "SEMANTIC",
                0.92,
                "SEMANTIC",
                "1.0 days",
                List.of("tag1"),
                truncated
        );
    }

    @Test
    @DisplayName("Challenge 2.1: POST /recall with truncated results sets X-Recall-Truncated: true and body field")
    void recall_truncated_setsHeaderAndBody() throws Exception {
        RecallResult res = createRecallResult("mem-1", true);
        when(memoryService.recall(any(RecallRequest.class))).thenReturn(List.of(res));

        var req = new RecallRequest("test query", 5, 1, List.of(), "COGNITIVE", "LEARN", 2);

        mvc.perform(post("/api/v1/memory/recall")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Recall-Truncated", "true"))
                .andExpect(jsonPath("$[0].id", is("mem-1")))
                .andExpect(jsonPath("$[0].truncated", is(true)));
    }

    @Test
    @DisplayName("Challenge 2.2: POST /recall with non-truncated results sets X-Recall-Truncated: false and body field")
    void recall_notTruncated_setsHeaderAndBodyFalse() throws Exception {
        RecallResult res = createRecallResult("mem-2", false);
        when(memoryService.recall(any(RecallRequest.class))).thenReturn(List.of(res));

        var req = new RecallRequest("test query", 5, 1, List.of(), "COGNITIVE", "LEARN", 0);

        mvc.perform(post("/api/v1/memory/recall")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Recall-Truncated", "false"))
                .andExpect(jsonPath("$[0].id", is("mem-2")))
                .andExpect(jsonPath("$[0].truncated", is(false)));
    }

    @Test
    @DisplayName("Challenge 2.3: POST /recall/response with truncated payload sets header and response flag")
    void recallResponse_truncated_setsHeaderAndPayload() throws Exception {
        RecallResult res = createRecallResult("mem-3", true);
        var response = new RecallResponse(List.of(res), true, 2, 1);
        when(memoryService.recallWithResponse(any(RecallRequest.class))).thenReturn(response);

        var req = new RecallRequest("test query", 5, 1, List.of(), "COGNITIVE", "LEARN", 2);

        mvc.perform(post("/api/v1/memory/recall/response")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Recall-Truncated", "true"))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.results[0].truncated", is(true)));
    }

    @Test
    @DisplayName("Challenge 2.4: POST /recall/response with non-truncated payload sets header and response flag false")
    void recallResponse_notTruncated_setsHeaderAndPayloadFalse() throws Exception {
        RecallResult res = createRecallResult("mem-4", false);
        var response = new RecallResponse(List.of(res), false, 5, 1);
        when(memoryService.recallWithResponse(any(RecallRequest.class))).thenReturn(response);

        var req = new RecallRequest("test query", 5, 1, List.of(), "COGNITIVE", "LEARN", 10);

        mvc.perform(post("/api/v1/memory/recall/response")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Recall-Truncated", "false"))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.results[0].truncated", is(false)));
    }

    @Test
    @DisplayName("Challenge 2.5: Jackson serialization and deserialization of partitionVisitBudget and truncated")
    void jsonSerializationParity() throws Exception {
        // Test RecallRequest deserialization
        String reqJson = "{\"query\":\"adversarial search\",\"topK\":3,\"partitionVisitBudget\":4}";
        RecallRequest parsedReq = mapper.readValue(reqJson, RecallRequest.class);
        assertThat(parsedReq.query()).isEqualTo("adversarial search");
        assertThat(parsedReq.topK()).isEqualTo(3);
        assertThat(parsedReq.partitionVisitBudget()).isEqualTo(4);

        // Test RecallResult serialization
        RecallResult result = createRecallResult("mem-5", true);
        String resJson = mapper.writeValueAsString(result);
        assertThat(resJson).contains("\"truncated\":true");

        // Test RecallResponse serialization
        RecallResponse resp = new RecallResponse(List.of(result), true, 4, 1);
        String respJson = mapper.writeValueAsString(resp);
        assertThat(respJson).contains("\"truncated\":true");
    }

    @Test
    @DisplayName("Challenge 2.6 (Edge Case): When recall returns empty results, header is false")
    void recall_emptyResults_edgeCase() throws Exception {
        when(memoryService.recall(any(RecallRequest.class))).thenReturn(List.of());

        var req = new RecallRequest("missing term", 5, 1, List.of(), "COGNITIVE", "LEARN", 1);

        mvc.perform(post("/api/v1/memory/recall")
                        .contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Recall-Truncated", "false"));
    }
}
