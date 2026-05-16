# Backend Embedded DB And Production SQLite Migration Results

## Goal

Preserve the benchmark output and the production migration verification results for the SQLite transition in `backend/solen-app`.

## Production Migration Scope

- Database: `HSQLDB -> SQLite`
- Event write path: `JDBC`
- Event cleanup path: `JDBC`
- Event query path: `JPA`

## Verification Commands

```bash
cd backend
./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test
./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest,top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest,top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest,top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test
./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest,top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest,top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest,top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest,top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test
```

## Latest Results

- `HSQLDB_JPA`: `HSQLDB_JPA write-batch=548118561ns 1824ops/s startup-max-id=1160101ns 861ops/s page-recent=23463233ns 4261ops/s cleanup-retention=1242729049ns 13679ops/s`
- `SQLITE_JPA`: `SQLITE_JPA write-batch=111417696ns 8975ops/s startup-max-id=693895ns 1441ops/s page-recent=5090291ns 19645ops/s cleanup-retention=707029572ns 24044ops/s`
- `SQLITE_JDBC`: `SQLITE_JDBC write-batch=10546871ns 94814ops/s startup-max-id=74503ns 13422ops/s page-recent=133673ns 748094ops/s cleanup-retention=181165713ns 93836ops/s`
- `SQLITE_PROD_SYNC`:
  - `SQLITE_PROD_SYNC target=167 duration=30 inserted=5010 elapsedNs=29994665368`
  - `SQLITE_PROD_SYNC target=250 duration=30 inserted=7500 elapsedNs=29996288473`
  - `SQLITE_PROD_SYNC target=333 duration=30 inserted=9990 elapsedNs=41725487994`
  - `SQLITE_PROD_SYNC target=500 duration=30 inserted=15000 elapsedNs=87901150127`
- Decision: synchronous JDBC on SQLite is sufficient for the current `167-333 event/s` production target, so a bounded asynchronous queue is not required in this phase and can remain a fallback only if future load exceeds the verified band.

## Notes

- Final combined verification command completed with `BUILD SUCCESS` on `2026-05-16T14:18:56+08:00`.
- The `500 event/s` point persisted all submitted rows but exceeded the nominal `30s` window, so it should be treated as an over-target stress datapoint rather than part of the accepted production band.
