# Backend SQLite Production Migration Design

## Goal

Define a production migration path for `backend/solen-app` that:

- replaces embedded `HSQLDB` with `SQLite`
- moves only the `event` write path from JPA to JDBC
- moves event retention cleanup from JPA to JDBC
- keeps event query on the existing JPA path for now
- uses a production-owned SQLite dialect strategy that fits the current Spring Boot and Hibernate versions

## Current State

- `backend/solen-app` uses `Spring Boot 2.6.7`.
- Dependency management pins Hibernate to `5.6.8.Final`.
- The application datasource is currently `jdbc:hsqldb:file:data/solen-data`.
- Schema initialization currently runs from `classpath:schema.sql`.
- `EventProcessorImpl` writes events with `eventRepository.saveAll(list)`.
- `SolenServerStarter` initializes the event ID generator with `eventRepository.getMaxId()`.
- `EventController` reads event history through `EventRepository.findAll(spec, page)`.
- `EventCleaner` deletes retained history with `eventRepository.deleteByTimeLessThan(date)` inside a transaction template.
- Current operating load is about `3000` terminals at `1-2 event/minute/device`.
- Target operating load is about `10000` terminals at `1-2 event/minute/device`, which is roughly `167-333 event/s` on average.

## Migration Scope

### In Scope

- replace HSQLDB with SQLite in production configuration
- add a production SQLite-compatible schema
- move only event insertion from JPA to JDBC
- move event retention cleanup from JPA to JDBC
- keep device and connection persistence on JPA
- keep event reads on JPA
- add a production-owned SQLite dialect class compatible with Hibernate 5.6
- apply SQLite runtime settings needed for a write-heavy embedded workload

### Out Of Scope

- migrating existing HSQLDB data into SQLite
- upgrading Spring Boot or Hibernate in the same project
- moving event query or cleanup to JDBC in this phase
- redesigning the event REST API
- changing backend modules outside `backend/solen-app`

## Key Constraint: Community SQLite Dialect

The community SQLite dialect exists in `org.hibernate.orm:hibernate-community-dialects`, but it is a Hibernate 6 era implementation.

Facts:

- `hibernate-community-dialects:6.2.0.Final` contains `org.hibernate.community.dialect.SQLiteDialect`
- the implementation depends on Hibernate 6 APIs such as `DatabaseVersion`, `SqlAstTranslator`, `SqlTypes`, and `jakarta.persistence`
- the current application is on `Spring Boot 2.6.7` and `Hibernate 5.6.8.Final`
- there is no matching `hibernate-community-dialects` artifact line for Hibernate 5.6 that can be safely dropped into the current stack

Conclusion:

- the community dialect can be used as a semantic reference
- it cannot be used directly in the current production stack without a larger framework upgrade

## Approaches Considered

### Approach 1: Minimal Production Dialect On Current Stack

Description:

- keep Spring Boot `2.6.7` and Hibernate `5.6.8.Final`
- add a production-owned `SQLiteDialect` implementation for Hibernate 5.6
- switch datasource and schema to SQLite
- move event inserts to JDBC only

Pros:

- lowest migration risk
- targets the measured bottlenecks directly
- keeps the formal change set focused on database replacement plus event write optimization
- avoids `javax` to `jakarta` migration costs

Cons:

- the project owns a small custom dialect implementation
- some Hibernate 5 plus SQLite edge cases remain the application's responsibility

### Approach 2: Full Framework Upgrade First, Then Use Community Dialect

Description:

- upgrade to Spring Boot 3.x and Hibernate 6.x first
- adopt community `SQLiteDialect`
- then migrate from HSQLDB to SQLite and change event writes to JDBC

Pros:

- avoids owning a custom dialect long term
- moves the project closer to current upstream support

Cons:

- much larger project scope
- forces `javax.*` to `jakarta.*` migration across the application
- creates risk across security, validation, persistence, and framework integration that is unrelated to the database switch itself

### Approach 3: Mixed Compatibility Attempt

Description:

- try to bring in community dialect artifacts or partially port Hibernate 6 code while staying on Boot 2.6

Pros:

- appears to reduce custom maintenance on paper

Cons:

- highest technical risk
- mixes incompatible major-version assumptions
- likely results in partial custom code anyway, but with worse complexity than a clean Hibernate 5 dialect

## Recommendation

Use Approach 1.

Reasoning:

- the performance evaluation already showed that the main gains come from switching to SQLite and moving event writes off JPA
- those gains do not depend on a Spring Boot or Hibernate major upgrade
- the community dialect is useful as a reference, but not as a drop-in dependency for the current stack
- a small production Hibernate 5 dialect is lower risk than bundling a platform upgrade into this persistence migration

