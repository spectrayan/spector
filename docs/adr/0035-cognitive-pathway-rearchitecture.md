# ADR-0035: Cognitive Pathway Framework Rearchitecture

| Field | Value |
|:---|:---|
| **Status** | Accepted (Implemented) |
| **Date** | 2026-09-14 |
| **Authors** | Spector Maintainers & Architecture Working Group |
| **Deciders** | Spector Technical Steering Committee (TSC) |
| **Supersedes** | None |
| **Superseded By** | None |
| **Last Verified** | 2026-09-16 (Verified against `main`) |

---

- **Status:** Accepted
- **Date:** 2026-09-14
- **Deciders:** Spector Memory / Nucleus
- **Affects:** `nucleus/spector-commons` (`commons.pathway`), `memory/spector-memory` (`memory.pathway.*`), `memory/spector-memory` (`runtime.SpectorRuntime`, `bootstrap.SpectorMemoryFactory`)
- **Supersedes:** Informal pathway-facade convention introduced with `CognitivePathway<S>` (no prior ADR)
- **Companion:** [ADR-0036 — Pathway Error Handling, Isolation, and Circuit Breakers](0036-pathway-error-handling-and-circuit-breakers.md)
- **Prerequisite:** [ADR-0037 — Ingestion Boundary and Sensory Relocation](0037-ingestion-boundary-and-sensory-relocation.md) (M1.5)
- **Code baseline:** all line references verified against `main` @ `33af1601`
- **Implementation status:** delivered on `feat/cognitive-pathway-rearchitecture`. Post-implementation review and remediation recorded in `.kiro/specs/cognitive-pathway-rearchitecture/tasks.md` (Internal Specification). Sections amended after implementation: §6.6 (outcome import in `finally`, `required(false)` check), §7.1.1 (`StageBuilder` additions).

---

## 1. Context

Spector Memory expresses cognition as *pathways* — Remember, Recall, Reflect, Dream, Decide, Wander, Express — each a chain of `SynapticRelay` stages conducted by `CognitivePathway<S>` in `spector-commons`.

That conductor is sound. It already provides:

- sequential conduction with short-circuit (`transmit` → `false`)
- `ErrorPolicy.FAIL_FAST` / `DEGRADE_GRACEFULLY`
- `GatedRelay` + `Specification`
- `DivergentRelay` + `DivergentCapable` fork/merge
- `CircuitBreakerRelay`, `ConsolidationRelay`
- interceptor decoration
- `TraceableSignal` / `RelayTrace`

The surrounding *domain* layer is not sound.

### 1.1 Current shape

```
RecallPathway / RememberPathway / ReflectPathway / …
        │  owns
        ▼
CognitivePathway<S>          ← conductor (commons)
        │  conducts
        ▼
SynapticRelay<S>[]           ← stages
        │  mutate
        ▼
XxxSignal                    ← request + workspace + service locator
```

Each domain class is a standalone final type. There is no `Pathway` interface. `SpectorRuntime` holds seven concrete fields and exposes seven getters.

### 1.2 Failure modes we are fixing

All counts below were verified against `main` at commit `33af1601`.

| Symptom | Evidence |
|---|---|
| No common type | `RecallPathway` (L161) is the **only** pathway that does not implement `AutoCloseable`; the other six do. Callers cannot store `List<Pathway<?,?>>`. |
| Inconsistent entry-point naming | `ReflectPathway` and `DreamPathway` already expose `conduct(signal)`; `RecallPathway` and `RememberPathway` do not. Partial convergence on the target shape, never finished. |
| Cross-pathway calls overload the caller | `ReflectPathway` has **8 public entry points** — `conduct` ×1 (L183), `execute` ×4 (L206, L217, L228, L240), `reflect` ×3 (L305, L315, L326) — which chain down to a widest form. `DreamPathway` holds `RememberPathway` as a field (L76) *and* puts it on `DreamSignal` (L242). `DreamIngestionRelay` L74 calls `signal.rememberPathway().ingestCognitiveWithHeader(...)`. |
| Caller builds callee relays | Adding a Remember relay forces Dream / Reflect construction sites to change, even when they only wanted to *invoke* Remember. |
| Signals are god objects | `RecallSignal` carries 9 request/workspace fields **and 15 service-locator fields** (L53–67): kernel, partition registry, index, BM25 index, Hebbian graph, temporal chain, TKG, entity directory, hyper-entity graph, quantizer, co-activation tracker, suppression set, habituation penalty, surprise detector, prospective scheduler. |
| Hidden ambient state | `RecallPathway.ACTIVE_SIGNAL` (`InheritableThreadLocal`, L182) has **12 read sites across 3 files** — see §8.2. Not 3, as an earlier draft of this ADR claimed. |
| Nested recall corrupts ambient state | `execute` sets `ACTIVE_SIGNAL` (L413) and clears it in `finally` (L456). A nested recall therefore clears the *outer* conduction's signal on exit. `ConductionScope` fixes this as a side effect. |
| Factory explosion | `RecallPathwayFactory` has **6** positional `create` overloads (L33, L57, L82, L115, L152, L190); the widest takes **26 relay parameters** plus an interceptor, with a forest of `if (relay != null) builder.gated(...)` null checks. `ReflectPathwayFactory` has 3, `RememberPathwayFactory` 2. One new AISME relay = new overload + constructor surgery. |
| Only one signal is traceable | `RecallSignal` (L34) is the **sole** signal implementing `TraceableSignal` (and `DivergentCapable`). The other six implement neither, so `CognitivePathway`'s trace recording — gated on `signal instanceof TraceableSignal` — is inert for six of seven pathways. |
| Duplicated façade boilerplate | Every pathway reimplements builder, interceptor, try/catch → `SpectorPathwayException`, and close. |
| A pathway doubles as a storage SPI | `RememberPathway implements IngestionTarget` (L71), an SPI from `spector-ingestion`. Its `void ingest(...)` is what blocks `Pathway<RememberSignal, RememberResult>`. See §8.1a and ADR-0037. |

### 1.3 Constraints

- Do not rewrite the conductor. `CognitivePathway<S>` stays the engine.
- Public verbs stay: `recall(...)`, `ingest(...)`, `reflect(...)`, `dream(...)`. They become sugar over `conduct`.
- Pathways remain process-wide singletons owned by `SpectorRuntime` (already true).
- Hot path (Recall SIMD scan) must not pay for extra allocations per relay beyond what exists today.
- Java 25, virtual threads, existing `ConcurrentTasks` / `MemoryScope`.
- Biological naming is retained.

---

## 2. Decision

We split three concerns that are currently one class:

1. **`Pathway<I,O>`** — a cognitive operation that can be addressed, invoked, and composed.
2. **`CognitivePathway<S>`** — the relay conductor (unchanged role; optionally aliased as `PathwayEngine`).
3. **`PathwayContext` + `AttributeBag`** — shared services and per-conduction scratch. Signals stop being service locators.

Cross-pathway calls happen through a **`PathwayCatalog`** (implemented by `SpectorRuntime`) and a **`PathwayRelay`** adapter. A caller never constructs the callee’s relays and never holds the callee as a field unless it *is* the composition root.

```
SpectorRuntime  ──implements──▶  PathwayCatalog
        │
        │ register(Remember, Recall, Reflect, Dream, Decide, Wander, Express)
        ▼
   PathwayContext ──contains──▶ catalog, kernel, typed services, AttributeBag
        │
        ▼
   DreamPathway.conduct(DreamSignal)
        │
        ├─ SynapticRelay…
        └─ PathwayRelay<RememberPathway>  ──catalog.invoke──▶  RememberPathway.conduct(RememberSignal)
                                                                  │
                                                                  └─ CognitivePathway<RememberSignal>
```

---

## 3. Goals and non-goals

### Goals

- One interface every domain pathway implements.
- Invoke pathway B from pathway A without A knowing B’s relays, factory, or constructor.
- Generic, typed, sharable state across pathways and relays.
- Collapse Reflect/Dream/Recall execute overloads.
- Delete `RecallPathway.ACTIVE_SIGNAL` and all 12 of its read sites.
- Replace positional `*PathwayFactory` overloads with recipes.
- Preserve existing relay semantics (gate, diverge, short-circuit, intercept, trace).
- Make tracing work for all seven pathways, not just Recall.
- Stop `RememberPathway` doubling as an `IngestionTarget` implementation (ADR-0037).
- Give error handling and circuit breaking a first-class nested-pathway story (ADR-0036).

### Non-goals

