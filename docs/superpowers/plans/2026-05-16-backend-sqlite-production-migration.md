# Backend SQLite Production Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `HSQLDB` with `SQLite` in `backend/solen-app`, move event insertion and retention cleanup to JDBC, keep event reads on JPA, and add targeted correctness and throughput verification for the production migration path.

**Architecture:** Keep `device`, `connection`, event reads, and `getMaxId()` on the existing Spring Data JPA path, but move `event` inserts and retention delete to dedicated JDBC components on the same SQLite datasource. Introduce a production-owned Hibernate 5 `SQLiteDialect`, SQLite-specific schema/configuration, and migration-focused tests that verify both correctness and the target `167-333 event/s` average load band under a simple uniform-arrival model.

**Tech Stack:** Java 8, Spring Boot 2.6.7, Hibernate 5.6.8.Final, Spring Data JPA, JDBC, SQLite, JUnit 5, Maven Surefire

---

## File Structure

- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialect.java`
  Responsibility: production Hibernate 5 SQLite dialect with only the features needed by the current app.
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriter.java`
  Responsibility: JDBC batch insert path for `event` rows.
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleaner.java`
  Responsibility: JDBC retention delete path for `event` rows.
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcRow.java`
  Responsibility: small write-model object for JDBC event persistence arguments.
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcMapper.java`
  Responsibility: convert `EventDomain` to JDBC row values while preserving `MapConverter` JSON semantics.
- Modify: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/service/EventProcessorImpl.java`
  Responsibility: delegate event inserts to JDBC while leaving device and connection updates on JPA.
- Modify: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/service/EventCleaner.java`
  Responsibility: delegate retention delete to JDBC.
- Modify: `backend/solen-app/src/main/resources/application.yml`
  Responsibility: switch production datasource and schema configuration from HSQLDB to SQLite and expose SQLite runtime settings.
- Create: `backend/solen-app/src/main/resources/schema-sqlite.sql`
  Responsibility: production SQLite schema for `device`, `connection`, and `event`.
- Modify: `backend/solen-app/pom.xml`
  Responsibility: add SQLite JDBC runtime dependency for production use and remove HSQLDB runtime dependency if no longer needed.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialectTest.java`
  Responsibility: narrow production dialect compatibility checks.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/EventJdbcMapperTest.java`
  Responsibility: verify JDBC row mapping preserves event details serialization.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriterIntegrationTest.java`
  Responsibility: verify JDBC event insert path writes rows readable through JPA.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleanerIntegrationTest.java`
  Responsibility: verify JDBC retention delete removes expected rows without breaking read-path expectations.
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/SqliteProductionPathLoadTest.java`
  Responsibility: targeted uniform-arrival production-shape verification in the `167-333 event/s` range.
- Modify: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md` or create it if still absent
  Responsibility: preserve actual measured migration-path results and decision notes.

### Task 1: Promote SQLite From Test Dependency To Production Dependency

**Files:**
- Modify: `backend/solen-app/pom.xml`
- Create: `backend/solen-app/src/main/resources/schema-sqlite.sql`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/sqlite/SqliteProductionResourceTest.java`

- [ ] **Step 1: Write the failing production resource test**

```java
package top.fengpingtech.solen.app.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteProductionResourceTest {
    @Test
    void loadsProductionSqliteSchema() {
        assertTrue(new ClassPathResource("schema-sqlite.sql").exists());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SqliteProductionResourceTest test`
Expected: FAIL because `schema-sqlite.sql` and the test class do not exist yet.

- [ ] **Step 3: Add the SQLite JDBC dependency for production and remove HSQLDB runtime dependency if no longer needed**

Use this dependency block shape in `backend/solen-app/pom.xml`:

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
</dependency>
```

If the application will no longer boot against HSQLDB after the migration, remove this block:

```xml
<dependency>
    <groupId>org.hsqldb</groupId>
    <artifactId>hsqldb</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 4: Add the production SQLite schema file**

Create `backend/solen-app/src/main/resources/schema-sqlite.sql` with SQLite-native DDL matching current entity usage:

