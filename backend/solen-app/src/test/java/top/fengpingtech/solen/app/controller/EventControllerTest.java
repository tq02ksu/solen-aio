package top.fengpingtech.solen.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.sql.Timestamp;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import top.fengpingtech.solen.app.SolenApplicationTests;
import top.fengpingtech.solen.app.auth.SecurityContext;
import top.fengpingtech.solen.app.controller.bean.EventBean;
import top.fengpingtech.solen.app.controller.bean.EventQueryRequest;

class EventControllerTest extends SolenApplicationTests {

    @Autowired
    private EventController eventController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private javax.persistence.EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", null));
        jdbcTemplate.update("delete from event");
        jdbcTemplate.update("delete from connection");
        jdbcTemplate.update("delete from device");
        insertDevice("55500001");
        insertDevice("55500002");
        insertDevice("99900001");
        insertEvent(1006L, "99900001", "2026-01-01 00:00:06.000");
        insertEvent(1005L, "55500002", "2026-01-01 00:00:05.000");
        insertEvent(1004L, "55500001", "2026-01-01 00:00:04.000");
        insertEvent(1003L, "99900001", "2026-01-01 00:00:03.000");
        insertEvent(1002L, "55500002", "2026-01-01 00:00:02.000");
        insertEvent(1001L, "55500001", "2026-01-01 00:00:01.000");
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
        SecurityContextHolder.clearContext();
    }

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
        assertIterableEquals(
                List.of("55500002", "55500001"),
                firstPage.stream().map(EventBean::getDeviceId).toList());

        request.setPageNo(2);

        List<EventBean> secondPage = eventController.list(request);

        assertEquals(2, secondPage.size());
        assertIterableEquals(
                List.of(1002L, 1001L),
                secondPage.stream().map(EventBean::getEventId).toList());
        assertIterableEquals(
                List.of("55500002", "55500001"),
                secondPage.stream().map(EventBean::getDeviceId).toList());

        request.setPageNo(10);

        List<EventBean> tenthPage = eventController.list(request);

        assertEquals(0, tenthPage.size());
    }

    @Test
    void shouldNotTriggerNPlusOneWhenMappingEventPage() {
        Statistics statistics = sessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        EventQueryRequest request = new EventQueryRequest();
        request.setPageNo(1);
        request.setPageSize(2);

        List<EventBean> page = eventController.list(request);

        assertEquals(2, page.size());
        assertEquals(2, statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getEntityFetchCount());
    }

    private SessionFactory sessionFactory() {
        return entityManagerFactory.unwrap(SessionFactory.class);
    }

    private void insertDevice(String deviceId) {
        jdbcTemplate.update(
                "insert into device (device_id, status, lac, ci, input_stat, output_stat, rssi, voltage, temperature, gravity, uptime, lat, lng) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                deviceId,
                "NORMAL",
                1L,
                1L,
                0,
                0,
                0,
                3.7d,
                25.0d,
                0,
                0,
                24.0d,
                118.0d);
    }

    private void insertEvent(Long eventId, String deviceId, String time) {
        jdbcTemplate.update(
                "insert into event (event_id, device_id, type, time, details) values (?, ?, ?, ?, ?)",
                eventId,
                deviceId,
                "CONNECT",
                Timestamp.valueOf(time),
                "{\"source\":\"test\"}");
    }
}
