/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.scheduler.TaskRunAuditRecord;
import com.spectrayan.spector.memory.scheduler.TaskStatus;
import com.spectrayan.spector.synapse.memory.MemoryService;
import com.spectrayan.spector.synapse.scheduler.TaskDto.RescheduleCronRequest;
import com.spectrayan.spector.synapse.scheduler.TaskDto.RescheduleIntervalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
@DisplayName("TaskManagementController — MVC Slice Tests")
class TaskManagementControllerTest {

    @Autowired WebApplicationContext wac;
    @Autowired ObjectMapper mapper;
    @MockitoBean MemoryService memoryService;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("GET /api/v1/tasks returns list of tasks")
    void testListTasks() throws Exception {
        TaskStatus status1 = new TaskStatus("sleep-consolidation", "default", "NORMAL", null, new Date(), "Sleep consolidation");
        when(memoryService.listTasks()).thenReturn(List.of(status1));

        mvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("sleep-consolidation")))
                .andExpect(jsonPath("$[0].state", is("NORMAL")));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} returns 200 when found and 404 when absent")
    void testGetTask() throws Exception {
        TaskStatus status1 = new TaskStatus("rem-dreaming", "default", "NORMAL", null, new Date(), "REM dreaming");
        when(memoryService.getTask("rem-dreaming")).thenReturn(Optional.of(status1));
        when(memoryService.getTask("unknown-task")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/tasks/rem-dreaming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("rem-dreaming")));

        mvc.perform(get("/api/v1/tasks/unknown-task"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/trigger delegates to memoryService")
    void testTriggerTask() throws Exception {
        mvc.perform(post("/api/v1/tasks/sleep-consolidation/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("TRIGGERED")))
                .andExpect(jsonPath("$.taskId", is("sleep-consolidation")));

        verify(memoryService).triggerTask("sleep-consolidation");
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/pause and resume delegate to memoryService")
    void testPauseAndResume() throws Exception {
        mvc.perform(post("/api/v1/tasks/sleep-consolidation/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAUSED")));

        verify(memoryService).pauseTask("sleep-consolidation");

        mvc.perform(post("/api/v1/tasks/sleep-consolidation/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESUMED")));

        verify(memoryService).resumeTask("sleep-consolidation");
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/reschedule-interval validates input and delegates")
    void testRescheduleInterval() throws Exception {
        RescheduleIntervalRequest req = new RescheduleIntervalRequest(120);

        mvc.perform(post("/api/v1/tasks/sleep-consolidation/reschedule-interval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESCHEDULED")));

        verify(memoryService).rescheduleTaskInterval("sleep-consolidation", Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/reschedule-cron validates input and delegates")
    void testRescheduleCron() throws Exception {
        RescheduleCronRequest req = new RescheduleCronRequest("0 0/10 * * * ?");

        mvc.perform(post("/api/v1/tasks/sleep-consolidation/reschedule-cron")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESCHEDULED")));

        verify(memoryService).rescheduleTaskCron("sleep-consolidation", "0 0/10 * * * ?");
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id}/audit and GET /api/v1/tasks/audit return audit history")
    void testAuditEndpoints() throws Exception {
        Instant now = Instant.now();
        TaskRunAuditRecord record = new TaskRunAuditRecord(
                "run-1", "sleep-consolidation", "default", now, now.plusMillis(50),
                Duration.ofMillis(50), "SUCCESS", null, null
        );

        when(memoryService.getTaskAuditHistory("sleep-consolidation", 20)).thenReturn(List.of(record));
        when(memoryService.getRecentAuditHistory(50)).thenReturn(List.of(record));

        mvc.perform(get("/api/v1/tasks/sleep-consolidation/audit?limit=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskId", is("sleep-consolidation")))
                .andExpect(jsonPath("$[0].status", is("SUCCESS")));

        mvc.perform(get("/api/v1/tasks/audit?limit=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskId", is("sleep-consolidation")));
    }
}
