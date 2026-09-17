# ADR-0036: Pathway Error Handling, Isolation, and Circuit Breakers

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
- **Affects:** `commons.pathway` (`ErrorPolicy`, `CircuitBreakerRelay`, `CognitivePathway`, `CognitivePathwayException`, `DivergentRelay`), domain pathways, `commons.error`
- **Depends on:** [ADR-0035 — Cognitive Pathway Framework Rearchitecture](0035-cognitive-pathway-rearchitecture.md)
- **Supersedes:** Implicit two-value `ErrorPolicy` as the *entire* resilience story
- **Code baseline:** all line references verified against `main` @ `33af1601`
- **Implementation status:** delivered on `feat/cognitive-pathway-rearchitecture`. Sections amended after implementation: §14 (KG enrichment is not retried; policy/decorator assertions now live in `PathwayResilienceWiringTest`).

---

## 1. Context

ADR-0035 makes pathways composable. Composition without a resilience model just moves the failure: Dream calling Remember can either swallow a cortical-write crash or take the whole sleep cycle down with it, and there is no specified answer.

Today the engine has three primitives:

1. **`ErrorPolicy.FAIL_FAST`** — throw, stop the remaining relays. `SpectorException` is rethrown as-is; anything else becomes `CognitivePathwayException(pathway, relay, cause)`.
2. **`ErrorPolicy.DEGRADE_GRACEFULLY`** — log warning, record `TraceStatus.DEGRADED`, continue.
3. **`CircuitBreakerRelay`** — consecutive-failure counter, `CLOSED → OPEN → HALF_OPEN`, default 5 failures / 30s cooldown (L40–41). On OPEN it *returns `true`* (L104: bypass, continue the pathway). Failures always rethrow (L110); the breaker only skips the *next* calls. Any `Exception` counts as a failure — no type filter. HALF_OPEN does not cap concurrent probes, and any single success closes the circuit.

These are necessary and insufficient.

### 1.1 Gaps

| Gap | Why it hurts |
|---|---|
| Two policies, no taxonomy | A validation error (`SpectorValidationException`) and a remote-LLM timeout are treated the same. Validation should never trip a breaker. Timeouts should. |
| No timeout | A hung embedding provider blocks a virtual thread until the provider’s own socket timeout, if any. The pathway cannot declare a budget. |
| No retry | Transient embed / LLM / remote-rerank blips fail a `FAIL_FAST` stage that could have succeeded on attempt 2. Blind retry on Remember’s cortical write would be dangerous — so retry must be *opt-in and classified*. |
| Nested policy is undefined | Remember’s write is `FAIL_FAST`. Dream’s ingest stage should be `DEGRADE_GRACEFULLY`. Today Dream catches inside the relay. After ADR-0035, `PathwayRelay` must define the intersection. |
| Breaker bypass looks like success | OPEN returns `true` with no trace status `BYPASSED`, no degraded flag on the signal, no metric. Callers cannot tell “ingest skipped because Remember is on fire.” |
| One breaker shape | Same 5/30s for an in-process graph walk and an LLM call. No per-target config, no shared breaker across pathways hitting the same downstream. |
| No isolation | A runaway Dream → Remember loop can saturate the same virtual-thread scheduler Recall is using. No bulkhead. |
| Divergent + FAIL_FAST | `DivergentRelay` cancels the sibling story only via `ConcurrentTasks.forkJoinAll` throwing. Per-branch `DEGRADE_GRACEFULLY` works; interaction with breakers is unspecified. |
| No conduction-level result | Callers get either a report or an exception. Partial success (Reflect pruned 4 of 14 stages) is buried in log lines. |
| `ConsolidationRelay` errors vanish | Fire-and-forget exceptions are whatever `ConcurrentTasks.fireAndForget` does. Unspecified. |

### 1.2 What we will not do

- Hystrix-style thread-pool isolation. We are on virtual threads; isolation is a *concurrency cap + timeout*, not a platform thread pool per relay.
- Retrying every relay by default.
- Mapping every `ErrorCode` onto a unique policy. Classification is a small enum (see §3).
- Making `DEGRADE_GRACEFULLY` the default. Writes and scoring stay fail-fast unless the recipe says otherwise.

---

## 2. Decision

1. Keep `ErrorPolicy` as the *stage disposition* (stop vs continue).
2. Add an **error class** (`FaultKind`) so breakers, retries, and metrics can tell transient from permanent.
3. Add **stage decorators** — timeout, retry, circuit breaker, bulkhead — composed in a fixed order, configured per relay in the recipe.
4. Define the **nested-pathway matrix**: callee throws → caller stage policy decides; callee degrades internally → caller sees a successful nested result plus a degraded flag.
5. Promote circuit breaking from “wrapper with two ints” to a **named breaker** that can be shared across pathways hitting the same downstream.
6. Return **`ConductionOutcome`** metadata on the context (not a breaking change to `conduct` return types). Domain reports already exist; they gain a standard degraded/skipped section.

---

## 3. Fault taxonomy

```java
public enum FaultKind {
    /** Input failed validation. Never retry. Never trip a breaker. */
    VALIDATION,

    /** Domain invariant (duplicate id with fail-on-dup, missing kernel). Never retry. Never trip. */
    CONTRACT,

    /** Expected empty / gated-off / short-circuit. Not a fault. Stage continues. */
    CONTROL,

    /**
     * Thread was interrupted. Distinct from {@link #CONTROL}: an interrupted
     * conduction ALWAYS stops (§4.1), whereas CONTROL always continues.
     * Never retry. Never trip a breaker.
     */
    INTERRUPTED,

    /** Transient I/O, timeout, remote 429/503, embed provider hiccup. Retryable. Trips breaker. */
    TRANSIENT,

    /** Remote 4xx (other than 429), parse failure from LLM, corrupt optional payload. No retry. May trip. */
    DOWNSTREAM,

    /** Bug, NPE, assertion. No retry. Does not trip a downstream breaker (it is our bug). */
    INTERNAL
}
```

`CONTROL` and `INTERRUPTED` are separate because an earlier draft mapped `InterruptedException` to `CONTROL` while simultaneously specifying "CONTROL + `InterruptedException` → `FAIL_FAST` regardless of stage policy." One kind cannot mean both *continue* and *always stop*.

Classification lives in one place:

```java
public final class Faults {
    private Faults() {}

    public static FaultKind kindOf(Throwable t) {
        if (t instanceof SpectorValidationException) return FaultKind.VALIDATION;
        if (t instanceof CognitivePathwayException cpe && cpe.kind() != null) return cpe.kind();
        if (t instanceof java.util.concurrent.TimeoutException) return FaultKind.TRANSIENT;
        if (t instanceof java.net.http.HttpTimeoutException) return FaultKind.TRANSIENT;
        if (t instanceof java.io.IOException) return FaultKind.TRANSIENT;
        if (t instanceof InterruptedException) return FaultKind.INTERRUPTED;
        if (t instanceof SpectorException se) return kindFrom(se.errorCode());
        return FaultKind.INTERNAL;
    }
}
```

Two notes on the existing code this has to interoperate with:

- `ConcurrentExecutionException` (`commons/concurrent`) is a **checked** `Exception` and is **not** part of the `SpectorException` tree. `Faults.kindOf` must unwrap it — `DivergentRelay` already unwraps it by hand (L100–106) — otherwise every divergent branch failure classifies as `INTERNAL`.
- `CognitivePathwayException` currently hardcodes `ErrorCode.INTERNAL_ERROR` in all three constructors, so today pathway failures are indistinguishable from NPEs. `ErrorCode.MEMORY_PATHWAY_FAILED` (`310_017`, `"Cognitive pathway execution failed for {}: {}"`, category `MEMORY`) already exists and is unused. Switch to it in R1.

