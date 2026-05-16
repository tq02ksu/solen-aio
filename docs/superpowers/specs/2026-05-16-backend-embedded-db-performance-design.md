# Backend Embedded DB Performance Design

## Goal

Analyze the current backend persistence model, evaluate embedded database options for a write-heavy IoT workload, and define a low-resource performance optimization path for `backend/solen-app`.

## Current State

- The main backend application is `backend/solen-app`.
- Persistence currently uses Spring Data JPA.
- The configured embedded database is file-based `HSQLDB` via `jdbc:hsqldb:file:data/solen-data`.
- Core persistent entities are `device`, `connection`, and `event`.
- `event` is the main write hotspot:
  - startup reads `max(eventId)` to initialize the event ID generator
  - runtime writes event batches through `eventRepository.saveAll(...)`
  - the API queries event history with pageable filtered reads
  - scheduled cleanup deletes old events by time retention
- Existing event indexes are on `device_id`, `type`, and `time`.
- Event list queries sort by `eventId DESC` and may filter by `startTime`, `endTime`, `deviceId`, `startId`, and `type`.

## Problem Statement

The backend needs better performance for a write-heavy IoT deployment while keeping memory and operational overhead low. The current `HSQLDB + JPA` combination is functional, but it is not the best fit for sustained event-heavy writes because:

- JPA adds ORM overhead on the hottest write path.
- The database choice is not optimized for a compact single-node append-heavy workload.
- Event retention cleanup can become an expensive background operation as the event table grows.

## Goals

- Improve sustained event write throughput.
- Reduce resource usage for the embedded persistence layer.
- Keep the overall application structure stable.
- Preserve the current REST-level behavior for event queries.
- Keep the change within a medium refactor scope.
- Compare candidate approaches with repeatable automated tests instead of relying only on static design judgment.
- Keep the evaluation artifacts so the same comparison can be re-run later after code or configuration changes.
- Compare both the existing JPA path and a narrower JDBC path where that helps isolate ORM overhead from database-engine behavior.

## Non-Goals

- Rewriting the whole backend persistence model.
- Moving to an external database service.
- Reworking protocol/server modules unrelated to persistence.
- Redesigning the event API contract.

## Workload Characteristics

- IoT events are append-heavy and continuous.
- `device` and `connection` represent lower-volume metadata and current state.
- `event` is high-churn historical data with retention-based deletion.
- Query patterns are bounded and known ahead of time, rather than arbitrary analytics.

## Options Considered

### Option 1: Keep JPA and replace HSQLDB with SQLite

Description:

- Switch the embedded database from HSQLDB to SQLite.
- Keep `device`, `connection`, and `event` on JPA initially.
- Tune schema and indexes around the existing access patterns.

Pros:

- Smallest architectural change.
- Lower operational and memory overhead than the current setup.
- Keeps repository and service structure mostly intact.
- Fastest path to measure whether the database switch alone gives enough benefit.

Cons:

- Event write throughput is still constrained by ORM overhead.
- Cleanup and query behavior still depend on the JPA path for the hottest table.
- May not be enough if event volume grows sharply.

### Option 2: SQLite for all data, still evaluated through JPA

Description:

- Switch the embedded database to SQLite.
- Keep `device`, `connection`, and `event` on JPA for the evaluation phase.
- Measure whether the current repository-based event path remains acceptable after the database switch.

Pros:

- Gives a direct answer to the immediate question: whether changing only the embedded database is enough.
- Preserves the real application access path during evaluation.
- Keeps the comparison closer to production behavior than a hand-written SQL harness.

Cons:

- If JPA remains the bottleneck, the evaluation only identifies the problem and does not solve it by itself.

### Option 3: Dedicated embedded KV/LSM engine for events

Description:

- Keep metadata on JPA.
- Move events to a store such as RocksDB.

Pros:

- Highest write-oriented headroom.
- Good fit for append-heavy event ingestion.

Cons:

- Query model becomes more complex.
- Requires manual secondary indexing and retention handling.
- Larger refactor than needed for the current scope.

### Option 4: SQLite event-path evaluation through JDBC

Description:

