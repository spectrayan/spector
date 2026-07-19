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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.config.SynapseSalienceProvider.InterestEntry;
import com.spectrayan.spector.synapse.memory.MemoryAccessObject;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller providing unified endpoints under /api/v1/salience for frontend settings management.
 */
@RestController
@RequestMapping("/api/v1/salience")
public class UserSalienceController {

    private static final Logger log = LoggerFactory.getLogger(UserSalienceController.class);

    private final SynapseSalienceProvider salienceProvider;
    private final CognitiveSoulService soulService;
    private final MemoryAccessObject mao;
    private final ConfigRepository configRepository;
    private final ObjectMapper mapper;

    public UserSalienceController(SynapseSalienceProvider salienceProvider,
                                  CognitiveSoulService soulService,
                                  MemoryAccessObject mao,
                                  ConfigRepository configRepository,
                                  ObjectMapper mapper) {
        this.salienceProvider = salienceProvider;
        this.soulService = soulService;
        this.mao = mao;
        this.configRepository = configRepository;
        this.mapper = mapper;
    }

    @GetMapping("/{scope}/{id}")
    public ResponseEntity<UserProfileDto> getProfile(@PathVariable String scope, @PathVariable String id) {
        log.debug("Fetching user salience profile for scope={}, id={}", scope, id);
        var snapshot = salienceProvider.snapshot();
        Optional<PersonaContext> personaOpt = soulService.loadUserSoul();

        List<InterestEntryDto> interestsList = snapshot.interests().stream()
                .map(e -> new InterestEntryDto(e.topic(), e.level().name()))
                .toList();

        List<InterestEntryDto> disinterestsList = snapshot.disinterests().stream()
                .map(e -> new InterestEntryDto(e.topic(), e.level().name()))
                .toList();

        IcnuDto icnuWeights = null;
        if (snapshot.icnuWeights() != null) {
            icnuWeights = new IcnuDto(
                    snapshot.icnuWeights().interest(),
                    snapshot.icnuWeights().challenge(),
                    snapshot.icnuWeights().novelty(),
                    snapshot.icnuWeights().urgency()
            );
        } else {
            icnuWeights = new IcnuDto(0.25f, 0.25f, 0.25f, 0.25f);
        }

        Float alpha = snapshot.alpha() != null ? snapshot.alpha() : 0.6f;
        Float beta = snapshot.beta() != null ? snapshot.beta() : 0.4f;
        Float flashbulbThreshold = 3.0f;

        UserProfileDto response = new UserProfileDto(
                interestsList,
                disinterestsList,
                icnuWeights,
                alpha,
                beta,
                flashbulbThreshold,
                personaOpt.orElse(null)
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{scope}/{id}")
    public ResponseEntity<Map<String, Object>> saveProfile(
            @PathVariable String scope,
            @PathVariable String id,
            @RequestBody SaveProfileRequest request) {

        log.info("Saving user salience profile via unified PUT endpoint for scope={}, id={}", scope, id);

        // 1. Process interests & disinterests
        List<InterestEntry> interestEntries = new ArrayList<>();
        if (request.interests() != null) {
            for (var entry : request.interests().entrySet()) {
                try {
                    InterestLevel lvl = InterestLevel.valueOf(entry.getValue().toUpperCase());
                    interestEntries.add(new InterestEntry(entry.getKey(), lvl));
                } catch (Exception e) {
                    log.warn("Skipping invalid interest level: {} for topic: {}", entry.getValue(), entry.getKey());
                }
            }
        }

        List<InterestEntry> disinterestEntries = new ArrayList<>();
        if (request.disinterests() != null) {
            for (var entry : request.disinterests().entrySet()) {
                try {
                    InterestLevel lvl = InterestLevel.valueOf(entry.getValue().toUpperCase());
                    disinterestEntries.add(new InterestEntry(entry.getKey(), lvl));
                } catch (Exception e) {
                    log.warn("Skipping invalid disinterest level: {} for topic: {}", entry.getValue(), entry.getKey());
                }
            }
        }

        salienceProvider.updateInterests(interestEntries, disinterestEntries);

        // 2. Process icnuWeights, alpha, beta
        IcnuWeights icnu = null;
        if (request.icnuWeights() != null) {
            float interest = request.icnuWeights().interest() != null ? request.icnuWeights().interest() : 0.25f;
            float challenge = request.icnuWeights().challenge() != null ? request.icnuWeights().challenge() : 0.25f;
            float novelty = request.icnuWeights().novelty() != null ? request.icnuWeights().novelty() : 0.25f;
            float urgency = request.icnuWeights().urgency() != null ? request.icnuWeights().urgency() : 0.25f;
            icnu = new IcnuWeights(interest, challenge, novelty, urgency);
        }

        salienceProvider.updateScoringWeights(icnu, request.alpha(), request.beta());

        // 3. Process persona if present
        if (request.persona() != null) {
            soulService.saveUserSoul(request.persona());
        }

        // 4. Save SALIENCE settings to H2 Database
        try {
            Map<String, Object> values = new HashMap<>();
            values.put("interestsList", interestEntries.stream().map(e -> Map.of("topic", e.topic(), "level", e.level().name())).toList());
            values.put("disinterestsList", disinterestEntries.stream().map(e -> Map.of("topic", e.topic(), "level", e.level().name())).toList());
            if (icnu != null) {
                values.put("icnuWeights", Map.of(
                        "interest", icnu.interest(),
                        "challenge", icnu.challenge(),
                        "novelty", icnu.novelty(),
                        "urgency", icnu.urgency()
                ));
            }
            if (request.alpha() != null) values.put("alpha", request.alpha());
            if (request.beta() != null) values.put("beta", request.beta());

            ScopedConfig sc = new ScopedConfig(
                    "user:default:default",
                    ConfigCategory.SALIENCE,
                    values,
                    java.time.Instant.now(),
                    "default"
            );
            configRepository.save(sc);
            log.info("Persisted user salience profile to database under category SALIENCE");
        } catch (Exception e) {
            log.warn("Failed to persist user salience profile to database: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Salience profile and persona saved successfully"
        ));
    }

    @DeleteMapping("/{scope}/{id}")
    public ResponseEntity<Map<String, Object>> deleteProfile(@PathVariable String scope, @PathVariable String id) {
        log.info("Resetting user salience profile for scope={}, id={}", scope, id);
        salienceProvider.updateInterests(List.of(), List.of());
        salienceProvider.updateScoringWeights(null, null, null);
        soulService.saveUserSoul(null);
        configRepository.delete("user:default:default", ConfigCategory.SALIENCE);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Salience profile and persona reset to defaults"
        ));
    }

