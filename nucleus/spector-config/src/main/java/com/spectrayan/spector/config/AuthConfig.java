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
package com.spectrayan.spector.config;

import java.time.Duration;
import java.util.List;

/**
 * Configuration POJO for multi-user authentication settings.
 */
public class AuthConfig {

    private boolean enabled = false;
    private JwtConfig jwt = new JwtConfig();
    private RefreshConfig refresh = new RefreshConfig();
    private OidcConfig oidc = new OidcConfig();
    private DefaultAdminConfig defaultAdmin = new DefaultAdminConfig();
    private Pbkdf2Config pbkdf2 = new Pbkdf2Config();
    private LockoutConfig lockout = new LockoutConfig();
    private List<String> publicPaths = List.of("/actuator/health", "/api/docs");

    public AuthConfig() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public JwtConfig getJwt() { return jwt; }
    public void setJwt(JwtConfig jwt) { this.jwt = jwt; }

    public RefreshConfig getRefresh() { return refresh; }
    public void setRefresh(RefreshConfig refresh) { this.refresh = refresh; }

    public OidcConfig getOidc() { return oidc; }
    public void setOidc(OidcConfig oidc) { this.oidc = oidc; }

    public DefaultAdminConfig getDefaultAdmin() { return defaultAdmin; }
    public void setDefaultAdmin(DefaultAdminConfig defaultAdmin) { this.defaultAdmin = defaultAdmin; }

    public Pbkdf2Config getPbkdf2() { return pbkdf2; }
    public void setPbkdf2(Pbkdf2Config pbkdf2) { this.pbkdf2 = pbkdf2; }

    public LockoutConfig getLockout() { return lockout; }
    public void setLockout(LockoutConfig lockout) { this.lockout = lockout; }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) {
        if (publicPaths != null && !publicPaths.isEmpty()) {
            this.publicPaths = publicPaths;
        }
    }

    // Record-style accessors for backward compatibility
    public boolean enabled() { return isEnabled(); }
    public JwtConfig jwt() { return getJwt(); }
    public RefreshConfig refresh() { return getRefresh(); }
    public OidcConfig oidc() { return getOidc(); }
    public DefaultAdminConfig defaultAdmin() { return getDefaultAdmin(); }
    public Pbkdf2Config pbkdf2() { return getPbkdf2(); }
    public LockoutConfig lockout() { return getLockout(); }
    public List<String> publicPaths() { return getPublicPaths(); }

    public static class JwtConfig {
        private String secret;
        private Duration ttl = Duration.ofHours(1);

        public JwtConfig() {}
        public JwtConfig(String secret, Duration ttl) {
            this.secret = secret;
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) this.ttl = ttl;
        }

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }

        public String secret() { return getSecret(); }
        public Duration ttl() { return getTtl(); }
    }

    public static class RefreshConfig {
        private Duration ttl = Duration.ofDays(30);

        public RefreshConfig() {}
        public RefreshConfig(Duration ttl) {
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) this.ttl = ttl;
        }

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }

        public Duration ttl() { return getTtl(); }
    }

    public static class OidcConfig {
        private String jwksUrl = "";
        private String issuer = "";

        public OidcConfig() {}
        public OidcConfig(String jwksUrl, String issuer) {
            if (jwksUrl != null) this.jwksUrl = jwksUrl;
            if (issuer != null) this.issuer = issuer;
        }

        public String getJwksUrl() { return jwksUrl; }
        public void setJwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }

        public String jwksUrl() { return getJwksUrl(); }
        public String issuer() { return getIssuer(); }
    }

    public static class DefaultAdminConfig {
        private String password;

        public DefaultAdminConfig() {}
        public DefaultAdminConfig(String password) { this.password = password; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String password() { return getPassword(); }
    }

    public static class Pbkdf2Config {
        private int iterations = 310_000;

        public Pbkdf2Config() {}
        public Pbkdf2Config(int iterations) {
            if (iterations > 0) this.iterations = iterations;
        }

        public int getIterations() { return iterations; }
        public void setIterations(int iterations) { this.iterations = iterations; }

        public int iterations() { return getIterations(); }
    }

    public static class LockoutConfig {
        private int maxAttempts = 5;
        private int minutes = 15;

        public LockoutConfig() {}
        public LockoutConfig(int maxAttempts, int minutes) {
            if (maxAttempts > 0) this.maxAttempts = maxAttempts;
            if (minutes > 0) this.minutes = minutes;
        }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getMinutes() { return minutes; }
        public void setMinutes(int minutes) { this.minutes = minutes; }

        public int maxAttempts() { return getMaxAttempts(); }
        public int minutes() { return getMinutes(); }
    }
}
