# Requirements Document

## Introduction

This feature adds first-class multi-user support to the OSS Spector server (`synapse/spector-synapse`): authenticated identities, scope/role-based authorization, and complete per-user data isolation across the filesystem, memory-routing, REST, and MCP-tool layers. Spector OSS is single-tenant, multi-user — there is no organization/tenant boundary; the isolation boundary is the individual authenticated user.

The feature builds entirely on the Spring Security stack already present in `spector-synapse` (filter chain, `Authentication`/`GrantedAuthority`, `Pbkdf2PasswordEncoder`, OAuth2 Resource Server `JwtDecoder`, `UserDetailsService` + `DaoAuthenticationProvider`) and the existing H2 + Spring `JdbcClient` + Flyway + HikariCP persistence. No new security primitives are added to `nucleus/spector-commons`, and the per-user sharded path reuses the existing `StorageLayout.namespaceDirSharded(...)` helper in `memory/spector-memory`.

The feature is off by default (`spector.auth.enabled=false`), preserving the current single shared `SpectorMemory` and the legacy single shared-key `ROLE_API` behavior exactly. When enabled, identity is derived from validated credentials and drives a deterministic filesystem path so that no user can read or resolve another user's data. These requirements are derived from the approved design document.

## Glossary

- **Spector_Server**: The `spector-synapse` gateway application that hosts REST endpoints, the MCP-over-HTTP servlet, and the Spring Security filter chain.
- **User**: The single isolation boundary. A principal identified by a 13-character TSID (`user_id`). The `username` is a login handle only and is never used in paths, the JWT `sub` claim, or foreign keys.
- **User_Id**: The 13-character TSID that identifies a User; used as the Spring Security principal name and the JWT `sub` claim.
- **Namespace**: A User's isolated memory workspace, named after the User_Id itself (`{userId}`, the TSID), resolved to a single-level sharded directory.
- **Authentication_Filter**: The extended `ApiKeyAuthenticationFilter` (`OncePerRequestFilter`) that validates hashed per-user API keys and the legacy shared key.
- **Resource_Server**: The OAuth2 Resource Server `JwtDecoder` configuration that validates server-issued HS256 tokens and external OIDC RS256 tokens.
- **Auth_Controller**: The REST controller at `/api/v1/auth` that exposes the identity lifecycle endpoints.
- **User_Account_Store**: The JDBC-backed store (`UserAccountStore` + `JdbcUserDetailsService`) responsible for user account persistence, lookup, password changes, and lockout counters.
- **User_Memory_Registry**: The `UserMemoryRegistry` component that resolves a per-user `SpectorMemory` instance on the request thread.
- **Memory_Service**: The `MemoryService` that resolves the per-user memory instance on the request thread and captures it in async write tasks.
- **Password_Encoder**: The Spring Security `Pbkdf2PasswordEncoder` bean used to hash and verify passwords.
- **Lockout_Listener**: The `ApplicationListener` for authentication success/failure events that enforces account lockout.
- **Storage_Layout**: The existing `StorageLayout.namespaceDirSharded(basePath, namespaceId)` helper that resolves a single-level sharded per-user directory.
- **Layout_Migrator**: The `LayoutMigrator` in `memory/spector-memory` that performs idempotent, versioned relocation of a flat layout into the default user's namespace.
- **Anonymous**: The state where no non-anonymous `Authentication` is bound (auth disabled, or credentials absent on a public path); resolves to the single shared memory and the User_Id value `"default"`.
- **Scope**: A fine-grained permission expressed as a `GrantedAuthority` with the `SCOPE_` prefix (e.g., `SCOPE_memory:read`). Roles use the `ROLE_` prefix (`ROLE_ADMIN`, `ROLE_USER`, `ROLE_API`, `ROLE_AGENT`).

## Requirements

### Requirement 1: Feature Toggle and Backward Compatibility

**User Story:** As an operator, I want multi-user authentication to be off by default, so that existing single-user deployments continue to behave exactly as before until I explicitly enable it.

#### Acceptance Criteria