- Replacing `CognitivePathway` with a new engine.
- Introducing a DI framework (Spring, Guice). Runtime + context is enough.
- Making signals immutable. Working memory stays mutable; services become immutable-per-conduction.
- Unifying all signal types into one class.
- Changing MCP / REST / SDK request shapes.

---

## 4. Alternatives considered

| Option | Why rejected |
|---|---|
| Keep façades, add a marker interface only | Does not fix cross-pathway wiring or god signals. |
| Make domain pathways *extend* `CognitivePathway` | Collapses “operation” and “conductor”. Conductor is generic over a signal; operation has an input *and* an output. |
| Global static `Pathways.get(RecallPathway.class)` | Hidden coupling, untestable, fights `SpectorRuntime` as the existing process singleton. |
| Pass every collaborator through every `execute` overload | Status quo. Overload combinatorics. |
| Event bus between pathways | Async, unordered, hard to trace, wrong for “Dream ingest *then* continue”. Nested conduct is synchronous and scoped. |
| One shared `CognitiveSignal` for all pathways | Forces every relay to downcast. Per-pathway signals stay; they implement `ContextualSignal`. |

---

## 5. Package layout

New types live in commons so memory, metrics, and tests share them.

```
nucleus/spector-commons/src/main/java/com/spectrayan/spector/commons/pathway/
    CognitivePathway.java              // unchanged conductor
    SynapticRelay.java                 // + optional Lifecycle
    GatedRelay.java, DivergentRelay.java, NamedRelay.java
    CircuitBreakerRelay.java           // extended in ADR-0036
    ConsolidationRelay.java
    ErrorPolicy.java                   // extended in ADR-0036
    Specification.java
    TraceableSignal.java, RelayTrace.java
    CognitivePathwayException.java     // extended in ADR-0036

    Pathway.java                       // NEW
    AbstractPathway.java               // NEW
    ContextualSignal.java              // NEW
    AbstractSignal.java                // NEW — base for all seven signals (§6.3)
    PathwayContext.java                // NEW
    DefaultPathwayContext.java         // NEW
    AttributeBag.java                  // NEW
    Key.java                           // NEW
    PathwayCatalog.java                // NEW
    DefaultPathwayCatalog.java         // NEW
    PathwayRelay.java                  // NEW
    PathwayComposer.java               // NEW
    PathwayRecipe.java                 // NEW
    RelayFactory.java                  // NEW
    ConductionOutcome.java             // NEW (detail in ADR-0036 §5)
    ConductionScope.java               // NEW — ONE per root conduction; frame stack
                                       //       for cycle detection + nested traces
    PathwayExceptions.java             // NEW — single wrap() helper (§6.2)
```

Domain pathways stay in `memory.pathway.{recall,remember,reflect,dream,decide,wander,express}` and implement `Pathway<I,O>`.

Note: an earlier draft listed `ConductionResult.java`. The type is named `ConductionOutcome` (ADR-0036 §5); there is only one such type.

---

## 6. Core types

### 6.1 `Pathway<I, O>`

```java
package com.spectrayan.spector.commons.pathway;

public interface Pathway<I, O> extends AutoCloseable {

    String name();

    Class<I> inputType();

    Class<O> outputType();

    /**
     * Conduct {@code input}. If {@code input} is a {@link ContextualSignal}
     * and has no context yet, the implementation MUST refuse with
     * {@link IllegalStateException} — context is bound by the catalog or by
     * {@link #conduct(PathwayContext, Object)}.
     */
    O conduct(I input);

    /**
     * Bind {@code ctx} onto the input (when it is a {@link ContextualSignal})
     * and conduct. Used by {@link PathwayCatalog} and {@link PathwayRelay}.
     */
    default O conduct(PathwayContext ctx, I input) {
        if (input instanceof ContextualSignal cs) {
            cs.bind(ctx);
        }
        return conduct(input);
    }

    @Override
    default void close() {
        // domain pathways override when they own AutoCloseable collaborators
    }
}
```

Public verbs remain on the concrete type and delegate:

```java
public final class RecallPathway
        extends AbstractPathway<RecallSignal, List<CognitiveResult>> {

    public List<CognitiveResult> recall(String query, RecallOptions options) {
        return conduct(contextOrThrow(), RecallSignal.forTextQuery(query, options));
    }
}
```

`contextOrThrow()` is supplied by the runtime at attach time (see §9). Standalone unit tests pass a test context explicitly.

### 6.2 `AbstractPathway<S, O>`

Kills the copy-paste `try/catch` + exception wrap in Decide / Wander / Express / Dream / Reflect.

```java
public abstract class AbstractPathway<S extends ContextualSignal, O>
        implements Pathway<S, O> {

    private final String name;
    private final Class<S> inputType;
    private final Class<O> outputType;
    private final CognitivePathway<S> engine;

    protected AbstractPathway(String name,
                              Class<S> inputType,
                              Class<O> outputType,
                              CognitivePathway<S> engine) {
        this.name = Objects.requireNonNull(name);
        this.inputType = inputType;
        this.outputType = outputType;
        this.engine = Objects.requireNonNull(engine);
    }

    @Override public final String name() { return name; }
    @Override public final Class<S> inputType() { return inputType; }
    @Override public final Class<O> outputType() { return outputType; }

    /** Visible for metrics interceptors and tests. */
    public final CognitivePathway<S> engine() { return engine; }

    @Override
    public final O conduct(S signal) {
        Objects.requireNonNull(signal, name + " signal");
        final PathwayContext ctx = signal.context();
        if (ctx == null) {
            throw new CognitivePathwayException(name, "<entry>", FaultKind.CONTRACT, false,
                    new IllegalStateException(name + " conducted without PathwayContext"));
        }
        final ConductionScope scope = ctx.scope();
        scope.enter(name);
        try {
            engine.conduct(signal);
            // Finish MUST be set before project(): Remember's project() reads it to
            // decide RememberResult.skipped() when dedup short-circuited the engine.
            ctx.outcome().finish(scope.shortCircuited(name)
                    ? ConductionOutcome.Finish.SHORT_CIRCUITED
                    : ConductionOutcome.Finish.COMPLETED);
            return project(signal);
        } catch (RuntimeException e) {
            ctx.outcome().finish(ConductionOutcome.Finish.FAILED);
            throw PathwayExceptions.wrap(name, e);   // ADR-0036 §3
        } finally {
            scope.leave(name);
        }
    }

    /**
     * Map finished signal → public output.
     *
     * <p>MUST tolerate a partially-populated signal: the engine may have
     * short-circuited. Read {@code signal.context().outcome().finish()} to
     * distinguish completion from short-circuit. MUST pass the outcome onto
     * the returned report where the report has an outcome field (ADR-0036 §5).</p>
     */
    protected abstract O project(S signal);
}
```

`CognitivePathway.conduct` stays exactly as it is. Domain exception wrapping moves *here*, once.

Three deliberate choices in the sketch above:

1. **`catch (RuntimeException)` is sufficient, and this is load-bearing.** `CognitivePathway.conduct` already catches `Exception` internally and rethrows only unchecked types (`SpectorException` as-is, else `CognitivePathwayException`). So `engine.conduct` cannot throw a checked exception. `Error` is deliberately not caught.
2. **Missing context is `CONTRACT`, not a bare `IllegalStateException`.** Per the repo's error convention, pathway-layer failures carry an `ErrorCode`. Raw `IllegalArgumentException` / `IllegalStateException` throws in `CircuitBreakerRelay` (L78, L81) and `DivergentRelay` (L69) are pre-existing debt to clean up in the same pass.
3. **`AbstractPathway` does not notify any circuit breaker.** ADR-0036 §9.3 assigns both permit acquisition and success/failure recording to the `PathwayRelay` decorator, so the accounting lives in one place. See ADR-0036 §9.3.

### 6.3 `ContextualSignal`

```java
public interface ContextualSignal extends TraceableSignal {

    PathwayContext context();

    /**
     * Binds (or re-binds) the context. Implementations store the reference;
     * they do not copy services onto fields.
     *
     * <p>MUST be idempotent-overwrite, not once-only. A retried
     * {@link PathwayRelay} stage (ADR-0036 §8) constructs a fresh nested input
     * per attempt but binds a fresh nested context each time; a once-only
     * contract would make retry throw.</p>
     */
    void bind(PathwayContext ctx);
}
```

