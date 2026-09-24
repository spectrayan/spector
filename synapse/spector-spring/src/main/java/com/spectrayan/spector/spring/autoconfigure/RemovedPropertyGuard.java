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
package com.spectrayan.spector.spring.autoconfigure;

import java.util.Map;

import org.springframework.core.env.Environment;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.SpectorPropertyConstants;

/**
 * Refuses to start when the Spring {@link Environment} sets a Spector configuration property that no
 * longer exists.
 *
 * <p>{@code SpectorConfigFactory} already rejects removed keys, but only on the standalone load path.
 * Spring binds {@code @ConfigurationProperties} straight onto the properties beans without consulting
 * that factory, and its relaxed binding will happily populate a field whose backing property has been
 * withdrawn. Without this guard a Spring Boot operator who sets a removed key would be told nothing
 * while the value took an effect that is no longer supported — the silent-ignore failure the removal
 * exists to end.</p>
 *
 * <p>Checked against the {@link Environment} rather than the bound bean because a bound bean cannot
 * distinguish "operator set this" from "this is the default".</p>
 */
public final class RemovedPropertyGuard {

    private RemovedPropertyGuard() {
    }

    /**
     * Throws if any removed property is present in the environment.
     *
     * @param environment the Spring environment, may be {@code null} (then nothing is checked)
     * @throws SpectorConfigException naming the removed key and its replacement
     */
    public static void check(Environment environment) {
        if (environment == null) {
            return;
        }
        for (Map.Entry<String, String> entry : SpectorPropertyConstants.REMOVED_PROPERTIES.entrySet()) {
            String removed = entry.getKey();
            if (!environment.containsProperty(removed)) {
                continue;
            }
            String replacement = entry.getValue();
            String value = environment.getProperty(removed, "");
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    String.format(
                            "Configuration property '%s' has been removed; it is still set (to '%s'). Use '%s' "
                                    + "instead — it is now the single source of embedding dimensionality, and the "
                                    + "engine derives its own width from it. Set '%s: %s' and delete '%s'. If you "
                                    + "configure dimensionality through the SPECTOR_EMBEDDING_DIMS environment "
                                    + "variable, no change is needed: it now maps only to the replacement.",
                            removed, value, replacement, replacement, value, removed),
                    true);
        }
    }
}