```sql
create table if not exists device (
    device_id varchar(32) not null primary key,
    status varchar(32) not null,
    lac bigint not null,
    ci bigint not null,
    input_stat integer not null,
    output_stat integer not null,
    rssi integer not null,
    voltage double not null,
    temperature double not null,
    gravity integer not null,
    uptime integer not null,
    lat double not null,
    lng double not null
);

create table if not exists connection (
    connection_id varchar(64) not null primary key,
    device_id varchar(32) not null
);

create index if not exists idx_connection_device_id on connection (device_id);

create table if not exists event (
    event_id bigint not null primary key,
    device_id varchar(32) not null,
    type varchar(32) not null,
    time timestamp not null,
    details varchar(1024) not null
);

create index if not exists idx_event_device_id on event (device_id);
create index if not exists idx_event_type on event (type);
create index if not exists idx_event_time on event (time);
create index if not exists idx_event_device_id_event_id on event (device_id, event_id desc);
```

- [ ] **Step 5: Run the resource test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SqliteProductionResourceTest test`
Expected: PASS with one executed test.

### Task 2: Add The Production Hibernate 5 SQLite Dialect

**Files:**
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialect.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialectTest.java`

- [ ] **Step 1: Write the failing dialect test**

```java
package top.fengpingtech.solen.app.persistence.sqlite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteDialectTest {
    @Test
    void supportsLimitAndTimestampSelection() {
        SQLiteDialect dialect = new SQLiteDialect();

        assertTrue(dialect.supportsLimit());
        assertEquals("select current_timestamp", dialect.getCurrentTimestampSelectString());
        assertEquals("select * from event limit ? offset ?", dialect.getLimitString("select * from event", true));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SQLiteDialectTest test`
Expected: FAIL because the production dialect does not exist yet.

- [ ] **Step 3: Add the production dialect class with the minimal Hibernate 5 feature set**

Create `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialect.java` with this implementation skeleton:

```java
package top.fengpingtech.solen.app.persistence.sqlite;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;

import java.sql.Types;

public class SQLiteDialect extends Dialect {
    public SQLiteDialect() {
        registerColumnType(Types.BIT, "boolean");
        registerColumnType(Types.TINYINT, "tinyint");
        registerColumnType(Types.SMALLINT, "smallint");
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.BIGINT, "bigint");
        registerColumnType(Types.FLOAT, "float");
        registerColumnType(Types.REAL, "real");
        registerColumnType(Types.DOUBLE, "double");
        registerColumnType(Types.NUMERIC, "numeric");
        registerColumnType(Types.DECIMAL, "decimal");
        registerColumnType(Types.CHAR, "char");
        registerColumnType(Types.VARCHAR, "text");
        registerColumnType(Types.LONGVARCHAR, "text");
        registerColumnType(Types.DATE, "text");
        registerColumnType(Types.TIME, "text");
        registerColumnType(Types.TIMESTAMP, "text");
        registerColumnType(Types.BINARY, "blob");
        registerColumnType(Types.VARBINARY, "blob");
        registerColumnType(Types.LONGVARBINARY, "blob");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.CLOB, "text");
        registerColumnType(Types.BOOLEAN, "boolean");
    }

    @Override
    public boolean hasAlterTable() {
        return false;
    }

    @Override
    public boolean dropConstraints() {
        return false;
    }

    @Override
    public String getAddColumnString() {
        return "add column";
    }

    @Override
    public String getCurrentTimestampSelectString() {
        return "select current_timestamp";
    }

    @Override
    public boolean isCurrentTimestampSelectStringCallable() {
        return false;
    }

    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public String getLimitString(String query, boolean hasOffset) {
        return query + (hasOffset ? " limit ? offset ?" : " limit ?");
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new IdentityColumnSupportImpl() {
            @Override
            public boolean supportsIdentityColumns() {
                return true;
            }

            @Override
            public boolean hasDataTypeInIdentityColumn() {
                return false;
            }

            @Override
            public String getIdentityColumnString(int type) {
                return "integer";
            }

            @Override
            public String getIdentitySelectString(String table, String column, int type) {
                return "select last_insert_rowid()";
            }
        };
    }
}
```

- [ ] **Step 4: Run the dialect test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SQLiteDialectTest test`
Expected: PASS with one executed test.

### Task 3: Switch Production Configuration From HSQLDB To SQLite

**Files:**
- Modify: `backend/solen-app/src/main/resources/application.yml`
- Test: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/sqlite/SqliteApplicationConfigTest.java`

- [ ] **Step 1: Write the failing configuration test**

```java
package top.fengpingtech.solen.app.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SqliteApplicationConfigTest {
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.sql.init.schema-locations[0]}")
    private String schemaLocation;

    @Test
    void usesSqliteDatasourceAndSchema() {
        assertTrue(datasourceUrl.startsWith("jdbc:sqlite:"));
        assertTrue(schemaLocation.contains("schema-sqlite.sql"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SqliteApplicationConfigTest test`
