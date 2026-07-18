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
package com.spectrayan.spector.synapse.agent.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * FlowSpec marketplace — discover, install, customize, and share agent templates.
 *
 * <p>Acts as a registry of available templates with metadata, popularity tracking,
 * and installation management.</p>
 */
@Service
public class FlowSpecMarketplace {

    private static final Logger log = LoggerFactory.getLogger(FlowSpecMarketplace.class);

    /** Marketplace entry with metadata. */
    public record MarketplaceEntry(
            String id,
            String name,
            String description,
            String domain,
            String author,
            String version,
            List<String> tags,
            long installCount,
            double rating,
            boolean builtin
    ) {
        public MarketplaceEntry {
            if (tags == null) tags = List.of();
        }
    }

    private final Map<String, MarketplaceEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> installCounts = new ConcurrentHashMap<>();

    /** Register a template in the marketplace. */
    public void publish(MarketplaceEntry entry) {
        entries.put(entry.id(), entry);
        log.info("[Marketplace] Published: {} v{} by {}", entry.name(), entry.version(), entry.author());
    }

    /** Search templates by keyword. */
    public List<MarketplaceEntry> search(String keyword) {
        var lower = keyword.toLowerCase();
        return entries.values().stream()
                .filter(e -> e.name().toLowerCase().contains(lower)
                        || e.description().toLowerCase().contains(lower)
                        || e.tags().stream().anyMatch(t -> t.toLowerCase().contains(lower)))
                .toList();
    }

    /** List templates by domain. */
    public List<MarketplaceEntry> byDomain(String domain) {
        return entries.values().stream()
                .filter(e -> e.domain().equalsIgnoreCase(domain))
                .toList();
    }

    /** Get top templates by install count. */
    public List<MarketplaceEntry> popular(int limit) {
        return entries.values().stream()
                .sorted(Comparator.comparingLong(MarketplaceEntry::installCount).reversed())
                .limit(limit)
                .toList();
    }

    /** Install a template (increment counter). */
    public Optional<MarketplaceEntry> install(String templateId) {
        var entry = entries.get(templateId);
        if (entry == null) return Optional.empty();

        var counter = installCounts.computeIfAbsent(templateId, _ -> {
            var adder = new LongAdder();
            adder.add(entry.installCount());
            return adder;
        });
        counter.increment();

        var updated = new MarketplaceEntry(
                entry.id(), entry.name(), entry.description(), entry.domain(),
                entry.author(), entry.version(), entry.tags(),
                counter.sum(), entry.rating(), entry.builtin()
        );
        entries.put(templateId, updated);
        log.info("[Marketplace] Installed: {} (total={})", entry.name(), updated.installCount());
        return Optional.of(updated);
    }

    /** Get a template by ID. */
    public Optional<MarketplaceEntry> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** All available templates. */
    public List<MarketplaceEntry> all() {
        return List.copyOf(entries.values());
    }

    /** Total templates in marketplace. */
    public int size() { return entries.size(); }
}
