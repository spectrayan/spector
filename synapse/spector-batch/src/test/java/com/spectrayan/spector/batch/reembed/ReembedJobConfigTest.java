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
package com.spectrayan.spector.batch.reembed;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

import com.spectrayan.spector.batch.SpectorMemoryResolver;

class ReembedJobConfigTest {

    @Test
    void testReembedJobConfiguration() {
        JobRepository jobRepository = mock(JobRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        SpectorMemoryResolver memoryResolver = mock(SpectorMemoryResolver.class);
        com.spectrayan.spector.memory.SpectorMemory memory = mock(com.spectrayan.spector.memory.SpectorMemory.class);
        com.spectrayan.spector.memory.SpectorMemoryAdmin admin = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        com.spectrayan.spector.memory.MemoryIndex index = mock(com.spectrayan.spector.memory.MemoryIndex.class);
        
        when(memoryResolver.resolve("default")).thenReturn(memory);
        when(memory.admin()).thenReturn(admin);
        when(admin.index()).thenReturn(index);
        when(index.orderedIds()).thenReturn(java.util.Collections.emptyList());

        ReembedJobConfig config = new ReembedJobConfig(jobRepository, transactionManager, memoryResolver);
        
        Job job = config.reembedJob();
        assertNotNull(job);
        assertNotNull(config.reembedStep("default", 100L, true));
    }
}
