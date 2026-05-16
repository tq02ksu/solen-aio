# Backend Embedded DB Performance Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build repeatable performance evaluation tests in `backend/solen-app` that compare `HSQLDB + JPA`, `SQLite + JPA`, and `SQLite + JDBC`, and preserve both the runnable code and the measured results in repository documentation for future re-evaluation.

**Architecture:** Add a dedicated evaluation test package under `backend/solen-app/src/test/java` with a shared workload builder and stable result format. Use the real Spring Data JPA path for `HSQLDB + JPA` and `SQLite + JPA`, add a SQLite-only JDBC path to isolate ORM overhead, then store the measured summaries in a repository markdown file so later runs can compare against the saved baseline.

**Tech Stack:** Java 8, Spring Boot 2.6, Spring Data JPA, JDBC, JUnit 5, HSQLDB, SQLite, Maven Surefire

---

## File Structure

- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbVariant.java`
  Responsibility: enumerate the embedded database variants under evaluation.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/PerfRunSummary.java`
  Responsibility: hold measured scenario results and render stable summary lines for documentation.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfProperties.java`
  Responsibility: compute per-variant Spring datasource/JPA properties for the test ApplicationContext.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfWorkload.java`
  Responsibility: build the representative device/event dataset and expose reusable workload sizes.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSupport.java`
  Responsibility: create isolated database paths, seed data through JPA, measure repository-based operations, and format results.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJdbcPerfSupport.java`
  Responsibility: reuse the same workload shape against SQLite through direct JDBC operations.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfHsqldbTest.java`
  Responsibility: run the workload against `HSQLDB + JPA` and emit a stable summary.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSqliteTest.java`
  Responsibility: run the same workload against `SQLite + JPA` and emit a stable summary.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJdbcPerfSqliteTest.java`
  Responsibility: run the workload against `SQLite + JDBC` and emit a stable summary.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfComparisonTest.java`
  Responsibility: compare the two JPA variants inside one repeatable verification-oriented test run.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbMixedPerfComparisonTest.java`
  Responsibility: compare all preserved summary lines across `HSQLDB + JPA`, `SQLite + JPA`, and `SQLite + JDBC` in one repeatable run.
- Create: `backend/solen-app/src/test/resources/perf/application-perf-base.properties`
  Responsibility: shared Spring test configuration for the performance harness.
- Create: `backend/solen-app/src/test/resources/perf/schema-sqlite.sql`
  Responsibility: SQLite-compatible schema used by the JPA evaluation tests.
- Create: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md`
  Responsibility: preserve the evaluation process, commands, environment notes, and measured results for future re-runs.
- Modify: `backend/solen-app/pom.xml`
  Responsibility: add the SQLite JDBC dependency needed by the JPA evaluation tests.
- Reference: `backend/solen-app/src/main/resources/schema.sql`
  Responsibility: source of truth for current table/index intent.
- Reference: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/domain/EventDomain.java`
  Responsibility: event entity shape under evaluation.
- Reference: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/repository/EventRepository.java`
  Responsibility: repository operations to measure, especially `saveAll`, `getMaxId`, and retention delete.

### Task 1: Add SQLite Test Dependency And Evaluation Resource Skeleton

**Files:**
- Modify: `backend/solen-app/pom.xml`
- Create: `backend/solen-app/src/test/resources/perf/application-perf-base.properties`
- Create: `backend/solen-app/src/test/resources/perf/schema-sqlite.sql`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/PerfResourceSmokeTest.java`

- [ ] **Step 1: Write the failing resource smoke test**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfResourceSmokeTest {
    @Test
    void loadsEvaluationResources() {
        assertTrue(new ClassPathResource("perf/application-perf-base.properties").exists());
        assertTrue(new ClassPathResource("perf/schema-sqlite.sql").exists());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.PerfResourceSmokeTest test`
Expected: FAIL because the test class and resource files do not exist yet.

- [ ] **Step 3: Add the SQLite JDBC dependency in `backend/solen-app/pom.xml`**

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Create the shared base properties file**