1. WHERE `spector.auth.enabled` is `false`, THE Spector_Server SHALL grant access to every requested path, including `/api/**` and `/mcp`, without requiring or evaluating any authentication credential.
2. WHERE `spector.auth.enabled` is `false`, WHEN a request presents an API key equal to the configured shared key, THE Authentication_Filter SHALL bind an Authentication carrying the `ROLE_API` authority to the security context for that request only.
3. WHERE `spector.auth.enabled` is `false`, IF a request presents an API key that does not equal the configured shared key, THEN THE Spector_Server SHALL still grant access to the requested path and SHALL NOT bind any authenticated Authentication for that request.
4. WHERE `spector.auth.enabled` is `false`, THE User_Memory_Registry SHALL return the same single shared `SpectorMemory` instance for every request, regardless of the number of concurrent callers.
5. WHERE `spector.auth.enabled` is `false`, THE Layout_Migrator SHALL leave the existing flat filesystem layout unchanged and SHALL NOT create any per-user sharded directories.
6. WHERE `spector.auth.enabled` is `false`, WHEN the Spector_Server completes startup, THE Spector_Server SHALL emit exactly one warning-level log entry indicating that the server is unauthenticated and should not be exposed beyond localhost.

### Requirement 2: Username and Password Login

**User Story:** As a user, I want to log in with a username and password, so that I receive tokens that authenticate my subsequent requests.

#### Acceptance Criteria

1. WHEN a username and password matching an existing, enabled, non-locked User are submitted to the `/api/v1/auth/login` endpoint, THE Auth_Controller SHALL return an HTTP 200 response carrying a server-issued HS256 access token that expires 3600 seconds (1 hour) after issuance and a refresh token that expires 2,592,000 seconds (30 days) after issuance.
2. WHEN a login access token is issued, THE Auth_Controller SHALL set the token `sub` claim to the User_Id (never the login username) and include a claim carrying the User's scopes and roles.
3. IF the submitted credentials do not match an existing, enabled, non-locked User, THEN THE Auth_Controller SHALL return HTTP 401 with a uniform error message that is identical whether or not the username exists, and SHALL NOT retain or log the submitted plaintext password.
4. THE User_Account_Store SHALL delegate password verification to the Password_Encoder using its built-in constant-time comparison.
5. IF a User accumulates 5 consecutive failed login attempts, THEN THE Auth_Controller SHALL lock that User account for 900 seconds (15 minutes) measured from the last failed attempt, and a subsequent successful authentication SHALL reset the consecutive-failure count to 0 and clear the lock.
6. WHILE a User account is locked, THE Auth_Controller SHALL reject every login attempt for that account with HTTP 401 and the same uniform error message used for invalid credentials, without verifying the submitted password.
7. IF a login request omits the username field or the password field, THEN THE Auth_Controller SHALL return HTTP 400 with an error message indicating the missing required field and SHALL NOT attempt credential verification.

### Requirement 3: Server-Issued JWT Validation

**User Story:** As an API client, I want my server-issued bearer token validated, so that only valid, unexpired tokens grant access.

#### Acceptance Criteria

1. WHEN a request presents a server-issued HS256 bearer token, THE Resource_Server SHALL validate, within 200 milliseconds, the token signature against the configured `spector.auth.jwt.secret`, the issuer claim, and the expiry claim, applying a clock-skew tolerance of at most 60 seconds when evaluating expiry.
2. WHEN a valid, unexpired server-issued token is validated, THE Resource_Server SHALL bind an Authentication whose principal name is the token `sub` claim and whose authorities are the union of the values present in the token scope claim and the token roles claim.
3. IF a presented bearer token has an invalid signature or fails issuer validation, THEN THE Resource_Server SHALL return HTTP 401 with an invalid-credentials error and SHALL NOT bind an Authentication.
4. IF a presented bearer token is expired beyond the 60-second clock-skew tolerance, THEN THE Resource_Server SHALL return HTTP 401 with a token-expired error and SHALL NOT bind an Authentication.
5. IF a presented bearer token is malformed or cannot be parsed as an HS256 JWT, THEN THE Resource_Server SHALL return HTTP 401 with an invalid-credentials error and SHALL NOT bind an Authentication.
6. IF a presented bearer token is missing the `sub` claim, THEN THE Resource_Server SHALL return HTTP 401 with an invalid-credentials error and SHALL NOT bind an Authentication.

