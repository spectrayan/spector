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
package com.spectrayan.spector.synapse.admin.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the append-only audit log and SIEM export pipeline
 * ({@code spector.audit.*}, ADR-0087, Issue #1059).
 */
@Component
@ConfigurationProperties(prefix = "spector.audit")
public class AuditProperties {

    /**
     * Retention window in days for audit events. Defaults to 365 days (1 year).
     * When {@code <= 0}, audit events are retained indefinitely.
     */
    private int retentionDays = 365;

    /**
     * Whether recorded audit events are exported in real time onto the platform EventBus / SSE stream
     * for SIEM ingestion. Defaults to {@code true}.
     */
    private boolean siemExportEnabled = true;

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isSiemExportEnabled() {
        return siemExportEnabled;
    }

    public void setSiemExportEnabled(boolean siemExportEnabled) {
        this.siemExportEnabled = siemExportEnabled;
    }
}
