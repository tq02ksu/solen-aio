package top.fengpingtech.solen.app.persistence.event;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.domain.EventDomain;

@Component
public class EventJdbcWriter {
    private static final Logger logger = LoggerFactory.getLogger(EventJdbcWriter.class);
    private static final long ACTIVE_WRITE_SLEEP_MS = 10L;

    private static final String INSERT_SQL =
            "insert into event (event_id, device_id, type, time, details) values (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    private final EventJdbcMapper mapper;

    private final TransactionTemplate transactionTemplate;

    private final BlockingQueue<EventDomain> writeQueue;

    private final int batchSize;

    private final long flushIntervalMs;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private Thread workerThread;

    public EventJdbcWriter(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${solen.event.writer.queue-capacity:20000}") int queueCapacity,
            @Value("${solen.event.writer.batch-size:200}") int batchSize,
            @Value("${solen.event.writer.flush-interval-ms:200}") long flushIntervalMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = new EventJdbcMapper();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.writeQueue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(1L, flushIntervalMs);
    }

    @PostConstruct
    public void start() {
        workerThread = new Thread(this::runLoop, "event-jdbc-writer");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<EventDomain> pending = new ArrayList<>();
        writeQueue.drainTo(pending);
        if (!pending.isEmpty()) {
            flushBatchWithRetry(pending);
        }
    }

    public void enqueue(List<EventDomain> events) {
        if (events.isEmpty()) {
            return;
        }

        for (EventDomain event : events) {
            try {
                // Backpressure: block producer when queue is full instead of dropping events.
                writeQueue.put(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while enqueueing event", e);
            }
        }
    }

    public void insert(List<EventDomain> events) {
        if (events.isEmpty()) {
            return;
        }

        transactionTemplate.execute(status -> {
            jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(), (PreparedStatement ps, EventDomain event) -> {
                EventJdbcRow row = mapper.toRow(event);
                Date eventTime = row.getTime() != null ? row.getTime() : new Date();
                ps.setLong(1, row.getEventId());
                ps.setString(2, row.getDeviceId());
                ps.setString(3, row.getType());
                ps.setTimestamp(4, new Timestamp(eventTime.getTime()));
                ps.setString(5, row.getDetails());
            });
            return null;
        });
    }

    private void runLoop() {
        List<EventDomain> batch = new ArrayList<>(batchSize);

        while (running.get() || !writeQueue.isEmpty()) {
            try {
                EventDomain first = writeQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                }

                if (!batch.isEmpty()) {
                    writeQueue.drainTo(batch, Math.max(0, batchSize - batch.size()));
                }

                if (!batch.isEmpty()) {
                    flushBatchWithRetry(batch);
                    batch.clear();
                    if (running.get()) {
                        TimeUnit.MILLISECONDS.sleep(ACTIVE_WRITE_SLEEP_MS);
                    }
                }
            } catch (InterruptedException e) {
                if (!running.get()) {
                    break;
                }
            } catch (Throwable e) {
                logger.error("error while flushing event queue", e);
            }
        }
    }

    private void flushBatchWithRetry(List<EventDomain> events) {
        while (true) {
            try {
                insert(events);
                logger.info("event batch saved, size={}, queueSize={}", events.size(), writeQueue.size());
                return;
            } catch (Throwable e) {
                logger.error("event batch flush failed, size={}, retrying", events.size(), e);
                try {
                    TimeUnit.MILLISECONDS.sleep(200L);
                } catch (InterruptedException ie) {
                    if (!running.get()) {
                        return;
                    }
                }
            }
        }
    }
}