Expected: FAIL because production configuration still points to HSQLDB and `schema.sql`.

- [ ] **Step 3: Update `application.yml` to use SQLite and the new schema file**

Replace the datasource and schema section with this shape:

```yml
spring:
  datasource:
    driver-class-name: org.sqlite.JDBC
    url: jdbc:sqlite:data/solen-data.sqlite
    username: ""
    password: ""
  jpa:
    database-platform: top.fengpingtech.solen.app.persistence.sqlite.SQLiteDialect
    hibernate:
      ddl-auto: none
  sql:
    init:
      mode: always
      schema-locations: [ classpath:schema-sqlite.sql ]
```

Keep existing unrelated configuration unchanged.

- [ ] **Step 4: Run the configuration test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SqliteApplicationConfigTest test`
Expected: PASS with one executed test.

### Task 4: Add Event JDBC Mapping That Preserves Existing JSON Semantics

**Files:**
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcRow.java`
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcMapper.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/EventJdbcMapperTest.java`
- Reference: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/domain/support/MapConverter.java`

- [ ] **Step 1: Write the failing JDBC mapper test**

```java
package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.server.model.EventType;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventJdbcMapperTest {
    @Test
    void mapsEventDomainToJdbcRow() {
        EventJdbcMapper mapper = new EventJdbcMapper();
        EventDomain event = EventDomain.builder()
                .eventId(42L)
                .device(DeviceDomain.builder().deviceId("device-1").build())
                .type(EventType.MESSAGE_RECEIVING)
                .time(new Date(1_700_000_000_000L))
                .details(Collections.singletonMap("content", "hello"))
                .build();

        EventJdbcRow row = mapper.toRow(event);

        assertEquals(Long.valueOf(42L), row.getEventId());
        assertEquals("device-1", row.getDeviceId());
        assertEquals("MESSAGE_RECEIVING", row.getType());
        assertEquals("{\"content\":\"hello\"}", row.getDetails());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcMapperTest test`
Expected: FAIL because the JDBC row and mapper classes do not exist yet.

- [ ] **Step 3: Add the JDBC row value object**

Create `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcRow.java`:

```java
package top.fengpingtech.solen.app.persistence.event;

import java.util.Date;

public class EventJdbcRow {
    private final Long eventId;
    private final String deviceId;
    private final String type;
    private final Date time;
    private final String details;

    public EventJdbcRow(Long eventId, String deviceId, String type, Date time, String details) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.type = type;
        this.time = time;
        this.details = details;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getType() {
        return type;
    }

    public Date getTime() {
        return time;
    }

    public String getDetails() {
        return details;
    }
}
```

- [ ] **Step 4: Add the JDBC mapper using the same `MapConverter` serialization**

Create `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcMapper.java`:

```java
package top.fengpingtech.solen.app.persistence.event;

import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.domain.support.MapConverter;

public class EventJdbcMapper {
    private final MapConverter mapConverter = new MapConverter();

    public EventJdbcRow toRow(EventDomain event) {
        return new EventJdbcRow(
                event.getEventId(),
                event.getDevice().getDeviceId(),
                event.getType().name(),
                event.getTime(),
                mapConverter.convertToDatabaseColumn(event.getDetails())
        );
    }
}
```

- [ ] **Step 5: Run the mapper test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcMapperTest test`
Expected: PASS with one executed test.

### Task 5: Move Event Insertion From JPA To JDBC

**Files:**
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriter.java`
- Modify: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/service/EventProcessorImpl.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriterIntegrationTest.java`

- [ ] **Step 1: Write the failing event JDBC writer integration test**

```java
package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.model.EventType;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EventJdbcWriterIntegrationTest {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EventJdbcWriter eventJdbcWriter;

    @Test
    void writesEventsReadableThroughJpa() {
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId("device-jdbc-write")
                .status(ConnectionStatus.NORMAL)
                .lac(1L)
                .ci(1L)
                .inputStat(0)
                .outputStat(0)
                .rssi(-50)
                .voltage(3.7d)
                .temperature(25.0d)
                .gravity(0)
                .uptime(1)
                .lat(0.0d)
                .lng(0.0d)
                .build()));

        eventJdbcWriter.insert(java.util.Collections.singletonList(EventDomain.builder()
                .eventId(1001L)
                .device(device)
                .type(EventType.MESSAGE_RECEIVING)
                .time(new Date(1_700_000_000_000L))
                .details(Collections.singletonMap("content", "payload"))
                .build()));

        assertEquals(1L, eventRepository.count());
        assertEquals(Long.valueOf(1001L), eventRepository.getMaxId());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest test`
Expected: FAIL because `EventJdbcWriter` does not exist yet.

- [ ] **Step 3: Add the JDBC writer component**

Create `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriter.java`:

```java
package top.fengpingtech.solen.app.persistence.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.fengpingtech.solen.app.domain.EventDomain;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Component
public class EventJdbcWriter {
    private static final String INSERT_SQL =
            "insert into event (event_id, device_id, type, time, details) values (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final EventJdbcMapper mapper;