- Keep the application on its current JPA structure for the primary comparison.
- Add a SQLite-only JDBC benchmark path that exercises the same event-oriented workload with direct SQL.
- Use it to separate database-engine limits from JPA overhead.

Pros:

- Gives a useful lower-overhead comparison point for the same embedded database.
- Helps answer whether poor `SQLite + JPA` results are caused mainly by ORM overhead.
- Preserves the benchmark harness for later redesign work.

Cons:

- Adds a second persistence path in the benchmark code.
- The JDBC path is not the current production access path, so it should inform decisions rather than replace the JPA comparison.

## Recommendation

Use a phased design:

### Phase 1

- Replace HSQLDB with SQLite.
- Keep the current JPA model for all entities.
- Tighten schema, indexes, and SQLite runtime settings.

### Phase 2

- If `SQLite + JPA` does not meet the target write throughput, use the preserved benchmark harness as the baseline for a later storage-path redesign.

This phased recommendation is the best fit for the requested constraints:

- prioritizes write throughput and low resource usage
- avoids an unnecessary all-at-once persistence rewrite
- allows real measurement after a smaller first step
- preserves a clear upgrade path if the event table remains the bottleneck

The go/no-go decision between phases should be driven by automated performance evaluation in the test suite.

## Target Design

The target design keeps the change constrained to `backend/solen-app` while creating a phased path from a simple database replacement to a more specialized event persistence path only if measurement requires it.

### Architecture

- `backend/solen-app` remains the only backend module changed.
- SQLite becomes the embedded database for the application.
- `device`, `connection`, and `event` remain on the current JPA/repository model during evaluation.
- The evaluation code lives alongside the module as repeatable test code plus written result summaries.

### Data Model Direction

- Keep the logical entities unchanged: `device`, `connection`, `event`.
- Normalize SQLite DDL to match JPA mappings cleanly.
- Make `event_id` the dominant ordering column for ingestion and descending history reads.
- Treat the event table as the main performance boundary for future optimization.

### SQLite Runtime Strategy

Use SQLite as a single-node embedded store optimized for durable write-heavy operation.

Recommended runtime settings to validate in implementation:

- WAL journal mode for better writer/reader overlap.
- `synchronous=NORMAL` or stricter only if durability requirements demand it.
- Reasonable busy timeout to reduce transient lock failures.
- Explicit transaction batching for event writes.
- Periodic maintenance such as checkpointing and `VACUUM` only when measurement justifies it.

These settings must be validated against actual ingestion and retention workloads rather than enabled blindly.

### Schema And Indexing

### Device and connection

- Preserve the current table responsibilities.
- Recheck column definitions for SQLite-friendly types.
- Keep the `connection(device_id)` index.

### Event

Phase 1 index direction:

- Keep an index for `time` cleanup.
- Add or validate an index aligned to descending `eventId` paging.
- Keep indexes for `device_id` and `type`.
- If event list traffic frequently combines `deviceId` with paging, prefer a composite index such as `(device_id, event_id DESC)`.

Avoid speculative indexes beyond the known API query shapes.

### Application Changes

### Phase 1 changes

- Replace HSQLDB dependency and datasource configuration with SQLite.
- Update schema DDL to SQLite-compatible syntax.
- Keep `EventRepository`, `DeviceRepository`, and `ConnectionRepository` in place.
- Review Hibernate batch settings and flush behavior for event ingestion.
- Review the event cleanup query behavior under SQLite and ensure it does not create long blocking delete windows.
- Add repeatable JPA-based performance tests that exercise event ingestion, event paging, startup ID initialization, and retention cleanup for each candidate database.
- Save the measured outcomes in a project document so future re-evaluation can compare results against earlier runs.

### Phase 2 preparation in Phase 1

- Keep the evaluation harness variant-driven so later candidate storage paths can be added without replacing the original comparison.
- Preserve representative workload builders and result format so later runs remain comparable over time.
- Add a JDBC-only SQLite evaluation path in the harness so later tuning can distinguish ORM cost from database cost.

## Performance Evaluation Approach

