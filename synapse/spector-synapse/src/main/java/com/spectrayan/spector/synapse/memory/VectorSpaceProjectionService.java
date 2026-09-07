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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.events.EmbeddingProjectionTelemetry.ProjectedPoint;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * High-performance dimensionality reduction service for 3D Vector Space visualization.
 *
 * <p>Projects high-dimensional INT8/Float vector embeddings down to 3D visual coordinates
 * \((X, Y, Z)\) using Power-Iteration Principal Component Analysis (PCA). Also maintains
 * the projection basis to project arbitrary query vectors into the identical spatial frame.</p>
 */
@Service
public class VectorSpaceProjectionService {

    private static final Logger log = LoggerFactory.getLogger(VectorSpaceProjectionService.class);
    private static final float TARGET_SPREAD = 22.0f; // Visual coordinate boundary [-22, 22]

    public record ProjectionResult(
            List<ProjectedPoint> points,
            int totalCount,
            int vectorDimension,
            float[] explainedVariance
    ) {}

    // Cached projection basis for real-time query vector alignment
    private volatile float[] meanVector;
    private volatile float[][] principalComponents; // [3][dim]
    private volatile float[] coordinateScale;       // [3] scale factors

    /**
     * Projects all non-tombstoned memories from the given {@link SpectorMemory} instance to 3D.
     */
    public ProjectionResult project(SpectorMemory memory) {
        if (memory == null) {
            return new ProjectionResult(Collections.emptyList(), 0, 0, new float[]{0, 0, 0});
        }

        List<CognitiveRecord> records = memory.admin().listAll();
        if (records.isEmpty()) {
            return new ProjectionResult(Collections.emptyList(), 0, 0, new float[]{0, 0, 0});
        }

        // Group valid records by vector dimension to pick the dominant dimension
        java.util.Map<Integer, List<CognitiveRecord>> byDim = new java.util.HashMap<>();
        for (CognitiveRecord rec : records) {
            if (rec != null && !rec.isTombstoned()) {
                byte[] qv = rec.quantizedVector();
                if (qv != null && qv.length > 0) {
                    byDim.computeIfAbsent(qv.length, k -> new ArrayList<>()).add(rec);
                }
            }
        }

        if (byDim.isEmpty()) {
            // Fallback for metadata-only records
            List<ProjectedPoint> points = generateDeterministicLayout(records);
            return new ProjectionResult(points, records.size(), 0, new float[]{0.33f, 0.33f, 0.33f});
        }

        // Select the dominant dimension (dimension with most records)
        int dimension = byDim.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue(java.util.Comparator.comparingInt(List::size)))
                .map(java.util.Map.Entry::getKey)
                .orElse(0);

        List<CognitiveRecord> validRecords = byDim.getOrDefault(dimension, Collections.emptyList());
        int n = validRecords.size();
        float[][] vectors = new float[n][dimension];
        for (int i = 0; i < n; i++) {
            byte[] qv = validRecords.get(i).quantizedVector();
            int copyLen = Math.min(dimension, qv != null ? qv.length : 0);
            for (int d = 0; d < copyLen; d++) {
                vectors[i][d] = qv[d] / 127.0f;
            }
        }