`ErrorCode` mapping (extend `commons.error` only if a code is missing):

| ErrorCode (existing / add) | FaultKind |
|---|---|
| validation / bad request codes | `VALIDATION` |
| `INTERNAL_ERROR` (default today on `CognitivePathwayException`) | `INTERNAL` unless cause says otherwise |
| new `PATHWAY_TIMEOUT` | `TRANSIENT` |
| new `PATHWAY_CIRCUIT_OPEN` | `TRANSIENT` (the *call* was rejected; the downstream is sick) |
| new `PATHWAY_CYCLE` | `CONTRACT` |
| new `PATHWAY_BULKHEAD` | `TRANSIENT` |
| new `PATHWAY_NESTED_FAILED` | inherit from cause |

`CognitivePathwayException` gains fields:

```java
public class CognitivePathwayException extends SpectorServerException {
    private final String pathwayName;
    private final String relayName;
    private final FaultKind kind;
    private final boolean nested;

    public CognitivePathwayException(String pathwayName, String relayName,
                                     FaultKind kind, boolean nested, Throwable cause) { ... }

    public FaultKind kind() { return kind; }
    public boolean nested() { return nested; }
}
```

Existing three constructors stay; they default `kind = Faults.kindOf(cause)`, `nested = false`.

`AbstractPathway` wrapping (ADR-0035 §6.2) uses `Faults.kindOf` and does **not** flatten a `CognitivePathwayException` that already has names — nested exceptions stay nested via `cause`, and `nested = true` is set by `PathwayRelay` only.

---

## 4. Stage disposition (`ErrorPolicy`) — extended

Keep the two existing values. Add one:

```java
public enum ErrorPolicy {
    FAIL_FAST,
    DEGRADE_GRACEFULLY,
    /**
     * Stage failure becomes a short-circuit (stop remaining relays) WITHOUT
     * throwing out of {@code CognitivePathway.conduct}. Used for gates that
     * mean "this conduction is done" rather than "the process is broken"
     * (e.g. DreamGateRelay deciding not to dream).
     *
     * Relays can already return false to short-circuit. ABORT is for the
     * *exception* path: treat the throw as a clean stop.
     */
    ABORT
}
```

`ABORT` records `TraceStatus.SHORT_CIRCUITED` (not `FAILED`), does not trip breakers, and does not mark the context degraded.

### 4.1 Conductor loop (updated)

Current loop in `CognitivePathway.conduct`: try transmit → on exception FAIL_FAST throw / DEGRADE log. Change to:

```
for entry in entries:
    try:
        continue = entry.relay.transmit(signal)
        record EXECUTED or SHORT_CIRCUITED
        if !continue: break
    catch Exception e:
        kind = Faults.kindOf(e)
        if kind == INTERRUPTED:                    # overrides stage policy
            Thread.currentThread().interrupt()
            record FAILED
            throw wrap(e)
        switch entry.errorPolicy:
            FAIL_FAST:
                record FAILED
                throw wrap(e)          # existing SpectorException preservation
            DEGRADE_GRACEFULLY:
                record DEGRADED
                context.outcome().markDegraded(entry.name, kind, e)
                continue
            ABORT:
                record SHORT_CIRCUITED
                break
```

`INTERRUPTED` bypasses the policy switch entirely: restore the interrupt flag, record `FAILED`, throw regardless of the stage's `ErrorPolicy`. An interrupted conduction does not continue "gracefully." Breakers are **not** notified — an interrupt is our caller's decision, not a downstream fault.

Two notes on the conductor as it exists today:

- The loop catches only `Exception`; `Error` escapes uncaught. That stays.
- `ErrorPolicy` is read in `if`/`else`, and a repo-wide check confirms **no exhaustive `switch` over `ErrorPolicy` anywhere** in `memory`, `nucleus`, or `synapse`. Adding `ABORT` is therefore source-compatible. It is still a public enum in commons, so downstream consumers outside this repo could have exhaustive switches — note it in release notes.
- The conductor must write outcome marks only when the signal carries a context. Until ADR-0035 M6 lands, six of seven signals are not `ContextualSignal`; guard with `instanceof` and no-op otherwise.

### 4.2 Nested matrix (`PathwayRelay`)

Callee is `RememberPathway`. Caller stage is Dream ingest.

| Callee result | Caller stage policy | Caller pathway result |
|---|---|---|
| returns normally, no degraded marks | any | absorb output, continue |
| returns normally, callee marked degraded internally (Remember graph-link degraded) | any | absorb output, copy degraded marks into caller outcome with prefix `dream_ingest/`, continue |
| throws `VALIDATION` / `CONTRACT` | `FAIL_FAST` | wrap as nested, throw |
| throws `VALIDATION` / `CONTRACT` | `DEGRADE_GRACEFULLY` | mark degraded, continue (do not absorb) |
| throws `VALIDATION` / `CONTRACT` | `ABORT` | short-circuit caller |
| throws `TRANSIENT` / `DOWNSTREAM` / `INTERNAL` | `FAIL_FAST` | wrap as nested, throw |
| throws `TRANSIENT` / `DOWNSTREAM` / `INTERNAL` | `DEGRADE_GRACEFULLY` | mark degraded, continue (do not absorb) |
| throws `TRANSIENT` / `DOWNSTREAM` / `INTERNAL` | `ABORT` | short-circuit caller |
| breaker OPEN on caller’s `PathwayRelay` (Remember not even invoked) | any | record `BYPASSED`, mark degraded with `PATHWAY_CIRCUIT_OPEN`, continue if policy ≠ `FAIL_FAST`; throw if `FAIL_FAST` |

Rule in one sentence: **the callee’s internal policies decide whether the callee throws; the caller’s stage policy decides whether that throw kills the caller.**

`PathwayRelay` implementation:

```java
try {
    O output = catalog.invoke(targetType, signal.context(), nestedInput);
    absorb.accept(signal, output);
    signal.context().outcome().importFrom(nestedInput.context().outcome(), name);
    return true;
} catch (Exception e) {
    throw new CognitivePathwayException(
            signal.context().scope().pathwayName(),
            name,
            Faults.kindOf(e),
            true,
            e);
    // CognitivePathway.conduct then applies THIS stage's ErrorPolicy
}
```

Do not catch-and-swallow inside `PathwayRelay`. The conductor is the only place that interprets `ErrorPolicy`. That keeps one code path.

---

## 5. `ConductionOutcome`

Lives on the context, not on every signal. Avoids seven copies.

```java
public final class ConductionOutcome {

    public enum Finish { COMPLETED, SHORT_CIRCUITED, FAILED }

    public record Mark(
            String scope,                 // "dream/dream_ingest"
            FaultKind kind,
            String message,
            String errorCode              // nullable
    ) {}

    private Finish finish = Finish.COMPLETED;
    private final List<Mark> degraded = new CopyOnWriteArrayList<>();
    private final List<Mark> bypassed = new CopyOnWriteArrayList<>();

    public void markDegraded(String scope, FaultKind kind, Throwable cause) { ... }
    public void markBypassed(String scope, String reason) { ... }
    public void finish(Finish f) { this.finish = f; }

    public boolean degraded() { return !degraded.isEmpty(); }
    public Finish finish() { return finish; }
    public List<Mark> degradedMarks() { return List.copyOf(degraded); }
    public List<Mark> bypassedMarks() { return List.copyOf(bypassed); }

    void importFrom(ConductionOutcome child, String prefix) { ... }
}
```

