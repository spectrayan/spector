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
package com.spectrayan.spector.kernel.store;

public record BanditStats(
        float ema,
        int totalSignals,
        int positiveSignals,
        long lastUpdatedMs
) {
    public static final BanditStats EMPTY = new BanditStats(0f, 0, 0, 0L);

    public BanditStats update(boolean positive, float alpha) {
        float signal = positive ? 1.0f : 0.0f;
        float newEma = (totalSignals == 0) ? signal : (ema * (1.0f - alpha) + signal * alpha);
        return new BanditStats(
                newEma,
                totalSignals + 1,
                positiveSignals + (positive ? 1 : 0),
                System.currentTimeMillis()
        );
    }
}