Every existing signal implements this — and this is real work, not a rename. **Today only `RecallSignal` implements `TraceableSignal`.** The other six (`RememberSignal`, `ReflectSignal`, `DreamSignal`, `DecideSignal`, `WanderSignal`, `ExpressSignal`) implement nothing, which means:

- `CognitivePathway.conduct` records no traces for them at all (its trace block is gated on `signal instanceof TraceableSignal && isTraceEnabled()`).
- §12's nested trace tree and ADR-0036 §13's trace-status table are **inert for six of seven pathways** until they extend `AbstractSignal`.
- ADR-0036's `GatedRelay`-records-`BYPASSED` fix is likewise a no-op outside Recall until then.

`AbstractSignal` is therefore scheduled explicitly in **M6** (§15), not treated as incidental. `ConductionOutcome` living on the context rather than on each signal (ADR-0036 §5) is what keeps outcome reporting working for all seven pathways *before* that migration lands.

`DivergentCapable` is unchanged and orthogonal — `RecallSignal` remains its only implementor.

A small base class avoids seven copies of bind/traces:

```java
public abstract class AbstractSignal implements ContextualSignal, TraceableSignal {

    private PathwayContext context;
    private final List<RelayTrace> traces = new ArrayList<>();
    private final Object tracesLock = new Object();

    @Override public final PathwayContext context() { return context; }

    @Override
    public final void bind(PathwayContext ctx) {
        this.context = Objects.requireNonNull(ctx);
    }

    @Override
    public boolean isTraceEnabled() {
        return context != null && context.traceEnabled();
    }

    @Override
    public void recordTrace(RelayTrace trace) {
        if (!isTraceEnabled()) return;
        synchronized (tracesLock) { traces.add(trace); }
    }

    @Override
    public List<RelayTrace> traces() {
        synchronized (tracesLock) { return List.copyOf(traces); }
    }
}
```

`RecallSignal.fork()` copies candidates/attributes and **shares** `context()` (same conduction). `PathwayContext.nested` is only used when crossing a pathway boundary.

### 6.4 `Key<T>` and `AttributeBag`

```java
public final class Key<T> {
    private final String id;
    private final Class<T> type;

    private Key(String id, Class<T> type) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
    }

    public static <T> Key<T> of(String id, Class<T> type) {
        return new Key<>(id, type);
    }

    public String id() { return id; }
    public Class<T> type() { return type; }

    @Override public boolean equals(Object o) { /* id + type */ }
    @Override public int hashCode() { return Objects.hash(id, type); }
}

public interface AttributeBag {
    <T> void put(Key<T> key, T value);

    /**
     * @throws CognitivePathwayException with {@link FaultKind#CONTRACT} and
     *         {@code ErrorCode.MEMORY_PATHWAY_FAILED} when the key is absent.
     *         Never a bare {@code IllegalStateException} — pathway-layer
     *         failures carry an ErrorCode so they are classifiable by
     *         {@code Faults.kindOf} (ADR-0036 §3).
     */
    <T> T get(Key<T> key);

    <T> Optional<T> find(Key<T> key);
    boolean contains(Key<?> key);
    AttributeBag snapshot();               // shallow immutable copy for fork()
}
```

Implementation: `ConcurrentHashMap<Key<?>, Object>` for bags that cross divergent branches; `IdentityHashMap` is forbidden (keys are value-equal).

The same rule applies to `PathwayContext.get(Class)` and `get(Key)`.

Canonical keys live next to the type that owns them, not on a god interface:

```java
public final class PathwayKeys {
    private PathwayKeys() {}

    public static final Key<CoActivationMemory> CO_ACTIVATION =
            Key.of("coActivation", CoActivationMemory.class);
    public static final Key<TemporalKnowledgeGraph> TKG =
            Key.of("tkg", TemporalKnowledgeGraph.class);
    public static final Key<EntityDirectory> ENTITY_DIRECTORY =
            Key.of("entityDirectory", EntityDirectory.class);
    public static final Key<Boolean> TEXT_SEARCH_EXECUTED =
            Key.of("textSearchExecuted", Boolean.class);
    public static final Key<Float> EFFECTIVE_TEMPERATURE =
            Key.of("effectiveTemperature", Float.class);
    public static final Key<Integer> LAST_INGESTED_IDX =
            Key.of("lastIngestedIdx", Integer.class);
}
```

Stringly-typed `signal.attributes()` (`Map<String,Object>`) is deprecated and delegated to the bag for one release.

### 6.5 `PathwayContext`

```java
public interface PathwayContext {

    String conductionId();                 // UUID string, stable for the root call
    String namespaceId();                  // may be null for process-global ops
    NamespaceKernel kernel();              // may be null
    PathwayCatalog catalog();
    ConductionScope scope();
    boolean traceEnabled();

    <T> T get(Class<T> type);              // exactly one registration
    <T> Optional<T> find(Class<T> type);
    <T> T get(Key<T> key);
    <T> Optional<T> find(Key<T> key);

    AttributeBag bag();

    /** Degraded / bypassed marks and finish state. ADR-0036 §5. */
    ConductionOutcome outcome();

    /**
     * Child context for a nested pathway invocation.
     *
     * <p>Shares catalog, services, bag, outcome, AND the SAME
     * {@link ConductionScope} instance by reference. Pushes {@code segment}
     * as a new frame on that shared scope. Does NOT share the parent signal.</p>
     */
    PathwayContext nested(String segment);
}
```

#### 6.5.1 `ConductionScope` — one instance per root conduction

An earlier draft described `nested()` as producing a "new scope frame" while §6.6 called `assertNotOnStack` on the *parent* scope. Those are incompatible: if `nested()` allocated a fresh `ConductionScope`, the pathway-name stack would reset at every hop and the `Remember → Dream → Remember` cycle in §10.5 could never be detected.

Settled semantics:

- **One `ConductionScope` per root conduction.** `nested()` shares it by reference and pushes a frame; it never allocates a new one.
- `enter(name)` / `leave(name)` push and pop frames. `assertNotOnStack(name)` walks the frames.
- `pathwayName()` returns the innermost pathway frame; `segment()` returns the dotted path used for trace prefixes and outcome mark scopes.
- `shortCircuited(name)` reports whether the engine broke early for that frame.

**Thread confinement.** `ConductionScope` is **not** thread-safe and must not be. `DivergentRelay` runs branches on separate virtual threads and forks share the context (§6.3), so concurrent `enter()` from N branches would corrupt the frame stack. Two rules follow:

1. `DivergentCapable.fork()` does not push a frame (§10.7) — a fork is same-pathway parallelism.
2. **`PathwayRelay` is forbidden inside a `DivergentRelay` branch.** `PathwayComposer.divergent(...)` rejects any branch that is, or wraps, a `PathwayRelay` at *build* time. No current recipe needs nested pathway invocation inside a parallel branch; if one ever does, the fix is a per-fork scope copy merged on join, which is a separate change.

`ConductionOutcome` is a different matter and *is* safe to share across forks — its mark lists are `CopyOnWriteArrayList` (ADR-0036 §5).

#### 6.5.2 Mutable services must be bound as accessors, not values

`bind(Class<T>, instance)` snapshots a reference into an immutable service map built once per namespace. That is correct for genuinely stable collaborators and **wrong** for anything with a runtime setter, because the context would pin a stale value for the process lifetime.

Two live examples, both currently reached through `signal.rememberPathway()`:

| Service | Why a value bind is wrong |
|---|---|
| Soul version | `RememberPathway.currentSoulVersion` is `volatile short` (L82), mutated at runtime via `DefaultSpectorMemory.setSoulVersion` → `rememberPathway.setSoulVersion` (L1500). Binding the `short` would make persona switches invisible to Reflect and Dream. |
| `ScalarQuantizer` | Swappable via `updateCognitiveRouter` / `updateTextDataStore`. |

Rule: **if the service has a setter, bind a supplier/accessor interface, not the value.** See §8.1b for the two interfaces this introduces.

`DefaultPathwayContext` is an immutable service map + shared bag + nested scope:

```java
public final class DefaultPathwayContext implements PathwayContext {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        public Builder conductionId(String id);
        public Builder kernel(NamespaceKernel kernel);
        public Builder catalog(PathwayCatalog catalog);
        public Builder traceEnabled(boolean enabled);
        public <T> Builder bind(Class<T> type, T instance);
        public <T> Builder bind(Key<T> key, T instance);
        public Builder bag(AttributeBag bag);
        public PathwayContext build();
    }
}
```

**Registration rule:** `bind(Class, instance)` fails if that class is already bound. Ambiguous types (`List`, `Map`, common interfaces with several implementations) **must** use `Key<T>`.