`PathwayContext.outcome()` returns this. `AbstractPathway.conduct` sets `FAILED` when it wraps an exception, `SHORT_CIRCUITED` when the engine breaks without throwing, `COMPLETED` otherwise.

Domain reports should surface it, without breaking existing constructors — add an optional field or a decorator:

```java
public record ReflectReport( /* existing fields */, ConductionOutcome outcome) {
    public boolean degraded() { return outcome != null && outcome.degraded(); }
}
```

For one release, `outcome` may be null when built by legacy tests. New `project` methods always pass it.

`DefaultSpectorMemory` / MCP tools do not change response JSON yet. A follow-up can add `"degraded": true` to recall metadata. Out of scope here except to require the field to exist in-process.

---

## 6. Stage decorators — composition order

Decorators wrap the inner relay. Applied from inside out so the mental model matches the recipe:

```
bulkhead → timeout → retry → circuit breaker → named relay → user relay
```

Why this order:

| Layer | Outside of | Reason |
|---|---|---|
| Bulkhead | everything | Reject before allocating a timeout task or a retry attempt. |
| Timeout | retry + breaker | Each *attempt* has a budget. Retry sees `TimeoutException` as `TRANSIENT`. |
| Retry | breaker | The breaker counts *attempts that exhausted retry*, not every 429. |
| Circuit breaker | user relay | OPEN skips the user relay entirely. HALF_OPEN lets one attempt through. |

Recipe API (extends ADR-0035 composer):

```java
composer.stage("remote_rerank")
        .relay(new CognitiveRerankRelay(...))
        .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
        .timeout(Duration.ofMillis(80))
        .retry(RetryPolicy.of(2, Duration.ofMillis(10), FaultKind.TRANSIENT))
        .breaker(BreakerRef.of("rerank-remote", CircuitBreakerConfig.remote()))
        .bulkhead(BulkheadConfig.of(8))
        .add();
```

Shorthand for the common case stays:

```java
composer.relay(name, relay, policy);                       // no decorators
composer.circuitBreaker(name, relay, cfg, policy);         // breaker only
```

Implementation: each decorator is a `SynapticRelay` wrapper, same as today’s `NamedRelay` / `CircuitBreakerRelay` / `GatedRelay`. `PathwayComposer.add` builds the chain and then applies the pathway-level interceptor (metrics) **outside** everything, so metrics see timeouts and open-circuit bypasses.

```
interceptor (metrics)
  └─ bulkhead
       └─ timeout
            └─ retry
                 └─ circuit breaker
                      └─ NamedRelay
                           └─ user relay
```

Gated relays wrap *after* naming and *before* interceptors, so a closed gate does not count as a breaker success and does not consume a bulkhead permit:

```
interceptor
  └─ GatedRelay
       └─ (decorator chain)
            └─ user relay
```

`PathwayComposer.gated(...)` is responsible for that placement.

---

## 7. Timeout

```java
public final class TimeoutRelay<S> implements SynapticRelay<S> {

    private final SynapticRelay<S> delegate;
    private final Duration budget;

    @Override
    public boolean transmit(S signal) throws Exception {
        try {
            return ConcurrentTasks.callWithTimeout(() -> delegate.transmit(signal), budget);
        } catch (TimeoutException e) {
            throw new CognitivePathwayException(
                    /* pathway from signal.context().scope() */,
                    relayName(),
                    FaultKind.TRANSIENT,
                    false,
                    e);
        }
    }
}
```

### 7.1 Reuse `forkJoinPartial`, do not add a second timeout primitive

An earlier draft said "if `ConcurrentTasks.callWithTimeout` does not exist, add it." A deadline-aware primitive **already exists**:

```java
// ConcurrentTasks L543
public static <T> PartialResult<T> forkJoinPartial(
        List<LabeledTask<T>> tasks, Duration timeout) throws InterruptedException;
```

It already does everything `TimeoutRelay` needs, on both execution paths:

- structured: `Joiner.awaitAll()` + `Configuration.withTimeout(timeout)`, swallows `StructuredTaskScope.TimeoutException`, then classifies each subtask `SUCCESS` / `FAILED` / `UNAVAILABLE`
- classic: per-future `get(remaining, MILLISECONDS)` against a computed deadline, `cancel(true)` on expiry
- returns labelled successes, timeouts, and failures rather than a bare throw

Extract the single-task path out of it as `callWithTimeout(Callable<T>, Duration)` so both share one implementation. Do **not** write a parallel timeout mechanism with different cancellation semantics — that is how the two paths drift.

Requirements are unchanged: virtual thread for the attempt, `cancel(true)` on expiry, and **the delegate must honour interruption**.

### 7.2 Only interruptible relays may declare a budget

This is a hard rule, and §7.3 exists because an earlier draft violated it.

`cancel(true)` sets the interrupt flag. That aborts a blocking socket read. It does **not** abort:

- a `MemorySegment` read/write (no interruptible channel involved, no `InterruptedException`)
- a tight in-process compute loop that never checks `Thread.interrupted()`

A relay that cannot observe an interrupt is not made faster by wrapping it — the caller merely *reports* a timeout while the work continues on a detached thread. `PathwayComposer` rejects `.timeout(...)` on any relay that does not declare interruptibility, mirroring the `IdempotentRelay` gate for `.retry(...)` (§8).

### 7.3 Nested Remember gets **no** timeout

**Corrected from an earlier draft, which assigned it 200ms.** That is unsafe and the mitigation ("make write honour interrupt only between records") does not work.

`CorticalWriteTransactionRelay` writes to mmap'd `MemorySegment` regions plus the WAL. Those writes are not interruptible. So on a 200ms expiry you get the worst possible combination:

1. The timeout fires and the caller records a degraded mark — Dream believes the scene was **not** persisted.
2. The write **completes anyway** on the detached thread.
3. Dream then applies Hebbian inhibition to a pair it believes failed, and the outcome says "skipped" while the bundle says "written."

That is a torn-state hazard introduced *by* the resilience layer.

Nested Remember is protected by **admission control only** — bulkhead (§10) and circuit breaker (§9) — because those decide whether to *start*, and "started or not" is a state you can reason about. If a latency guard is wanted later, it must be a pre-check that rejects before invocation, never a mid-flight cancellation.

**Default budgets (starting values, overridable):**

| Stage class | Budget | Interruptible? |
|---|---|---|
| In-process scan / score / graph walk / sort | **none** (do not wrap) | no |
| Cortical write / WAL journal | **none** — never | no (mmap) |
| Nested Remember from Dream / Reflect | **none** — bulkhead + breaker only (§7.3) | no (wraps a write) |
| Embedding provider | 2s | yes (HTTP) |
| Sparse / SPLADE encoder | 2s | yes (HTTP) |
| Remote reranker | 80ms | yes (HTTP) |
| LLM entity extract / dream scene construct | 8s | yes (HTTP) |

Every budgeted row is remote HTTP. That is not a coincidence — it is the rule from §7.2.

Timeouts are never implicit. A recipe that omits `.timeout(...)` has no budget.

---

## 8. Retry

```java
public final class RetryPolicy {
    private final int maxAttempts;              // includes the first try; 1 = no retry
    private final Duration initialBackoff;
    private final double jitter;                // 0.0–1.0
    private final EnumSet<FaultKind> retryOn;

    public static RetryPolicy none() { return of(1, Duration.ZERO, EnumSet.noneOf(FaultKind.class)); }

    public static RetryPolicy of(int maxAttempts, Duration backoff, FaultKind... kinds) { ... }
}
```

Rules:

1. Default `retryOn = {TRANSIENT}`.
2. Never retry `VALIDATION`, `CONTRACT`, `CONTROL`, `INTERNAL`.
3. `DOWNSTREAM` is opt-in only (LLM returned garbage — retrying may help; usually it will not).
4. Backoff is `initial * 2^(attempt-1)` plus jitter `± jitter * backoff`.
5. Sleep uses `LockSupport.parkNanos` on the current virtual thread.
6. If a timeout wrapper is present, each attempt gets a fresh timeout.
7. **Idempotency gate.** A relay must implement `IdempotentRelay` to be retry-wrapped:

```java
public interface IdempotentRelay {
    /** True if transmit() may be called again after a throw. */
    default boolean idempotent() { return true; }
}
```

Remember’s `CorticalWriteTransactionRelay` does **not** implement it. Composer rejects `.retry(...)` on a non-idempotent relay at build time.

Embed, remote rerank, LLM scene construct, BM25 remote — yes.

Attempt accounting: if all attempts fail, the last exception is thrown. The breaker (outside retry) sees **one** failure.

---

## 9. Circuit breaker — redesign

### 9.1 Keep the state machine, fix the holes

States stay `CLOSED / OPEN / HALF_OPEN`. Changes:

| Today | After |
|---|---|
| Anonymous wrapper; cannot share | Named `CircuitBreaker` instance, looked up by `BreakerRef` |
| Counts every exception | Counts `TRANSIENT` and `DOWNSTREAM` only |
| OPEN returns `true` (looks like success) | OPEN throws `CircuitOpenException` (`FaultKind.TRANSIENT`, `ErrorCode.PATHWAY_CIRCUIT_OPEN`) **or** returns bypass if the wrapper is configured `onOpen = BYPASS` |
| HALF_OPEN lets every concurrent caller probe | HALF_OPEN allows `halfOpenProbes` (default 1) in-flight probes |
| Success in HALF_OPEN always closes | Requires `halfOpenSuccesses` consecutive probe successes (default 1) |
| No metrics | `onStateChange(name, from, to)` consumer + existing observation interceptor |
| No trace | Records `BYPASSED` + outcome mark when OPEN and policy is degrade |

```java
public enum OnOpen { FAIL, BYPASS }

/** Shared trip behaviour for one named downstream. NO onOpen here — see below. */
public final class CircuitBreakerConfig {
    private final int failureThreshold;          // default 5
    private final Duration cooldown;             // default 30s
    private final int halfOpenProbes;            // default 1
    private final int halfOpenSuccesses;         // default 1
    private final EnumSet<FaultKind> tripOn;     // default TRANSIENT, DOWNSTREAM

    public static CircuitBreakerConfig inProcess() {
        return builder().failureThreshold(20).cooldown(Duration.ofSeconds(5)).build();
    }

    public static CircuitBreakerConfig remote() {
        return builder().failureThreshold(5).cooldown(Duration.ofSeconds(30)).build();
    }
}

/** Per-call-site usage of a shared breaker. onOpen lives HERE. */
public record BreakerRef(String name, CircuitBreakerConfig config, OnOpen onOpen) {
    public static BreakerRef of(String name, CircuitBreakerConfig cfg, OnOpen onOpen) { … }
}
```

#### `onOpen` is a call-site decision, not a downstream property

An earlier draft put `onOpen` on `CircuitBreakerConfig` and defaulted it to "FAIL for `FAIL_FAST` stages, BYPASS for DEGRADE." That contradicts the shared-breaker model that is the whole point of §9.3.

`embed-provider` is **one** breaker shared by Recall and Dream, and it needs opposite behaviour on each:

| Call site | Wants | Why |
|---|---|---|
| Recall query transduction | `FAIL` | no query vector ⇒ recall cannot proceed |
| Dream scene embedding | `BYPASS` | skip the scene, keep dreaming |

One shared config cannot express both. Trip *state* is shared; the reaction to an open circuit is local. A config whose behaviour silently changed based on the stage it was attached to would also be spooky action at a distance.

#### Registry semantics on config mismatch

```java
public interface CircuitBreakerRegistry {
    CircuitBreaker get(String name, CircuitBreakerConfig cfg);
}
```

**First registration wins.** A later `get` with a non-equal config for the same name logs at WARN and returns the existing breaker; it does not mutate it and does not throw. Leaving this unspecified would make trip thresholds depend on class-initialisation order.

`CircuitBreakerConfig` implements `equals` so the mismatch check is meaningful.

`CircuitBreakerRelay` becomes a view over a shared `CircuitBreaker`:

```java
public final class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final CircuitBreakerConfig config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger halfOpenInFlight = new AtomicInteger();
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger();
    private final AtomicLong lastStateChangeMs = new AtomicLong();

    /** @param onOpen supplied by the CALL SITE via BreakerRef, not by config. */
    public Permit tryAcquire(OnOpen onOpen) { ... }   // throws CircuitOpenException or BYPASS permit
    public void onSuccess(Permit p) { ... }
    public void onFailure(Permit p, FaultKind kind) { ... }
}
```

`CircuitBreakerRegistry` lives on the process context (bound once on `SpectorRuntime`), with the first-wins semantics specified above.

Bound once on `SpectorRuntime`. Two pathways that call the same embed provider use `BreakerRef.of("embed-provider")` and share trip state. That is the point — Dream and Recall should not independently hammer a dying Ollama.

### 9.2 Acquire algorithm

```
state = current
if OPEN:
    if now - lastChange > cooldown:
        CAS OPEN → HALF_OPEN
        // fall through
    else:
        if onOpen == FAIL: throw CircuitOpenException
        else: return Permit.bypass()
if HALF_OPEN:
    if inFlight >= halfOpenProbes: treat as OPEN (fail or bypass)
    else inFlight++
        return Permit.probe()
// CLOSED
return Permit.closed()
```

On success: if probe, `halfOpenSuccesses++`; if `>= config.halfOpenSuccesses`, CAS to CLOSED and reset counters. On failure of a trip-able kind: if probe, immediately OPEN; if closed, `failures++` and OPEN at threshold. Non-trip kinds (`VALIDATION`, `CONTRACT`, `INTERNAL`, `CONTROL`, `INTERRUPTED`) never increment and never change state.

### 9.3 Where breakers belong

`onOpen` is per row because it is a call-site decision (§9.1) — note `embed-provider` appearing twice with the *same* shared name and *opposite* `onOpen`. That is the intended design, and it is why `onOpen` cannot live on the shared config.

| Relay / boundary | Breaker | Shared name | onOpen |
|---|---|---|---|
| Query embedding (Recall) | yes | `embed-provider` | **FAIL** — no vector ⇒ recall cannot proceed |
| Scene embedding (Dream) | yes | `embed-provider` (same) | **BYPASS** — skip the scene, keep dreaming |
| Sparse encoder | yes | `sparse-embed` | BYPASS (lexical path can continue) |
| Remote rerank | yes | `rerank-remote` | BYPASS |
| LLM scene construct / entity extract | yes | `llm-provider` | BYPASS |
| Nested Remember from Dream | yes | `pathway:remember` | BYPASS |
| Nested Remember from Reflect | yes | `pathway:remember` (same) | BYPASS |
| Cortical scan, graph walk, sort | no | — | — |
| WAL journal | no | — | — |
| Dedup / tag transduction | no | — | — |

A pathway-level breaker (`pathway:remember`) trips when Remember itself is unhealthy (repeated `INTERNAL`/`TRANSIENT` out of `conduct`), not when a single graph-link relay degraded internally.