```properties
spring.jpa.hibernate.ddl-auto=none
spring.sql.init.mode=never
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.jdbc.batch_size=200
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

- [ ] **Step 5: Create the SQLite schema file**

```sql
create table if not exists device (
    device_id text not null primary key,
    status text not null,
    lac integer not null,
    ci integer not null,
    input_stat integer not null,
    output_stat integer not null,
    rssi integer not null,
    voltage real not null,
    temperature real not null,
    gravity integer not null,
    uptime integer not null,
    lat real not null,
    lng real not null
);

create table if not exists connection (
    connection_id text not null primary key,
    device_id text not null
);

create index if not exists idx_connection_device_id on connection (device_id);

create table if not exists event (
    event_id integer not null primary key,
    device_id text not null,
    type text not null,
    time text not null,
    details text not null
);

create index if not exists idx_event_device_id on event (device_id);
create index if not exists idx_event_type on event (type);
create index if not exists idx_event_time on event (time);
create index if not exists idx_event_device_id_event_id on event (device_id, event_id desc);
```

- [ ] **Step 6: Run the resource smoke test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.PerfResourceSmokeTest test`
Expected: PASS with one executed test.

### Task 2: Build Variant And Result Types For Stable Re-Runs

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbVariant.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/PerfRunSummary.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/PerfSummaryTest.java`

- [ ] **Step 1: Write the failing summary formatting test**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfSummaryTest {
    @Test
    void formatsStableSummaryLine() {
        PerfRunSummary summary = new PerfRunSummary(EmbeddedDbVariant.SQLITE_JPA);
        summary.add("write-batch", 1000L, 500L);
        summary.add("startup-max-id", 1L, 100L);

        assertEquals(
                "SQLITE_JPA write-batch=1000ns 500ops/s startup-max-id=1ns 100ops/s",
                summary.toSummaryLine());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.PerfSummaryTest test`
Expected: FAIL because the summary classes do not exist yet.

- [ ] **Step 3: Create `EmbeddedDbVariant.java`**

```java
package top.fengpingtech.solen.app.perf;

enum EmbeddedDbVariant {
    HSQLDB_JPA,
    SQLITE_JPA,
    SQLITE_JDBC
}
```

- [ ] **Step 4: Create `PerfRunSummary.java`**

```java
package top.fengpingtech.solen.app.perf;

import java.util.LinkedHashMap;
import java.util.Map;

final class PerfRunSummary {
    private final EmbeddedDbVariant variant;
    private final Map<String, String> values = new LinkedHashMap<>();

    PerfRunSummary(EmbeddedDbVariant variant) {
        this.variant = variant;
    }

    void add(String scenario, long elapsedNanos, long operationsPerSecond) {
        values.put(scenario, elapsedNanos + "ns " + operationsPerSecond + "ops/s");
    }

    String toSummaryLine() {
        StringBuilder builder = new StringBuilder(variant.name());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(" ")
                    .append(entry.getKey())
                    .append("=")
                    .append(entry.getValue());
        }
        return builder.toString();
    }
}
```

- [ ] **Step 5: Run the summary formatting test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.PerfSummaryTest test`
Expected: PASS with one executed test.

### Task 3: Build The JPA Evaluation Support Layer

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfProperties.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfWorkload.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSupport.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSupportTest.java`