    public EventJdbcWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = new EventJdbcMapper();
    }

    @Transactional
    public void insert(List<EventDomain> events) {
        jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(), (PreparedStatement ps, EventDomain event) -> {
            EventJdbcRow row = mapper.toRow(event);
            ps.setLong(1, row.getEventId());
            ps.setString(2, row.getDeviceId());
            ps.setString(3, row.getType());
            ps.setTimestamp(4, new Timestamp(row.getTime().getTime()));
            ps.setString(5, row.getDetails());
        });
    }
}
```

- [ ] **Step 4: Replace JPA event save with JDBC insert in `EventProcessorImpl`**

Update constructor and persistence call in `backend/solen-app/src/main/java/top/fengpingtech/solen/app/service/EventProcessorImpl.java` to this shape:

```java
private final EventJdbcWriter eventJdbcWriter;

public EventProcessorImpl(DeviceRepository deviceRepository,
                          ConnectionRepository connectionRepository,
                          EventRepository eventRepository,
                          EventJdbcWriter eventJdbcWriter) {
    this.deviceRepository = deviceRepository;
    this.connectionRepository = connectionRepository;
    this.eventRepository = eventRepository;
    this.eventJdbcWriter = eventJdbcWriter;
}

// ...

eventJdbcWriter.insert(list);
```

Do not change the device or connection update logic in this task.

- [ ] **Step 5: Run the integration test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest test`
Expected: PASS with one executed test.

### Task 6: Move Event Retention Cleanup From JPA To JDBC

**Files:**
- Create: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleaner.java`
- Modify: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/service/EventCleaner.java`
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleanerIntegrationTest.java`

- [ ] **Step 1: Write the failing JDBC cleaner integration test**

```java
package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.model.EventType;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EventJdbcCleanerIntegrationTest {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EventJdbcWriter eventJdbcWriter;

    @Autowired
    private EventJdbcCleaner eventJdbcCleaner;

    @Test
    void deletesRowsBeforeRetentionCutoff() {
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId("device-jdbc-clean")
                .status(ConnectionStatus.NORMAL)
                .lac(1L)
                .ci(1L)
                .inputStat(0)
                .outputStat(0)
                .rssi(-50)
                .voltage(3.7d)
                .temperature(25.0d)
                .gravity(0)
                .uptime(1)
                .lat(0.0d)
                .lng(0.0d)
                .build()));

        eventJdbcWriter.insert(java.util.Arrays.asList(
                EventDomain.builder().eventId(2001L).device(device).type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_700_000_000_000L)).details(Collections.singletonMap("content", "old")).build(),
                EventDomain.builder().eventId(2002L).device(device).type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_800_000_000_000L)).details(Collections.singletonMap("content", "new")).build()
        ));

        int deleted = eventJdbcCleaner.deleteBefore(new Date(1_750_000_000_000L));

        assertEquals(1, deleted);
        assertEquals(1L, eventRepository.count());
        assertEquals(Long.valueOf(2002L), eventRepository.getMaxId());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest test`
Expected: FAIL because `EventJdbcCleaner` does not exist yet.

- [ ] **Step 3: Add the JDBC cleaner component**

Create `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleaner.java`:

```java
package top.fengpingtech.solen.app.persistence.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Date;

@Component
public class EventJdbcCleaner {
    private final JdbcTemplate jdbcTemplate;

    public EventJdbcCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int deleteBefore(Date cutoff) {
        return jdbcTemplate.update("delete from event where time < ?", new Timestamp(cutoff.getTime()));
    }
}
```

- [ ] **Step 4: Replace JPA cleanup call in `EventCleaner`**

Update `backend/solen-app/src/main/java/top/fengpingtech/solen/app/service/EventCleaner.java` so it depends on `EventJdbcCleaner` and executes:

```java
eventJdbcCleaner.deleteBefore(date);
```

Keep the current scheduled cadence and `TransactionTemplate` structure unchanged in this task.

- [ ] **Step 5: Run the cleaner integration test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest test`
Expected: PASS with one executed test.