**What belongs where**

| Kind | Home | Examples |
|---|---|---|
| Process-wide services | Context `Class<T>` bind | `MemoryIndex`, `EmbeddingProvider`, `ScalarQuantizer`, `HebbianGraphBase`, `PathwayCatalog` |
| Per-namespace handle | Context | `NamespaceKernel`, `PartitionManager` |
| Optional subsystem | Context `Key<T>` | `ContinuousHopfieldNetwork`, `LlmProvider`, `CognitiveManifold` |
| Per-request inputs / outputs | Signal fields | query text, options, candidates, dream scenes, `ReflectReport` accumulators |
| Cross-relay scratch | `AttributeBag` | `TEXT_SEARCH_EXECUTED`, effective temperature |
| Cross-pathway scratch | same bag, via `nested()` | dream quality score consumed by Remember tags |

Relays that today call `RecallPathway.activeSignal().temporalKnowledgeGraph()` become:

```java
signal.context().find(PathwayKeys.TKG).orElse(this.fallbackTkg)
```

### 6.6 `PathwayCatalog`

```java
public interface PathwayCatalog {

    <I, O> void register(Class<? extends Pathway<I, O>> type, Pathway<I, O> instance);

    <I, O> Optional<Pathway<I, O>> find(Class<? extends Pathway<I, O>> type);

    <I, O> Pathway<I, O> require(Class<? extends Pathway<I, O>> type);

    <I, O> O invoke(Class<? extends Pathway<I, O>> type, PathwayContext ctx, I input);

    Collection<Pathway<?, ?>> all();
}
```

`DefaultPathwayCatalog` is a `ConcurrentHashMap<Class<?>, Pathway<?,?>>`. Register-once: a second `register` of the same type throws.

`invoke` is the only legal nested entry:

```java
@Override
public <I, O> O invoke(Class<? extends Pathway<I, O>> type,
                       PathwayContext ctx,
                       I input) {
    final Pathway<I, O> pathway = require(type);
    // Same ConductionScope instance throughout (§6.5.1) — nested() shares it by
    // reference, so this guard sees every frame from the root conduction down.
    ctx.scope().assertNotOnStack(pathway.name());          // PATHWAY_CYCLE — ADR-0036 §3
    final ConductionOutcome childOutcome = new ConductionOutcome();
    try {
        return pathway.conduct(ctx.nestedWithOutcome(pathway.name(), childOutcome), input);
    } finally {
        // MUST be in finally (ADR-0036 §4.2): a DEGRADE_GRACEFULLY caller stage still
        // has to report *why* the nested conduction failed. Importing only on the
        // success path silently drops every mark from a failed nested pathway.
        ctx.outcome().importFrom(childOutcome, pathway.name());
    }
}
```

`require` throws `CognitivePathwayException` with `FaultKind.CONTRACT` when the type is not registered.

**Amendment (implementation).** Two things the original sketch got wrong:

1. **Outcome import belongs here, not only in `PathwayRelay`** — and it must be in a `finally`. This is better than the original design: production nests through `catalog.invoke` directly for 1:N writes (§8.4 option b), so putting the import solely in `PathwayRelay` would lose outcome merging on the path actually taken.
2. **A caller that may run without the callee registered must check `catalog.find(type).isPresent()` first.** `invoke` throws `CONTRACT` on a missing registration, so a bare `catalog != null` guard is insufficient. This is the hand-rolled equivalent of `PathwayRelay`'s `required(false)` (§6.7), and it is easy to get wrong — `DreamIngestionRelay` regressed on exactly this during implementation and was caught only by `DreamPathwayNestingTest`.

`SpectorRuntime` implements this interface (or owns a `DefaultPathwayCatalog` and delegates). The seven getters stay as convenience:

```java
public RecallPathway recallPathway() {
    return (RecallPathway) catalog.require(RecallPathway.class);
}
```

### 6.7 `PathwayRelay<S, I, O>` — composition primitive

This is what stops Dream from owning Remember.

```java
public final class PathwayRelay<S extends ContextualSignal, I, O>
        implements SynapticRelay<S> {

    private final String name;
    private final Class<? extends Pathway<I, O>> targetType;
    private final Function<S, I> toInput;
    private final BiConsumer<S, O> absorb;
    private final boolean required;            // false → skip if catalog has no target

    @Override
    public boolean transmit(S signal) throws Exception {
        final PathwayCatalog catalog = signal.context().catalog();
        final Optional<Pathway<I, O>> target = catalog.find(targetType);
        if (target.isEmpty()) {
            if (required) {
                throw new CognitivePathwayException(
                        signal.context().scope().pathwayName(),
                        name,
                        new IllegalStateException(targetType.getSimpleName() + " not registered"));
            }
            return true;
        }
        final I nestedInput = toInput.apply(signal);
        final O output = catalog.invoke(targetType, signal.context(), nestedInput);
        absorb.accept(signal, output);
        return true;
    }

    @Override
    public String relayName() { return name; }

    public static <S extends ContextualSignal, I, O> Builder<S, I, O> to(
            Class<? extends Pathway<I, O>> targetType) {
        return new Builder<>(targetType);
    }

    public static final class Builder<S extends ContextualSignal, I, O> { /* fluent */ }
}
```

Dream ingestion stage:

```java
composer.relay(
    RelayNames.DREAM_INGEST,
    PathwayRelay.<DreamSignal, RememberSignal, RememberResult>to(RememberPathway.class)
        .named(RelayNames.DREAM_INGEST)
        .from(DreamPorts::toRememberSignal)
        .into(DreamPorts::absorbRemembered)
        .required(false)                 // dream still succeeds if remember is absent in tests
        .build(),
    ErrorPolicy.DEGRADE_GRACEFULLY       // see ADR-0036 for nested policy interaction
);
```

`DreamPorts` is a package-private mapper. Remember never hears about dreams. Dream never hears about Remember relays.

Reflect gist / promotion stages use the same adapter. Reflect’s eight `execute` overloads collapse to:

```java
public ReflectReport reflect(ReflectSignal signal) {
    return conduct(signal);
}

public ReflectReport reflect(ReflectSignal.Builder signalBuilder) {
    return conduct(signalBuilder.build());
}
```

The builder on `ReflectSignal` takes partition manager, index, sweep spec, checkpoint store. It does **not** take `RememberPathway`.

### 6.8 Port interfaces (optional, recommended)

Pathways that are called from others expose a port so mappers stay honest:

```java
public interface RememberPort {
    RememberSignal toRememberSignal();
    void absorbRemembered(RememberResult result);
}
```

Only `DreamSignal` and the Reflect gist working object implement this. `RecallSignal` does not.

`RememberResult` is new and tiny — Remember currently returns `void` / mutates internals:

```java
public record RememberResult(
        String memoryId,
        int memoryIndex,
        boolean dedupHit,
        MemoryType type,
        MemorySource source
) {
    public static RememberResult skipped() { /* dedup / gated */ }
}
```

`RememberPathway.project` returns this. Existing `ingest*` methods stay `void` and ignore the result.

---

## 7. Recipes replace positional factories

### 7.1 `PathwayRecipe` and `PathwayComposer`

```java
public interface PathwayRecipe<S> {
    void compose(PathwayComposer<S> composer);
}

public interface PathwayComposer<S> {

    PathwayComposer<S> withInterceptor(Function<SynapticRelay<S>, SynapticRelay<S>> interceptor);

    PathwayComposer<S> relay(String name, SynapticRelay<S> relay, ErrorPolicy policy);

    PathwayComposer<S> gated(String name, Predicate<S> gate, SynapticRelay<S> relay, ErrorPolicy policy);

    PathwayComposer<S> divergent(String name, List<SynapticRelay<S>> branches, List<ErrorPolicy> policies);

    PathwayComposer<S> consolidate(String name, Consumer<S> asyncAction);

    PathwayComposer<S> circuitBreaker(String name, SynapticRelay<S> relay, CircuitBreakerConfig cfg, ErrorPolicy policy);

    PathwayComposer<S> pathway(String name, PathwayRelay<S, ?, ?> nested, ErrorPolicy policy);

    /** Skip the stage when the factory cannot resolve the relay (optional AISME). */
    PathwayComposer<S> optional(String name, Class<? extends SynapticRelay<S>> type, ErrorPolicy policy);

    CognitivePathway<S> build();
}
```

`PathwayComposer` is a thin wrapper over today’s `CognitivePathway.Builder`. No behavior change.

