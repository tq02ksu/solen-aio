package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import top.fengpingtech.solen.app.SolenApplication;
import top.fengpingtech.solen.app.repository.ConnectionRepository;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.app.service.EventProcessorImpl;
import top.fengpingtech.solen.server.model.ConnectionEvent;
import top.fengpingtech.solen.server.model.EventType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(classes = SolenApplication.class)
class EventProcessorJdbcRollbackIntegrationTest {
    private static final String DATASOURCE_URL = sqliteUrl("event-processor-rollback-");

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
    private EventJdbcWriter eventJdbcWriter;

    @Autowired
    private EventProcessorImpl eventProcessor;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void rollsBackDeviceAndConnectionChangesWhenJdbcInsertFails() {
        eventJdbcWriter.insert(Collections.singletonList(top.fengpingtech.solen.app.domain.EventDomain.builder()
                .eventId(9001L)
                .device(top.fengpingtech.solen.app.domain.DeviceDomain.builder().deviceId("seed-device").build())
                .type(EventType.MESSAGE_RECEIVING)
                .time(new Date(1_700_000_000_000L))
                .details(Collections.singletonMap("content", "seed"))
                .build()));

        ConnectionEvent event = new ConnectionEvent();
        event.setEventId(9001L);
        event.setConnectionId("conn-rollback");
        event.setDeviceId("device-rollback");
        event.setType(EventType.CONNECT);
        event.setTime(new Date(1_700_000_001_000L));
        event.setLac(11L);
        event.setCi(22L);

        eventProcessor.processEvents(Collections.singletonList(event));

        assertFalse(deviceRepository.findById("device-rollback").isPresent());
        assertFalse(connectionRepository.findById("conn-rollback").isPresent());
        assertEquals(1L, eventRepository.count());
    }
}
