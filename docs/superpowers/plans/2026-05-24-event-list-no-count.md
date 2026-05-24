# Event List No-Count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop `/api/event/list` from executing `count(*)` during pagination and return an empty list when `pageNo >= 100`.

**Architecture:** Keep the controller contract unchanged and replace Spring Data `Page` usage with a direct JPA paged query that only fetches the current slice of rows. Preserve the existing tenant filters and fetch-join behavior that removed the event-to-device N+1 issue.

**Tech Stack:** Spring Boot, Spring Data JPA, Hibernate Criteria API, JUnit 5

---

### Task 1: Lock in the desired controller behavior with tests

**Files:**
- Modify: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/controller/EventControllerTest.java`

- [ ] **Step 1: Write the failing test updates**

```java
@Test
void shouldApplyTenantFilterBeforePagination() {
    EventQueryRequest request = new EventQueryRequest();
    request.setPageNo(1);
    request.setPageSize(2);

    List<EventBean> firstPage = eventController.list(request);

    assertEquals(2, firstPage.size());
    assertIterableEquals(
            List.of(1005L, 1004L),
            firstPage.stream().map(EventBean::getEventId).toList());

    request.setPageNo(2);

    List<EventBean> secondPage = eventController.list(request);

    assertEquals(2, secondPage.size());
    assertIterableEquals(
            List.of(1002L, 1001L),
            secondPage.stream().map(EventBean::getEventId).toList());

    request.setPageNo(10);

    List<EventBean> tenthPage = eventController.list(request);

    assertEquals(0, tenthPage.size());

    request.setPageNo(100);

    List<EventBean> cappedPage = eventController.list(request);

    assertEquals(0, cappedPage.size());
}

@Test
void shouldQueryEventPageWithoutCount() {
    Statistics statistics = sessionFactory().getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    EventQueryRequest request = new EventQueryRequest();
    request.setPageNo(1);
    request.setPageSize(2);

    List<EventBean> page = eventController.list(request);

    assertEquals(2, page.size());
    assertEquals(1, statistics.getPrepareStatementCount());
    assertEquals(0, statistics.getEntityFetchCount());
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `./mvnw -pl solen-app -Dtest=EventControllerTest test`
Expected: FAIL because the current implementation still uses `eventRepository.findAll(spec, page)`, which executes a count query and does not short-circuit `pageNo >= 100`.

### Task 2: Replace `Page` usage with direct paged query

**Files:**
- Modify: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/controller/EventController.java`

- [ ] **Step 1: Add the guard for high page numbers**

```java
if (request.getPageNo() == null || request.getPageNo() < 1) {
    request.setPageNo(1);
}

if (request.getPageNo() >= 100) {
    return List.of();
}
```

- [ ] **Step 2: Replace `PageRequest` + repository paging with direct JPA paging**

```java
int offset = (request.getPageNo() - 1) * request.getPageSize();

List<EventDomain> events = eventRepository.findPage(spec, offset, request.getPageSize());
return eventMapper.mapToBean(events);
```

- [ ] **Step 3: Remove no-longer-needed Spring Data page imports**

```java
import java.util.List;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
```

### Task 3: Add a repository method that pages without `count(*)`

**Files:**
- Modify: `backend/solen-app/src/main/java/top/fengpingtech/solen/app/repository/EventRepository.java`

- [ ] **Step 1: Add the custom repository contract to the existing interface**

```java
public interface EventRepository
        extends JpaRepository<EventDomain, Long>, JpaSpecificationExecutor<EventDomain>, EventRepositoryCustom {
```

- [ ] **Step 2: Create the custom repository interface**

```java
package top.fengpingtech.solen.app.repository;

import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import top.fengpingtech.solen.app.domain.EventDomain;

public interface EventRepositoryCustom {
    List<EventDomain> findPage(Specification<EventDomain> specification, int offset, int limit);
}
```

- [ ] **Step 3: Implement the custom repository using Criteria API pagination**

```java
package top.fengpingtech.solen.app.repository;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import top.fengpingtech.solen.app.domain.EventDomain;

public class EventRepositoryImpl implements EventRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<EventDomain> findPage(Specification<EventDomain> specification, int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EventDomain> query = cb.createQuery(EventDomain.class);
        Root<EventDomain> root = query.from(EventDomain.class);

        root.fetch("device");
        query.select(root).distinct(true);
        query.where(specification.toPredicate(root, query, cb));
        query.orderBy(cb.desc(root.get("eventId")));

        TypedQuery<EventDomain> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);
        return typedQuery.getResultList();
    }
}
```

### Task 4: Verify the regression and keep N+1 fixed

**Files:**
- Modify: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/controller/EventControllerTest.java`
- Test: `backend/solen-app/src/test/java/top/fengpingtech/solen/app/persistence/sqlite/SQLiteDialectTest.java`

- [ ] **Step 1: Run the updated event controller tests**

Run: `./mvnw -pl solen-app -Dtest=EventControllerTest test`
Expected: PASS with the page cap enforced and no extra count query.

- [ ] **Step 2: Run the SQLite dialect test to confirm pagination still binds correctly**

Run: `./mvnw -pl solen-app -Dtest=top.fengpingtech.solen.app.persistence.sqlite.SQLiteDialectTest test`
Expected: PASS with the custom `LimitHandler` still binding `limit` then `offset`.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-05-24-event-list-no-count.md \
  backend/solen-app/src/main/java/top/fengpingtech/solen/app/controller/EventController.java \
  backend/solen-app/src/main/java/top/fengpingtech/solen/app/repository/EventRepository.java \
  backend/solen-app/src/main/java/top/fengpingtech/solen/app/repository/EventRepositoryCustom.java \
  backend/solen-app/src/main/java/top/fengpingtech/solen/app/repository/EventRepositoryImpl.java \
  backend/solen-app/src/test/java/top/fengpingtech/solen/app/controller/EventControllerTest.java
git commit -m "perf: remove event list count query"
```
