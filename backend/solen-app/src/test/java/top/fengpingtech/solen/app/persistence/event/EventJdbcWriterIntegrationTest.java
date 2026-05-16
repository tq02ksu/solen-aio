package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = SolenApplication.class)
class EventJdbcWriterIntegrationTest {
    private static final String DATASOURCE_URL = "jdbc:sqlite:/tmp/opencode/event-jdbc-writer-"
            + UUID.randomUUID() + ".sqlite";

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

        eventJdbcWriter.insert(Collections.singletonList(EventDomain.builder()
                .eventId(1001L)
                .device(device)
                .type(EventType.MESSAGE_RECEIVING)
                .time(new Date(1_700_000_000_000L))
                .details(Collections.singletonMap("content", "payload"))
                .build()));

        EventDomain saved = eventRepository.findById(1001L)
                .orElseThrow(() -> new AssertionError("missing JDBC-written event"));

        assertEquals(1L, eventRepository.count());
        assertEquals(Long.valueOf(1001L), eventRepository.getMaxId());
        assertEquals("device-jdbc-write", saved.getDevice().getDeviceId());
        assertEquals(EventType.MESSAGE_RECEIVING, saved.getType());
        assertEquals("payload", saved.getDetails().get("content"));
    }
}
