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
package com.spectrayan.spector.synapse.security.toolaccess;

import com.spectrayan.spector.synapse.security.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Synapse-owned per-agent tool authorization (ADR-0035).
 *
 * <p>Policy is loaded once from {@code /security/tool-access.yml} on the classpath
 * and cached immutably. {@link SecurityProperties.ToolAccessProperties} may
 * override the YAML {@code default} and master enable flag.</p>
 *
 * <p>Soul ≠ access: {@code AgentSoul.tools} is only a capability hint. When non-empty,
 * the effective set is {@code Synapse policy ∩ soul.tools}; when empty, Synapse policy
 * alone applies.</p>
 */
public final class ToolAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(ToolAccessPolicy.class);

    static final String RESOURCE_PATH = "/security/tool-access.yml";
    static final String WILDCARD = "*";

    /** Production classpath snapshot (immutable after class init). */
    private static final PolicyDocument CACHED = loadClasspath();

    private final boolean enabled;
    private final DefaultMode defaultWhenNoEntry;
    private final Map<String, AgentRule> agents;

    /** Production constructor: classpath YAML + optional SecurityProperties override. */
    public ToolAccessPolicy(SecurityProperties.ToolAccessProperties props) {
        this(CACHED, props);
    }

    /**
     * Visible for tests — inject a parsed document without re-reading the classpath.
     */
    ToolAccessPolicy(PolicyDocument document, SecurityProperties.ToolAccessProperties props) {
        Objects.requireNonNull(document, "document");
        SecurityProperties.ToolAccessProperties effective =
                props != null ? props : new SecurityProperties.ToolAccessProperties();
        this.enabled = effective.isEnabled();
        DefaultMode fromProps = effective.getDefaultWhenNoEntry();
        this.defaultWhenNoEntry = fromProps != null ? fromProps : document.defaultMode();
        this.agents = document.agents();
        log.info("[ToolAccessPolicy] enabled={} defaultWhenNoEntry={} agentEntries={}",
                enabled, defaultWhenNoEntry, agents.size());
    }

    /** Test helper: fully synthetic policy (no classpath). */
    static ToolAccessPolicy of(DefaultMode defaultMode,
                               Map<String, AgentRule> agents,
                               boolean enabled) {
        PolicyDocument doc = new PolicyDocument(
                defaultMode != null ? defaultMode : DefaultMode.DENY_ALL,
                agents != null ? Map.copyOf(agents) : Map.of());
        SecurityProperties.ToolAccessProperties props = new SecurityProperties.ToolAccessProperties();
        props.setEnabled(enabled);
        props.setDefaultWhenNoEntry(defaultMode);
        return new ToolAccessPolicy(doc, props);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public DefaultMode defaultWhenNoEntry() {
        return defaultWhenNoEntry;
    }

    /**
     * Returns whether {@code toolName} may be invoked for the given agent.
     *
     * @param agentId        soul/agent id (may be null/blank)
     * @param toolName       registered tool name
     * @param soulToolsHint  {@code AgentSoul.tools} capability hint (may be null/empty)
     * @param registered     all registered tool names (used for allow-all / omit-allow)
     */
    public boolean isAllowed(String agentId,
                             String toolName,
                             List<String> soulToolsHint,
                             Collection<String> registered) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        Set<String> effective = effectiveTools(agentId, soulToolsHint, registered);
        return effective.contains(toolName);
    }

    /**
     * Computes the effective tool name set for list and execute (must not diverge).
     *
     * <ol>
     *   <li>Synapse policy for {@code agentId} (exact, then {@code *}, then default)</li>
     *   <li>If {@code soulToolsHint} is non-empty → intersect</li>
     * </ol>
     */
    public Set<String> effectiveTools(String agentId,
                                      List<String> soulToolsHint,
                                      Collection<String> registered) {
        Set<String> registeredSet = registered != null
                ? new LinkedHashSet<>(registered)
                : Set.of();

        Set<String> fromPolicy;
        if (!enabled) {
            fromPolicy = registeredSet;
        } else {
            fromPolicy = resolvePolicySet(agentId, registeredSet);
        }

        if (soulToolsHint == null || soulToolsHint.isEmpty()) {
            return Collections.unmodifiableSet(fromPolicy);
        }

        Set<String> hint = new LinkedHashSet<>();
        for (String name : soulToolsHint) {
            if (name != null && !name.isBlank()) {
                hint.add(name);
            }
        }
        Set<String> intersection = new LinkedHashSet<>();
        for (String name : fromPolicy) {
            if (hint.contains(name)) {
                intersection.add(name);
            }
        }
        return Collections.unmodifiableSet(intersection);
    }

    private Set<String> resolvePolicySet(String agentId, Set<String> registered) {
        AgentRule rule = lookupRule(agentId);
        if (rule == null) {
            return defaultWhenNoEntry == DefaultMode.ALLOW_ALL
                    ? new LinkedHashSet<>(registered)
                    : new LinkedHashSet<>();
        }
        return applyRule(rule, registered);
    }

    private AgentRule lookupRule(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            AgentRule exact = agents.get(agentId);
            if (exact != null) {
                return exact;
            }
        }
        return agents.get(WILDCARD);
    }

    private static Set<String> applyRule(AgentRule rule, Set<String> registered) {
        Set<String> base;
        if (rule.allow() != null) {
            base = new LinkedHashSet<>();
            for (String name : rule.allow()) {
                if (registered.contains(name)) {
                    base.add(name);
                }
            }
        } else {
            base = new LinkedHashSet<>(registered);
        }
        if (rule.deny() != null) {
            for (String name : rule.deny()) {
                base.remove(name);
            }
        }
        return base;
    }

    /** Stable permission-denied message for tool execute paths (no sensitive payload). */
    public static String permissionDeniedMessage(String toolName) {
        return "Error: Permission denied for tool '" + toolName
                + "' [SPE-500-013]. This agent is not authorized to use this tool.";
    }

    private static PolicyDocument loadClasspath() {
        try (InputStream in = ToolAccessPolicy.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.error("[ToolAccessPolicy] Missing classpath resource {}; fail-closed deny-all",
                        RESOURCE_PATH);
                return PolicyDocument.failClosed();
            }
            return parse(in);
        } catch (IOException ex) {
            log.error("[ToolAccessPolicy] Failed to read {}; fail-closed deny-all",
                    RESOURCE_PATH, ex);
            return PolicyDocument.failClosed();
        }
    }

    /**
     * Parses tool-access YAML. Invalid documents fail-closed to deny-all
     * (empty agent map) so a packaging error cannot open tools.
     */
    static PolicyDocument parse(InputStream in) {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map<?, ?> map)) {
                log.error("[ToolAccessPolicy] YAML root must be a mapping; fail-closed deny-all");
                return PolicyDocument.failClosed();
            }

            DefaultMode defaultMode = parseDefault(map.get("default"));
            Map<String, AgentRule> agents = parseAgents(map.get("agents"));
            log.info("[ToolAccessPolicy] Loaded default={} agentEntries={} from {}",
                    defaultMode, agents.size(), RESOURCE_PATH);
            return new PolicyDocument(defaultMode, agents);
        } catch (RuntimeException ex) {
            log.error("[ToolAccessPolicy] Invalid YAML {}; fail-closed deny-all",
                    RESOURCE_PATH, ex);
            return PolicyDocument.failClosed();
        }
    }

    private static DefaultMode parseDefault(Object raw) {
        if (raw == null) {
            return DefaultMode.DENY_ALL;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("allow-all".equals(value) || "allowall".equals(value)) {
            return DefaultMode.ALLOW_ALL;
        }
        if ("deny-all".equals(value) || "denyall".equals(value)) {
            return DefaultMode.DENY_ALL;
        }
        log.error("[ToolAccessPolicy] Unknown default '{}'; fail-closed deny-all", raw);
        return DefaultMode.DENY_ALL;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, AgentRule> parseAgents(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            log.error("[ToolAccessPolicy] 'agents' must be a mapping; ignoring");
            return Map.of();
        }
        Map<String, AgentRule> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String id = String.valueOf(entry.getKey()).trim();
            if (id.isEmpty()) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> ruleMap)) {
                log.warn("[ToolAccessPolicy] Skipping agent '{}' — rule must be a mapping", id);
                continue;
            }
            List<String> allow = parseNameList(ruleMap.get("allow"));
            List<String> deny = parseNameList(ruleMap.get("deny"));
            // allow key present (even empty list) → allowlist mode; absent → null (all minus deny)
            boolean allowPresent = ruleMap.containsKey("allow");
            result.put(id, new AgentRule(allowPresent ? allow : null, deny));
        }
        return Map.copyOf(result);
    }

    private static List<String> parseNameList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            log.warn("[ToolAccessPolicy] Expected list of tool names, got {}; treating as empty",
                    raw.getClass().getSimpleName());
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String name && !name.isBlank()) {
                names.add(name.strip());
            }
        }
        return List.copyOf(names);
    }

    /** When no matching agent entry exists. */
    public enum DefaultMode {
        DENY_ALL,
        ALLOW_ALL
    }

    /**
     * Per-agent rule. {@code allow == null} means “all registered tools” before deny;
     * {@code allow} empty means nothing allowed (explicit empty allowlist).
     */
    public record AgentRule(List<String> allow, List<String> deny) {
        public AgentRule {
            allow = allow != null ? List.copyOf(allow) : null;
            deny = deny != null ? List.copyOf(deny) : List.of();
        }
    }

    record PolicyDocument(DefaultMode defaultMode, Map<String, AgentRule> agents) {
        PolicyDocument {
            defaultMode = defaultMode != null ? defaultMode : DefaultMode.DENY_ALL;
            agents = agents != null ? Map.copyOf(agents) : Map.of();
        }

        static PolicyDocument failClosed() {
            return new PolicyDocument(DefaultMode.DENY_ALL, Map.of());
        }
    }
}