## Target Design

### Architecture

- `device` and `connection` stay on Spring Data JPA
- `event` reads stay on Spring Data JPA
- `event` retention cleanup moves to a dedicated JDBC cleaner component
- `event` inserts move to a dedicated JDBC writer component
- all paths share one SQLite datasource
- schema stays controlled by SQL scripts, not Hibernate DDL generation

### Production SQLite Dialect Strategy

Add a production dialect class under application code, not test code.

Suggested location:

- `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialect.java`

Design principles:

- implement only the Hibernate 5.6 features that the current application actually needs
- keep the class intentionally small and explicit
- use the community Hibernate 6 dialect as semantic reference only, not as copied implementation structure

Required capabilities for this migration phase:

- SQLite-friendly column type registration for current entity mappings
- `supportsLimit()` and `getLimitString(...)`
- identity column support using `last_insert_rowid()` semantics where required
- current timestamp select support
- conservative `alter table` and constraint declarations aligned with SQLite limitations

Not required in this phase:

- advanced function registration
- SQL AST translator customization from Hibernate 6
- automatic DDL generation support beyond what the application already avoids
- broad compatibility coverage for unsupported JPQL edge cases outside current repository usage

### Datasource And Runtime Configuration

Replace HSQLDB datasource configuration with SQLite.

Recommended SQLite settings to validate during implementation:

- WAL journal mode
- `busy_timeout`
- explicit transaction boundaries for event write batches
- `synchronous=NORMAL` unless stricter durability is required by product expectations

These settings should be applied through controlled datasource initialization, not left implicit.

Reliability assumptions for this migration phase:

- normal runtime behavior should not lose events
- process crash or restart may lose events that have only reached an in-memory buffer and were not flushed yet
- if a later asynchronous buffer is introduced, backpressure must be preferred over dropping events during normal operation

### Schema Strategy

- add a SQLite production schema file
- make SQLite schema the active initialization script when SQLite is configured
- keep schema explicit and SQLite-native
- maintain indexes needed by current read and cleanup paths

Event table requirements:

- preserve `event_id` as the primary ordering key
- preserve `time` index for cleanup
- preserve `device_id` and `type` filtering support
- prefer `(device_id, event_id desc)` for recent event paging when device-scoped history is common

### Event Write Path Design

Current state:

- `EventProcessorImpl.processEventsInternal(...)` builds `List<EventDomain>` and persists with `eventRepository.saveAll(list)`

Target state:

- keep event-to-domain translation in the service layer
- replace `eventRepository.saveAll(list)` with a dedicated JDBC batch writer such as `EventJdbcWriter`
- the writer inserts directly into `event`
- write batching is explicit and transaction-scoped
- first implementation uses synchronous JDBC writes
- if throughput verification fails, the same writer boundary can later be fronted by a bounded in-memory queue with asynchronous batch flush

Responsibilities:

- `EventProcessorImpl`
  - keep domain conversion logic
  - keep device and connection updates on JPA
  - delegate event inserts to the JDBC writer
- `EventJdbcWriter`
  - own the SQL insert statement
  - batch insert translated event rows
  - handle `details` serialization consistently with existing JPA mapping semantics

If a later asynchronous mode is needed:

- use a bounded in-memory queue
- use blocking/backpressure when the queue is full
- do not drop events during normal runtime
- accept loss of unflushed buffered events on process failure or restart

### Read And Cleanup Path Design

Keep these unchanged in this phase:

- `EventRepository.getMaxId()`
- `EventRepository.findAll(spec, page)`

Rationale:

- the measured hotspot is event ingestion, not read-path correctness
- changing only the write path limits migration risk
- keeping reads on JPA preserves existing behavior while the new database engine is introduced

Cleanup target state:

- replace `eventRepository.deleteByTimeLessThan(...)` in `EventCleaner` with a dedicated JDBC cleaner such as `EventJdbcCleaner`
- keep the current cleanup strategy initially: periodic delete up to the retention cutoff in one logical operation
- defer batched or chunked cleanup redesign unless verification shows that delete contention is a practical problem

## Risks And Mitigations

### Risk 1: SQLite Write Locking Under Burst Traffic

Why it matters:

- SQLite allows concurrent readers, but writes still serialize at the database level

Mitigation:

- use WAL mode
- use bounded batch sizes
- keep write transactions short
- configure `busy_timeout`
- verify ingestion under representative concurrent device traffic
- verify at average-rate checkpoints aligned to the target deployment: `167 event/s`, `250 event/s`, `333 event/s`, and a protective over-target point such as `500 event/s`

