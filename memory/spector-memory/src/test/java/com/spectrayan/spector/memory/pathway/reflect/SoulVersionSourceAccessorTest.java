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
package com.spectrayan.spector.memory.pathway.reflect;

import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;
import com.spectrayan.spector.memory.pathway.reflect.relay.SoulDriftRefusionRelay;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.persist.PartitionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verification suite for Phase 6 (ADR-0035 M4.5):
 * Asserts accessor semantics for mutable services (SoulVersionSource, ScalarQuantizer)
 * and verifies decoupling from RememberPathway on Reflect relays.
 */
@DisplayName("Phase 6 (ADR-0035 M4.5): SoulVersionSource & ScalarQuantizer Accessor Tests")
class SoulVersionSourceAccessorTest {

    private static final int DIMS = 16;

    @Test
    @DisplayName("M4.5.4: Mutating setSoulVersion after context construction is immediately visible to Reflect relay (accessor semantics)")
    void mutatingSoulVersionAfterContextConstructionIsImmediatelyVisibleToReflectRelay() {
        ScalarQuantizer quantizer = Mockito.mock(ScalarQuantizer.class);
        when(quantizer.decode(Mockito.any(byte[].class))).thenReturn(new float[DIMS]);

        EngramLayout layout = new EngramLayout(DIMS);
        SemanticMemory semanticMemory = new SemanticMemory(DIMS, 100);
        WorkingMemory workingMemory = new WorkingMemory(DIMS, 100);

        CognitiveMemoryRouter router = new CognitiveMemoryRouter(workingMemory, semanticMemory, null, null);
        PartitionManager partitionManager = Mockito.mock(PartitionManager.class);
        PartitionHandle handle = new PartitionHandle(0, null, router, null, false);
        when(partitionManager.snapshot()).thenReturn(List.of(handle));

        // Write a stale memory with soulVersion = 1
        EncodingHeader header = new EncodingHeader(
                System.currentTimeMillis(),
                0L,
                1.0f,
                0.4f,
                0,
                (short) 0,
                (byte) 10,
                (byte) 0,
                (byte) 5,
                1.0f,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (short) 1,
                2.5f,
                (byte) 0
        );
        byte[] quantized = new byte[layout.quantizedVecBytes()];
        semanticMemory.write(header, quantized);

        // Mutable soul version container
        var mutableVersion = new java.util.concurrent.atomic.AtomicInteger(1);
        SoulVersionSource versionSource = () -> (short) mutableVersion.get();

        // Construct PathwayContext BEFORE mutating soul version
        var ctx = DefaultPathwayContext.builder()
                .conductionId("test-soul-accessor-conduction")
                .bind(SoulVersionSource.class, versionSource)
                .bind(ScalarQuantizer.class, quantizer)
                .build();

        // ReflectSignal no longer has any RememberPathway field at all (ADR-0035 M5.3)
        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .quantizer(null)       // Retrieved from context!
                .soulDriftRefusionEnabled(true)
                .soulDriftRefusionBatchSize(10)
                .build();
        signal.bind(ctx);

        // Mutate soul version AFTER context construction
        mutableVersion.set(5);

        // Transmit through SoulDriftRefusionRelay
        SoulDriftRefusionRelay relay = new SoulDriftRefusionRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
        assertThat(signal.soulDriftedCount()).isEqualTo(1);
        assertThat(signal.soulRefusedCount()).isEqualTo(1);

        // Verify the record header was stamped with the updated soul version 5
        long offset = semanticMemory.recordOffset(0);
        short newVersion = layout.readSoulVersion(semanticMemory.segment(), offset);
        assertThat(newVersion).isEqualTo((short) 5);

        semanticMemory.close();
        workingMemory.close();
    }

    @Test
    @DisplayName("M4.5.1: RememberPathway implements SoulVersionSource and provides dynamic accessor semantics")
    void rememberPathwayImplementsSoulVersionSourceWithDynamicAccessorSemantics() {
        RememberPathway rememberPathway = Mockito.mock(RememberPathway.class);
        when(rememberPathway.currentSoulVersion()).thenReturn((short) 1);

        // Verify RememberPathway implements SoulVersionSource
        assertThat(rememberPathway).isInstanceOf(SoulVersionSource.class);

        // Bind RememberPathway as SoulVersionSource in context
        var ctx = DefaultPathwayContext.builder()
                .conductionId("remember-soul-ctx")
                .bind(SoulVersionSource.class, rememberPathway)
                .build();

        assertThat(ctx.get(SoulVersionSource.class).currentSoulVersion()).isEqualTo((short) 1);

        // Mutate RememberPathway currentSoulVersion after context construction
        when(rememberPathway.currentSoulVersion()).thenReturn((short) 9);

        // Must immediately reflect mutated version
        assertThat(ctx.get(SoulVersionSource.class).currentSoulVersion()).isEqualTo((short) 9);
    }

    @Test
    @DisplayName("M4.5.2: ScalarQuantizer bound via dynamic supplier in context reflects live updates")
    void scalarQuantizerBoundViaSupplierReflectsLiveUpdates() {
        ScalarQuantizer quantizerA = Mockito.mock(ScalarQuantizer.class);
        ScalarQuantizer quantizerB = Mockito.mock(ScalarQuantizer.class);

        AtomicReference<ScalarQuantizer> activeQuantizer = new AtomicReference<>(quantizerA);

        var ctx = DefaultPathwayContext.builder()
                .conductionId("sq-dynamic-ctx")
                .bindSupplier(ScalarQuantizer.class, activeQuantizer::get)
                .build();

        assertThat(ctx.find(ScalarQuantizer.class)).containsSame(quantizerA);

        // Swap quantizer dynamically (e.g. partition roll / updateCognitiveRouter)
        activeQuantizer.set(quantizerB);

        assertThat(ctx.find(ScalarQuantizer.class)).containsSame(quantizerB);
    }
}
