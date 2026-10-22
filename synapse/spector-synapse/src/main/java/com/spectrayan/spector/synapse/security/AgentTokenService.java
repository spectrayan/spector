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
package com.spectrayan.spector.synapse.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * ES256 agent tokens pinned to one namespace.
 *
 * <p>The issuer keeps the P-256 private key. Servers verify with the public JWKS only.
 * A token is written to a file and is never returned as log text.</p>
 */
public final class AgentTokenService {

    /** Issuer claim for agent tokens. Distinct from the HS256 server issuer. */
    public static final String ISSUER = "spector-agent";

    public static final Duration DEFAULT_TTL = Duration.ofDays(30);
    public static final Duration MAX_TTL = Duration.ofDays(90);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    /** Re-read a token file once it is this close to expiry. */
    public static final Duration ROTATE_WITHIN = Duration.ofMinutes(10);

    private static final Set<PosixFilePermission> OWNER_READ_WRITE =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private AgentTokenService() {
    }

    /**
     * Creates an ES256 key pair. The private key file is mode 0600. The JWKS file holds only the
     * public key.
     */
    public static KeyMaterial keygen(Path privateKeyFile, Path jwksFile) {
        Objects.requireNonNull(privateKeyFile, "privateKeyFile");
        Objects.requireNonNull(jwksFile, "jwksFile");
        try {
            ECKey key = new ECKeyGenerator(Curve.P_256)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.ES256)
                    .generate();
            writeOwnerOnly(privateKeyFile, key.toJSONString());
            JWKSet publicSet = new JWKSet(key.toPublicJWK());
            writeUtf8(jwksFile, publicSet.toString(false));
            return new KeyMaterial(privateKeyFile, jwksFile, key.getKeyID());
        } catch (JOSEException | IOException e) {
            throw new SpectorConfigException(ErrorCode.JWT_KEY_INVALID, e,
                    privateKeyFile.toString(), e.getMessage());
        }
    }

    /**
     * Signs a token pinned to {@code namespace} and writes it to {@code outFile} (mode 0600).
     * The compact token is not included in the returned description.
     */
    public static Issued issue(Path privateKeyFile, String agentId, String namespace,
                               Duration ttl, Collection<String> scopes, Collection<String> roles,
                               Path outFile) {
        Objects.requireNonNull(privateKeyFile, "privateKeyFile");
        Objects.requireNonNull(outFile, "outFile");
        if (agentId == null || agentId.isBlank()) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "--agent", "blank");
        }
        if (namespace == null || namespace.isBlank() || !isSlug(namespace)) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "--namespace", namespace);
        }
        Duration lifetime = ttl == null ? DEFAULT_TTL : ttl;
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAX_TTL) > 0) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "--ttl", lifetime);
        }
        try {
            ECKey key = ECKey.parse(Files.readString(privateKeyFile));
            Instant now = Instant.now();
            Instant exp = now.plus(lifetime);
            String jti = UUID.randomUUID().toString();
            List<String> scopeList = sanitize(scopes);
            if (scopeList.isEmpty()) {
                scopeList = List.of("spector:memory:read", "spector:memory:write");
            }
            List<String> roleList = sanitize(roles);
            if (roleList.isEmpty()) {
                roleList = List.of("agent");
            }
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(agentId.trim())
                    .jwtID(jti)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim("ns", List.of(namespace.trim()))
                    .claim("scope", String.join(" ", scopeList))
                    .claim("roles", roleList)
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(key.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new ECDSASigner(key));
            writeOwnerOnly(outFile, jwt.serialize());
            return new Issued(agentId.trim(), namespace.trim(), jti, exp, outFile);
        } catch (IOException | ParseException | JOSEException e) {
            throw new SpectorConfigException(ErrorCode.JWT_KEY_INVALID, e,
                    privateKeyFile.toString(), e.getMessage());
        }
    }

    /**
     * Verifies signature, issuer, expiry (with {@link #CLOCK_SKEW}) and subject.
     * Does not require the token to be namespace-pinned; callers that need a pin use
     * {@link #requirePinned(Verified)}.
     */
    public static Verified verify(Path tokenFile, Path jwksFile) {
        Objects.requireNonNull(tokenFile, "tokenFile");
        Objects.requireNonNull(jwksFile, "jwksFile");
        try {
            if (!Files.isRegularFile(tokenFile)) {
                throw new SpectorConfigException(ErrorCode.MCP_TOKEN_REQUIRED,
                        "token file does not exist: " + tokenFile);
            }
            String compact = Files.readString(tokenFile).trim();
            return verifyCompact(compact, jwksFile);
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, e, e.getMessage());
        }
    }

    public static Verified verifyCompact(String compact, Path jwksFile) {
        if (compact == null || compact.isBlank()) {
            throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, "token is empty");
        }
        try {
            SignedJWT jwt = SignedJWT.parse(compact.trim());
            if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())) {
                throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID,
                        "algorithm " + jwt.getHeader().getAlgorithm());
            }
            if (!Files.isRegularFile(jwksFile)) {
                throw new SpectorConfigException(ErrorCode.JWT_KEY_INVALID, jwksFile.toString(), "file missing");
            }
            JWKSet set = JWKSet.parse(Files.readString(jwksFile));
            String kid = jwt.getHeader().getKeyID();
            JWK jwk = kid != null ? set.getKeyByKeyId(kid) : null;
            if (jwk == null && set.getKeys().size() == 1) {
                jwk = set.getKeys().get(0);
            }
            if (!(jwk instanceof ECKey ec)) {
                throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, "no matching EC public key");
            }
            if (!jwt.verify(new ECDSAVerifier(ec.toPublicJWK()))) {
                throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, "signature rejected");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID,
                        "issuer " + claims.getIssuer());
            }
            Date exp = claims.getExpirationTime();
            if (exp == null || Instant.now().isAfter(exp.toInstant().plus(CLOCK_SKEW))) {
                throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, "expired");
            }
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, "missing subject");
            }
            List<String> ns = stringList(claims.getClaim("ns"));
            List<String> nsid = stringList(claims.getClaim("nsid"));
            return new Verified(sub, ns, nsid, claims.getJWTID(), exp.toInstant(),
                    claims.getStringClaim("scope"), stringList(claims.getClaim("roles")));
        } catch (ParseException | JOSEException | IOException e) {
            throw new SpectorConfigException(ErrorCode.AGENT_TOKEN_INVALID, e, e.getMessage());
        }
    }

    /**
     * Requires exactly one namespace: one {@code ns} value, one {@code nsid} value, or one of each
     * (slug and id of the same namespace).
     */
    public static Pinned requirePinned(Verified verified) {
        Objects.requireNonNull(verified, "verified");
        List<String> ns = verified.namespaces();
        List<String> nsid = verified.namespaceIds();
        boolean oneSlug = ns.size() == 1 && isSlug(ns.get(0));
        boolean oneId = nsid.size() == 1 && isSlug(nsid.get(0));
        if ((oneSlug && nsid.isEmpty()) || (oneId && ns.isEmpty()) || (oneSlug && oneId)) {
            return new Pinned(verified, oneSlug ? ns.get(0) : null, oneId ? nsid.get(0) : null);
        }
        throw new SpectorConfigException(ErrorCode.MCP_TOKEN_NOT_PINNED,
                "ns=" + ns + " nsid=" + nsid);
    }

    public static boolean isSlug(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    }

    public static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String s) {
            return s.isBlank() ? List.of() : List.of(s.trim());
        }
        if (raw instanceof Collection<?> values) {
            List<String> out = new ArrayList<>();
            for (Object value : values) {
                if (value != null && !value.toString().isBlank()) {
                    out.add(value.toString().trim());
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }

    private static List<String> sanitize(Collection<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private static void writeOwnerOnly(Path path, String content) throws IOException {
        writeUtf8(path, content);
        try {
            Files.setPosixFilePermissions(path, OWNER_READ_WRITE);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX volumes cannot express 0600; the write still happened.
        }
    }

    private static void writeUtf8(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record KeyMaterial(Path privateKeyFile, Path jwksFile, String keyId) {
    }

    /** Description of an issued token. The compact serialization is intentionally absent. */
    public record Issued(String subject, String namespace, String jti, Instant expiresAt, Path file) {
    }

    public record Verified(
            String subject,
            List<String> namespaces,
            List<String> namespaceIds,
            String jti,
            Instant expiresAt,
            String scope,
            List<String> roles) {
    }

    public record Pinned(Verified verified, String slug, String namespaceId) {
        public String bindSelector() {
            return slug != null ? slug : namespaceId;
        }

        public boolean matches(String selector) {
            if (selector == null) {
                return false;
            }
            String trimmed = selector.trim();
            return trimmed.equals(slug) || trimmed.equals(namespaceId);
        }

        public String describe() {
            return slug != null ? slug : namespaceId;
        }
    }
}
