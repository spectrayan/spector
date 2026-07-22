# Implementation Plan: Multi-User Support with Authentication and Per-User Data Isolation

## Overview

This plan converts the design into incremental Java (JDK 25, `--enable-preview`) coding tasks for
`synapse/spector-synapse` (with a small, additive contribution to `memory/spector-memory` for
layout versioning/migration). Work proceeds bottom-up: configuration and schema foundations first,
then the storage/sharding and migration layer, then the Spring Security stack (password encoder,
user store, lockout, principal resolution, API keys, JWT decoders, filter chain), then the auth
REST surface, then per-user memory routing and request-thread capture, and finally MCP isolation
and end-to-end wiring. Each step builds on the previous ones and ends by wiring components into the
running filter chain and controllers so no code is left orphaned.

All tasks honor repo constraints: no `synchronized` (use `ReentrantLock`/`StampedLock`/
`ConcurrentHashMap`), no `System.out` (SLF4J only), secrets via `${env:VAR}`, `AutoCloseable` for
native-resource holders, parameterized `JdbcClient` statements, and BSL headers matching existing
`spector-synapse` files. Property-based tests use **jqwik**; unit/integration tests use JUnit 5 +
AssertJ + Mockito and run under the `-Psynapse` profile.

## Tasks

- [x] 1. Configuration and dependency foundation
  - [x] 1.1 Add OAuth2 resource server dependency and `AuthProperties`
    - Add `spring-boot-starter-oauth2-resource-server` to the `spector-synapse` `pom.xml` and add
      the `jqwik` test dependency to the reactor test scope
    - Add an `AuthProperties` sub-record to the existing `SynapseProperties`
      (`@ConfigurationProperties("spector")`) record covering `enabled`, `jwt.secret`, `jwt.ttl`,
      `refresh.ttl`, `oidc.jwks-url`, `oidc.issuer`, `default-admin.password`, `pbkdf2.iterations`,
      `lockout.max-attempts`, `lockout.minutes`, `public-paths`, with documented defaults
    - Wire secrets (`jwt.secret`, `default-admin.password`) through `${env:VAR}` placeholders in
      the config YAML; define no tenant-scoped keys
    - _Requirements: 18.1, 18.2, 18.4, 18.6_

  - [x] 1.2 Enforce startup validation for auth configuration
    - Fail startup with a key-identifying error when `jwt.secret`/`default-admin.password` resolve
      empty while `enabled=true`, when `pbkdf2.iterations` < 1 (default 310000 when absent),
      when `lockout.max-attempts` is outside 1..100, or `lockout.minutes` is outside 1..1440
    - _Requirements: 13.6, 14.6, 14.7, 18.3, 18.5_

  - [x]* 1.3 Write unit tests for `AuthProperties` binding and startup validation
    - Test default application, `${env:VAR}` resolution, and each invalid-range abort path
    - _Requirements: 13.6, 14.6, 14.7, 18.3, 18.4, 18.5_

- [x] 2. Relational schema (Flyway `V2__multi_user_auth.sql`)
  - [x] 2.1 Author the `V2__multi_user_auth.sql` migration
    - Create `users`, `api_keys`, `refresh_tokens`, `jti_blocklist` tables per the ER model
      (no `tenants` table, no tenant foreign keys); `username` unique, hashes stored as columns
    - Ensure `FlywayConfig` picks up the migration under `db/migration`
    - _Requirements: 15.1, 15.2, 16.3, 18.6_

  - [x] 2.2 Write integration test for the Flyway migration
    - Apply `V2__multi_user_auth.sql` on a fresh in-memory H2 datasource and assert the schema
      (tables, unique `username`, no `tenants`)
    - _Requirements: 15.1, 15.2, 16.3_