**Acquire and record both happen in the `PathwayRelay` decorator.** An earlier draft split them — permit acquired by the caller's `PathwayRelay`, failure recorded by `AbstractPathway.conduct` in the callee — which lets success and failure accounting drift, because the callee has no `Permit` and cannot know whether it was a probe. `AbstractPathway` therefore notifies **no** breaker (ADR-0035 §6.2); the decorator chain owns the whole permit lifecycle:

```java
Permit p = breaker.tryAcquire(ref.onOpen());   // may throw CircuitOpenException or return bypass
if (p.isBypass()) { outcome.markBypassed(scope, "circuit_open:" + ref.name()); return true; }
try {
    O out = catalog.invoke(target, ctx, in);
    breaker.onSuccess(p);
    …
} catch (Exception e) {
    breaker.onFailure(p, Faults.kindOf(e));
    throw new CognitivePathwayException(…, nested = true, e);
}
```

This also keeps the rule from §4.2 intact: the conductor remains the only place that interprets `ErrorPolicy`.

### 9.4 Existing `CircuitBreakerRelay` compatibility

Keep the class. Reimplement it as:

```java
public CircuitBreakerRelay(SynapticRelay<S> delegate, int threshold, long cooldownMs) {
    this(delegate, new CircuitBreaker("anon-" + delegate.relayName(),
            CircuitBreakerConfig.builder()
                .failureThreshold(threshold)
                .cooldown(Duration.ofMillis(cooldownMs))
                .onOpen(OnOpen.BYPASS)       // preserves today's return-true behavior
                .build()));
}
```

Existing `CognitivePathway.Builder.circuitBreaker(...)` keeps working. New recipes prefer named breakers.

Tests in `CognitivePathwayTest` that trip OPEN and expect continue-without-throw still pass (`OnOpen.BYPASS`).

---

## 10. Bulkhead (concurrency isolation)

```java
public final class BulkheadConfig {
    private final int maxInFlight;               // permits
    private final Duration wait;                 // 0 = non-blocking reject
    private final OnReject onReject;             // FAIL or BYPASS
}

public enum OnReject { FAIL, BYPASS }

public final class BulkheadRelay<S> implements SynapticRelay<S> {
    private final Semaphore permits;
    // acquire(wait):
    //   fail → throw CognitivePathwayException(PATHWAY_BULKHEAD, TRANSIENT)
    //       or return true after outcome.markBypassed(...)
}
```

Virtual threads make this a *logical* cap, not a platform-thread cap. It exists so Dream cannot enqueue 200 Remember conducts on top of live Recall.

| Bulkhead | maxInFlight | wait | onReject |
|---|---|---|---|
| `pathway:remember` nested calls | 4 | 50ms | BYPASS for Dream, FAIL for user-facing ingest (user ingest does not use this bulkhead) |
| `llm-provider` | 2 | 0 | BYPASS |
| `embed-provider` | 32 | 20ms | FAIL |
| Recall itself | none | — | — |

User-facing `memory.remember(...)` does **not** go through the Dream bulkhead. Nested calls from `PathwayRelay` pass `BulkheadRef.of("pathway:remember")` only on that relay.

Shared registry, same pattern as breakers: `BulkheadRegistry` on process context.

---

## 11. Divergent branches

Existing behavior preserved, plus:

1. Branch exceptions classified with `Faults.kindOf`.
2. `FAIL_FAST` branch still fails `forkJoinAll` and fails the divergent relay.
3. `DEGRADE_GRACEFULLY` branch records a degraded mark with scope `divergentName/branchRelayName` and does not join that fork (today it already omits failed forks from `successfulForks`).
4. A breaker wrapping the *divergent relay as a whole* counts one failure if the divergent relay throws (i.e. any FAIL_FAST branch failed). Per-branch breakers wrap the branch relays themselves.
5. Timeouts on a branch apply to that branch only. A timed-out `FAIL_FAST` branch fails the divergent relay; a timed-out `DEGRADE` branch degrades.
6. **`ABORT` is not a valid branch policy.** `DivergentRelay.transmit` always returns `true` (L109) and `CognitivePathway.Builder.divergent(...)` hard-codes the outer entry to `FAIL_FAST` (L219), so a branch cannot short-circuit the enclosing pathway. `PathwayComposer.divergent(...)` rejects `ABORT` in `branchPolicies` at build time rather than silently ignoring it.
7. **`PathwayRelay` is not permitted in a branch.** `ConductionScope` is thread-confined (ADR-0035 §6.5.1) and branches run on separate virtual threads, so nested invocation would corrupt the frame stack. Rejected at build time.
8. `DivergentRelay` currently uses the no-timeout `ConcurrentTasks.forkJoinAll` (L100), so one hung branch hangs the pathway. Migrating it to `forkJoinPartial(List<LabeledTask<T>>, Duration)` (L543) would give per-branch deadlines and labelled failures for free — the labels map directly onto outcome mark scopes. Optional, sequenced after R5.

Do not add a circuit breaker *inside* `DivergentRelay`. Compose it outside or on each branch.

---

## 12. Consolidation (async) errors

`ConsolidationRelay` today fires and forgets. Specify:

```java
ConcurrentTasks.fireAndForget(() -> {
    try {
        asyncAction.accept(signal);
    } catch (Exception e) {
        log.warn("consolidation '{}' failed: {}", name, e.toString());
        // do NOT mutate the signal — the pathway has moved on
        ObservationHooks.recordConsolidationFailure(name, Faults.kindOf(e));
    }
});
```

Rules:

- Consolidation failures never fail the parent conduction.
- They never trip `pathway:*` breakers.
- They *may* trip a named downstream breaker if the async action uses a decorated client (embed, LLM) that has its own breaker. That breaker lives in the client, not in `ConsolidationRelay`.
- No retry at the consolidation wrapper. The action can retry internally if idempotent.

> **Implementation note (delivered).** `ConsolidationRelay` initially shipped without this body, relying on `ConcurrentTasks.fireAndForget`'s generic catch — which swallowed correctly but logged without the relay name and emitted no metric. The specified `try`/`catch` is now in place. The hook is `PathwayObservationHook.onConsolidationFailure(relay, kind)` (a `default` no-op, resolved from the signal's context) rather than the sketch's `ObservationHooks.recordConsolidationFailure`, matching the `on*` naming the rest of the hook interface uses.

---

## 13. Trace status

`RelayTrace.TraceStatus` today: `EXECUTED`, `BYPASSED`, `SHORT_CIRCUITED`, `DEGRADED`, `FAILED`.

Keep all five. Assignment:

| Event | Status | detail |
|---|---|---|
| transmit returned true | `EXECUTED` | null |
| transmit returned false | `SHORT_CIRCUITED` | null |
| gate closed | `BYPASSED` | specification reason |
| breaker OPEN + BYPASS | `BYPASSED` | `circuit_open:<name>` |
| bulkhead reject + BYPASS | `BYPASSED` | `bulkhead:<name>` |
| exception + DEGRADE | `DEGRADED` | `kind + message` |
| exception + FAIL_FAST | `FAILED` | `kind + message` |
| exception + ABORT | `SHORT_CIRCUITED` | `aborted:kind` |
| timeout | `FAILED` or `DEGRADED` per policy | `timeout:<budget>` |

Gated-off already logs the specification reason at DEBUG (`GatedRelay` L58–61, only when DEBUG is enabled and the gate is a `Specification`). Also put it on the trace when tracing is on (`detail = spec.unsatisfiedReason(signal)`).

Today gated-off is invisible in traces: `GatedRelay` returns `true` without the conductor knowing it skipped, so the conductor records `EXECUTED` with a null detail. **`TraceStatus.BYPASSED` is currently produced nowhere in main source** — a repo-wide grep finds zero producers. Both the gated-off case and the OPEN-circuit bypass masquerade as `EXECUTED`.