- [ ] **Step 1: Write the failing support-layer test**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfSupportTest {
    @Test
    void buildsVariantSpecificDatasourceProperties() throws Exception {
        Path sqlitePath = EmbeddedDbJpaPerfSupport.createDatabasePath("sqlite-jpa", ".sqlite");
        Map<String, Object> sqlite = EmbeddedDbJpaPerfProperties.forVariant(EmbeddedDbVariant.SQLITE_JPA, sqlitePath);
        assertEquals("org.sqlite.JDBC", sqlite.get("spring.datasource.driver-class-name"));
        assertTrue(sqlite.get("spring.datasource.url").toString().startsWith("jdbc:sqlite:"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfSupportTest test`
Expected: FAIL because the support classes do not exist yet.

- [ ] **Step 3: Create `EmbeddedDbJpaPerfProperties.java`**

```java
package top.fengpingtech.solen.app.perf;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class EmbeddedDbJpaPerfProperties {
    private EmbeddedDbJpaPerfProperties() {
    }

    static Map<String, Object> forVariant(EmbeddedDbVariant variant, Path databasePath) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (variant == EmbeddedDbVariant.HSQLDB_JPA) {
            properties.put("spring.datasource.driver-class-name", "org.hsqldb.jdbc.JDBCDriver");
            properties.put("spring.datasource.url", "jdbc:hsqldb:file:" + databasePath.toString());
            properties.put("spring.datasource.username", "sa");
            properties.put("spring.datasource.password", "");
        } else {
            properties.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
            properties.put("spring.datasource.url", "jdbc:sqlite:" + databasePath.toString());
            properties.put("spring.datasource.username", "");
            properties.put("spring.datasource.password", "");
            properties.put("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        }
        return properties;
    }
}
```

- [ ] **Step 4: Create `EmbeddedDbJpaPerfWorkload.java`**

```java
package top.fengpingtech.solen.app.perf;

final class EmbeddedDbJpaPerfWorkload {
    static final int DEVICE_COUNT = 100;
    static final int EVENTS_PER_DEVICE = 200;
    static final int WRITE_BATCH_DEVICE_COUNT = 20;
    static final int WRITE_BATCH_EVENTS_PER_DEVICE = 50;
    static final int PAGE_SIZE = 100;
    static final String RETENTION_CUTOFF = "2026-05-16 12:30:00";

    private EmbeddedDbJpaPerfWorkload() {
    }
}
```

- [ ] **Step 5: Create `EmbeddedDbJpaPerfSupport.java`**

```java
package top.fengpingtech.solen.app.perf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class EmbeddedDbJpaPerfSupport {
    private EmbeddedDbJpaPerfSupport() {
    }

    static Path createDatabasePath(String prefix, String suffix) throws IOException {
        Path directory = Files.createTempDirectory(prefix);
        Path path = directory.resolve("perf" + suffix);
        Files.deleteIfExists(path);
        return path;
    }
}
```

- [ ] **Step 6: Run the support-layer test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfSupportTest test`
Expected: PASS with one executed test.

### Task 4: Build The SQLite JDBC Evaluation Support Layer

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJdbcPerfSupport.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJdbcPerfSupportTest.java`

- [ ] **Step 1: Write the failing JDBC support test**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJdbcPerfSupportTest {
    @Test
    void formatsJdbcSummaryWithSqliteVariant() {
        PerfRunSummary summary = EmbeddedDbJdbcPerfSupport.emptySummary();
        assertTrue(summary.toSummaryLine().startsWith("SQLITE_JDBC"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSupportTest test`
Expected: FAIL because the JDBC support class does not exist yet.

- [ ] **Step 3: Create `EmbeddedDbJdbcPerfSupport.java`**

```java
package top.fengpingtech.solen.app.perf;

final class EmbeddedDbJdbcPerfSupport {
    private EmbeddedDbJdbcPerfSupport() {
    }

    static PerfRunSummary emptySummary() {
        return new PerfRunSummary(EmbeddedDbVariant.SQLITE_JDBC);
    }
}
```

- [ ] **Step 4: Run the JDBC support test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSupportTest test`
Expected: PASS with one executed test.

### Task 5: Add A Correctness Guard For The JPA Dataset

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfCorrectnessTest.java`
- Modify: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSupport.java`

- [ ] **Step 1: Write the failing correctness test skeleton**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

class EmbeddedDbJpaPerfCorrectnessTest {
    @Test
    void loadsAndQueriesRepresentativeDataset() {
        throw new UnsupportedOperationException("implement JPA dataset correctness test");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfCorrectnessTest test`
Expected: FAIL with `UnsupportedOperationException`.

- [ ] **Step 3: Implement dataset seeding and repository-based correctness checks**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfCorrectnessTest {
    @ParameterizedTest
    @EnumSource(EmbeddedDbVariant.class)
    void loadsAndQueriesRepresentativeDataset(EmbeddedDbVariant variant) {
        EmbeddedDbJpaPerfContext context = EmbeddedDbJpaPerfSupport.startContext(variant);
        try {
            EmbeddedDbJpaPerfSupport.seedDataset(context);
            assertEquals(EmbeddedDbJpaPerfWorkload.DEVICE_COUNT * EmbeddedDbJpaPerfWorkload.EVENTS_PER_DEVICE,
                    context.eventRepository().count());
            assertEquals(EmbeddedDbJpaPerfWorkload.PAGE_SIZE,
                    EmbeddedDbJpaPerfSupport.queryRecentPage(context, "device-0001").size());
            assertTrue(EmbeddedDbJpaPerfSupport.deleteRetentionSlice(context) > 0);
        } finally {
            context.close();
        }
    }
}
```

- [ ] **Step 4: Run the correctness test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfCorrectnessTest test`
Expected: PASS with one case for `HSQLDB_JPA` and one case for `SQLITE_JPA`.

### Task 6: Add The Repeatable JPA Performance Evaluation Tests

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfHsqldbTest.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSqliteTest.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfComparisonTest.java`
- Modify: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJpaPerfSupport.java`

- [ ] **Step 1: Write the failing comparison test skeleton**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfComparisonTest {
    @Test
    void comparesHsqldbAndSqliteViaJpa() {
        throw new UnsupportedOperationException("implement JPA comparison test");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest test`
Expected: FAIL with `UnsupportedOperationException`.

- [ ] **Step 3: Implement variant-specific tests that emit stable summary lines**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfHsqldbTest {
    @Test
    void printsHsqldbJpaSummary() {
        PerfRunSummary summary = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.HSQLDB_JPA);
        assertTrue(summary.toSummaryLine().startsWith("HSQLDB_JPA "));
        System.out.println(summary.toSummaryLine());
    }
}
```

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfSqliteTest {
    @Test
    void printsSqliteJpaSummary() {
        PerfRunSummary summary = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.SQLITE_JPA);
        assertTrue(summary.toSummaryLine().startsWith("SQLITE_JPA "));
        System.out.println(summary.toSummaryLine());
    }
}
```

- [ ] **Step 4: Implement the comparison test and the repository-based evaluation flow**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfComparisonTest {
    @Test
    void comparesHsqldbAndSqliteViaJpa() {
        PerfRunSummary hsqldb = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.HSQLDB_JPA);
        PerfRunSummary sqlite = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.SQLITE_JPA);

        assertNotNull(hsqldb.toSummaryLine());
        assertNotNull(sqlite.toSummaryLine());
        assertTrue(hsqldb.toSummaryLine().contains("write-batch="));
        assertTrue(sqlite.toSummaryLine().contains("startup-max-id="));

        System.out.println(hsqldb.toSummaryLine());
        System.out.println(sqlite.toSummaryLine());
    }
}
```

Inside `EmbeddedDbJpaPerfSupport.runEvaluation(...)`, measure these repository-backed scenarios in this order:

```java
- `seedDataset(context)` for baseline data
- `measureWriteBatch(context)` using `eventRepository.saveAll(...)`
- `measureStartupMaxId(context)` using `eventRepository.getMaxId()`
- `measureRecentPage(context)` using `eventRepository.findAll(spec, pageRequest)`
- `measureRetentionDelete(context)` using `eventRepository.deleteByTimeLessThan(...)`
```

Each measurement must convert elapsed nanos plus derived ops/sec into `PerfRunSummary.add(...)` entries with these exact keys:

```java
write-batch
startup-max-id
page-recent
cleanup-retention
```

- [ ] **Step 5: Run the comparison test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest test`
Expected: PASS and print one stable summary line for `HSQLDB_JPA` and one for `SQLITE_JPA`.

### Task 7: Add The Repeatable SQLite JDBC Performance Evaluation Test

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJdbcPerfSqliteTest.java`
- Modify: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/perf/EmbeddedDbJdbcPerfSupport.java`

- [ ] **Step 1: Write the failing SQLite JDBC test skeleton**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

class EmbeddedDbJdbcPerfSqliteTest {
    @Test
    void printsSqliteJdbcSummary() {
        throw new UnsupportedOperationException("implement SQLite JDBC comparison test");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test`
Expected: FAIL with `UnsupportedOperationException`.

- [ ] **Step 3: Implement the SQLite JDBC evaluation flow**

Inside `EmbeddedDbJdbcPerfSupport`, add a `runEvaluation()` method that measures the same scenario keys and order as the JPA path:

```java
write-batch
startup-max-id
page-recent
cleanup-retention
```

Use SQLite JDBC with the shared workload shape from `EmbeddedDbJpaPerfWorkload`, and keep the summary format identical to the JPA runs.

- [ ] **Step 4: Implement the SQLite JDBC test**

```java
package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJdbcPerfSqliteTest {
    @Test
    void printsSqliteJdbcSummary() {
        PerfRunSummary summary = EmbeddedDbJdbcPerfSupport.runEvaluation();
        assertTrue(summary.toSummaryLine().startsWith("SQLITE_JDBC "));
        assertTrue(summary.toSummaryLine().contains("write-batch="));
        System.out.println(summary.toSummaryLine());
    }
}
```

- [ ] **Step 5: Run the SQLite JDBC test to verify it passes**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test`
Expected: PASS and print one stable summary line for `SQLITE_JDBC`.

### Task 8: Preserve The Process And Results In Repository Documentation

**Files:**
- Create: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md`
- Modify: `docs/superpowers/specs/2026-05-16-backend-embedded-db-performance-design.md`

- [ ] **Step 1: Write the results document skeleton with process instructions**

```md
# Backend Embedded DB Performance Results

## Goal

Preserve the repeatable comparison process and the measured results for `HSQLDB + JPA`, `SQLite + JPA`, and `SQLite + JDBC` in `backend/solen-app`.

## How To Re-Run

Run:

```bash
cd backend
./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest test
./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test
```

Capture the printed summary lines and update the latest run section below.

## Environment Notes

- Java: <fill after execution>
- OS: <fill after execution>
- Date: <fill after execution>

## Latest Run

- `HSQLDB_JPA`: <fill after execution>
- `SQLITE_JPA`: <fill after execution>
- `SQLITE_JDBC`: <fill after execution>
- Decision: <fill after execution>
```

- [ ] **Step 2: Create the results document with the skeleton**

Use `apply_patch` to add the markdown file exactly.

- [ ] **Step 3: Run the JPA comparison test and the SQLite JDBC test and capture the real summary lines**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test`
Expected: PASS and print the measured `HSQLDB_JPA`, `SQLITE_JPA`, and `SQLITE_JDBC` summary lines.

- [ ] **Step 4: Replace the result placeholders with actual values from the test run**

Update the markdown file so these fields contain real output:

```md
- Java: 1.8.x or the exact runtime shown locally
- OS: linux or the exact runtime shown locally
- Date: YYYY-MM-DD from the actual run
- `HSQLDB_JPA`: the exact summary line payload from the test output
- `SQLITE_JPA`: the exact summary line payload from the test output
- `SQLITE_JDBC`: the exact summary line payload from the test output
- Decision: a one-line conclusion based on the measured output
```

- [ ] **Step 5: Add an evaluation outcome section to the design doc**

```md
## Evaluation Outcome

- Result document: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md`
- Baseline: `HSQLDB + JPA`
- Candidate: `SQLite + JPA`
- Supplemental diagnostic path: `SQLite + JDBC`
- Re-run command: `cd backend && ./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test`
```

- [ ] **Step 6: Run the narrowest final verification**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfCorrectnessTest,top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest test`
Expected: PASS with correctness coverage plus stable printed summary lines.

## Self-Review

- Spec coverage: the plan covers JPA comparison, supplemental JDBC comparison, repeatable code, preserved result documents, and future re-runs.
- Placeholder scan: only the measured output fields in the result document are intentionally filled after execution; all implementation tasks are explicit.
- Consistency: all tasks stay in `backend/solen-app`, share one stable workload/result format, and preserve comparable outputs across JPA and JDBC paths.
