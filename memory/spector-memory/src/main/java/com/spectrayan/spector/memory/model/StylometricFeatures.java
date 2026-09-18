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
package com.spectrayan.spector.memory.model;

public record StylometricFeatures(
        float meanSentenceLength,
        float sentenceLengthVariance,
        float typeTokenRatio,
        float clauseComplexity,
        float commaRate,
        float dashRate,
        float ellipsisRate,
        float exclamationRate,
        float questionRate,
        float formalityScore
) {
    public static final StylometricFeatures NEUTRAL = new StylometricFeatures(
            15.0f, 5.0f, 0.70f, 1.0f, 0.05f, 0.01f, 0.01f, 0.01f, 0.05f, 0.50f
    );
    
    public StylometricFeatures {
        meanSentenceLength = Float.isFinite(meanSentenceLength) ? Math.max(0, meanSentenceLength) : 15.0f;
        sentenceLengthVariance = Float.isFinite(sentenceLengthVariance) ? Math.max(0, sentenceLengthVariance) : 5.0f;
        typeTokenRatio = Float.isFinite(typeTokenRatio) ? Math.clamp(typeTokenRatio, 0.0f, 1.0f) : 0.70f;
        clauseComplexity = Float.isFinite(clauseComplexity) ? Math.max(0, clauseComplexity) : 1.0f;
        commaRate = Float.isFinite(commaRate) ? Math.max(0, commaRate) : 0.05f;
        dashRate = Float.isFinite(dashRate) ? Math.max(0, dashRate) : 0.01f;
        ellipsisRate = Float.isFinite(ellipsisRate) ? Math.max(0, ellipsisRate) : 0.01f;
        exclamationRate = Float.isFinite(exclamationRate) ? Math.max(0, exclamationRate) : 0.01f;
        questionRate = Float.isFinite(questionRate) ? Math.max(0, questionRate) : 0.05f;
        formalityScore = Float.isFinite(formalityScore) ? Math.clamp(formalityScore, 0.0f, 1.0f) : 0.50f;
    }
}