### Task 7: Add Mixed JPA-Read And JDBC-Write Correctness Coverage

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/SqliteMixedPersistenceCorrectnessTest.java`

- [ ] **Step 1: Write the failing mixed-path correctness test**

```java
package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.model.EventType;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SqliteMixedPersistenceCorrectnessTest {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EventJdbcWriter eventJdbcWriter;

    @Test
    void readsJdbcWrittenEventsThroughJpaPaging() {
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId("device-mixed")
                .status(ConnectionStatus.NORMAL)
                .lac(1L)
                .ci(1L)
                .inputStat(0)
                .outputStat(0)
                .rssi(-50)
                .voltage(3.7d)
                .temperature(25.0d)
                .gravity(0)
                .uptime(1)
                .lat(0.0d)
                .lng(0.0d)
                .build()));

        eventJdbcWriter.insert(java.util.Arrays.asList(
                EventDomain.builder().eventId(3001L).device(device).type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_700_000_000_000L)).details(Collections.singletonMap("content", "a")).build(),
                EventDomain.builder().eventId(3002L).device(device).type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_700_000_001_000L)).details(Collections.singletonMap("content", "b")).build()
        ));

        java.util.List<EventDomain> page = eventRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("device").get("deviceId"), "device-mixed"),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "eventId"))
        ).getContent();

        assertEquals(2, page.size());
        assertEquals(Long.valueOf(3002L), page.get(0).getEventId());
        assertEquals("b", page.get(0).getDetails().get("content"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest test`
Expected: FAIL until the SQLite production path is fully wired.

- [ ] **Step 3: Make the smallest fixes needed for JPA reads to work against JDBC-written SQLite rows**

Likely areas to adjust if the test fails:

```java
- confirm the SQLite schema column names match current entity naming expectations
- confirm the production dialect is active for JPA reads
- confirm JDBC writes store `details` in the same JSON format as `MapConverter`
- confirm timestamp values round-trip cleanly through SQLite and JPA
```

Only change code required by the failing test.

- [ ] **Step 4: Run the mixed-path correctness test to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest test`
Expected: PASS with one executed test.

### Task 8: Add Uniform-Arrival Load Verification For The Target Throughput Band

**Files:**
- Create: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/SqliteProductionPathLoadTest.java`

- [ ] **Step 1: Write the failing load verification test skeleton**

```java
package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;

class SqliteProductionPathLoadTest {
    @Test
    void verifiesSynchronousJdbcAtTargetAverageRate() {
        throw new UnsupportedOperationException("implement SQLite production load verification");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
Expected: FAIL with `UnsupportedOperationException`.

- [ ] **Step 3: Implement the uniform-arrival load verification**

Implement `SqliteProductionPathLoadTest` so it:

```java
- prepares one SQLite-backed application context
- seeds the required devices once
- drives synchronous JDBC inserts at a simple uniform-arrival model
- runs at least one target-rate verification point from the approved band, starting with `167 event/s`
- records elapsed time and inserted count
- asserts that all submitted events are persisted
```

Use these implementation constants in the test:

```java
private static final int TARGET_EVENTS_PER_SECOND = 167;
private static final int TEST_DURATION_SECONDS = 30;
private static final int EXPECTED_EVENT_COUNT = TARGET_EVENTS_PER_SECOND * TEST_DURATION_SECONDS;
```

At the end of the test, assert:

```java
assertEquals(EXPECTED_EVENT_COUNT, eventRepository.count());
```

Print a summary line like:

```java
System.out.println("SQLITE_PROD_SYNC target=" + TARGET_EVENTS_PER_SECOND
        + " duration=" + TEST_DURATION_SECONDS
        + " inserted=" + EXPECTED_EVENT_COUNT
        + " elapsedNs=" + elapsedNanos);
```

- [ ] **Step 4: Run the load verification to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
Expected: PASS and print one `SQLITE_PROD_SYNC ...` summary line.

- [ ] **Step 5: Extend the test to the full target band after the first point is green**

Expand the verification to run the same scenario at:

```java
167 event/s
250 event/s
333 event/s
500 event/s
```

Keep the same summary format and assert that each scenario persists every submitted event.

- [ ] **Step 6: Run the expanded load verification to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
Expected: PASS and print one `SQLITE_PROD_SYNC ...` summary line per rate.

### Task 9: Add Query-And-Cleanup Contention Verification

**Files:**
- Modify: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/event/SqliteProductionPathLoadTest.java`

- [ ] **Step 1: Write the failing concurrent verification skeleton**

Add this second test method to `SqliteProductionPathLoadTest`:

```java
@Test
void verifiesWritesWhileJpaReadsAndJdbcCleanupRun() {
    throw new UnsupportedOperationException("implement concurrent SQLite production verification");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest#verifiesWritesWhileJpaReadsAndJdbcCleanupRun test`
Expected: FAIL with `UnsupportedOperationException`.

- [ ] **Step 3: Implement concurrent write-read-cleanup verification**

Implement the test so it:

```java
- runs synchronous JDBC inserts at a uniform rate, starting with `167 event/s`
- periodically runs JPA pageable recent-event reads in a second thread
- periodically runs JDBC cleanup against old rows in a third thread
- records any exceptions from writer, reader, or cleaner threads
- asserts no exceptions occurred
- asserts the final row count matches inserted rows minus expected cleanup removals
```

Keep the first version simple. Do not add asynchronous queue logic in this task.

- [ ] **Step 4: Run the concurrent verification to verify it passes**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest#verifiesWritesWhileJpaReadsAndJdbcCleanupRun test`
Expected: PASS with one executed test.

### Task 10: Preserve Migration Results And Final Verification

**Files:**
- Create or Modify: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md`
- Modify: `docs/superpowers/specs/2026-05-16-backend-sqlite-production-migration-design.md`

- [ ] **Step 1: Create the production migration results document if it does not exist**

Use this skeleton:

```md
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
```

## Latest Results

- `HSQLDB_JPA`: <fill after execution>
- `SQLITE_JPA`: <fill after execution>
- `SQLITE_JDBC`: <fill after execution>
- `SQLITE_PROD_SYNC`: <fill after execution>
- Decision: <fill after execution>
```

- [ ] **Step 2: Run the full migration verification command set and capture actual outputs**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest,top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest,top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest,top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest,top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
Expected: PASS and print the benchmark plus production-path summary lines.

- [ ] **Step 3: Replace the placeholders in the results document with actual values**

Fill in:

```md
- `HSQLDB_JPA`: exact printed summary line
- `SQLITE_JPA`: exact printed summary line
- `SQLITE_JDBC`: exact printed summary line
- `SQLITE_PROD_SYNC`: exact printed summary line(s)
- Decision: one-line conclusion on whether synchronous JDBC is sufficient or whether a bounded queue should be planned next
```

- [ ] **Step 4: Add an implementation outcome section to the production migration spec**

Append this section to `docs/superpowers/specs/2026-05-16-backend-sqlite-production-migration-design.md`:

```md
## Implementation Outcome

- Result document: `docs/superpowers/results/2026-05-16-backend-embedded-db-jpa-performance.md`
- Production dialect: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialect.java`
- Event writer: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcWriter.java`
- Event cleaner: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/persistence/event/EventJdbcCleaner.java`
- Final verification command: `cd backend && ./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.perf.EmbeddedDbJpaPerfComparisonTest,top.fengpingtech.solen.app.perf.EmbeddedDbJdbcPerfSqliteTest,top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest,top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest,top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest,top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
```

- [ ] **Step 5: Run the narrowest final verification again**

Run: `./mvnw -pl solen-app -am -DfailIfNoTests=false -Dtest=top.fengpingtech.solen.app.persistence.event.EventJdbcWriterIntegrationTest,top.fengpingtech.solen.app.persistence.event.EventJdbcCleanerIntegrationTest,top.fengpingtech.solen.app.persistence.event.SqliteMixedPersistenceCorrectnessTest,top.fengpingtech.solen.app.persistence.event.SqliteProductionPathLoadTest test`
Expected: PASS with all migration-path tests green.

## Self-Review

- Spec coverage: the plan covers production SQLite configuration, production Hibernate 5 dialect, JDBC event insert and cleanup, preserved JPA reads, target-rate verification, and result documentation.
- Placeholder scan: every task includes exact file paths, concrete tests, explicit commands, and expected results.
- Type consistency: `EventJdbcWriter`, `EventJdbcCleaner`, `EventJdbcMapper`, and `SQLiteDialect` names are used consistently across tasks.