#### 7.1.1 Amendment (implementation) — `StageBuilder` additions

Three methods were added during implementation that this ADR did not anticipate. Recorded because each encodes a constraint the design missed.

| Method | Why it exists |
|---|---|
| `gate(Predicate<S>)` | The decorator chain wraps the supplied relay, so passing a pre-built `GatedRelay` places the gate **innermost** — a closed gate would then consume a bulkhead permit and register as a circuit-breaker success. `gate()` places it outermost, which is what ADR-0036 §6 actually requires. The original `RecallRecipe` COLBERT_RERANK wiring had exactly this inversion. |
| `timeoutIfInterruptible(Duration)` | Recipes receive *injected* relays. Strict `timeout()` correctly rejects a relay that does not declare `InterruptibleRelay`, but test doubles never declare it — Mockito answers un-stubbed `default` methods with the type default, so a mocked relay reports `interruptible() == false`. This variant skips the budget instead of failing the build. |
| `retryIfIdempotent(RetryPolicy)` | Same reasoning for `IdempotentRelay`. |

The safety property is unchanged either way: **a relay that does not declare the marker never receives a budget or a retry.** The only difference is whether the omission is a build failure (statically-known relay) or a logged skip (injected relay). `CorticalWriteTransactionRelay` declares neither marker, so it stays protected in both modes.

### 7.2 `RelayFactory`

Resolved once at pathway *build* time, not per conduction.

```java
public interface RelayFactory {
    <S> SynapticRelay<S> create(Class<? extends SynapticRelay<S>> type);
}
```

Default implementation: a map populated by the domain pathway constructor (same place relays are `new`’d today). This is **not** general-purpose DI.

### 7.3 Example: Remember recipe (replaces `RememberPathwayFactory`)

```java
public final class RememberRecipe implements PathwayRecipe<RememberSignal> {

    private final DedupGuardRelay dedup;
    private final SynapticTagTransductionRelay tags;
    private final DopaminergicSurpriseRelay surprise;
    private final CorticalWriteTransactionRelay write;
    private final SynapticGraphLinkingRelay graph;
    private final KnowledgeGraphEnrichmentRelay kg;

    @Override
    public void compose(PathwayComposer<RememberSignal> c) {
        c.relay(RelayNames.DEDUP_GUARD,           dedup,    ErrorPolicy.FAIL_FAST)
         .relay(RelayNames.TAG_TRANSDUCTION,      tags,     ErrorPolicy.FAIL_FAST)
         .relay(RelayNames.DOPAMINERGIC_SURPRISE, surprise, ErrorPolicy.FAIL_FAST)
         .relay(RelayNames.CORTICAL_WRITE,        write,    ErrorPolicy.FAIL_FAST)
         .relay(RelayNames.GRAPH_LINKING,         graph,    ErrorPolicy.DEGRADE_GRACEFULLY)
         .relay(RelayNames.KG_ENRICHMENT,         kg,       ErrorPolicy.DEGRADE_GRACEFULLY);
    }
}
```

`RememberPathwayFactory.create(...)` becomes a deprecated one-liner that builds this recipe. Adding a relay is one line in `compose`, not a new overload.

Recall’s four factory overloads collapse to one recipe with `optional(...)` for AISME relays (`HopfieldAssociativeRelay`, `ManifoldRerankRelay`, `ConsciousAccessRelay`, …). If the pathway constructor did not instantiate the relay (feature off), `optional` no-ops.

### 7.4 Reflect companion-skip becomes a gate

Today `ReflectPathwayFactory.companion` wraps every relay in a lambda that checks `sweepSpec.runCompanionRelays()`. That is a `Specification`:

```java
public static final Specification<ReflectSignal> COMPANION =
        Specification.of("companion relays disabled by sweep spec",
                s -> s.sweepSpec() == null || s.sweepSpec().runCompanionRelays());
```

```java
c.gated(RelayNames.HEBBIAN_HOMEOSTASIS, COMPANION, hebbian, ErrorPolicy.DEGRADE_GRACEFULLY);
```

WAL / pruning stay ungated (`FAIL_FAST`) as they are today.

---

## 8. Domain pathway mapping

### 8.1 Remember — `Pathway<RememberSignal, RememberResult>`

- Constructor still creates the six relays from cortex / graph / index builders.
- Drops nothing functionally.
- Gains `RememberResult`.
- Does **not** take other pathways.
- **Stops implementing `IngestionTarget`** — see §8.1a.

`project()` returns `RememberResult.skipped()` when the engine short-circuited (dedup hit or a closed gate), which is why §6.2 sets `Finish` before calling `project`.

Note that Remember is not uniformly `void` today: `ingestCognitiveWithHeader` (L369) already returns `boolean`. `RememberResult` generalises an existing return value rather than inventing one.

### 8.1a Remember sheds `IngestionTarget`

`RememberPathway implements IngestionTarget, AutoCloseable` (L71) is the single hardest blocker on `Pathway<RememberSignal, RememberResult>`, because `IngestionTarget.ingest(String, String, float[])` is `void` and pins the void-returning entry points in place.

It is also a stale abstraction. The interface's own javadoc names two implementations:

```
 *   <li><b>EngineIngestionTarget</b> (spector-engine): VectorStore → HNSW → BM25</li>
 *   <li><b>CognitiveIngestionTarget</b> (spector-memory): quantize → surprise → tier route → WAL</li>
```

**Neither exists.** `spector-engine` was deleted, and `CognitiveIngestionTarget` was collapsed into `RememberPathway` — `DefaultImportanceProvider` L87 still carries the comment `// Gaming detection logging (matches original CognitiveIngestionTarget)`. `RememberPathway` is the only production implementor, and it overrides neither `storeParentMetadata` nor `onBatchComplete`, so parent-document tracking and batch WAL flush are **silently dropped** for every connector ingest today.

Decision: **`IngestionTarget` is deleted, not adapted.** Callers use `SpectorMemory.remember(...)`, the same single entry point MCP and REST already use. Full analysis, module moves, and the `SpectorMemory.target()` deprecation are specified in **[ADR-0037](0037-ingestion-boundary-and-sensory-relocation.md)**.

Sequencing consequence: the `IngestionTarget` removal is **M1.5** in §15 — it must land before M2, because M2 is what declares `implements Pathway<I,O>`.

### 8.1b Service extraction is not pathway invocation

`PathwayRelay` + `catalog.invoke` solves "A invokes B." It does **not** solve "A reads B's mutable state," and four relays do exactly that — holding `rememberPathway` purely as a state accessor, never invoking it:

| Site | What it actually reads |
|---|---|
| `SoulDriftRefusionRelay` L73 | `currentSoulVersion()` |
| `SoulDriftRefusionRelay` L107, L191 | `quantizer()` |
| `ProceduralCrystallizationRelay` L111 | `currentSoulVersion()` |
| `DreamIngestionRelay` L57 | `currentSoulVersion()` |
| `ConstructiveMemoryPersistenceRelay` L113 | `currentSoulVersion()` |

M5 removes `rememberPathway` from `ReflectSignal` and `DreamSignal`. `SoulDriftRefusionRelay` has no pathway to invoke — it wants a soul version and a quantizer — so **M5 as originally sequenced would break it with nowhere to land.**

Two narrow services are introduced and bound on the context, as accessors per §6.5.2:

```java
/** Current soul/persona version. Accessor, not a value — mutated at runtime. */
@FunctionalInterface
public interface SoulVersionSource {
    short currentSoulVersion();
}
```

`ScalarQuantizer` is bound with `bind(Class, …)` but sourced through a `Supplier`-backed holder so `updateCognitiveRouter` remains visible.

The five call sites become:

```java
short soulVer = signal.context().get(SoulVersionSource.class).currentSoulVersion();
ScalarQuantizer q = signal.context().find(ScalarQuantizer.class).orElse(this.fallbackQuantizer);
```

`RememberPathway` implements `SoulVersionSource` — a one-method interface it already satisfies — which keeps `setSoulVersion` as the single writer while removing the type dependency from Reflect and Dream.

This is **M4.5** in §15, strictly before M5.

### 8.2 Recall — `Pathway<RecallSignal, List<CognitiveResult>>`

- Deletes `private static final InheritableThreadLocal<RecallSignal> ACTIVE_SIGNAL`.
- Deletes `public static RecallSignal activeSignal()`.
- `execute(kernel, signal)` becomes:

