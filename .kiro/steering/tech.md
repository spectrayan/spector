# Tech Stack & Build System

## Backend (Java)

| Component | Version/Tool |
|-----------|-------------|
| JDK | 25 (with `jdk.incubator.vector` and `--enable-preview`) |
| Build | Maven 3.9+ (22-module reactor) |
| Web/gRPC | Armeria 1.39.1 |
| Spring | Spring Boot 4.1.0 (spector-spring only) |
| JSON | Jackson 3.x |
| MCP | MCP SDK 2.0.0-M3 |
| AI/LLM | LangChain4j 1.17.1, Spring AI 2.0.0 |
| Logging | SLF4J 2.0.17 + Logback 1.5.34 |
| Metrics | Micrometer 1.17.0 |
| Config | Apache Commons Configuration 2.15.0 |
| Testing | JUnit 5.11.4, AssertJ 3.27.7, Mockito 5.17.0 |
| Benchmarks | JMH 1.37 |
| Coverage | JaCoCo 0.8.15 |

## Frontend (Cortex — Angular)

| Component | Version/Tool |
|-----------|-------------|
| Framework | Angular 22 + Angular Material 22 |
| Language | TypeScript 6.0.2 |
| 3D | Three.js 0.184 |
| Charts | Chart.js 4.4 |
| Reactive | RxJS 7.8 |
| Testing | Vitest 4.0.8 |
| Formatting | Prettier 3.8.1 (100 char width, single quotes) |
| Package Manager | npm 11.12.1 |

## Documentation

- MkDocs Material site in `docs/`

## Common Commands

### Java (Maven) — run from repo root

```bash
# Compile all modules
mvn clean compile

# Run all tests
mvn clean test

# Compile + test including synapse (gated behind profile)
mvn clean compile -Psynapse
mvn verify -Psynapse

# Install all modules locally (needed first time for dep resolution)
mvn clean install -Psynapse -DskipTests

# Single module test
mvn test -pl nucleus/spector-core

# Single test class
mvn test -pl nucleus/spector-core -Dtest=DotProductTest

# Build distribution JAR
mvn package -pl synapse/spector-dist -am -DskipTests

# Run the server
java --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED --enable-preview -jar synapse/spector-dist/target/spector.jar --config spector.yml
```

### Angular (Cortex) — run from `cortex/spector-cortex/`

```bash
npm start        # Dev server
npm run build    # Production build
npm test         # Run tests (Vitest)
```

### Documentation

```bash
python -m mkdocs build --clean   # Build docs site
python -m mkdocs serve           # Local dev server
```

## Critical Java Constraints

- NEVER use `synchronized` — always `ReentrantLock` or `StampedLock` (virtual thread pinning)
- NEVER use `System.out.println` — always SLF4J `LoggerFactory.getLogger()`
- NEVER hardcode SIMD lane widths — use `FloatVector.SPECIES_PREFERRED`
- NEVER commit secrets/tokens — use `${env:VAR}` placeholders in config YAML
- All classes holding native resources MUST implement `AutoCloseable`
- Zero allocations in hot paths — reuse buffers, use `MemorySegment.asSlice()`

## Git Conventions

- Conventional Commits: `<type>(<scope>): <description>`
- Types: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `chore`
- Scope: module name without `spector-` prefix (e.g., `memory`, `core`, `mcp`)
- Branches: `feat/desc`, `fix/desc`, `perf/desc`, `docs/desc`
- All commits require `Signed-off-by` line (`git commit -s`)
