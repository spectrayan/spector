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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

/**
 * Configuration properties POJO for multi-user authentication settings.
 */
public class AuthProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = DEFAULT_AUTH_ENABLED;
    private JwtProperties jwt = new JwtProperties();
    private RefreshProperties refresh = new RefreshProperties();
    private OidcProperties oidc = new OidcProperties();
    private DefaultAdminProperties defaultAdmin = new DefaultAdminProperties();
    private Pbkdf2Properties pbkdf2 = new Pbkdf2Properties();
    private LockoutProperties lockout = new LockoutProperties();
    private List<String> publicPaths = DEFAULT_AUTH_PUBLIC_PATHS;

    public AuthProperties() {}

    public AuthProperties(boolean enabled, JwtProperties jwt, RefreshProperties refresh,
                          OidcProperties oidc, DefaultAdminProperties defaultAdmin,
                          Pbkdf2Properties pbkdf2, LockoutProperties lockout,
                          List<String> publicPaths) {
        this.enabled = enabled;
        if (jwt != null) this.jwt = jwt;
        if (refresh != null) this.refresh = refresh;
        if (oidc != null) this.oidc = oidc;
        if (defaultAdmin != null) this.defaultAdmin = defaultAdmin;
        if (pbkdf2 != null) this.pbkdf2 = pbkdf2;
        if (lockout != null) this.lockout = lockout;
        if (publicPaths != null && !publicPaths.isEmpty()) this.publicPaths = publicPaths;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public JwtProperties getJwt() { return jwt; }
    public void setJwt(JwtProperties jwt) { if (jwt != null) this.jwt = jwt; }

    public RefreshProperties getRefresh() { return refresh; }
    public void setRefresh(RefreshProperties refresh) { if (refresh != null) this.refresh = refresh; }

    public OidcProperties getOidc() { return oidc; }
    public void setOidc(OidcProperties oidc) { if (oidc != null) this.oidc = oidc; }

    public DefaultAdminProperties getDefaultAdmin() { return defaultAdmin; }
    public void setDefaultAdmin(DefaultAdminProperties defaultAdmin) { if (defaultAdmin != null) this.defaultAdmin = defaultAdmin; }

    public Pbkdf2Properties getPbkdf2() { return pbkdf2; }
    public void setPbkdf2(Pbkdf2Properties pbkdf2) { if (pbkdf2 != null) this.pbkdf2 = pbkdf2; }

    public LockoutProperties getLockout() { return lockout; }
    public void setLockout(LockoutProperties lockout) { if (lockout != null) this.lockout = lockout; }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) {
        if (publicPaths != null && !publicPaths.isEmpty()) {
            this.publicPaths = publicPaths;
        }
    }

    public boolean enabled() { return isEnabled(); }
    public JwtProperties jwt() { return getJwt(); }
    public RefreshProperties refresh() { return getRefresh(); }
    public OidcProperties oidc() { return getOidc(); }
    public DefaultAdminProperties defaultAdmin() { return getDefaultAdmin(); }
    public Pbkdf2Properties pbkdf2() { return getPbkdf2(); }
    public LockoutProperties lockout() { return getLockout(); }
    public List<String> publicPaths() { return getPublicPaths(); }

    public static class JwtProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private String secret;
        private Duration ttl = DEFAULT_AUTH_JWT_TTL;

        public JwtProperties() {}
        public JwtProperties(String secret, Duration ttl) {
            this.secret = secret;
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) this.ttl = ttl;
        }

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) {
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) this.ttl = ttl;
        }

        public String secret() { return getSecret(); }
        public Duration ttl() { return getTtl(); }
    }

    public static class RefreshProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private Duration ttl = DEFAULT_AUTH_REFRESH_TTL;

        public RefreshProperties() {}
        public RefreshProperties(Duration ttl) {
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) this.ttl = ttl;
        }

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) {
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) this.ttl = ttl;
        }

        public Duration ttl() { return getTtl(); }
    }

    public static class OidcProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private String jwksUrl = DEFAULT_AUTH_OIDC_JWKS_URL;
        private String issuer = DEFAULT_AUTH_OIDC_ISSUER;

        public OidcProperties() {}
        public OidcProperties(String jwksUrl, String issuer) {
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

    public static class DefaultAdminProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private String password;

        public DefaultAdminProperties() {}
        public DefaultAdminProperties(String password) { this.password = password; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String password() { return getPassword(); }
    }

    public static class Pbkdf2Properties implements Serializable {
        private static final long serialVersionUID = 1L;
        private int iterations = DEFAULT_AUTH_PBKDF2_ITERATIONS;

        public Pbkdf2Properties() {}
        public Pbkdf2Properties(int iterations) {
            if (iterations > 0) this.iterations = iterations;
        }

        public int getIterations() { return iterations; }
        public void setIterations(int iterations) {
            if (iterations > 0) this.iterations = iterations;
        }

        public int iterations() { return getIterations(); }
    }

    public static class LockoutProperties implements Serializable {
        private static final long serialVersionUID = 1L;
        private int maxAttempts = DEFAULT_AUTH_LOCKOUT_MAX_ATTEMPTS;
        private int minutes = DEFAULT_AUTH_LOCKOUT_MINUTES;

        public LockoutProperties() {}
        public LockoutProperties(int maxAttempts, int minutes) {
            if (maxAttempts > 0) this.maxAttempts = maxAttempts;
            if (minutes > 0) this.minutes = minutes;
        }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts > 0) this.maxAttempts = maxAttempts;
        }
        public int getMinutes() { return minutes; }
        public void setMinutes(int minutes) {
            if (minutes > 0) this.minutes = minutes;
        }

        public int maxAttempts() { return getMaxAttempts(); }
        public int minutes() { return getMinutes(); }
    }
}
