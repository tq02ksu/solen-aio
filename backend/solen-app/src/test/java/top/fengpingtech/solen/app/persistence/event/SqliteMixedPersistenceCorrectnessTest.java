package top.fengpingtech.solen.app.persistence.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.SolenApplication;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.model.EventType;

@SpringBootTest(classes = SolenApplication.class)
class SqliteMixedPersistenceCorrectnessTest {
    private static final String DATASOURCE_URL =
            "jdbc:sqlite:/tmp/opencode/sqlite-mixed-persistence-" + UUID.randomUUID() + ".sqlite";

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATASOURCE_URL);
        registry.add("solen.server.port", () -> 0);
    }

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

        eventJdbcWriter.insert(Arrays.asList(
                EventDomain.builder()
                        .eventId(3001L)
                        .device(device)
                        .type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_700_000_000_000L))
                        .details(Collections.singletonMap("content", "a"))
                        .build(),
                EventDomain.builder()
                        .eventId(3002L)
                        .device(device)
                        .type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_700_000_001_000L))
                        .details(Collections.singletonMap("content", "b"))
                        .build()));

        List<EventDomain> page = eventRepository
                .findAll(
                        (root, query, cb) -> cb.equal(root.get("device").get("deviceId"), "device-mixed"),
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "eventId")))
                .getContent();

        assertEquals(2, page.size());
        assertEquals(Long.valueOf(3002L), page.get(0).getEventId());
        assertEquals("b", page.get(0).getDetails().get("content"));
    }
}