        // Compute mean vector
        float[] mean = new float[dimension];
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < dimension; d++) {
                mean[d] += vectors[i][d];
            }
        }
        for (int d = 0; d < dimension; d++) {
            mean[d] /= n;
        }

        // Center vectors (X - mean)
        float[][] centered = new float[n][dimension];
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < dimension; d++) {
                centered[i][d] = vectors[i][d] - mean[d];
            }
        }

        // Compute top 3 Principal Components via Power Iteration
        float[][] components = new float[3][dimension];
        float[] variances = new float[3];
        Random rnd = new Random(42); // Deterministic seed for visual stability

        for (int k = 0; k < 3; k++) {
            float[] w = new float[dimension];
            for (int d = 0; d < dimension; d++) {
                w[d] = (float) rnd.nextGaussian();
            }
            normalize(w);

            // 15 power iterations
            for (int iter = 0; iter < 15; iter++) {
                float[] next = new float[dimension];
                for (int i = 0; i < n; i++) {
                    float dot = dotProduct(centered[i], w);
                    for (int d = 0; d < dimension; d++) {
                        next[d] += dot * centered[i][d];
                    }
                }

                // Orthogonalize against prior principal components (Gram-Schmidt)
                for (int prev = 0; prev < k; prev++) {
                    float proj = dotProduct(next, components[prev]);
                    for (int d = 0; d < dimension; d++) {
                        next[d] -= proj * components[prev][d];
                    }
                }

                float norm = (float) Math.sqrt(dotProduct(next, next));
                if (norm > 1e-7f) {
                    for (int d = 0; d < dimension; d++) {
                        w[d] = next[d] / norm;
                    }
                }
            }

            components[k] = w;

            // Estimate component variance
            float varSum = 0;
            for (int i = 0; i < n; i++) {
                float dot = dotProduct(centered[i], w);
                varSum += dot * dot;
            }
            variances[k] = varSum / n;
        }

        // Project centered vectors to 3D
        float[][] projected = new float[n][3];
        float maxAbsX = 1e-4f, maxAbsY = 1e-4f, maxAbsZ = 1e-4f;
        for (int i = 0; i < n; i++) {
            projected[i][0] = dotProduct(centered[i], components[0]);
            projected[i][1] = dotProduct(centered[i], components[1]);
            projected[i][2] = dotProduct(centered[i], components[2]);

            maxAbsX = Math.max(maxAbsX, Math.abs(projected[i][0]));
            maxAbsY = Math.max(maxAbsY, Math.abs(projected[i][1]));
            maxAbsZ = Math.max(maxAbsZ, Math.abs(projected[i][2]));
        }

        // Scale factors to fit nicely in [-TARGET_SPREAD, TARGET_SPREAD]
        float scaleX = TARGET_SPREAD / maxAbsX;
        float scaleY = TARGET_SPREAD / maxAbsY;
        float scaleZ = TARGET_SPREAD / maxAbsZ;

        // Store basis for real-time query vector projection
        this.meanVector = mean;
        this.principalComponents = components;
        this.coordinateScale = new float[]{scaleX, scaleY, scaleZ};

        // Construct DTO point list
        List<ProjectedPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CognitiveRecord rec = validRecords.get(i);
            float x = projected[i][0] * scaleX;
            float y = projected[i][1] * scaleY;
            float z = projected[i][2] * scaleZ;

            String label = rec.text() != null
                    ? (rec.text().length() > 28 ? rec.text().substring(0, 28) + "..." : rec.text())
                    : rec.id();

            points.add(new ProjectedPoint(
                    rec.id(),
                    x, y, z,
                    rec.memoryType() != null ? rec.memoryType().name() : "WORKING",
                    rec.importance(),
                    label
            ));
        }

        float totalVar = variances[0] + variances[1] + variances[2] + 1e-6f;
        float[] explained = new float[]{
                variances[0] / totalVar,
                variances[1] / totalVar,
                variances[2] / totalVar
        };

        log.debug("[VectorSpaceProjection] Projected {} records to 3D (dim={}, variance=[{:.2f}, {:.2f}, {:.2f}])",
                n, dimension, explained[0], explained[1], explained[2]);

        return new ProjectionResult(points, n, dimension, explained);
    }

    /**
     * Projects a query vector into the active 3D basis coordinate space.
     *
     * @param queryVector float embedding array (e.g. from embedder)
     * @return 3D coordinates [x, y, z] or null if basis is uninitialized
     */
    public float[] projectQuery(float[] queryVector) {
        if (queryVector == null || meanVector == null || principalComponents == null || coordinateScale == null) {
            return null;
        }

        int dim = Math.min(queryVector.length, meanVector.length);
        float[] centered = new float[dim];
        for (int d = 0; d < dim; d++) {
            centered[d] = queryVector[d] - meanVector[d];
        }

        float qx = dotProduct(centered, principalComponents[0]) * coordinateScale[0];
        float qy = dotProduct(centered, principalComponents[1]) * coordinateScale[1];
        float qz = dotProduct(centered, principalComponents[2]) * coordinateScale[2];

        return new float[]{qx, qy, qz};
    }

    private static float dotProduct(float[] a, float[] b) {
        float sum = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static void normalize(float[] v) {
        float norm = (float) Math.sqrt(dotProduct(v, v));
        if (norm > 1e-7f) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= norm;
            }
        }
    }

    private static List<ProjectedPoint> generateDeterministicLayout(List<CognitiveRecord> records) {
        List<ProjectedPoint> points = new ArrayList<>(records.size());
        for (CognitiveRecord rec : records) {
            if (rec == null || rec.isTombstoned()) continue;
            int hash = rec.id().hashCode();
            float x = ((hash & 0xFF) / 128.0f - 1.0f) * 15.0f;
            float y = (((hash >> 8) & 0xFF) / 128.0f - 1.0f) * 15.0f;
            float z = (((hash >> 16) & 0xFF) / 128.0f - 1.0f) * 15.0f;

            String label = rec.text() != null
                    ? (rec.text().length() > 28 ? rec.text().substring(0, 28) + "..." : rec.text())
                    : rec.id();

            points.add(new ProjectedPoint(
                    rec.id(), x, y, z,
                    rec.memoryType() != null ? rec.memoryType().name() : "WORKING",
                    rec.importance(), label
            ));
        }
        return points;
    }
}