### Requirement 4: External OIDC JWT Validation

**User Story:** As an operator integrating an external identity provider, I want OIDC RS256 tokens validated via JWKS, so that users authenticated by my IdP can access Spector.

#### Acceptance Criteria

1. WHERE `spector.auth.oidc.jwks-url` is non-empty, THE Resource_Server SHALL verify the RS256 signature of external bearer tokens using the public keys retrieved from that JWKS endpoint.
2. WHEN an external RS256 token is validated, THE Resource_Server SHALL confirm that the token issuer claim equals `spector.auth.oidc.issuer` and that the current time is not later than the token expiration claim, allowing at most 60 seconds of clock-skew tolerance.
3. WHEN both a server issuer and an external OIDC issuer are configured, THE Resource_Server SHALL select the validating decoder based on the token issuer claim.
4. IF an external RS256 token carries an issuer that does not equal `spector.auth.oidc.issuer`, THEN THE Resource_Server SHALL reject the request with HTTP 401 and an invalid-credentials error, and SHALL NOT establish an authenticated session.
5. IF the JWKS endpoint cannot be reached or returns no usable signing key within 5 seconds, THEN THE Resource_Server SHALL reject the request with HTTP 401 and an error indicating the token could not be validated, and SHALL NOT establish an authenticated session.
6. IF the RS256 signature verification of an external bearer token fails, THEN THE Resource_Server SHALL reject the request with HTTP 401 and an invalid-credentials error, and SHALL NOT establish an authenticated session.
7. IF an external RS256 token's expiration claim is earlier than the current time beyond the 60-second clock-skew tolerance, THEN THE Resource_Server SHALL reject the request with HTTP 401 and an error indicating the token has expired, and SHALL NOT establish an authenticated session.

### Requirement 5: Per-User API Key Authentication

**User Story:** As a user, I want to authenticate agents with a per-user API key, so that non-interactive clients can access only my data.

#### Acceptance Criteria

1. WHEN a request presents both the `Authorization: Bearer <key>` header and the `X-API-Key` header, THE Authentication_Filter SHALL extract the API key from the `Authorization: Bearer <key>` header and ignore the `X-API-Key` header.
2. WHEN a request presents exactly one of the `Authorization: Bearer <key>` header or the `X-API-Key` header with a value between 1 and 512 characters, THE Authentication_Filter SHALL extract the API key from the present header.
3. WHERE `spector.auth.enabled` is `true` AND a presented API key hashes with SHA-256 to a non-revoked, non-expired row in the `api_keys` table, THE Authentication_Filter SHALL bind an Authentication whose principal is the owning User_Id and whose authorities are the key's scopes.
4. WHERE `spector.auth.enabled` is `true`, IF a presented API key does not match any non-revoked, non-expired row in the `api_keys` table, THEN THE Authentication_Filter SHALL leave the security context unauthenticated.
5. WHERE `spector.auth.enabled` is `true`, IF a request presents neither the `Authorization: Bearer <key>` header nor the `X-API-Key` header, THEN THE Authentication_Filter SHALL leave the security context unauthenticated.
6. WHERE `spector.auth.enabled` is `false`, THE Authentication_Filter SHALL leave the security context unauthenticated without inspecting any API key.
7. THE Authentication_Filter SHALL exclude raw API key values from all log output, including messages emitted on validation-failure and exception paths.
8. THE Authentication_Filter SHALL continue the filter chain after processing the request regardless of whether authentication was bound.

### Requirement 6: Request Authorization by Scope and Role

**User Story:** As an operator, I want protected endpoints gated by scope and role, so that users can only perform operations they are permitted to perform.

#### Acceptance Criteria

