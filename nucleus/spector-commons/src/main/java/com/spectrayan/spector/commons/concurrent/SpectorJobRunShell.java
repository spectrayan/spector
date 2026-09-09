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
package com.spectrayan.spector.commons.concurrent;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.core.JobRunShell;
import org.quartz.spi.TriggerFiredBundle;

/**
 * Custom {@link JobRunShell} that inspects job metadata directly from the trigger bundle
 * without reflection, implementing {@link PlaneAware} for non-reflective plane routing.
 */
public final class SpectorJobRunShell extends JobRunShell implements PlaneAware {

    private final ThreadPlane plane;
    private final String poolName;

    public SpectorJobRunShell(Scheduler scheduler, TriggerFiredBundle bundle) {
        super(scheduler, bundle);
        ThreadPlane resolvedPlane = ThreadPlane.VIRTUAL;
        String resolvedPool = "quartz";

        if (bundle != null && bundle.getJobDetail() != null) {
            JobDetail detail = bundle.getJobDetail();
            if (detail.getJobDataMap() != null && detail.getJobDataMap().containsKey(SpectorQuartzThreadPool.JOB_DATA_PLANE)) {
                Object planeObj = detail.getJobDataMap().get(SpectorQuartzThreadPool.JOB_DATA_PLANE);
                if (planeObj instanceof ThreadPlane tp) {
                    resolvedPlane = tp;
                } else if (planeObj instanceof String str) {
                    try {
                        resolvedPlane = ThreadPlane.valueOf(str.trim());
                    } catch (IllegalArgumentException ignored) {}
                }
            } else if (detail.getJobClass() != null && detail.getJobClass().isAnnotationPresent(OnPlane.class)) {
                OnPlane annotation = detail.getJobClass().getAnnotation(OnPlane.class);
                resolvedPlane = annotation.value();
                resolvedPool = annotation.pool();
            }

            String group = detail.getKey() != null ? detail.getKey().getGroup() : null;
            if (group != null && !group.isBlank() && !group.equals(Scheduler.DEFAULT_GROUP)) {
                resolvedPool = (resolvedPlane == ThreadPlane.PLATFORM_WRITER ? "quartz-writer-" : "quartz-") + group;
            }

            if (detail.getJobDataMap() != null && detail.getJobDataMap().containsKey(SpectorQuartzThreadPool.JOB_DATA_POOL)) {
                String customPool = detail.getJobDataMap().getString(SpectorQuartzThreadPool.JOB_DATA_POOL);
                if (customPool != null && !customPool.isBlank()) {
                    resolvedPool = customPool;
                }
            }
        }

        this.plane = resolvedPlane;
        this.poolName = resolvedPool;
    }

    @Override
    public ThreadPlane plane() {
        return plane;
    }

    @Override
    public String poolName() {
        return poolName;
    }
}