### Risk 2: JPA Read Path Behaves Differently On SQLite

Why it matters:

- weak typing and SQL dialect differences can surface in pageable queries and date comparisons

Mitigation:

- keep a production-owned dialect
- verify `getMaxId`, pageable list queries, and retention delete explicitly against SQLite
- keep schema column types aligned with current entity mappings and converter behavior

### Risk 3: Inconsistent Event Serialization Between JDBC And JPA

Why it matters:

- event writes will use JDBC while event reads still use JPA converters

Mitigation:

- standardize event `details` serialization format
- reuse existing converter semantics rather than inventing a new write-only representation
- add focused correctness tests for round-trip event read behavior after JDBC insert

### Risk 4: Runtime Configuration Drift

Why it matters:

- SQLite behavior changes materially with pragma and connection settings

Mitigation:

- define required settings in application configuration or datasource initialization code
- document the chosen settings and why they were selected
- verify them during startup tests where practical

### Risk 5: Custom Dialect Becomes Unbounded

Why it matters:

- a small compatibility shim can grow into a hidden framework project if not bounded

Mitigation:

- explicitly limit the dialect to current application needs
- treat the community Hibernate 6 dialect as reference, not a feature checklist
- defer Boot/Hibernate major upgrade to a separate project

### Risk 6: Cleanup Contention Blocks Ingestion

Why it matters:

- SQLite serializes writes, so retention delete can interfere with ongoing event inserts more directly than in the current setup

Mitigation:

- move cleanup to JDBC so delete behavior is explicit and measurable
- keep cleanup on the same controlled datasource settings as writes
- run write-plus-cleanup concurrency tests at target average throughput
- keep chunked cleanup as a fallback optimization, but do not add it before the tests show need

## Verification Strategy

### Functional Verification

- application starts on SQLite
- device and connection persistence still work through JPA
- event writes succeed through JDBC
- event history queries still return correct results through JPA
- retention cleanup still deletes the expected rows
- startup event ID initialization still works

### Performance Verification

- re-run the existing embedded database comparison harness
- verify that the production-target shape aligns with the measured expectation: SQLite faster than HSQLDB, JDBC event writes faster than JPA event writes
- add a targeted production-path verification for the new event JDBC writer if needed
- add average-rate production-shape verification around `167-333 event/s`
- start with a simple uniform-arrival model
- if synchronous JDBC does not meet the target, use that failure data to justify a second-phase asynchronous queue design

### Regression Focus

- mixed JPA read plus JDBC write correctness on the same table
- timestamp comparisons on SQLite
- paging order by `eventId desc`
- JDBC retention delete behavior and write contention during cleanup windows
- queue-free synchronous behavior first, before evaluating any asynchronous fallback

## Rollout Plan

1. Add a production SQLite dialect class for Hibernate 5.6.
2. Add SQLite production schema and datasource configuration.
3. Switch application datasource from HSQLDB to SQLite.
4. Introduce a JDBC event writer component.
5. Replace `eventRepository.saveAll(list)` in `EventProcessorImpl` with the JDBC writer.
6. Introduce a JDBC event cleaner component and replace JPA-based retention delete in `EventCleaner`.
7. Keep `getMaxId` and event list queries on JPA.
8. Run correctness and performance verification against SQLite, starting with synchronous JDBC under uniform average load.
9. If synchronous JDBC misses the target, design the bounded asynchronous queue as a second phase.
10. Record runtime settings and migration outcome in repository docs.

## Deferred Work

- automatic migration from HSQLDB to SQLite
- moving event read queries to JDBC if later profiling justifies it
- chunked cleanup redesign if direct JDBC delete proves too disruptive under load
- framework upgrade to Spring Boot 3 and Hibernate 6 to adopt community dialects directly

## Decision Summary

- Formal migration target: `SQLite`
- Formal hot-path change: event writes and event cleanup move to `JDBC`
- Dialect strategy: production-owned minimal Hibernate 5 dialect
- Community dialect usage: reference only in this phase
- Framework upgrade: explicitly deferred to a separate future effort
- First verification mode: synchronous JDBC under simple uniform load
- Fallback if target is missed: bounded asynchronous buffer with blocking backpressure

## Implementation Outcome

- Result document: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md`
- Production dialect: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialect.java`
- Event writer: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriter.java`
- Event cleaner: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleaner.java`
- Final verification command: `cd backend && ./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest,top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest,top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest,top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest,top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