1. WHERE `spector.auth.enabled` is `true`, THE Spector_Server SHALL require a non-anonymous Authentication for every request whose path matches `/api/**` or `/mcp` and is not listed in `spector.auth.public-paths`.
2. WHERE `spector.auth.enabled` is `true`, THE Spector_Server SHALL permit access without a non-anonymous Authentication to each path listed in `spector.auth.public-paths` (default `/actuator/health`, `/api/docs`).
3. IF a request to a protected path carries no non-anonymous Authentication WHERE `spector.auth.enabled` is `true`, THEN THE Spector_Server SHALL return HTTP 401 with an authentication-required error indication, SHALL NOT invoke the downstream handler, and SHALL NOT perform any memory read or write.
4. IF an authenticated User lacks a scope authority or role authority required by the target endpoint WHERE `spector.auth.enabled` is `true`, THEN THE Spector_Server SHALL return HTTP 403 with an insufficient-permissions error indication, SHALL NOT invoke the downstream handler, and SHALL NOT perform any memory read or write.
5. WHERE `spector.auth.enabled` is `true`, WHEN authorizing a request against an endpoint whose required scope is expressed as a `GrantedAuthority` with the `SCOPE_` prefix or whose required role is expressed with the `ROLE_` prefix, THE Spector_Server SHALL grant access only when the authenticated Authentication holds every required scope authority and role authority for that endpoint.

### Requirement 7: Principal Resolution

**User Story:** As a developer, I want the current principal resolved from Spring Security, so that isolation decisions use the authenticated identity rather than a hardcoded value.

#### Acceptance Criteria

1. WHILE a non-anonymous Authentication is bound to the security context and its principal name is a non-null, non-empty value, THE Spector_Server SHALL resolve the current User_Id as that Authentication principal name.
2. WHILE no non-anonymous Authentication is bound to the security context, or `spector.auth.enabled` is `false`, THE Spector_Server SHALL resolve the current User_Id as the literal value `"default"`.
3. WHEN resolving the current User_Id, THE Spector_Server SHALL read the security context without modifying the bound Authentication, without initiating any authentication exchange, and without altering the security context state.
4. IF a non-anonymous Authentication is bound but its principal name is null or an empty string, THEN THE Spector_Server SHALL resolve the current User_Id as the literal value `"default"`.
5. WHEN the current User_Id is resolved two or more times within the same unchanged security context, THE Spector_Server SHALL return an identical User_Id value for each resolution.

### Requirement 8: Per-User Filesystem Isolation

**User Story:** As a user, I want my memory data stored in an isolated directory, so that no other user can read or resolve my data on disk.

#### Acceptance Criteria