Fix: `GatedRelay` records a `BYPASSED` trace itself when the signal is `TraceableSignal`, avoiding any change to `transmit`’s `boolean` contract.

> **Implementation note (delivered).** The relay-side variant above was superseded during implementation. The three self-bypassing relays — `GatedRelay`, `CircuitBreakerRelay`, `BulkheadRelay` — already record `outcome().markBypassed(scope, reason)`, and having each *also* record a trace would double-record: the conductor writes `EXECUTED` for the same relay as soon as `transmit` returns `true`. So the trace is derived in `CognitivePathway.conduct` instead. It snapshots `outcome().bypassedMarks().size()` before each `transmit` and, when the relay returns `true` but appended a mark scoped to that relay, traces `BYPASSED` with the mark's reason instead of `EXECUTED`. One trace per relay, `transmit`'s contract untouched, and any future self-bypassing relay is covered without further conductor changes. Scope matching accepts both a bare relay name and `pathway/relay`.
>
> `timeout:<budget>` is produced by `TimeoutRelay` wrapping the budget into the cause chain; the conductor's `detailOf` walks that chain so the timeout rows report the budget rather than a generic message.
>
> `BulkheadRelay` originally recorded a bare relay name with reason `bulkhead_full:`; it now follows the `pathway/relay` + `bulkhead:` convention this table specifies.
>
> Cover: `BypassedTraceTest`.

**This is inert for six of seven pathways until ADR-0035 M6.** Only `RecallSignal` implements `TraceableSignal` today, so `GatedRelay`’s new trace — and this whole table — apply to Recall alone until the six remaining signals extend `AbstractSignal`. `ConductionOutcome` (§5) lives on the context precisely so degraded/bypassed reporting works for all seven pathways *before* that migration lands.

---

## 14. Recommended policy per existing stage

This is the default recipe table. Feature-flagged AISME relays follow the AISME row.

### Remember

| Relay | Policy | Timeout | Retry | Breaker | Notes |
|---|---|---|---|---|---|
| Dedup guard | FAIL_FAST | — | — | — | Contract. |
| Tag transduction | FAIL_FAST | — | — | — | |
| Dopaminergic surprise | FAIL_FAST | — | — | — | In-process. |
| Cortical write | FAIL_FAST | **none** | no | — | Not idempotent **and not interruptible** (mmap + WAL). Never wrap — §7.2/§7.3. |
| Graph linking | DEGRADE | — | — | — | Already degrade. |
| KG enrichment | DEGRADE | 8s (LLM extract) | **none** — see below | `llm-provider` + bulkhead(2) | Optional path. |

The cortical-write row is corrected from an earlier draft that assigned it 200ms. See §7.3.

**Amendment (implementation) — KG enrichment is not retried.** This table originally specified `TRANSIENT ×2`. Implementation found that `KnowledgeGraphEnrichmentRelay` does more than call the LLM: it also runs `postIngestSync.syncEntityExtraction(...)` and `syncTemporalFacts(...)`, both of which mutate the entity and temporal graphs. Re-running after a partial failure can duplicate edges, so the relay deliberately declares `InterruptibleRelay` (timeout is safe — the LLM call honours interrupts) but **not** `IdempotentRelay`, and the composer therefore rejects any `.retry(...)` on it at build time.

The general rule this exposes: **a stage is only retryable if the *whole* stage is idempotent, not just its slowest call.** Splitting the LLM extraction from the graph write would make the extraction retryable; that refactor is out of scope here.

### Recall

Listed in **actual factory order** (`RecallPathwayFactory` L217–260), not an idealised order. An earlier draft omitted `SPACETIME_SCORING` and `LATERAL_INHIBITION` and placed BM25/RRF after graph expansion; the real pathway puts them before it.

| # | `RelayNames` | Policy | Timeout | Retry | Breaker |
|---|---|---|---|---|---|
| 1 | `TRANSDUCTION` (embed) | FAIL_FAST if no vector supplied, else DEGRADE | 2s | TRANSIENT ×2 | `embed-provider` (FAIL) |
| 2 | `PROSPECTIVE` | DEGRADE | — | — | — |
| 3 | `GOVERNED_RELEASE_GATE` | FAIL_FAST | — | — | — |
| 4 | `VECTOR_SEARCH` (cortical tier scan) | FAIL_FAST | — | — | — |
| 5 | `HOMEOSTATIC_BIAS` (AISME, gated) | DEGRADE | — | — | — |
| 6 | `FREE_ENERGY_GUIDED` (AISME, gated) | DEGRADE | — | — | — |
| 7 | `SPACETIME_SCORING` (gated) | DEGRADE | — | — | — |
| 8 | `SCORING` | FAIL_FAST | — | — | — |
| 9 | `BM25_SEARCH` (gated) | DEGRADE | — | — | — |
| 10 | `RRF_RESCORE` (gated) | DEGRADE | — | — | — |
| 11 | `GRAPH_EXPANSION` | DEGRADE | — | — | — |
| 12 | `HOPFIELD_ASSOCIATIVE` (AISME, gated) | DEGRADE | — | — | — |
| 13 | `EVIDENCE_FUSION` | FAIL_FAST | — | — | — |
| 14 | `LATERAL_INHIBITION` (gated) | DEGRADE | — | — | — |
| — | `COGNITIVE_RERANK` (remote) | DEGRADE | 80ms | TRANSIENT ×1 | `rerank-remote` (BYPASS) |
| — | `MMR_DIVERSITY` / `TEMPERATURE_SOFTMAX` / `SORT_AND_TRUNCATE` | FAIL_FAST | — | — | — |
| — | `CONSCIOUS_ACCESS` (AISME, gated) | DEGRADE | — | — | — |
| — | `MANIFOLD_RERANK`, `CONSTRUCTIVE_SIMULATION`, `CONSCIOUSNESS_CONTINUITY`, `EPISTEMIC_LEARNING` (AISME, gated) | DEGRADE | — | — | — |
| — | Listeners (`ConsolidationRelay`) | DEGRADE (inherent) | — | — | — |

**Do not hand-maintain this table.** It drifted once already. `CognitivePathwayParityTest$RecipeRelayParity` (ADR-0035 §16) pins recipe-built relay names *and order* against factory output for Remember, Recall, and Reflect.

**Amendment (implementation):** the policy/decorator half of that suggestion is now `PathwayResilienceWiringTest` in `spector-memory`, which walks each stage's wrapper chain via the public `delegate()` accessors and asserts:

- `CORTICAL_WRITE` carries no `Timeout`/`Retry`/`Breaker`/`Bulkhead` at all
- `KG_ENRICHMENT` has `Bulkhead` + `Breaker` + `Timeout` but **not** `Retry`
- decorator nesting order is `Bulkhead` outside `Breaker` outside `Timeout` (§6)
- `TRANSDUCTION` keeps `FAIL_FAST` and carries all three of breaker/retry/timeout
- the hot-path stages (`VECTOR_SEARCH`, `SCORING`, `EVIDENCE_FUSION`, `GRAPH_EXPANSION`) carry **no** decorators — the structural form of ADR-0035's no-hot-path-regression constraint
- `embedProviderFailFast()` and `embedProviderBypass()` share one breaker *name* while differing in `onOpen`, which is the §9.1 property that justified moving `onOpen` off the shared config

That suite is mutation-checked: deleting a single `.breaker(...)` line from a recipe fails it.

### Reflect