- [x] 3. Storage layout sharding validation and property coverage (memory/spector-memory)
  - [x] 3.1 Confirm and harden `StorageLayout.namespaceDirSharded` validation
    - Verify/adjust identifier rejection (contains `/`, `\`, `.`, null byte, U+0000..U+001F;
      null/empty/whitespace; length > 256) so it raises an invalid-identifier error, resolves no
      path, and performs no filesystem mutation; confirm two-segment `^[0-9a-f]{2}$` sharding from
      the SHA-256 hex digest and descendant-of-base resolution
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x]* 3.2 Write property test for per-user filesystem isolation
    - **Property 1: Per-user filesystem isolation (no overlap / no traversal)**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**

  - [x]* 3.3 Write property test for path determinism / purity
    - **Property 2: Path determinism / purity** (equal `Path` across repeated/concurrent calls, no
      filesystem mutation)
    - **Validates: Requirements 8.8**

  - [x]* 3.4 Write property test for sharding well-formedness
    - **Property 9: Sharding well-formedness** (two segments match `^[0-9a-f]{2}$` and equal the
      first/second SHA-256 hex byte-pairs)
    - **Validates: Requirements 8.7**

- [x] 4. Data-layout versioning and migration (memory/spector-memory)
  - [x] 4.1 Implement `DataLayoutVersion`
    - `CURRENT=4`, `LEGACY_FLAT=0`, `read`/`write` of `layout.version` (0 when absent), and
      `isLegacyFlat`
    - _Requirements: 17.2, 17.3, 17.5_

  - [x] 4.2 Implement `LayoutMigrator.migrateIfNeeded(dataRoot, defaultUserId)`
    - No-op when `read >= CURRENT`; otherwise copy flat `runtime/`+`partitions/` under
      `namespaces/AA/BB/{defaultUserId}/`, verify byte-for-byte, then set version to CURRENT;
      retain originals until verified; fail-closed leaving version unchanged on error
    - _Requirements: 17.1, 17.2, 17.3, 17.6, 17.7, 17.8_

  - [x]* 4.3 Write property test for migration idempotency and monotonicity
    - **Property 10: Migration idempotency & monotonicity** (double-apply equals single-apply; read
      version never decreases)
    - **Validates: Requirements 17.4, 17.5**

  - [x]* 4.4 Write unit test for migration fail-closed and verification
    - Seed a flat tree; assert byte-identical destinations, retained originals on failure, and
      unchanged version on error
    - _Requirements: 17.6, 17.7, 17.8_

- [x] 5. Checkpoint - foundation
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Password encoding and user account store
  - [x] 6.1 Expose `Pbkdf2PasswordEncoder` bean
    - Configure iteration count from `spector.auth.pbkdf2.iterations` (default 310000); rely on the
      encoder's built-in salting and constant-time `matches`
    - _Requirements: 14.1, 14.6, 14.7_

  - [x]* 6.2 Write property test for password round-trip
    - **Property 5: Password round-trip via `Pbkdf2PasswordEncoder`** (matches own encoding; differs
      for distinct passwords; independent per-encode salt still matches)
    - **Validates: Requirements 14.2, 14.3, 14.4**

  - [x] 6.3 Implement `UserAccountStore` with `JdbcClient`
    - `createUser` (generate 13-char TSID `user_id`, store `{pbkdf2}` hash, case-insensitive unique
      `username`), `changePassword`, `forceResetPassword`, `findByUsername`/`findByUserId`,
      `listUsers`, `deactivateUser`, `seedDefaultAdmin` (must-change-password), and
      `recordFailure`/`recordSuccess` lockout counters; never persist/log raw passwords
    - _Requirements: 12.1, 12.2, 12.7, 14.1, 14.5, 16.1, 16.2, 16.3, 16.4_

  - [x]* 6.4 Write property test for TSID / PII avoidance
    - **Property 8: TSID / PII avoidance** (13-char TSID `user_id`; `username` never appears in
      namespace path, JWT `sub`, API-key hashes, or foreign keys)
    - **Validates: Requirements 16.1, 16.2**

  - [x] 6.5 Implement `JdbcUserDetailsService`
    - `loadUserByUsername` returns `UserDetails` whose `getUsername()` is the TSID principal,
      password is the stored `{pbkdf2}` hash, authorities are `ROLE_*`+`SCOPE_*`, `isEnabled()` maps
      `active`, and `isAccountNonLocked()` maps `locked_until`
    - _Requirements: 7.1, 12.1_

  - [x]* 6.6 Write unit tests for `UserAccountStore` and `JdbcUserDetailsService`
    - Against in-memory H2: create/find/change-password, duplicate-username rejection, seed
      idempotency, and `UserDetails` mapping (locked/disabled)
    - _Requirements: 12.1, 12.2, 12.7, 16.1, 16.3, 16.4_

- [x] 7. Account lockout listener
  - [x] 7.1 Implement the lockout `ApplicationListener`
    - On `AuthenticationFailureBadCredentialsEvent`: when not currently locked, increment
      `failed_login_count`; lock (`locked_until = now + lockout.minutes`) at `max-attempts`; do not
      clear/extend or increment while already locked. On `AuthenticationSuccessEvent`: reset counter,
      clear lock, set `last_login_at`
    - _Requirements: 2.5, 13.1, 13.2, 13.3, 13.4, 13.5_

  - [x]* 7.2 Write property test for lockout monotonicity
    - **Property 6: Lockout monotonicity** (locked after ≥ max-attempts until `locked_until`
      elapses; failure never clears an existing lock; success resets count to 0)
    - **Validates: Requirements 2.5, 13.1, 13.2, 13.3, 13.4, 13.5**

- [x] 8. Principal resolution (`SecurityUtils` rewrite)
  - [x] 8.1 Rewrite `SecurityUtils` to read `SecurityContextHolder`
    - `getUserId()` returns the non-anonymous principal name (TSID) or `"default"` when anonymous,
      auth disabled, or principal name null/empty; add `getScopes()`/`hasScope()`/`isAuthenticated()`;
      pin `getTenantId()` to `"default"`; pure reads with no context mutation
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x]* 8.2 Write unit tests for `SecurityUtils`
    - Anonymous → `"default"`, authenticated → TSID, null/empty principal → `"default"`, scope
      extraction, and idempotent resolution within an unchanged context
    - _Requirements: 7.1, 7.2, 7.4, 7.5_

- [x] 9. API-key storage and extended authentication filter
  - [x] 9.1 Implement `ApiKeyStore`
    - `JdbcClient`-backed create (persist only SHA-256 hex hash, return raw key once), revoke, and
      `findActiveByHash` (non-revoked, non-expired) lookups
    - _Requirements: 12.6, 15.1, 15.3, 15.4, 15.6_

  - [x] 9.2 Extend `ApiKeyAuthenticationFilter`
    - Act only on `/api/`+`/mcp`; extract key preferring `Authorization: Bearer` over `X-API-Key`
      (value length 1..512); when auth enabled, bind the owning user's `Authentication` with the
      key's `SCOPE_*` on a matching active row, else leave unauthenticated; when auth disabled, keep
      legacy shared-key `ROLE_API`; never log raw keys; always continue the chain
    - _Requirements: 1.2, 1.3, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 15.3, 15.4_

  - [x]* 9.3 Write property test for API-key hash non-reversibility
    - **Property 7: API-key hash non-reversibility** (only SHA-256 persisted; stored hash ≠ raw;
      validation succeeds iff `SHA-256(presented) == stored` on an active row)
    - **Validates: Requirements 15.1, 15.3, 5.3**

  - [x]* 9.4 Write unit tests for the extended filter
    - Legacy shared-key `ROLE_API` (auth disabled), hashed per-user match (auth enabled), header
      precedence, unknown/revoked/expired key → unauthenticated, no raw-key logging, via
      `MockHttpServletRequest`/`MockFilterChain`
    - _Requirements: 1.2, 1.3, 5.1, 5.4, 5.5, 5.6, 5.7, 5.8_

- [x] 10. JWT decoding (server HS256 + external OIDC RS256)
  - [x] 10.1 Implement server HS256 `JwtDecoder`
    - `NimbusJwtDecoder` from `spector.auth.jwt.secret` validating signature, issuer, and expiry
      with ≤60s clock-skew; map authorities from `scope`/`roles`; require `sub`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 10.2 Implement OIDC RS256 `JwtDecoder` and issuer routing
    - JWKS-backed decoder (enabled only when `oidc.jwks-url` non-empty) validating RS256 signature,
      `iss == oidc.issuer`, and expiry with ≤60s skew and a 5s JWKS fetch bound; route by `iss` via
      `JwtIssuerAuthenticationManagerResolver`/composite decoder
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x]* 10.3 Write unit tests for both decoders
    - Valid/invalid/expired/malformed/missing-`sub` HS256; wrong-issuer/expired/unreachable-JWKS
      RS256; issuer-based decoder selection
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 4.3, 4.4, 4.5, 4.6, 4.7_

- [x] 11. Security composition root (`SecurityConfig`)
  - [x] 11.1 Assemble the `SecurityFilterChain` and providers
    - `@EnableWebSecurity`+`@EnableMethodSecurity`; register `DaoAuthenticationProvider` (encoder +
      `JdbcUserDetailsService`), OAuth2 resource server with the issuer-routing decoder, and the
      extended `ApiKeyAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`; when
      `enabled=false` → `permitAll` (legacy behavior) + one startup WARN about being unauthenticated;
      when `enabled=true` → stateless, CSRF off, `public-paths` permitted, `/api/**`+`/mcp` require
      authentication; enforce scope/role via `authorizeHttpRequests`/`@PreAuthorize`
    - _Requirements: 1.1, 1.6, 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x]* 11.2 Write property test for the auth invariant on protected paths
    - **Property 4: Auth invariant on protected paths** (non-anonymous `Authentication` observed
      downstream iff valid credentials satisfy route authorization; otherwise 401/403 and handler
      not invoked)
    - **Validates: Requirements 6.1, 6.3, 6.4**

  - [x]* 11.3 Write unit/slice tests for authorization gating
    - Public-path bypass, 401 on missing credential, 403 on insufficient scope/role, and the
      `enabled=false` permit-all + startup WARN
    - _Requirements: 1.1, 1.6, 6.2, 6.3, 6.4, 6.5_

- [x] 12. Checkpoint - security stack
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Identity lifecycle REST surface
  - [x] 13.1 Implement `RefreshTokenStore` and `JtiBlocklist`
    - Persist only SHA-256 hashes of refresh tokens; `findActive` (non-revoked, non-expired);
      add/query `jti_blocklist`
    - _Requirements: 12.4, 15.2, 15.5, 15.6_

  - [x] 13.2 Implement `AuthController` login/refresh/logout
    - `POST /login` via `AuthenticationManager` → HS256 access token (`sub=userId`, scope/roles
      claim, 3600s) + refresh token (2,592,000s); uniform 401 on bad/locked credentials (no
      enumeration, no plaintext retained/logged); 400 on missing fields; `POST /refresh` issues a
      new access token only for an active token; `POST /logout` blocklists the `jti`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 2.7, 12.3, 12.4, 19.2_

  - [x] 13.3 Implement account and API-key management endpoints
    - `POST /register` (scope-gated; unique 1..64 username, 8..128 password → returns TSID; 409
      duplicate / 400 length), `POST /change-password` (401 on wrong current; clears
      must-change-password), `GET/PUT /users/{id}` (`hasRole('ADMIN')`; 404 on missing), `POST`/
      `DELETE /api-keys` (per-user issuance, raw key shown once); 401/403 on missing/insufficient
      auth
    - _Requirements: 12.1, 12.2, 12.5, 12.6, 12.8, 12.10, 12.11, 12.12, 16.4_

  - [x]* 13.4 Write integration tests for the auth REST surface
    - login (success/uniform-401/locked/missing-field), refresh (valid/expired/revoked), logout
      blocklisting, register validation, change-password, admin user list/update, api-key once-only
    - _Requirements: 2.1, 2.3, 2.6, 2.7, 12.2, 12.3, 12.4, 12.8, 12.9, 12.11, 12.12_

- [x] 14. Per-user memory routing (`UserMemoryRegistry`)
  - [x] 14.1 Implement `UserMemoryRegistry` (`AutoCloseable`)
    - `resolveForCurrentRequest()` reads `SecurityContextHolder` on the request thread; shared
      instance when disabled/anonymous; else `ConcurrentHashMap.computeIfAbsent(userId, ...)` lazily
      building a `SpectorMemory` rooted at `namespaceDirSharded(base, userId)`; ignore
      client-supplied `namespace`/`workspace_id`/`agent_id`; LRU/idle cap closing evicted instances
      (never the shared one); fail-closed on construction error; `close()` closes all instances once
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 1.4, 19.3_

  - [x]* 14.2 Write property test for routing isolation under adversarial input
    - **Property 3: Routing isolation under adversarial client input** (any client-supplied
      namespace/workspace_id/agent_id still routes to the authenticated user's own namespace)
    - **Validates: Requirements 9.3, 11.2, 11.3**

  - [x]* 14.3 Write unit tests for `UserMemoryRegistry`
    - Anonymous → shared, authenticated → cached per-user, adversarial namespace ignored, eviction
      closes evicted instance, fail-closed on build error, `close()` closes all
    - _Requirements: 9.2, 9.4, 9.5, 9.6, 9.7, 1.4_

- [x] 15. Request-thread capture for asynchronous writes
  - [x] 15.1 Rework `MemoryAccessObject` to accept a resolved `SpectorMemory`
    - Replace the single injected instance; data-access methods take a `SpectorMemory mem` parameter
    - _Requirements: 10.2, 10.4_

  - [x] 15.2 Rework `MemoryService` to capture on the request thread
    - Resolve via `UserMemoryRegistry.resolveForCurrentRequest()` on the request thread and close
      over that reference in the `virtualThreadExecutor` lambda; never read `SecurityContextHolder`
      in the async body; confine and log failures without echoing payloads
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x]* 15.3 Write integration test for concurrent request-thread capture
    - Two authenticated users issue concurrent `remember`; assert each write lands in its own
      per-user instance and never crosses
    - _Requirements: 10.2, 10.4, 10.5_

- [x] 16. MCP tool isolation
  - [x] 16.1 Route MCP tools to the caller's namespace
    - In the `/mcp` servlet path, resolve memory via `UserMemoryRegistry.resolveForCurrentRequest()`
      on the servlet request thread; confine client-supplied `namespace`/`workspace_id`/`agent_id`
      to the authenticated namespace (ignore traversal/other-user values without denying on that
      basis); deny with a tool-error result on unresolved auth or resolution failure (no shared/
      other-user fallback); stdio (no context) → shared memory
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 19.1, 19.3, 19.4_

  - [x]* 16.2 Write integration tests for MCP isolation
    - Authenticated HTTP `remember`/`recall` route to caller namespace; two users never cross;
      unauthenticated/resolution-failure → tool error (no fallback); stdio → shared
    - _Requirements: 11.1, 11.4, 11.5, 11.6, 11.7_

- [x] 17. Fail-closed error handling and end-to-end wiring
  - [x] 17.1 Centralize fail-closed auth/error responses
    - Map missing credentials → 401, invalid → 401 (uniform), expired → 401, insufficient → 403,
      unsafe namespace identifier → 400 with DEBUG log omitting the raw value, DB-unavailable → 401
      with SLF4J log and no data mutation; exclude secrets/raw keys from all responses and logs;
      never fall back to shared/other-user memory when enabled
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6_

  - [x] 17.2 Wire migration and default-admin seeding into startup
    - When `enabled=true`, run `LayoutMigrator.migrateIfNeeded(dataRoot, defaultUserId)` and
      `UserAccountStore.seedDefaultAdmin(...)` at startup; when `enabled=false`, leave the flat
      layout untouched and skip seeding
    - _Requirements: 1.5, 12.7, 17.1_

  - [x]* 17.3 Write end-to-end isolation integration test
    - login → JWT → write as user A → recall as user B returns nothing; external OIDC stub JWKS
      accepted while wrong-issuer/expired rejected; migration runs only when enabled and is
      idempotent
    - _Requirements: 8.2, 9.2, 4.1, 4.4, 17.1, 17.4_

- [x] 18. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core
  implementation sub-tasks are never optional.
- Property tests (jqwik) validate the 10 universal correctness properties from the design; unit and
  integration tests (JUnit 5 + AssertJ + Mockito, `-Psynapse`) cover examples and edge cases.
- Each task references specific requirement sub-clauses for traceability, and property sub-tasks are
  placed next to the implementation they validate to catch errors early.
- Checkpoints provide incremental validation points across the security stack.
- Build/verify with `mvn verify -Psynapse`; honor JDK 25 `--enable-preview` and the repo's
  no-`synchronized`/no-`System.out`/`${env:VAR}`/`AutoCloseable` constraints.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.1", "3.2", "3.3", "3.4", "4.1"] },
    { "id": 2, "tasks": ["2.2", "4.2", "6.1", "8.1"] },
    { "id": 3, "tasks": ["4.3", "4.4", "6.2", "6.3", "8.2", "9.1", "10.1", "10.2"] },
    { "id": 4, "tasks": ["6.4", "6.5", "6.6", "7.1", "9.2", "9.3", "10.3", "14.1"] },
    { "id": 5, "tasks": ["7.2", "9.4", "11.1", "13.1", "14.2", "14.3", "15.1"] },
    { "id": 6, "tasks": ["11.2", "11.3", "13.2", "15.2", "16.1"] },
    { "id": 7, "tasks": ["13.3", "15.3", "16.2", "17.1", "17.2"] },
    { "id": 8, "tasks": ["13.4", "17.3"] }
  ]
}
```
