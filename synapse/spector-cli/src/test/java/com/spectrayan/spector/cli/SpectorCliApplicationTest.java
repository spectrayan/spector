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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.spectrayan.spector.memory.SpectorMemory;

@SpringBootTest(classes = SpectorCliApplication.class, properties = {
        "spring.main.lazy-initialization=true",
        "spring.main.banner-mode=off"
})
@ActiveProfiles("cli-remote")
class SpectorCliApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads_inRemoteProfile_spectorMemoryBeanIsNotCreated() {
        assertThat(context).isNotNull();
        assertThat(context.containsBean("spectorMemory")).isFalse();
        assertThat(context.getBeanNamesForType(SpectorMemory.class)).isEmpty();
    }

    @Test
    void contextLoads_cliBeansArePresent() {
        assertThat(context.containsBean("spectorCtl")).isTrue();
        assertThat(context.containsBean("spectorPicocliRunner")).isTrue();
        assertThat(context.containsBean("springPicocliFactory")).isTrue();
        assertThat(context.containsBean("mcpCommand")).isTrue();
        assertThat(context.containsBean("rememberCommand")).isTrue();
        assertThat(context.containsBean("recallCommand")).isTrue();
    }
}