Most companion relays stay `DEGRADE_GRACEFULLY` as today. `SYNAPTIC_PRUNING` and `WAL_JOURNAL` stay `FAIL_FAST`. Nested Remember (gist ingest) is `DEGRADE` + breaker `pathway:remember` + bulkhead, **no timeout** (§7.3).

Companion-relay skipping moves from `ReflectPathwayFactory`'s lambda wrapper to a `Specification` gate (ADR-0035 §7.4), which means a skipped companion relay becomes a `BYPASSED` trace with `unsatisfiedReason` as detail instead of being invisible.

### Dream

| Relay | Policy | Notes |
|---|---|---|
| Dream gate | ABORT | Not dreaming is not a failure. Today it degrades and continues through empty stages; ABORT saves the work. Behavior change: subsequent relays do not run. Report is empty-success, `Finish.SHORT_CIRCUITED`. |
| Salient seed … Langevin | DEGRADE | |
| Dream ingest (`PathwayRelay` → Remember) | DEGRADE | **no timeout** (§7.3), breaker `pathway:remember`, bulkhead 4 |
| Journal | DEGRADE | |

### Decide / Wander / Express

All current stages are `DEGRADE_GRACEFULLY`. Keep that. Decide with no candidates is a gate, not a fault. Wander idle-gate stays a gate (`return true` without work), not `ABORT`, because later continuity tracking may still run — confirm against `WanderGates.IS_IDLE` placement (it is the first stage; ABORT vs gate-skip is equivalent today). Prefer leaving Wander as-is to avoid behavior change.

---

## 15. Observation / metrics

Use the existing interceptor hook (`withInterceptor`) and `MemoryObservationHook`. Required meters (names indicative):

| Meter | Labels | When |
|---|---|---|
| `spector.pathway.conduct` timer | `pathway`, `finish` | end of `AbstractPathway.conduct` |
| `spector.pathway.relay` timer | `pathway`, `relay`, `status` | interceptor |
| `spector.pathway.degraded` counter | `pathway`, `relay`, `kind` | DEGRADE path |
| `spector.pathway.circuit` counter | `breaker`, `event=trip\|probe\|close\|reject` | `CircuitBreaker` state change |
| `spector.pathway.bulkhead.reject` counter | `bulkhead` | reject |
| `spector.pathway.timeout` counter | `pathway`, `relay` | timeout |
| `spector.pathway.retry` counter | `pathway`, `relay` | each extra attempt |
| `spector.pathway.nested` timer | `from`, `to` | `PathwayRelay` |

State-change log at INFO for trip/close, DEBUG for probe.

---

## 16. Implementation details

### 16.1 Files to add (commons)

```
FaultKind.java
Faults.java
CircuitOpenException.java          // extends CognitivePathwayException
CircuitBreaker.java
CircuitBreakerConfig.java
CircuitBreakerRegistry.java
BreakerRef.java
BulkheadConfig.java
BulkheadRegistry.java
BulkheadRelay.java
TimeoutRelay.java
RetryPolicy.java
RetryRelay.java
IdempotentRelay.java
InterruptibleRelay.java            // gate for .timeout() — §7.2
OnOpen.java
ConductionOutcome.java
```

`BreakerRef` carries `(name, config, onOpen)` — `onOpen` is deliberately **not** on `CircuitBreakerConfig` (§9.1).

`CircuitBreakerRelay` rewritten as a façade over `CircuitBreaker` (same public constructors).

`ErrorPolicy` gains `ABORT`.

`CognitivePathwayException` gains `kind`, `nested`.

`CognitivePathway.Builder` gains `stage()` fluent if we want; otherwise only `PathwayComposer` exposes the full decorator DSL. Minimum: composer only, so the conductor builder stays stable.

### 16.2 Files to change (commons)

- `CognitivePathway.conduct` — ABORT branch; record outcome if signal is `ContextualSignal`.
- `GatedRelay` — write `BYPASSED` trace when traceable.
- `DivergentRelay` — classify, mark outcome on degrade. **Delivered:** degrade now calls `Faults.kindOf` and records `markDegraded("<relay>/<branch>", kind, e)` on the parent outcome; previously it only logged a warning.
- `ConsolidationRelay` — swallow + metric, specified in §12.
- `CognitivePathwayTest` — new cases: ABORT, timeout, retry idempotency rejection at build, shared breaker across two pathways, nested degrade, cycle already in ADR-0035 tests.

### 16.3 Files to change (memory)

- Recipes gain decorator lines for embed / LLM / nested Remember only. Do not decorate every relay.
- `AbstractPathway` notifies `pathway:{name}` breaker on thrown conduct.
- Domain `project` methods attach `ConductionOutcome` onto reports.
- Dream gate policy flip to `ABORT` is an explicit behavior change — call it out in the PR, update Dream tests that expected later relays to run after a closed gate.

### 16.4 Concurrency notes

- `CircuitBreaker` state uses existing atomics. No lock on the hot path.
- HALF_OPEN probe cap uses `AtomicInteger` — undercounting probes by one under extreme race is acceptable; over-admitting probes is not. Use `getAndIncrement` and roll back if over cap.
- `BulkheadRelay` uses `Semaphore` (fair = false).
- Registries are process-wide, thread-safe, create-if-absent.
- Breaker state is **not** per-namespace. A dying embed provider is dying for every tenant. If a future multi-tenant remote has per-tenant quotas, use `BreakerRef.of("embed-provider:" + namespaceId)` at bind time — do not change the default.

### 16.5 What `ConcurrentTasks` must provide

```java
public final class ConcurrentTasks {
    // ── existing (verified line numbers) ──
    static <T> List<T> forkJoinAll(List<Callable<T>> tasks);              // L293 — no timeout
    static <A,B> Pair<A,B> forkJoin2(Callable<A>, Callable<B>);           // L334 — no timeout
    static void forkRunAll(List<Runnable> tasks);                         // L353
    static void fireAndForget(Runnable r);                               // L124 (+3 overloads)
    static void fireAndForgetAll(List<Runnable> tasks);                   // L259

    // ── ALREADY deadline-aware: reuse, do not reinvent ──
    static <T> PartialResult<T> forkJoinPartial(
            List<LabeledTask<T>> tasks, Duration timeout);                // L543

    // ── new: single-task specialisation EXTRACTED from forkJoinPartial ──
    static <T> T callWithTimeout(Callable<T> task, Duration budget) throws Exception;
}
```

An earlier draft said to add `callWithTimeout` "if it does not exist" without noticing that `forkJoinPartial` (L543) already implements deadline-based execution with cancellation, on both the structured (`Joiner.awaitAll()` + `Configuration.withTimeout`) and classic (`get(remaining, MILLISECONDS)` + `cancel(true)`) paths, and already classifies results as success / failure / timed-out.

`callWithTimeout` must therefore be **implemented on the same machinery**, not written independently: start a virtual thread, await the budget, `cancel(true)` on expiry, throw `TimeoutException`, preserve the task's own exception when it completes in time. Two independent timeout implementations will drift in cancellation semantics, and the structured/classic dual path is precisely the subtlety not to duplicate.

Also required: `Faults.kindOf` must unwrap `ConcurrentExecutionException`, which is a **checked** `Exception` outside the `SpectorException` tree. `DivergentRelay` L100–106 already unwraps it by hand; without the same handling in `Faults`, every divergent branch failure would classify as `INTERNAL` and never trip a breaker.

---

## 17. Testing matrix

