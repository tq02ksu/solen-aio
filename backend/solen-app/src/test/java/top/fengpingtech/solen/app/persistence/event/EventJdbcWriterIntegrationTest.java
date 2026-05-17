package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = SolenApplication.class)
class EventJdbcWriterIntegrationTest {
    private static final String DATASOURCE_URL = sqliteUrl("event-jdbc-writer-");

    private static String sqliteUrl(String prefix) {
        try {
            Path dir = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "opencode"));
            return "jdbc:sqlite:" + dir.resolve(prefix + UUID.randomUUID() + ".sqlite").toString().replace('\\', '/');
        } catch (IOException e) {
            throw new IllegalStateException("failed to prepare sqlite test path", e);
        }
    }

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

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            eventRepository.deleteAll();
            deviceRepository.deleteAll();
        });
    }

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

    @Test
    void enqueuesEventsAndFlushesAsynchronously() {
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId("device-jdbc-async")
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

        eventJdbcWriter.enqueue(Collections.singletonList(EventDomain.builder()
                .eventId(1003L)
                .device(device)
                .type(EventType.CONTROL_SENDING)
                .time(new Date(1_700_000_100_000L))
                .details(Collections.singletonMap("ctrl", "on"))
                .build()));

        EventDomain saved = awaitEvent(1003L, 5000L)
                .orElseThrow(() -> new AssertionError("missing asynchronously flushed event"));

        assertEquals(EventType.CONTROL_SENDING, saved.getType());
        assertEquals("device-jdbc-async", saved.getDevice().getDeviceId());
        assertEquals("on", saved.getDetails().get("ctrl"));
    }

    @Test
    void writesEventsWithFallbackTimeWhenTimestampIsNull() {
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId("device-jdbc-null-time")
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
                .eventId(1002L)
                .device(device)
                .type(EventType.DISCONNECT)
                .time(null)
                .details(Collections.emptyMap())
                .build()));

        EventDomain saved = eventRepository.findById(1002L)
                .orElseThrow(() -> new AssertionError("missing JDBC-written event with fallback time"));

        assertNotNull(saved.getTime());
        assertEquals(EventType.DISCONNECT, saved.getType());
        assertEquals("device-jdbc-null-time", saved.getDevice().getDeviceId());
    }

    private Optional<EventDomain> awaitEvent(long eventId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<EventDomain> saved = eventRepository.findById(eventId);
            if (saved.isPresent()) {
                return saved;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while awaiting async event", e);
            }
        }
        return Optional.empty();
    }
}