The implementation should include automated test cases that compare the practical performance of candidate embedded databases under the same workload shape, with JPA as the main path and JDBC as a supplemental comparison path.

### Candidate variants to evaluate

- Baseline: current `HSQLDB + JPA`
- Candidate A: `SQLite + JPA`
- Candidate B: `SQLite + JDBC`

Future variants may be added later, but the first evaluation pass should stay focused on these three combinations.

### Test style

- Use JUnit-based automated tests in `backend/solen-app/src/test/java`.
- Use the real Spring Data JPA path rather than replacing repository behavior with hand-written SQL in the comparison itself.
- Add a separate SQLite JDBC path that uses the same dataset shape and reporting format as the JPA path.
- Treat them as repeatable performance-comparison tests rather than microbenchmarks.
- Keep the workload synthetic but representative of the current event flow.
- Use isolated on-disk test databases per run to capture realistic file-backed behavior.
- Keep the workload builder and output format stable so the same tests can be re-run later.

### Scenarios to measure

- Batch event ingestion throughput
- Recent-history event paging latency
- Retention cleanup latency for old event rows
- Startup latency to obtain the next event ID
- Relative process memory impact where the test environment can observe it reliably

### Decision rules

- Prefer `SQLite + JPA` if it shows meaningful resource reduction and adequate write throughput against the baseline.
- Use `SQLite + JDBC` as a diagnostic comparison point when `SQLite + JPA` underperforms, not as an automatic replacement decision by itself.
- If `SQLite + JPA` still fails the write-throughput target, keep the JPA evaluation harness as the baseline for the next design iteration instead of discarding it.
- Keep the comparison focused on the observed backend hotspots rather than broad synthetic benchmarks unrelated to `solen-app`.

## Performance Risks And Mitigations

### Risk: SQLite write locking limits throughput

Mitigation:

- use WAL
- batch writes inside explicit transactions
- measure write contention under realistic concurrent ingest

### Risk: JPA remains the dominant bottleneck after moving to SQLite

Mitigation:

- make Phase 2 part of the design from the beginning
- benchmark before and after the database swap
- move only the event path to native SQL if needed

### Risk: Large retention deletes create stalls

Mitigation:

- prefer batched deletes over one large delete in Phase 2
- measure delete latency and its impact on ingestion
- keep the cleanup cadence configurable

### Risk: Existing schema portability assumptions break on SQLite

Mitigation:

- validate DDL against SQLite syntax directly
- run startup schema initialization in verification
- verify JPA mappings against the generated/initialized schema

## Verification Strategy

Success should be measured, not inferred.

### Functional verification

- application starts with SQLite-backed persistence
- event ingestion still persists and reads correctly
- event list API preserves current filter behavior
- cleanup still deletes rows older than retention

These checks should remain separate from the performance-comparison tests so correctness failures are easy to distinguish from speed regressions.

### Performance verification

Measure at minimum:

- events written per second under representative batch size
- process memory footprint before and after the database switch
- event query latency for recent-history paging
- cleanup latency and write-path impact during retention runs
- startup time to initialize the next event ID

Collect these measurements through automated test cases so the candidate approaches can be compared with the same dataset shape and execution flow.
Persist the measured summaries in repository documentation so later runs can compare against the recorded baseline.

### Decision gate after Phase 1

If Phase 1 shows acceptable write throughput and resource usage, stop there.

If Phase 1 improves resource usage but event ingestion remains insufficient, use the preserved harness and documentation to define the next evaluation target.

## Rollout Plan

1. Build the automated performance-comparison test harness around the current hotspots.
2. Establish baseline measurements for `HSQLDB + JPA`.
3. Introduce SQLite dependency and configuration.
4. Convert schema initialization to SQLite-compatible DDL.
5. Re-run the same tests against `SQLite + JPA`.
6. Run the same workload against `SQLite + JDBC`.
7. Record the measured results in repository documentation.
8. Decide whether the Phase 1 result is sufficient or whether a later design iteration is needed.

## Scope Check

This design is intentionally limited to backend persistence in `solen-app` and focuses on the embedded database and event-heavy performance path. It does not expand into unrelated protocol, frontend, or deployment changes.