1. WHERE `spector.auth.enabled` is `true`, THE Storage_Layout SHALL resolve each User's data directory as `namespaceDirSharded(basePath, userId)`.
2. FOR ANY two distinct User_Id values, THE Storage_Layout SHALL resolve two distinct directory paths where neither path is an ancestor of the other.
3. FOR ANY User_Id, THE Storage_Layout SHALL resolve a directory path that is a descendant of the configured base path and SHALL NOT resolve a path equal to, or outside of, the configured base path.
4. IF a User_Id produces a namespace identifier containing `/`, `\`, `.`, a null byte, or any character in the range U+0000 through U+001F, THEN THE Storage_Layout SHALL reject the identifier before path resolution, raise an error indicating an invalid identifier, resolve no path, and perform no filesystem mutation.
5. IF a User_Id is null, empty, or consists solely of whitespace characters, THEN THE Storage_Layout SHALL reject the identifier, raise an error indicating an invalid identifier, resolve no path, and perform no filesystem mutation.
6. IF a User_Id exceeds 256 characters in length, THEN THE Storage_Layout SHALL reject the identifier, raise an error indicating an invalid identifier, resolve no path, and perform no filesystem mutation.
7. FOR ANY namespace identifier, THE Storage_Layout SHALL produce two shard segments that each match `^[0-9a-f]{2}$` and equal the first and second byte-pairs of the SHA-256 hex digest of the identifier.
8. FOR ANY User_Id, THE Storage_Layout SHALL return byte-for-byte equal directory paths across repeated calls, including across concurrent calls from multiple threads, and SHALL perform no filesystem mutation during resolution.

### Requirement 9: Per-User Memory Routing

**User Story:** As a user, I want each request routed to my own memory workspace, so that reads and writes never touch another user's memory.

#### Acceptance Criteria

1. WHEN the User_Memory_Registry resolves memory for an authenticated request, THE User_Memory_Registry SHALL read the security context on the current request thread.
2. WHERE `spector.auth.enabled` is `true` AND the request is authenticated, THE User_Memory_Registry SHALL return exactly one cached `SpectorMemory` instance per User_Id, lazily built rooted at the User's sharded namespace directory, and SHALL return that same cached instance for every subsequent resolution of the same User_Id while it remains cached.
3. WHEN a request supplies `namespace`, `workspace_id`, or `agent_id` parameters, THE User_Memory_Registry SHALL return the instance rooted at the authenticated User's own sharded namespace directory regardless of the supplied values, and SHALL NOT allow those parameters to select another User's memory.
4. WHILE the request principal is Anonymous, THE User_Memory_Registry SHALL return the single shared `SpectorMemory` instance and SHALL NOT construct a per-user instance.
5. WHEN the Spector_Server shuts down, THE User_Memory_Registry SHALL close every cached per-user `SpectorMemory` instance exactly once and SHALL NOT return from shutdown until all per-user instances are closed.
6. WHEN caching a new instance would exceed the configured maximum instance count, THE User_Memory_Registry SHALL evict and close the cached per-user instance with the oldest last-resolution time before caching the new instance, and SHALL NOT evict the single shared instance.
7. IF memory resolution or lazy construction fails for any reason, THEN THE User_Memory_Registry SHALL fail the request closed, propagate the error to the caller, leave no partially constructed instance cached, and SHALL NOT return the shared instance or another User's instance.

### Requirement 10: Request-Thread Capture for Asynchronous Writes

**User Story:** As a user, I want asynchronous memory writes to use my resolved memory instance, so that concurrent writes from different users never cross.

#### Acceptance Criteria

1. WHEN the Memory_Service processes a write, THE Memory_Service SHALL resolve the per-user `SpectorMemory` instance by reading the security context on the request thread before dispatching the asynchronous write task.
2. THE Memory_Service SHALL bind the asynchronous write task to the `SpectorMemory` instance captured on the request thread for the entire execution of that task, and SHALL NOT resolve or substitute a different `SpectorMemory` instance within that task.
3. THE Memory_Service SHALL exclude any read of the security context from the asynchronous write task body.
4. WHEN two or more authenticated users issue concurrent writes, THE Memory_Service SHALL direct each write to the `SpectorMemory` instance resolved for that write's originating User_Id, and SHALL NOT apply any User's write to another User's `SpectorMemory` instance.
5. IF the asynchronous write task fails, THEN THE Memory_Service SHALL confine the failure to the captured User's `SpectorMemory` instance, SHALL NOT apply the write to any other User's `SpectorMemory` instance, and SHALL log the failure without echoing the written payload.

### Requirement 11: MCP Tool Isolation

**User Story:** As a user invoking MCP tools over HTTP, I want tool calls scoped to my namespace, so that my agent cannot access another user's memory.

#### Acceptance Criteria

1. WHEN an authenticated MCP tool is invoked over the `/mcp` HTTP servlet, THE Spector_Server SHALL resolve the caller's memory on the servlet request thread and route the tool exclusively to the memory instance rooted at the authenticated User's sharded namespace.
2. WHEN an MCP tool receives client-supplied `namespace`, `workspace_id`, or `agent_id` parameters, THE Spector_Server SHALL confine all operations to the authenticated User's namespace and SHALL NOT resolve those parameters to another User's memory.
3. IF a client-supplied `namespace`, `workspace_id`, or `agent_id` references another User's namespace or contains path-traversal sequences (including `.`, `..`, path separators, or absolute path prefixes), THEN THE Spector_Server SHALL ignore the client-supplied value, keep the invocation scoped to the authenticated User's namespace, and SHALL NOT deny the call solely on the basis of the ignored value.
4. IF memory resolution fails for an authenticated MCP call, THEN THE Spector_Server SHALL deny the call, return a tool error result whose content indicates that memory resolution failed, leave every User's memory unmodified, and SHALL NOT fall back to the shared memory or another User's memory.
5. IF an MCP tool is invoked over the `/mcp` HTTP servlet without a resolvable authenticated security context, THEN THE Spector_Server SHALL deny the call, return a tool error result whose content indicates that authentication is required, and SHALL NOT route the tool to the shared memory or any User's memory.
6. WHEN two or more authenticated users invoke MCP tools concurrently over HTTP, THE Spector_Server SHALL route each invocation to the invoking user's own namespace instance and SHALL NOT allow any invocation to read or write another concurrent user's namespace.
7. WHILE an MCP tool is invoked over stdio with no security context, THE Spector_Server SHALL route the tool to the single shared memory.

### Requirement 12: Account Lifecycle Management

**User Story:** As an administrator, I want to manage user accounts and API keys over REST, so that I can provision and maintain identities.

#### Acceptance Criteria

1. WHERE the caller holds the required registration scope, WHEN a registration request with a unique username of 1-64 characters and an 8-128 character password is submitted to `/api/v1/auth/register`, THE Auth_Controller SHALL create a new User with a generated 13-character TSID User_Id and return that User_Id.
2. WHEN a change-password request whose current password matches the stored hash and whose new password is 8-128 characters is submitted to `/api/v1/auth/change-password`, THE Auth_Controller SHALL update the stored password hash and clear the must-change-password flag.
3. WHEN a refresh token that exists, is not revoked, and is not expired is submitted to `/api/v1/auth/refresh`, THE Auth_Controller SHALL issue a new access token valid for the configured `spector.auth.jwt.ttl`.
4. WHEN a logout request is submitted to `/api/v1/auth/logout`, THE Auth_Controller SHALL add the token `jti` to the `jti_blocklist` so subsequent requests presenting that jti are rejected.
5. WHERE the caller holds the `ADMIN` role, WHEN a read request is submitted to `/api/v1/auth/users`, THE Auth_Controller SHALL return the list of User accounts.
6. WHERE the caller is authenticated, WHEN a request is submitted to `/api/v1/auth/api-keys`, THE Auth_Controller SHALL issue a per-user API key, persist only its non-reversible hash, and return the raw key value exactly once.
7. WHEN the Spector_Server first starts with authentication enabled, THE User_Account_Store SHALL idempotently seed a default administrator account with its must-change-password flag set to true.
8. IF a change-password request's current password does not match the stored hash, THEN THE Auth_Controller SHALL return HTTP 401 and SHALL NOT update the stored password hash.
9. IF a refresh request presents a token that does not exist, is revoked, or is expired, THEN THE Auth_Controller SHALL return HTTP 401 and SHALL NOT issue a new access token.
10. IF a caller lacks the required scope or role, or is unauthenticated, for a lifecycle endpoint, THEN THE Auth_Controller SHALL return HTTP 401 for a missing credential or HTTP 403 for insufficient permissions and SHALL NOT perform the requested operation.
11. WHERE the caller holds the `ADMIN` role, WHEN an update request targeting an existing User is submitted to `/api/v1/auth/users`, THE Auth_Controller SHALL apply the update to the targeted User account and return the updated account, and IF the targeted User does not exist, THEN THE Auth_Controller SHALL return HTTP 404 and SHALL NOT modify any account.
12. IF a registration request presents a username that already exists, or a username outside 1-64 characters, or a password outside 8-128 characters, THEN THE Auth_Controller SHALL return HTTP 409 for a duplicate username or HTTP 400 for a length violation, provide an error response indicating the specific validation failure, and SHALL NOT create a new User.

### Requirement 13: Account Lockout

**User Story:** As an operator, I want accounts locked after repeated failed logins, so that brute-force attacks are mitigated.

#### Acceptance Criteria

1. WHEN an authentication failure event occurs AND the User's `locked_until` is not later than the Spector_Server's current time, THE Lockout_Listener SHALL increment the User's `failed_login_count` by exactly 1.
2. WHEN the User's `failed_login_count` reaches or exceeds the configured `spector.auth.lockout.max-attempts` value, THE Lockout_Listener SHALL set the User's `locked_until` to the Spector_Server's current time plus `spector.auth.lockout.minutes` minutes.
3. WHILE a User's `locked_until` timestamp is later than the Spector_Server's current time, THE Spector_Server SHALL reject that User's login attempts with HTTP 401 even when the supplied password is correct.
4. IF an authentication failure occurs while a User's `locked_until` is later than the Spector_Server's current time, THEN THE Lockout_Listener SHALL NOT clear or extend the existing `locked_until` and SHALL NOT increment `failed_login_count`.
5. WHEN an authentication success event occurs while a User's `locked_until` is not later than the Spector_Server's current time, THE Lockout_Listener SHALL reset `failed_login_count` to zero, clear `locked_until`, and set `last_login_at` to the Spector_Server's current time.
6. IF `spector.auth.lockout.max-attempts` is not an integer between 1 and 100 inclusive, or `spector.auth.lockout.minutes` is not an integer between 1 and 1440 inclusive, THEN THE Spector_Server SHALL reject startup and emit an error indicating the invalid lockout configuration value.

### Requirement 14: Password Hashing

**User Story:** As a security-conscious operator, I want passwords stored as salted PBKDF2 hashes, so that stored credentials cannot be reversed.

#### Acceptance Criteria

1. WHEN a User is created or a password is changed, THE User_Account_Store SHALL store the password as a single `{pbkdf2}`-prefixed hash string produced by the Password_Encoder and SHALL NOT store the raw password in any persisted field or return it in any API response.
2. FOR ANY password between 1 and 256 characters, THE Password_Encoder SHALL confirm a match when matching that password against its own encoding.
3. FOR ANY two passwords with differing character sequences, THE Password_Encoder SHALL report no match when matching one password against the other's encoding.
4. WHEN the same password is encoded two or more times, THE Password_Encoder SHALL use an independent per-encode salt so the encoded outputs are not byte-for-byte identical, and each independently-salted encoding SHALL still match the original password.
5. THE User_Account_Store SHALL exclude raw passwords from all persisted fields and all log statements at every log level.
6. WHILE `spector.auth.pbkdf2.iterations` is an integer greater than or equal to 1, THE Password_Encoder SHALL use that value as the iteration count.
7. IF `spector.auth.pbkdf2.iterations` is absent, non-numeric, or less than 1, THEN THE Password_Encoder SHALL apply the documented default iteration count of 310000.

### Requirement 15: API Key and Refresh Token Hash Storage

**User Story:** As a security-conscious operator, I want API keys and refresh tokens stored as non-reversible hashes, so that a database leak does not expose usable credentials.

#### Acceptance Criteria

1. WHEN an API key is created, THE Spector_Server SHALL persist only the SHA-256 hex hash (64 lowercase hexadecimal characters) of the raw key value and SHALL NOT persist the raw key value.
2. WHEN a refresh token is created, THE Spector_Server SHALL persist only the SHA-256 hex hash (64 lowercase hexadecimal characters) of the raw token value and SHALL NOT persist the raw token value.
3. WHEN an API key is validated, THE Authentication_Filter SHALL grant access only if the SHA-256 hash of the presented key equals a stored hash on a row that is both non-revoked and non-expired.
4. IF the SHA-256 hash of a presented API key does not match any stored hash, OR matches only a revoked or expired row, THEN THE Authentication_Filter SHALL deny access and SHALL return a response indicating authentication failure without disclosing whether the key exists.
5. WHEN a refresh token is validated, THE Spector_Server SHALL grant renewal only if the SHA-256 hash of the presented token equals a stored hash on a row that is both non-revoked and non-expired, and IF no such row matches, THEN THE Spector_Server SHALL deny renewal and return a response indicating authentication failure.
6. THE Spector_Server SHALL exclude raw API key and refresh token values from all persisted storage after creation, retaining only their SHA-256 hex hashes.

### Requirement 16: TSID Identity and PII Avoidance

**User Story:** As a privacy-conscious operator, I want identities keyed by TSID rather than usernames, so that login handles never leak into paths, tokens, or data files.

#### Acceptance Criteria

1. WHEN a User is created, THE User_Account_Store SHALL assign a User_Id that is a 13-character TSID string and is unique across all existing Users.
2. THE Spector_Server SHALL exclude the `username` string, in whole or in part, from the resolved namespace directory path, the JWT `sub` claim, API key hashes, and all foreign keys.
3. THE User_Account_Store SHALL enforce that each `username` is unique across all Users using case-insensitive comparison.
4. IF a User creation or update request specifies a `username` that matches an existing User's `username` under case-insensitive comparison, THEN THE User_Account_Store SHALL reject the request, return an error indicating the username is already in use, and leave all existing User records unchanged.

### Requirement 17: Data Layout Versioning and Migration

**User Story:** As an operator enabling authentication on an existing deployment, I want my flat-layout data migrated safely into the default user's namespace, so that no data is lost and migration can run repeatedly.

#### Acceptance Criteria

1. WHERE `spector.auth.enabled` is `true` AND the stored layout version is below the current version, THE Layout_Migrator SHALL relocate the existing flat `runtime/` and `partitions/` directories under `namespaces/AA/BB/{defaultUserId}/`.
2. WHEN relocation completes AND the new layout passes verification, THE Layout_Migrator SHALL set the stored layout version to the current version.
3. WHERE the stored layout version is already at or above the current version, THE Layout_Migrator SHALL perform no filesystem changes and SHALL leave the stored layout version unchanged.
4. FOR ANY data root, THE Layout_Migrator SHALL produce the same final directory tree and stored layout version whether migration runs once or any number of consecutive times.
5. THE Layout_Migrator SHALL ensure the stored layout version never decreases across migrations.
6. WHILE relocating files, THE Layout_Migrator SHALL keep every already-copied file byte-identical to its source (identical byte length and identical byte content) AND SHALL retain the original flat `runtime/` and `partitions/` directories until the new layout is verified.
7. THE Layout_Migrator SHALL treat the new layout as verified only when every regular file under the original flat `runtime/` and `partitions/` directories has a corresponding file at its destination path with identical byte length and identical byte content.
8. IF relocation or verification does not complete successfully, THEN THE Layout_Migrator SHALL retain the original flat `runtime/` and `partitions/` directories unchanged, SHALL leave the stored layout version unchanged, AND SHALL surface an error indicating that migration failed.

### Requirement 18: Configuration Parameters

**User Story:** As an operator, I want authentication behavior controlled through `spector.auth.*` configuration with secrets injected from the environment, so that I can tune the feature without committing secrets.

#### Acceptance Criteria

1. THE Spector_Server SHALL bind the `spector.auth.*` configuration keys through an `AuthProperties` sub-record of the existing `SynapseProperties` record.
2. THE Spector_Server SHALL source `spector.auth.jwt.secret` and `spector.auth.default-admin.password` from environment variables using `${env:VAR}` placeholders.
3. IF the environment variable referenced by `spector.auth.jwt.secret` or `spector.auth.default-admin.password` is unset or resolves to an empty string at startup, THEN THE Spector_Server SHALL abort startup and emit an error message identifying the unresolved configuration key, without starting the authentication feature.
4. WHERE a `spector.auth.*` configuration key is not supplied, THE Spector_Server SHALL apply the documented default value defined for that key in the configuration reference.
5. IF a supplied `spector.auth.*` value fails its documented type or range validation, THEN THE Spector_Server SHALL abort startup and emit an error message identifying the invalid configuration key, without applying a partial configuration.
6. THE Spector_Server SHALL NOT define any tenant-scoped configuration keys.

### Requirement 19: Fail-Closed Error Handling

**User Story:** As a security-conscious operator, I want authentication and resolution failures to deny access without leaking information, so that errors cannot be exploited to reach another user's data.

#### Acceptance Criteria

1. IF authentication or memory resolution fails for any reason, THEN THE Spector_Server SHALL deny the request by returning an HTTP 401 or 403 response, SHALL NOT return any data belonging to another User, and SHALL NOT route the request to another User's namespace.
2. IF a login attempt fails for any reason, THEN THE Spector_Server SHALL return an HTTP 401 response whose status and body are identical whether or not the submitted username exists, so that the response does not reveal account existence.
3. WHILE authentication is enabled, IF authentication or memory resolution fails, THEN THE Spector_Server SHALL NOT fall back to the shared or anonymous memory workspace.
4. IF a User_Id resolves to an unsafe namespace identifier (one that contains path separators or path-traversal sequences, contains characters outside the permitted identifier set, or resolves to a directory outside the authenticated User's namespace), THEN THE Spector_Server SHALL return an HTTP 400 response and SHALL log the event at DEBUG level without including the raw identifier value.
5. IF the authentication data store is unavailable, THEN THE Spector_Server SHALL fail authentication with an HTTP 401 response, SHALL log the error through SLF4J, and SHALL leave the requesting User's stored data unmodified.
6. THE Spector_Server SHALL exclude secrets and raw API keys from all error responses and all log output.
