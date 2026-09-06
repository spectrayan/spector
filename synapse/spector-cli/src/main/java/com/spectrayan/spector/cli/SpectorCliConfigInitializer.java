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
package com.spectrayan.spector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * Early Spring environment bridge for the Spector CLI.
 * <p>
 * Bridges {@code --config <file>}, {@code spector.yml}, {@code --profile <p>},
 * and CLI argument overrides into Spring's {@link ConfigurableEnvironment}
 * before {@link com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties}
 * is bound.
 * </p>
 */
public class SpectorCliConfigInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(SpectorCliConfigInitializer.class);

    private final String[] args;

    public SpectorCliConfigInitializer(String[] args) {
        this.args = args != null ? args : new String[0];
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();

        // 1. Check for --profile / -p
        String profile = findArgValue("--profile", "-p");
        if (profile != null && !profile.isBlank()) {
            env.addActiveProfile(profile);
            log.debug("[Spector CLI] Activated profile: {}", profile);
        }

        // 2. Check for --config / -c or fallback to spector.yml
        String explicitConfig = findArgValue("--config", "-c");
        Path configPath = null;
        if (explicitConfig != null && !explicitConfig.isBlank()) {
            configPath = Path.of(explicitConfig);
        } else if (Files.exists(Path.of("spector.yml"))) {
            configPath = Path.of("spector.yml");
        }

        if (configPath != null && Files.exists(configPath)) {
            loadYamlProperties(env, configPath);
        }

        // 3. Collect CLI property overrides into a high-priority PropertySource
        Map<String, Object> overrides = new HashMap<>();

        String dims = findArgValue("--dims", null);
        if (dims != null && !dims.isBlank()) {
            overrides.put("spector.memory.dimensions", dims);
            overrides.put("spector.provider.embedding.dimensions", dims);
        }

        String capacity = findArgValue("--capacity", null);
        if (capacity != null && !capacity.isBlank()) {
            overrides.put("spector.memory.capacity", capacity);
        }

        String dataDir = findArgValue("--data-dir", null);
        if (dataDir != null && !dataDir.isBlank()) {
            overrides.put("spector.memory.persistence-path", dataDir);
            overrides.put("spector.memory.persistence-mode", "DISK");
        }

        String namespace = findArgValue("--namespace", null);
        if (namespace != null && !namespace.isBlank()) {
            overrides.put("spector.memory.namespace", namespace);
        }

        String ollamaUrl = findArgValue("--ollama-url", null);
        if (ollamaUrl != null && !ollamaUrl.isBlank()) {
            overrides.put("spector.provider.embedding.base-url", ollamaUrl);
            overrides.put("spector.embedding.base-url", ollamaUrl);
        }

        String ollamaModel = findArgValue("--ollama-model", null);
        if (ollamaModel != null && !ollamaModel.isBlank()) {
            overrides.put("spector.provider.embedding.model", ollamaModel);
            overrides.put("spector.embedding.model", ollamaModel);
        }

        String mode = findArgValue("--mode", null);
        if ("openclaw".equalsIgnoreCase(mode)) {
            overrides.put("spector.mode", "memory");
            overrides.put("spector.memory.enabled", "true");
            overrides.put("spector.memory.persistence-mode", "DISK");
            if (dataDir == null && explicitConfig == null) {
                String openclawDataDir = System.getProperty("user.home") + "/.openclaw/spector/data";
                overrides.put("spector.memory.persistence-path", openclawDataDir + "/memory");
            }
        } else if ("odysseus".equalsIgnoreCase(mode)) {
            overrides.put("spector.mode", "memory");
            overrides.put("spector.memory.enabled", "true");
            overrides.put("spector.memory.persistence-mode", "DISK");
            if (dataDir == null && explicitConfig == null) {
                String odysseusDataDir = System.getProperty("user.home") + "/.odysseus/spector/data";
                overrides.put("spector.memory.persistence-path", odysseusDataDir + "/memory");
            }
            overrides.put("spector.memory.default-ingestion-tier", "SEMANTIC");
        }

        if (!overrides.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("spectorCliOverrides", overrides));
            log.debug("[Spector CLI] Applied {} CLI property overrides", overrides.size());
        }
    }

    private void loadYamlProperties(ConfigurableEnvironment env, Path path) {
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> sources = loader.load("spectorConfigFile:" + path.getFileName(), new FileSystemResource(path));
            for (PropertySource<?> source : sources) {
                env.getPropertySources().addLast(source);
                log.info("[Spector CLI] Loaded configuration from {}", path);
            }
        } catch (IOException e) {
            log.warn("[Spector CLI] Could not load YAML configuration from {}: {}", path, e.getMessage());
        }
    }

    private String findArgValue(String longName, String shortName) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals(longName) || (shortName != null && arg.equals(shortName))) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    return args[i + 1];
                }
            } else if (arg.startsWith(longName + "=")) {
                return arg.substring((longName + "=").length());
            } else if (shortName != null && arg.startsWith(shortName + "=")) {
                return arg.substring((shortName + "=").length());
            }
        }
        return null;
    }
}
