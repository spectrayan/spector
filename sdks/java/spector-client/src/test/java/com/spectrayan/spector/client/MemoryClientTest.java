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

import com.spectrayan.spector.client.api.MemoryClient;
import com.spectrayan.spector.client.exception.MemoryNotFoundException;
import com.spectrayan.spector.client.exception.SpectorAuthException;
import com.spectrayan.spector.client.exception.SpectorServerException;
import com.spectrayan.spector.client.exception.SpectorValidationException;
import com.spectrayan.spector.client.generated.api.MemoryApi;
import com.spectrayan.spector.client.generated.invoker.ApiException;
import com.spectrayan.spector.client.generated.model.AcceptedResponse;
import com.spectrayan.spector.client.generated.model.BrowseRequest;
import com.spectrayan.spector.client.generated.model.BrowseResult;
import com.spectrayan.spector.client.generated.model.CompactionResult;
import com.spectrayan.spector.client.generated.model.MemoryStats;
import com.spectrayan.spector.client.generated.model.MemoryStatusResponse;
import com.spectrayan.spector.client.generated.model.MemoryTableResponse;
import com.spectrayan.spector.client.generated.model.MemoryTableRow;
import com.spectrayan.spector.client.generated.model.MemoryVectorResponse;
import com.spectrayan.spector.client.generated.model.RecallRequest;
import com.spectrayan.spector.client.generated.model.RecallResult;
import com.spectrayan.spector.client.generated.model.ReinforceByIdRequest;
import com.spectrayan.spector.client.generated.model.RememberRequest;
import com.spectrayan.spector.client.generated.model.ResolveRequest;
import com.spectrayan.spector.client.generated.model.SearchRequest;
import com.spectrayan.spector.client.generated.model.SearchResult;
import com.spectrayan.spector.client.generated.model.StoreRequest;
import com.spectrayan.spector.client.generated.model.StoreResponse;
import com.spectrayan.spector.client.generated.model.SuppressRequest;
import com.spectrayan.spector.client.generated.model.UpdateMemoryRequest;
import com.spectrayan.spector.client.generated.model.VacuumRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryClientTest {

    private MemoryApi mockApi;
    private MemoryClient client;

    @BeforeEach
    void setUp() {
        mockApi = mock(MemoryApi.class);
        client = new MemoryClient(mockApi);
    }

    @Test
    @DisplayName("store() delegates to MemoryApi and returns StoreResponse")
    void storeDelegation() throws Exception {
        StoreResponse expected = new StoreResponse().id("mem-1").message("STORED");
        when(mockApi.storeMemory(any(StoreRequest.class))).thenReturn(expected);

        StoreResponse response = client.store("Knowledge content", List.of("ai", "spector"));
        assertThat(response.getId()).isEqualTo("mem-1");
        assertThat(response.getMessage()).isEqualTo("STORED");

        ArgumentCaptor<StoreRequest> captor = ArgumentCaptor.forClass(StoreRequest.class);
        verify(mockApi).storeMemory(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Knowledge content");
        assertThat(captor.getValue().getTags()).containsExactly("ai", "spector");
    }

    @Test
    @DisplayName("remember() delegates asynchronously and returns AcceptedResponse")
    void rememberDelegation() throws Exception {
        AcceptedResponse expected = new AcceptedResponse().id("mem-2").status("ACCEPTED");
        when(mockApi.rememberMemory(any(RememberRequest.class))).thenReturn(expected);

        AcceptedResponse response = client.remember("Async memory", "EPISODIC", List.of("meeting", "sync"));
        assertThat(response.getId()).isEqualTo("mem-2");

        ArgumentCaptor<RememberRequest> captor = ArgumentCaptor.forClass(RememberRequest.class);
        verify(mockApi).rememberMemory(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Async memory");
        assertThat(captor.getValue().getTier()).isEqualTo("EPISODIC");
        assertThat(captor.getValue().getTags()).isEqualTo("meeting,sync");
    }

    @Test
    @DisplayName("recall() delegates to MemoryApi with fused scoring")
    void recallDelegation() throws Exception {
        RecallResult item = new RecallResult().id("mem-3").text("Result").cognitiveScore(0.95);
        when(mockApi.recallMemories(any(RecallRequest.class))).thenReturn(List.of(item));

        List<RecallResult> results = client.recall("search query", 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("mem-3");
        assertThat(results.get(0).getCognitiveScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("search() delegates pure vector search")
    void searchDelegation() throws Exception {
        SearchResult item = new SearchResult().id("mem-4").text("Vector match").similarity(0.88);
        when(mockApi.searchMemories(any(SearchRequest.class))).thenReturn(List.of(item));

        List<SearchResult> results = client.search("vector query", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("mem-4");
    }

    @Test
    @DisplayName("get() returns record on success, throws MemoryNotFoundException on 404")
    void getByIdAndNotFound() throws Exception {
        MemoryTableRow row = new MemoryTableRow().id("mem-5").text("Found memory");
        when(mockApi.getMemoryById("mem-5")).thenReturn(row);
        when(mockApi.getMemoryById("missing-id")).thenThrow(new ApiException(404, "Not found"));

        MemoryTableRow found = client.get("mem-5");
        assertThat(found.getText()).isEqualTo("Found memory");

        assertThatThrownBy(() -> client.get("missing-id"))
                .isInstanceOf(MemoryNotFoundException.class)
                .hasMessageContaining("missing-id");
    }

    @Test
    @DisplayName("find() returns Optional.of(row) on success, Optional.empty() on 404")
    void findReturnsOptional() throws Exception {
        MemoryTableRow row = new MemoryTableRow().id("mem-6").text("Present");
        when(mockApi.getMemoryById("mem-6")).thenReturn(row);
        when(mockApi.getMemoryById("absent")).thenThrow(new ApiException(404, "Not found"));

        Optional<MemoryTableRow> present = client.find("mem-6");
        assertThat(present).isPresent();
        assertThat(present.get().getText()).isEqualTo("Present");

        Optional<MemoryTableRow> absent = client.find("absent");
        assertThat(absent).isEmpty();
    }

    @Test
    @DisplayName("update() sends update request to MemoryApi")
    void updateDelegation() throws Exception {
        when(mockApi.updateMemory(eq("mem-7"), any(UpdateMemoryRequest.class))).thenReturn("updated");

        UpdateMemoryRequest updateReq = new UpdateMemoryRequest().text("New text");
        client.update("mem-7", updateReq);

        verify(mockApi).updateMemory(eq("mem-7"), eq(updateReq));
    }

    @Test
    @DisplayName("forget() sends tombstone delete request")
    void forgetDelegation() throws Exception {
        when(mockApi.forgetMemory("mem-8")).thenReturn(Map.of("status", "forgotten", "id", "mem-8"));
        client.forget("mem-8");
        verify(mockApi).forgetMemory("mem-8");
    }

    @Test
    @DisplayName("reinforce() sends LTP reinforcement valence")
    void reinforceDelegation() throws Exception {
        when(mockApi.reinforceMemory(eq("mem-9"), any(ReinforceByIdRequest.class)))
                .thenReturn(Map.of("status", "reinforced"));

        client.reinforce("mem-9", 1);

        ArgumentCaptor<ReinforceByIdRequest> captor = ArgumentCaptor.forClass(ReinforceByIdRequest.class);
        verify(mockApi).reinforceMemory(eq("mem-9"), captor.capture());
        assertThat(captor.getValue().getValence()).isEqualTo(1);
    }

    @Test
    @DisplayName("suppress() configures SuppressRequest action suppress and reason")
    void suppressDelegation() throws Exception {
        when(mockApi.suppressMemory(eq("mem-10"), any(SuppressRequest.class)))
                .thenReturn(Map.of("status", "suppressed"));

        client.suppress("mem-10", "Outdated info");
        ArgumentCaptor<SuppressRequest> captor = ArgumentCaptor.forClass(SuppressRequest.class);
        verify(mockApi).suppressMemory(eq("mem-10"), captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("suppress");
        assertThat(captor.getValue().getReason()).isEqualTo("Outdated info");
    }

    @Test
    @DisplayName("unsuppress() configures SuppressRequest action unsuppress")
    void unsuppressDelegation() throws Exception {
        when(mockApi.suppressMemory(eq("mem-10"), any(SuppressRequest.class)))
                .thenReturn(Map.of("status", "unsuppressed"));

        client.unsuppress("mem-10");
        ArgumentCaptor<SuppressRequest> captor = ArgumentCaptor.forClass(SuppressRequest.class);
        verify(mockApi).suppressMemory(eq("mem-10"), captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("unsuppress");
    }

    @Test
    @DisplayName("resolve() configures ResolveRequest resolved=true")
    void resolveDelegation() throws Exception {
        when(mockApi.resolveMemory(eq("mem-11"), any(ResolveRequest.class)))
                .thenReturn(Map.of("status", "resolved"));

        client.resolve("mem-11");
        ArgumentCaptor<ResolveRequest> captor = ArgumentCaptor.forClass(ResolveRequest.class);
        verify(mockApi).resolveMemory(eq("mem-11"), captor.capture());
        assertThat(captor.getValue().getResolved()).isTrue();
    }

    @Test
    @DisplayName("unresolve() configures ResolveRequest resolved=false")
    void unresolveDelegation() throws Exception {
        when(mockApi.resolveMemory(eq("mem-11"), any(ResolveRequest.class)))
                .thenReturn(Map.of("status", "unresolved"));

        client.unresolve("mem-11");
        ArgumentCaptor<ResolveRequest> captor = ArgumentCaptor.forClass(ResolveRequest.class);
        verify(mockApi).resolveMemory(eq("mem-11"), captor.capture());
        assertThat(captor.getValue().getResolved()).isFalse();
    }

    @Test
    @DisplayName("status() and stats() retrieve telemetry")
    void statusAndStats() throws Exception {
        MemoryStatusResponse status = new MemoryStatusResponse().totalMemories(42);
        MemoryStats stats = new MemoryStats().totalCount(42L);

        when(mockApi.getMemoryStatus()).thenReturn(status);
        when(mockApi.getMemoryStats()).thenReturn(stats);

        assertThat(client.status().getTotalMemories()).isEqualTo(42);
        assertThat(client.stats().getTotalCount()).isEqualTo(42L);
    }

    @Test
    @DisplayName("browse() matches tags via inverted index")
    void browseDelegation() throws Exception {
        BrowseResult item = new BrowseResult().id("mem-12").text("Tag match");
        when(mockApi.browseMemories(any(BrowseRequest.class))).thenReturn(List.of(item));

        List<BrowseResult> results = client.browse(List.of("project-x"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("mem-12");
    }

    @Test
    @DisplayName("table() returns paginated records")
    void tableDelegation() throws Exception {
        MemoryTableResponse tableResp = new MemoryTableResponse().totalCount(100);
        when(mockApi.getMemoryTable(0, 50, "SEMANTIC", false)).thenReturn(tableResp);

        MemoryTableResponse result = client.table(0, 50, "SEMANTIC", false);
        assertThat(result.getTotalCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("vector() retrieves quantized embedding vector")
    void vectorDelegation() throws Exception {
        MemoryVectorResponse vec = new MemoryVectorResponse().memoryId("mem-13");
        when(mockApi.getMemoryVector("mem-13")).thenReturn(vec);

        MemoryVectorResponse result = client.vector("mem-13");
        assertThat(result.getMemoryId()).isEqualTo("mem-13");
    }

    @Test
    @DisplayName("consolidate() triggers manual consolidation")
    void consolidateDelegation() throws Exception {
        client.consolidate();
        verify(mockApi).consolidateMemories();
    }

    @Test
    @DisplayName("vacuum() triggers compaction for tier")
    void vacuumDelegation() throws Exception {
        CompactionResult comp = new CompactionResult().tombstonesRemoved(15);
        when(mockApi.vacuumMemories(any(VacuumRequest.class))).thenReturn(comp);

        CompactionResult result = client.vacuum("WORKING");
        assertThat(result.getTombstonesRemoved()).isEqualTo(15);
    }

    @Test
    @DisplayName("raw() provides direct access to underlying MemoryApi")
    void rawAccess() {
        assertThat(client.raw()).isSameAs(mockApi);
    }

    @Test
    @DisplayName("401 Unauthorized translates to SpectorAuthException")
    void translatesAuthException() throws Exception {
        when(mockApi.getMemoryStatus()).thenThrow(new ApiException(401, "Unauthorized"));
        assertThatThrownBy(() -> client.status())
                .isInstanceOf(SpectorAuthException.class)
                .hasMessageContaining("Authentication/authorization failed");
    }

    @Test
    @DisplayName("400 Bad Request translates to SpectorValidationException")
    void translatesValidationException() throws Exception {
        when(mockApi.getMemoryStatus()).thenThrow(new ApiException(400, "Bad Request"));
        assertThatThrownBy(() -> client.status())
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("Validation error");
    }

    @Test
    @DisplayName("500 Internal Error translates to SpectorServerException")
    void translatesServerException() throws Exception {
        when(mockApi.getMemoryStatus()).thenThrow(new ApiException(500, "Internal Error"));
        assertThatThrownBy(() -> client.status())
                .isInstanceOf(SpectorServerException.class)
                .hasMessageContaining("Server error");
    }
}