```java
public List<CognitiveResult> execute(NamespaceKernel kernel, RecallSignal signal) {
    final PathwayContext ctx = baseContext.nested("recall").withKernel(kernel);
    return conduct(ctx, signal);
}
```

- `HebbianCoActivationListener.effectiveTracker()` and `TemporalFactWeavingStage.effectiveTkg()` read `signal.context()` when a signal is in hand; graph stages that are *not* relays (legacy pipeline objects invoked from a relay) receive the context as a method argument. No thread-local fallback.
- `wasLateral(memoryId)` moves to `RecallHistory` — **and this carries state with it.** `wasLateral` (L980) reads `recentRetrievalModes`, a bounded `ConcurrentHashMap<String, RetrievalMode>` (L199) with eviction at `RETRIEVAL_MODE_CACHE_MAX` (L635–645). The map moves too; the eviction policy must move intact. `ReinforcementHandler` then depends on `RecallHistory`, not `RecallPathway`.

#### 8.2.1 `ACTIVE_SIGNAL` has 12 read sites, not 3

An earlier draft scheduled this as "update three call sites." The real inventory:

| File | Sites | Nature |
|---|---|---|
| `RecallPathway.effectivePartitionRegistry()` L188–190 | 1 | **internal**, has field fallback `: this.partitionRegistry` |
| `RecallPathway.effectiveIndex()` L193–195 | 1 | **internal**, has field fallback `: this.index` |
| → called from L277, L278, L677, L678, L717, L807, L905, L906 | 8 | **recall hot path** |
| `TemporalFactWeavingStage` L56, L61, L66 | 3 | external |
| `HebbianCoActivationListener` L50 | 1 | external |

Two properties of the current code matter for migration:

1. The internal readers **fall back to instance fields** when the thread-local is unset. That fallback is what makes today's code tolerate a null `ACTIVE_SIGNAL`; `signal.context().find(...)` has no equivalent, so the fallback must be preserved explicitly during transition.
2. `execute` re-propagates the signal into listener threads deliberately (L429–433: `ACTIVE_SIGNAL.set(activeSig)` inside `ConcurrentTasks.fireAndForget`, `remove()` in `finally`) because pooled virtual threads do not reliably inherit. Any replacement must carry context into those async listeners explicitly.

**M4 is therefore split** (§15):

- **M4a** — thread `RecallSignal` explicitly into `effectiveIndex` / `effectivePartitionRegistry` and their 8 call sites, keeping the field fallback. Pure mechanical refactor, no `PathwayContext` involved, no behavior change, independently shippable and revertible.
- **M4b** — delete `ACTIVE_SIGNAL` + `activeSignal()`, migrate the 4 external readers to `signal.context()`, and pass context explicitly into the listener dispatch.

Doing this as one PR would touch the recall hot path with no intermediate safe state.

### 8.3 Reflect — `Pathway<ReflectSignal, ReflectReport>`

- Removes `RememberPathway` from every `execute` / `reflect` signature and from `ReflectSignal`.
- Gist extraction / crystallization stages that currently call `rememberPathway.ingest...` become `PathwayRelay`s.
- `SoftIdentityAnchorRelay` and AISME manifold relays stay ordinary relays.

### 8.4 Dream — `Pathway<DreamSignal, DreamReport>`

- Removes `rememberPathway` field from `DreamPathway` and from `DreamSignal`.
- `DreamIngestionRelay` either (a) becomes a `PathwayRelay` plus a mapper, or (b) keeps scene-selection logic and calls `catalog.invoke(RememberPathway.class, ctx, rememberSignal)` for each accepted scene. Prefer (a) if ingest is 1:1 with a scene batch; prefer (b) if one dream conduction writes N memories (loop inside the relay, still via catalog).
- Hebbian inhibition on failed pairs stays in Dream — that is Dream domain logic, not Remember.

### 8.5 Decide, Wander, Express

Already close. They extend `AbstractPathway`, keep inline gated recipes, and implement `project` as “return the report on the signal.”

`WanderPathway.close()` continues to close `ContinuityMemory`. `AbstractPathway.close()` is a hook they override.

### 8.6 Shared relays that are not pathways

`SpacetimeSeedRelay.DreamSeedRelay` / `WanderSeedRelay` stay relays, reused by two recipes. Promote to `SimulationPathway` only when simulation grows its own multi-stage circuit. Until then, do not invent a pathway for one relay.

---

## 9. Runtime wiring

`SpectorRuntime` already constructs pathways once and shares them across namespaces. After this ADR:

```java
public final class SpectorRuntime implements AutoCloseable {

    private final DefaultPathwayCatalog catalog = new DefaultPathwayCatalog();
    private final PathwayContext processContext;     // services, no kernel

    // existing builders populate pathways, then:
    void registerPathways() {
        catalog.register(RememberPathway.class, rememberPathway);
        catalog.register(RecallPathway.class,   recallPathway);
        catalog.register(ReflectPathway.class,  reflectPathway);
        catalog.register(DreamPathway.class,    dreamPathway);
        catalog.register(DecidePathway.class,   decidePathway);
        catalog.register(WanderPathway.class,   wanderPathway);
        catalog.register(ExpressPathway.class,  expressPathway);
    }

    public PathwayContext contextFor(NamespaceKernel kernel, boolean trace) {
        return processContext
                .nested("ns:" + kernel.namespaceId())
                .withKernel(kernel)
                .withTrace(trace);
    }
}
```

`DefaultSpectorMemory.recall(...)` becomes:

```java
public List<CognitiveResult> recall(String query, RecallOptions options) {
    RecallSignal signal = RecallSignal.forTextQuery(query, options);
    return runtime.catalog()
            .invoke(RecallPathway.class, runtime.contextFor(kernel, options.enableTrace()), signal);
}
```

Existing `memory.admin().recallPathway().recall(...)` keeps working because the concrete getter remains.

Attach-time capture (“first SpectorMemory fills in Recall if the runtime was not given one”) stays. After capture, `register` is called. Double-register is an error — today’s silent replacement becomes explicit.

---

## 10. Nested conduction rules

1. **Fresh signal.** Never pass a `DreamSignal` into Remember. Map to `RememberSignal`. Absorb a `RememberResult`.
2. **Shared context services, bag, outcome, and scope instance; new scope frame.** Traces record as `dream/dream_ingest/remember/cortical_write`. The `ConductionScope` object itself is shared by reference (§6.5.1) — only the frame is new.
3. **Synchronous.** Nested `conduct` blocks the caller relay. Fire-and-forget remains `ConsolidationRelay` only.
4. **Error policy is the caller’s stage policy.** Remember’s internal `FAIL_FAST` on cortical write still fails the Remember conduction; whether that fails *Dream* is Dream’s `PathwayRelay` `ErrorPolicy`. Full matrix: ADR-0036 §4.2.
5. **Cycle guard.** `ConductionScope` holds a stack of pathway names. Re-entering the same name throws `CognitivePathwayException` with code `PATHWAY_CYCLE`. Dream → Remember is legal. Remember → Dream → Remember is not.
6. **Reentrancy exception.** A pathway may be marked `reentrant()` only if it is stateless w.r.t. the current signal (none of the current seven are). Leave the hook; do not use it.
7. **Fork is not nest.** `DivergentCapable.fork()` is same-pathway parallelism. It shares context and does not push a scope frame.
8. **No `PathwayRelay` inside a divergent branch.** `ConductionScope` is thread-confined (§6.5.1) and divergent branches run on separate virtual threads, so nested invocation from a branch would corrupt the frame stack. `PathwayComposer.divergent(...)` rejects such branches at build time.
9. **Retry constructs a fresh nested input per attempt.** The retry decorator sits outside `PathwayRelay` (ADR-0036 §6), so `toInput.apply(signal)` re-runs and `bind` re-binds on each attempt — hence `bind` is idempotent-overwrite (§6.3). `absorb` runs at most once, only on the attempt that succeeds.

---

## 11. Lifecycle

```java
public interface SynapticRelay<S> {

    boolean transmit(S signal) throws Exception;

    default String relayName() { /* existing */ }

    default void open(PathwayContext processContext) {}

    default void close() {}
}
```

`AbstractPathway.close()` walks `engine` entries and closes relays that implement `AutoCloseable` or override `close()`. Today only Wander actually owns a resource (`ContinuityMemory`); this makes the contract uniform. Recall implements `AutoCloseable` like the others (no-op unless listeners need it).

Open is called once after catalog registration, with the process context (no kernel). Relays that need a kernel keep reading it from the *signal* context at transmit time.

---

## 12. Tracing