    @PostMapping("/rescore")
    public ResponseEntity<Map<String, Object>> rescoreMemories(@RequestBody(required = false) Map<String, String> body) {
        log.info("Triggered rescore of all memories with current salience profile");
        if (!mao.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                    "status", "skipped",
                    "message", "Memory engine not available (stub mode)"
            ));
        }

        long start = System.nanoTime();
        int rescored = mao.rescoreWithCurrentProfile();
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "rescored", rescored,
                "durationMs", durationMs,
                "message", "All memories rescored."
        ));
    }

    @GetMapping("/rescore/status")
    public ResponseEntity<Map<String, Object>> rescoreStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", "No active rescoring task running."
        ));
    }

    // --- DTOs ---

    public record UserProfileDto(
            List<InterestEntryDto> interestsList,
            List<InterestEntryDto> disinterestsList,
            IcnuDto icnuWeights,
            Float alpha,
            Float beta,
            Float flashbulbThreshold,
            PersonaContext persona
    ) {}

    public record InterestEntryDto(String topic, String level) {}

    public record IcnuDto(Float interest, Float challenge, Float novelty, Float urgency) {}

    public record SaveProfileRequest(
            Map<String, String> interests,
            Map<String, String> disinterests,
            IcnuDto icnuWeights,
            Float alpha,
            Float beta,
            Float flashbulbThreshold,
            PersonaContext persona
    ) {}
}
