package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.SolenApplication;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.service.EventCleaner;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.model.EventType;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = SolenApplication.class,
        properties = {
                "debug=false",
                "logging.level.root=INFO",
                "logging.level.top.fengpingtech.solen=INFO",
                "logging.level.io.netty=INFO",
                "logging.level.org.hibernate.SQL=INFO",
                "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=INFO",
                "logging.level.org.springframework.jdbc=INFO"
        }
)
class SqliteProductionPathLoadTest {
    private static final String DATASOURCE_URL = "jdbc:sqlite:/tmp/opencode/sqlite-production-path-load-"
            + UUID.randomUUID() + ".sqlite?busy_timeout=5000&journal_mode=WAL";
    private static final int TARGET_EVENTS_PER_SECOND = 167;
    private static final int TEST_DURATION_SECONDS = 30;
    private static final int EXPECTED_EVENT_COUNT = TARGET_EVENTS_PER_SECOND * TEST_DURATION_SECONDS;
    private static final int[] TARGET_EVENT_RATES = {167, 250, 333, 500};
    private static final int CONCURRENT_TEST_DURATION_SECONDS = 6;
    private static final int CONCURRENT_INSERTED_EVENT_COUNT = TARGET_EVENTS_PER_SECOND * CONCURRENT_TEST_DURATION_SECONDS;
    private static final int CONCURRENT_CLEANUP_EVENT_COUNT = 300;
    private static final long CONCURRENT_EVENT_TIME_BASE_MILLIS = 1_715_900_000_000L;
    private static final long CONCURRENT_EVENT_ID_BASE = 10_000L;
    private static final long READER_POLL_INTERVAL_NANOS = 20_000_000L;
    private static final long CLEANER_POLL_INTERVAL_NANOS = 250_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATASOURCE_URL);
        registry.add("solen.server.port", () -> 0);
        registry.add("solen.server.event-retention", () -> "36500d");
    }

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EventCleaner eventCleaner;

    @Test
    void verifiesSynchronousJdbcAtTargetAverageRate() {
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId("device-prod-load")
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

        long nextEventId = 1L;
        for (int targetRate : TARGET_EVENT_RATES) {
            jdbcTemplate.update("delete from event");

            int expectedEventCount = targetRate == TARGET_EVENTS_PER_SECOND
                    ? EXPECTED_EVENT_COUNT
                    : targetRate * TEST_DURATION_SECONDS;
            long startNanos = System.nanoTime();
            for (int eventIndex = 0; eventIndex < expectedEventCount; eventIndex++) {
                long targetNanos = startNanos + ((long) eventIndex * NANOS_PER_SECOND) / targetRate;
                long waitNanos = targetNanos - System.nanoTime();
                if (waitNanos > 0L) {
                    LockSupport.parkNanos(waitNanos);
                }

                eventJdbcWriter.insert(Collections.singletonList(EventDomain.builder()
                        .eventId(nextEventId++)
                        .device(device)
                        .type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_715_831_200_000L + eventIndex))
                        .details(Collections.singletonMap("content", "payload-" + eventIndex))
                        .build()));
            }
            long elapsedNanos = System.nanoTime() - startNanos;

            System.out.println("SQLITE_PROD_SYNC target=" + targetRate
                    + " duration=" + TEST_DURATION_SECONDS
                    + " inserted=" + expectedEventCount
                    + " elapsedNs=" + elapsedNanos);

            assertEquals(expectedEventCount, eventRepository.count());
        }
    }

    @Test
    void verifiesWritesWhileJpaReadsAndJdbcCleanupRun() throws InterruptedException, ExecutionException {
        jdbcTemplate.update("delete from event");

        String deviceId = "device-prod-concurrent-" + UUID.randomUUID();
        DeviceDomain device = transactionTemplate.execute(status -> deviceRepository.save(DeviceDomain.builder()
                .deviceId(deviceId)
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
        Date cleanupCutoff = new Date(CONCURRENT_EVENT_TIME_BASE_MILLIS + (CONCURRENT_CLEANUP_EVENT_COUNT * 1000L));
        ConcurrentLinkedQueue<Throwable> threadFailures = new ConcurrentLinkedQueue<>();
        AtomicBoolean writerFinished = new AtomicBoolean(false);
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        try {
            Future<?> writerFuture = executorService.submit(() -> {
                try {
                    long startNanos = System.nanoTime();
                    for (int eventIndex = 0; eventIndex < CONCURRENT_INSERTED_EVENT_COUNT; eventIndex++) {
                        long targetNanos = startNanos + ((long) eventIndex * NANOS_PER_SECOND) / TARGET_EVENTS_PER_SECOND;
                        long waitNanos = targetNanos - System.nanoTime();
                        if (waitNanos > 0L) {
                            LockSupport.parkNanos(waitNanos);
                        }

                        eventJdbcWriter.insert(Collections.singletonList(EventDomain.builder()
                                .eventId(CONCURRENT_EVENT_ID_BASE + eventIndex)
                                .device(device)
                                .type(EventType.MESSAGE_RECEIVING)
                                .time(new Date(CONCURRENT_EVENT_TIME_BASE_MILLIS + (eventIndex * 1000L)))
                                .details(Collections.singletonMap("content", "payload-" + eventIndex))
                                .build()));
                    }
                } catch (Throwable throwable) {
                    threadFailures.add(throwable);
                } finally {
                    writerFinished.set(true);
                }
            });

            Future<?> readerFuture = executorService.submit(() -> {
                try {
                    while (!writerFinished.get()) {
                        eventRepository.findAll(
                                (root, query, cb) -> cb.equal(root.get("device").get("deviceId"), deviceId),
                                PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "eventId"))
                        ).getContent();
                        LockSupport.parkNanos(READER_POLL_INTERVAL_NANOS);
                    }

                    eventRepository.findAll(
                            (root, query, cb) -> cb.equal(root.get("device").get("deviceId"), deviceId),
                            PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "eventId"))
                    ).getContent();
                } catch (Throwable throwable) {
                    threadFailures.add(throwable);
                }
            });

            Future<?> cleanerFuture = executorService.submit(() -> {
                try {
                    while (!writerFinished.get()) {
                        tryDeleteBefore(cleanupCutoff);
                        LockSupport.parkNanos(CLEANER_POLL_INTERVAL_NANOS);
                    }

                    tryDeleteBefore(cleanupCutoff);
                } catch (Throwable throwable) {
                    threadFailures.add(throwable);
                }
            });

            writerFuture.get();
            readerFuture.get();
            cleanerFuture.get();
        } finally {
            executorService.shutdownNow();
        }

        assertTrue(threadFailures.isEmpty(), () -> "Concurrent verification failures: "
                + threadFailures.stream().map(Throwable::toString).collect(Collectors.joining(", ")));

        long remainingEventCount = jdbcTemplate.queryForObject(
                "select count(*) from event where device_id = ?",
                Long.class,
                deviceId
        );

        assertEquals(CONCURRENT_INSERTED_EVENT_COUNT - CONCURRENT_CLEANUP_EVENT_COUNT, remainingEventCount);
    }

    private void tryDeleteBefore(Date cleanupCutoff) {
        try {
            eventJdbcCleaner.deleteBefore(cleanupCutoff);
        } catch (UncategorizedSQLException exception) {
            if (!isSqliteBusy(exception)) {
                throw exception;
            }
        }
    }

    private boolean isSqliteBusy(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("SQLITE_BUSY")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
