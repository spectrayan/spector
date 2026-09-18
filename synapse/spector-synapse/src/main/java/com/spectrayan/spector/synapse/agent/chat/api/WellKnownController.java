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
package com.spectrayan.spector.synapse.agent.chat.api;

import com.spectrayan.spector.synapse.agent.AgentCard;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for A2A well-known configuration and capability discovery.
 */
@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    private final CognitiveSoulService soulService;

    public WellKnownController(CognitiveSoulService soulService) {
        this.soulService = soulService;
    }

    /**
     * Publishes the default active agent's capability card (A2A standard).
     *
     * @return the default agent card
     */
    @GetMapping("/agent.json")
    public ResponseEntity<AgentCard> getWellKnownAgent() {
        return ResponseEntity.ok(AgentCard.from(soulService.getActiveSoul()));
    }
}