`RelayTrace` gains an optional scope prefix. Implementation: `CognitivePathway.conduct` already records `entry.relay().relayName()`. `DefaultPathwayContext.nested` sets `scope.segment()`. The recorder concatenates:

```
dream                  42_000_000 ns  EXECUTED
dream/dream_gate              120_000  EXECUTED
dream/salient_seed          3_100_000  EXECUTED
dream/dream_ingest          8_400_000  EXECUTED
dream/dream_ingest/remember 8_200_000  EXECUTED
dream/dream_ingest/remember/dedup_guard    180_000  EXECUTED
dream/dream_ingest/remember/cortical_write 6_400_000  EXECUTED
dream/langevin_discovery           0  BYPASSED
```

No change to `TraceableSignal`. Nested pathway traces are appended onto the *caller* signal after invoke returns (PathwayRelay copies `nestedInput.traces()` if both are `TraceableSignal`). Detail of failure traces: ADR-0036 §13.

---

## 13. Performance rules

- Context bind is one field write on the signal. No per-relay allocation.
- `context.get(Class)` is `IdentityHashMap` lookup on an immutable map sized at build time (~20 entries). Cheaper than today’s fluent-setter forest.
- `AttributeBag` is not touched on the SIMD hot loop. Cortical scan relays keep using signal fields (`candidates`, `queryVector`).
- Nested Remember from Dream is already a cold path (sleep / imagination). One extra mapper allocation is acceptable.
- Do not wrap every relay in `PathwayRelay`. Only actual pathway boundaries.

---

## 14. Testing

| Layer | What to test | Where |
|---|---|---|
| Conductor | Existing `CognitivePathwayTest` unchanged | `spector-commons` |
| Context / bag / keys | bind, missing key, nested shares bag, snapshot isolation on fork | new `PathwayContextTest` |
| Catalog | register-once, require, invoke pushes scope, cycle throws | new `PathwayCatalogTest` |
| PathwayRelay | maps in/out, missing target + required=false continues, required=true fails | new `PathwayRelayTest` |
| AbstractPathway | missing context throws, project is called, wrap of checked exceptions | new `AbstractPathwayTest` |
| Dream → Remember | fake `RememberPathway` registered; Dream ingest calls it N times; Remember failure degrades Dream | `DreamPathwayNestingTest` |
| Recall without ThreadLocal | listener reads TKG from context; after `conduct` returns, no leftover ambient state | replaces `activeSignal` tests |
| Nested recall | inner conduction does not clear the outer signal's context on exit (today's L456 bug) | `PathwayCatalogTest` |
| Scope shared through nesting | `Remember → Dream → Remember` throws `PATHWAY_CYCLE`; proves `nested()` did not reset the frame stack | `PathwayCatalogTest` |
| `PathwayRelay` in a divergent branch | `PathwayComposer.divergent(...)` throws at **build** time | `PathwayComposerTest` |
| Retry re-binds context | a retried `PathwayRelay` stage binds twice without throwing; `absorb` invoked once | `PathwayRelayTest` |
| `project()` sees `Finish` | Remember short-circuited on dedup returns `RememberResult.skipped()` | `AbstractPathwayTest` |
| `SoulVersionSource` is live | `setSoulVersion` after context construction is visible to a Reflect relay | `SoulDriftRefusionRelayTest` |
| Six signals traceable | each of the seven pathways records ≥1 `RelayTrace` when tracing is on | `PathwayTraceParityTest` |

Fakes:

```java
catalog.register(RememberPathway.class, new Pathway<>() {
    public String name() { return "remember"; }
    public Class<RememberSignal> inputType() { return RememberSignal.class; }
    public Class<RememberResult> outputType() { return RememberResult.class; }
    public RememberResult conduct(RememberSignal in) {
        return new RememberResult(in.id(), 0, false, in.type(), in.source());
    }
});
```

Dream tests no longer construct Remember’s six relays.

---

## 15. Migration

Incremental. Each step is independently shippable.

| Step | Change | Rollback |
|---|---|---|
| **M1** | Add commons types. No callers. Also: `CognitivePathwayException` uses `ErrorCode.MEMORY_PATHWAY_FAILED` instead of `INTERNAL_ERROR`; replace raw `IllegalArgumentException` in `CircuitBreakerRelay` / `DivergentRelay` with `SpectorValidationException`. | Delete the package files. |
| **M1.5** | **Delete `IngestionTarget`; `RememberPathway` stops implementing it.** Sink and pipeline call `SpectorMemory.remember(...)`. ADR-0037. | Restore the interface; it has one implementor. |
| **M2** | Domain pathways `implements Pathway<I,O>` *and* keep public verbs. `SpectorRuntime` implements/hosts the catalog. | Getters still work; catalog unused. |
| **M3** | Introduce `PathwayContext` on new conduct paths. Fluent setters on signals delegate to context and are `@Deprecated`. | Setters still populate both. |
| **M4a** | Thread `RecallSignal` into `effectiveIndex` / `effectivePartitionRegistry` and their 8 call sites. Field fallback retained. No context, no behavior change. | Mechanical revert of one file. |
| **M4b** | Delete `ACTIVE_SIGNAL` + `activeSignal()`. Migrate 4 external readers. Pass context explicitly into async listener dispatch. | Revert those 3 files. |
| **M4.5** | **Extract `SoulVersionSource` + `ScalarQuantizer` onto the context** (§8.1b). Unblocks the 5 state-accessor sites. | Sites fall back to the signal getter, still present. |
| **M5** | Dream + Reflect call Remember via catalog / `PathwayRelay`. Remove `rememberPathway` from their fields, signals, and execute overloads. | Restore field passing. |
| **M6** | Recipes replace factory overloads. **All six non-traceable signals extend `AbstractSignal`** (§6.3). Widest factory kept as `@Deprecated` wrapper. | Wrapper still used by any missed caller. |
| **M7** | Slim signals: remove service fields that are now on context. **Delivered:** the dead `kernel` field is gone from `Recall`/`Remember`/`Reflect`/`Dream`/`Wander` signals, with each `Builder.kernel(...)` and write site. Relays read the kernel from the context binding. | After one release of deprecation. |
| **M8** | **Rename `CognitivePathway` → `PathwayEngine`; one authoring API.** See §19. | Mechanical inverse rename. |

Ordering constraints, all of them load-bearing:

- **M1.5 before M2.** `implements Pathway<RememberSignal, RememberResult>` cannot coexist with `IngestionTarget`'s `void ingest`.
- **M4a before M4b.** M4b alone would touch 8 hot-path sites with no intermediate safe state.
- **M4.5 before M5.** M5 deletes the field that the 5 state-accessor sites read; without M4.5 they have nowhere to go.
- **M6 needs `AbstractSignal`** or §12's trace tree stays inert for six pathways.
- Do not start M7 until M4–M5 have soaked. Do not combine M5 and M6 in one PR.

Compatibility shims for one minor version:

```java
@Deprecated
public RememberPathway rememberPathway() { /* field on DreamSignal */ }

@Deprecated
public DreamSignal rememberPathway(RememberPathway p) {
    // no-op store; log once
    return this;
}
```

---

## 16. Consequences

### Positive

- Pathways are addressable peers. Adding an eighth pathway (e.g. `SimulatePathway`) is: implement `Pathway`, write a recipe, `catalog.register`. No existing constructor grows.
- Dream / Reflect constructors stop tracking Remember’s internals.
- Signals shrink to working memory. Relays become testable with a stub context.
- Thread-local ambient state goes away — required for correctness under virtual threads.
- Observability becomes a tree, not a flat list.

### Negative / accepted cost

- One extra type layer (`Pathway` vs `CognitivePathway`). Documented in §2 so newcomers do not collapse them again.
- Mappers (`DreamPorts`) must be written and kept in sync with `RememberSignal` fields. This is cheaper than constructor coupling.
- Catalog lookup on every nested call. Cold path only.
- Deprecation window on fluent setters and factory overloads.

### Risks

