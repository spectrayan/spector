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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

class SpringPicocliFactoryTest {

    static class SampleCommand implements Runnable {
        @Override
        public void run() {}
    }

    static class UnregisteredCommand implements Runnable {
        @Override
        public void run() {}
    }

    @Test
    void create_beanFoundInContext_returnsBeanFromContext() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        SampleCommand expected = new SampleCommand();
        when(context.getBean(SampleCommand.class)).thenReturn(expected);

        SpringPicocliFactory factory = new SpringPicocliFactory(context);
        SampleCommand actual = factory.create(SampleCommand.class);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void create_beanNotFoundInContext_fallsBackToDefaultFactory() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(UnregisteredCommand.class))
                .thenThrow(new NoSuchBeanDefinitionException(UnregisteredCommand.class));

        SpringPicocliFactory factory = new SpringPicocliFactory(context);
        UnregisteredCommand actual = factory.create(UnregisteredCommand.class);

        assertThat(actual).isNotNull();
        assertThat(actual).isInstanceOf(UnregisteredCommand.class);
    }
}
