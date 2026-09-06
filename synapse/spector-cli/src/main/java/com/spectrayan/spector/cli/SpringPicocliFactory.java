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

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

/**
 * Picocli factory that delegates command instance creation to the Spring {@link ApplicationContext}.
 * <p>
 * If a command class is registered as a Spring bean, it is retrieved from the context (with all
 * autowired/injected dependencies). If not found, it falls back to Picocli's default factory.
 * </p>
 */
@Component
public class SpringPicocliFactory implements CommandLine.IFactory {

    private final ApplicationContext applicationContext;

    public SpringPicocliFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        try {
            return applicationContext.getBean(cls);
        } catch (NoSuchBeanDefinitionException e) {
            return CommandLine.defaultFactory().create(cls);
        }
    }
}