| Risk | Mitigation |
|---|---|
| Context used as a dump | Review rule: new `Class<T>` binds require a one-line justification in the PR. Prefer `Key<T>` for optionals. |
| Cycle introduced by a new pathway | `ConductionScope.assertNotOnStack` in `invoke`, over a scope shared through `nested()` (§6.5.1). Test in `PathwayCatalogTest`. |
| Missed `activeSignal` call site | `grep -rn activeSignal` in CI on M4b. Compile-fail once the method is deleted. M4a first removes the 8 internal readers, leaving 4. |
| Recipe forgets a relay | Parity test: `CognitivePathwayParityTest` compares recipe-built engine relay names **and order** to the current factory output for all seven pathways. |
| Stale soul version after M4.5 | `SoulVersionSource` is an accessor, never a snapshot (§6.5.2). Test asserts post-construction `setSoulVersion` visibility. |
| Scope corruption from a parallel branch | `ConductionScope` is thread-confined; `PathwayRelay` banned inside `DivergentRelay` at build time (§10.8). |
| Trace tree silently empty | `PathwayTraceParityTest` fails if any of the seven pathways records zero traces with tracing on. |

---

## 17. Implementation checklist

Commons (`spector-commons`):

- [ ] `Key`, `AttributeBag`, `PathwayContext`, `DefaultPathwayContext`
- [ ] `Pathway`, `AbstractPathway`, `ContextualSignal`, `AbstractSignal`, `PathwayExceptions`
- [ ] `PathwayCatalog`, `DefaultPathwayCatalog`
- [ ] `ConductionScope` — one per root conduction, thread-confined (§6.5.1)
- [ ] `PathwayRelay`
- [ ] `PathwayRecipe`, `PathwayComposer` (delegate to existing builder)
- [ ] `PathwayComposer.divergent(...)` rejects `PathwayRelay` branches at build time
- [ ] `RelayFactory` (map-backed)
- [ ] `CognitivePathwayException` → `ErrorCode.MEMORY_PATHWAY_FAILED`
- [ ] Raw `IllegalArgumentException` → `SpectorValidationException` in `CircuitBreakerRelay`, `DivergentRelay`
- [ ] Tests listed in §14

Memory:

- [ ] **`RememberPathway` stops implementing `IngestionTarget`** (M1.5, ADR-0037)
- [ ] Each domain pathway extends `AbstractPathway` / implements `Pathway`
- [ ] `SpectorRuntime` hosts the catalog and builds process context
- [ ] `RememberResult` + Remember `project` (incl. `skipped()` on short-circuit)
- [ ] `SoulVersionSource`; `ScalarQuantizer` bound on context (M4.5)
- [ ] `DreamPorts`, Reflect ports
- [ ] M4a: thread `RecallSignal` into the 8 internal `effective*` call sites
- [ ] M4b: delete `ACTIVE_SIGNAL` + 4 external readers
- [ ] `wasLateral` + `recentRetrievalModes` (with eviction) move to `RecallHistory`
- [ ] Collapse Reflect / Dream execute overloads
- [x] Recipes for Remember / Recall / Reflect — **superseded by §19: all seven pathways now have a recipe, none stay inline**
- [ ] All six non-traceable signals extend `AbstractSignal` (M6)
- [ ] Deprecate fluent service setters on signals

Docs:

- [ ] This ADR
- [ ] ADR-0036 (error handling, isolation, circuit breakers)
- [ ] ADR-0037 (ingestion boundary and sensory relocation)
- [ ] Short note on `docs/architecture/overview.md` pointing at all three

---

## 18. Revisit when

- A pathway needs asynchronous nested invocation (not fire-and-forget consolidation). That would require a different primitive than `PathwayRelay`.
- Relay graphs become data-driven (external YAML). Recipes would become the compiler target; this ADR does not block that.
- A DI container is adopted process-wide. `RelayFactory` / context binds would delegate to it; `Pathway` / catalog stay.

Error handling, isolation, retries, timeouts, bulkheads, and the circuit-breaker model for nested pathways are specified in **ADR-0036**.

---

## 19. M8 — `PathwayEngine` rename and a single authoring API

Added after the ten implementation phases landed, in response to a review question that is itself
the evidence for the change: *"shouldn't `build()` return a `Pathway` instance — `CognitivePathway`
is neither a pathway nor does it implement the interface?"*

### 19.1 The naming was inverted

Two types, genuinely different, and the one named `CognitivePathway` was not the pathway:

| Type | Shape | Responsibility |
|---|---|---|
| `Pathway<I, O>` | `I → O` | The public operation. Scope enter/leave, `ConductionOutcome` finish, `onConduct` metric, exception wrapping, and `project(S) → O`. |
| `PathwayEngine<S>` (was `CognitivePathway<S>`) | `S → S` | The relay conductor. Runs the stage list, applies each stage's `ErrorPolicy`, records traces. No output type, no projection. |

`AbstractPathway<S, O>` bridges them by **composition**, not inheritance — and it already called the
field `engine` with an `engine()` accessor. The code knew it was an engine; only the type name
pretended otherwise.

Renamed: `CognitivePathway` → `PathwayEngine`, and its static factory `pathway(name)` → `builder(name)`,
which removes the word "pathway" from the authoring call entirely. Blast radius was 154 references
across 32 files in three modules (`spector-commons`, `spector-memory`, `spector-metrics`) — nothing in
`synapse`, `cli`, `mcp` or `spring`. `CognitivePathwayException` keeps its name: it is about pathway
failure in general, and renaming it would be a public break with no clarity gain.

### 19.2 Rejected: make the engine implement `Pathway<S, S>`

Superficially tidy, since `S → S` is a valid `I → O`. Rejected because two implementations would
then satisfy one interface with **different semantics**: the engine's `conduct` skips the scope entry,
outcome finish and metric that `AbstractPathway.conduct` adds. A `PathwayCatalog` lookup could hand a
caller a bare conductor where it expected a full pathway — precisely the class of confusion this ADR
set out to remove. Do not propose this again.

### 19.3 Rejected: `composer.buildAs(name, in, out, projection)`

Would let `build()` hand back a `Pathway<S, O>` and delete `AbstractPathway` subclassing for the
simple cases. Rejected for now: the `project` methods on Recall, Reflect and Dream read substantial
signal state and would be worse as lambdas, and all seven pathways still need classes for their
builders and config. Revisit only if a pathway appears with a one-line projection and no builder.

### 19.4 One authoring API — every pathway is recipe + composer

Before this step, four of seven pathways used `PathwayComposer` and three authored stages on the raw
engine builder. That was not a resilience gap — ADR-0036 §14's "Decide / Wander / Express" row is
uniformly `DEGRADE_GRACEFULLY` with nothing remote to isolate — but it meant **two ways to build the
same structure, where the raw one bypasses every build-time guard**: rejecting retry on a
non-idempotent relay, a timeout on a non-interruptible one, and `ABORT` or `PathwayRelay` inside a
divergent branch. Those guards live in the composer, not the builder.

Delivered:

- New recipes: `DreamRecipe`, `WanderRecipe`, `ExpressRecipe`, `DecideRecipe`.
- `WanderPathway`, `ExpressPathway`, `DecidePathway` migrated off the raw builder onto `PathwayComposer`.
- `DreamPathway`'s inline 13-stage list moved verbatim into `DreamRecipe`. Dream was the worst case:
  it *used* the composer but had no recipe, so the M6.4 parity gate could not see the shape of the
  pathway with the most delicate wiring in the system — an `ABORT` gate, a budgeted LLM stage, and a
  nested-Remember stage that must have breaker and bulkhead but deliberately no timeout.
- Relay names for all four moved into `RelayNames` with values preserved verbatim. Relay names are
  load-bearing: they key traces, outcome scopes and the parity assertions. Express keeps its
  CamelCase names for that reason, inconsistent though they look next to everything else.
- Production code no longer routes through the `@Deprecated` factories. `RememberPathway` and
  `ReflectPathway` now call `PathwayComposer` + recipe directly; `RememberPathwayFactory`,
  `ReflectPathwayFactory` and `RecallPathwayFactory` remain solely as external-caller shims.
- `RecipeShapeParityTest` pins shape and policy for the four new recipes, plus Dream's decorator
  placement. With the existing `PathwayParityTest.RecipeRelayParity` covering Remember/Recall/Reflect
  against their factories, all seven pathways now have an asserted shape.

The engine builder remains public because `DefaultPathwayComposer` is implemented on top of it, and
tests that exercise the conductor directly legitimately use it. It is no longer the authoring path for
any production pathway.

> One process note worth recording: the decorator-walk helper in `RecipeShapeParityTest` initially
> returned an empty chain for every stage, because the composer re-wraps each stage in a `NamedRelay`
> that the walk did not unwrap. Every `doesNotContain` assertion passed — for the wrong reason. The
> test now carries an explicit anti-vacuity case asserting the walk finds decorators where they are
> known to exist. Shape tests that can silently degrade to tautologies need that guard.