| Case | Expected |
|---|---|
| FAIL_FAST + `SpectorValidationException` | rethrown, breaker untouched |
| FAIL_FAST + `IOException` | wrapped `CognitivePathwayException` kind=TRANSIENT, breaker +1 |
| DEGRADE + `IOException` | conduct returns, outcome.degraded, breaker +1 |
| ABORT + any | conduct returns, finish=SHORT_CIRCUITED, breaker untouched |
| Timeout inside DEGRADE | degraded mark `timeout:*`, kind=TRANSIENT |
| Retry 3 attempts then success | breaker +0, retry counter +2 |
| Retry on CorticalWrite at compose time | builder throws (`IdempotentRelay` gate) |
| **Timeout on CorticalWrite at compose time** | **builder throws (not interruptible, §7.2)** |
| **Timeout on nested Remember `PathwayRelay`** | **builder throws — admission control only, §7.3** |
| `INTERRUPTED` + DEGRADE stage | conduct throws anyway, interrupt flag restored, breaker untouched |
| `ABORT` as a divergent branch policy | `PathwayComposer.divergent(...)` throws at build time (§11.6) |
| `PathwayRelay` as a divergent branch | builder throws at build time (§11.7) |
| Shared `embed-provider`, two `onOpen` | Recall site throws `CircuitOpenException`, Dream site records `BYPASSED`, from one trip event |
| Registry `get` with mismatched config | returns first-registered breaker, logs WARN, does not throw |
| `ConcurrentExecutionException` wrapping `IOException` | classifies TRANSIENT, not INTERNAL |
| Breaker trips at 5 TRANSIENT | 6th call OPEN; BYPASS → `BYPASSED` trace; FAIL → `CircuitOpenException` |
| VALIDATION × 100 | breaker stays CLOSED |
| Shared `embed-provider` tripped by Dream | Recall embed bypasses/fails per its onOpen |
| HALF_OPEN single probe | second concurrent caller sees OPEN behavior |
| Nested Remember throws TRANSIENT, Dream ingest DEGRADE | Dream completes, no RememberResult absorbed, mark `dream_ingest` |
| Nested Remember graph-link DEGRADE internally | Dream completes, result absorbed, imported mark `dream_ingest/graph_linking` |
| Bulkhead exhausted, BYPASS | ingest skipped, Dream continues |
| Interrupt during conduct | interrupt flag set, conduction fails FAST |
| Consolidation throw | parent succeeds, metric increments |

---

## 18. Migration

| Step | After ADR-0035 step | Change |
|---|---|---|
| R1 | M1 | Add `FaultKind` (incl. `INTERRUPTED`), `Faults` (unwrapping `ConcurrentExecutionException`), `ConductionOutcome`, extend exception. Switch `CognitivePathwayException` to `ErrorCode.MEMORY_PATHWAY_FAILED`. No behavior change. |
| R2 | M2 | Conductor understands `ABORT` + `INTERRUPTED` override. No recipe uses `ABORT` yet. |
| R3 | M2 | Reimplement `CircuitBreakerRelay` on named `CircuitBreaker`; `onOpen` moves to `BreakerRef`. Legacy constructors pass `OnOpen.BYPASS` to preserve today's return-true behavior. |
| R4 | M5 | `PathwayRelay` nested matrix + outcome import + full permit lifecycle in the decorator (§9.3). |
| R5 | M6 | Composer decorator DSL, incl. build-time rejection of: `.retry()` on non-idempotent, `.timeout()` on non-interruptible, `ABORT`/`PathwayRelay` in divergent branches. Wire embed / LLM / nested Remember only. |
| R6 | M6 | Dream gate → `ABORT`. Isolated PR. |
| R6.5 | M6 | `GatedRelay` records `BYPASSED`. Only meaningful once all six signals extend `AbstractSignal` — sequence after ADR-0035 M6, not before. |
| R7 | after soak | Attach outcome onto public reports / MCP metadata. |
| R8 | optional | Migrate `DivergentRelay` from `forkJoinAll` to `forkJoinPartial` for per-branch deadlines (§11.8). |

Do not flip `OnOpen` defaults from BYPASS to FAIL on existing `circuitBreaker(...)` builder calls. New named breakers choose per §9.3.

---

## 19. Consequences

### Positive

- Nested pathway calls have a defined failure story.
- Breakers actually isolate sick downstreams *and* can be shared, which is the only way they help a process with seven pathways.
- Validation bugs stop looking like provider outages.
- Partial success is inspectable (`ConductionOutcome`) instead of being a log archaeology problem.
- Timeouts give Recall / Dream a budget instead of inheriting whatever the JDK HTTP client does.

### Negative / accepted

- `ABORT` is a behavior change for Dream gate if we take §14. Isolated behind R6.
- More types in commons. Accepted; resilience as “two enums and a wrapper” is what got us here.
- Shared breakers couple tenants. Accepted; embed providers are process-wide today.
- Retry can amplify load. Mitigated by breaker-outside-retry, bulkhead, and idempotency gate.

### Risks

| Risk | Mitigation |
|---|---|
| Timeout that interrupts a write mid-WAL | **Resolved by removing the timeout, not by tuning it** (§7.3). `MemorySegment`/WAL writes are not interruptible, so a budget would report a timeout while the write completed on a detached thread — torn state. Nested Remember and cortical write get bulkhead + breaker only. `PathwayComposer` rejects `.timeout()` on both at build time. |
| Breaker flap | `halfOpenSuccesses` default 1 is aggressive; raise to 2 for `llm-provider` if production flaps. |
| Outcome forgotten on a new report | Parity test: every `project` method passes `signal.context().outcome()`. |
| Resilience table drifts from the pathway | §14's Recall table already drifted once. `CognitivePathwayParityTest` asserts relay names, order, **and** policy/decorator assignment against the factory. |
| `ABORT` breaks a downstream exhaustive switch | No exhaustive `switch` over `ErrorPolicy` exists in this repo (verified across `memory`, `nucleus`, `synapse`). It is a public commons enum, so flag the addition in release notes for external consumers. |
| Decorators silently no-op before M6 | Outcome marks work immediately (context-based); trace statuses do not (`TraceableSignal`-gated). R6.5 is sequenced after ADR-0035 M6 for exactly this reason. |

---

## 20. Revisit when

- A downstream needs adaptive breakers (error-rate window instead of consecutive failures). Add a second `CircuitBreaker` implementation behind the same interface; do not change recipes.
- Multi-node Spector needs distributed breakers. Process-local is correct until there is a shared embed fleet with a sidecar.
- Conduct must return `CompletableFuture`. That is a different ADR; retries/timeouts here assume blocking virtual threads.

---

## 21. Decision summary

1. Stage disposition stays `ErrorPolicy` (+ `ABORT`).
2. Faults are classified (`FaultKind`) before any breaker or retry looks at them. `INTERRUPTED` is distinct from `CONTROL` and overrides stage policy.
3. Decorators compose bulkhead → timeout → retry → breaker → relay.
4. Nested calls: callee decides throw vs return; caller stage policy decides kill vs continue; outcomes merge by prefix. The `PathwayRelay` decorator owns the entire breaker permit lifecycle — `AbstractPathway` notifies nothing.
5. Circuit breakers are named, shareable, and kind-aware. **Trip state is shared; `onOpen` is per call site** (`BreakerRef`), because one downstream legitimately needs FAIL at one site and BYPASS at another. Registry is first-registration-wins.
6. Bulkheads isolate nested and remote work on virtual threads.
7. **Only interruptible relays may declare a timeout.** Writes and nested Remember get no budget at all — admission control only. Every budgeted stage in §7.3 is remote HTTP.
8. Async consolidation never fails the parent.
9. Reuse `ConcurrentTasks.forkJoinPartial` for deadlines; do not add a second timeout primitive.
10. Trace-status work is inert until all seven signals are `ContextualSignal`; outcome-based reporting is not, which is why outcome lives on the context.
