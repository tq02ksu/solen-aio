package top.fengpingtech.solen.app.perf;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

final class EmbeddedDbJdbcPerfSupport {
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String EVENT_DETAILS = "{\"content\":\"payload\"}";

    private EmbeddedDbJdbcPerfSupport() {}

    static PerfRunSummary runEvaluation() {
        try {
            Path databasePath = EmbeddedDbJpaPerfSupport.createDatabasePath("sqlite-jdbc", ".sqlite");
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(SQLITE_DRIVER);
            dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("perf/schema-sqlite.sql"));
            populator.execute(dataSource);

            try (Connection connection = dataSource.getConnection()) {
                seedDataset(connection);

                PerfRunSummary summary = new PerfRunSummary(EmbeddedDbVariant.SQLITE_JDBC);
                long writeBatchNanos = measureWriteBatch(connection);
                summary.add(
                        "write-batch",
                        writeBatchNanos,
                        computeOperationsPerSecond(
                                EmbeddedDbJpaPerfWorkload.WRITE_BATCH_DEVICE_COUNT
                                        * EmbeddedDbJpaPerfWorkload.WRITE_BATCH_EVENTS_PER_DEVICE,
                                writeBatchNanos));
                long startupMaxIdNanos = measureStartupMaxId(connection);
                summary.add("startup-max-id", startupMaxIdNanos, computeOperationsPerSecond(1, startupMaxIdNanos));
                long recentPageNanos = measureRecentPage(connection);
                summary.add(
                        "page-recent",
                        recentPageNanos,
                        computeOperationsPerSecond(EmbeddedDbJpaPerfWorkload.PAGE_SIZE, recentPageNanos));
                long retentionDeleteNanos = measureRetentionDelete(connection);
                summary.add(
                        "cleanup-retention",
                        retentionDeleteNanos,
                        computeOperationsPerSecond(
                                EmbeddedDbJpaPerfWorkload.DEVICE_COUNT * 170L, retentionDeleteNanos));
                return summary;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to run SQLite JDBC perf evaluation", e);
        }
    }

    private static void seedDataset(Connection connection) throws SQLException {
        executeInTransaction(connection, () -> {
            try (PreparedStatement deviceStatement = connection.prepareStatement(
                            "insert into device (device_id, status, lac, ci, input_stat, output_stat, rssi, voltage, temperature, gravity, uptime, lat, lng) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    PreparedStatement eventStatement = connection.prepareStatement(
                            "insert into event (event_id, device_id, type, time, details) values (?, ?, ?, ?, ?)")) {
                for (int deviceIndex = 0; deviceIndex < EmbeddedDbJpaPerfWorkload.DEVICE_COUNT; deviceIndex++) {
                    String deviceId = String.format("device-%04d", deviceIndex);
                    bindDevice(deviceStatement, deviceId);
                    deviceStatement.addBatch();

                    for (int eventIndex = 0; eventIndex < EmbeddedDbJpaPerfWorkload.EVENTS_PER_DEVICE; eventIndex++) {
                        bindEvent(
                                eventStatement,
                                (long) deviceIndex * EmbeddedDbJpaPerfWorkload.EVENTS_PER_DEVICE + eventIndex + 1,
                                deviceId,
                                1_715_831_200_000L + eventIndex * 1_000L);
                        eventStatement.addBatch();
                    }
                }

                deviceStatement.executeBatch();
                eventStatement.executeBatch();
            }
        });
    }

    private static long measureWriteBatch(Connection connection) throws SQLException {
        long nextEventId = readMaxEventId(connection) + 1L;
        long start = System.nanoTime();
        executeInTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into event (event_id, device_id, type, time, details) values (?, ?, ?, ?, ?)")) {
                for (int deviceIndex = 0;
                        deviceIndex < EmbeddedDbJpaPerfWorkload.WRITE_BATCH_DEVICE_COUNT;
                        deviceIndex++) {
                    String deviceId = String.format("device-%04d", deviceIndex);
                    for (int eventIndex = 0;
                            eventIndex < EmbeddedDbJpaPerfWorkload.WRITE_BATCH_EVENTS_PER_DEVICE;
                            eventIndex++) {
                        bindEvent(
                                statement,
                                nextEventId
                                        + (long) deviceIndex * EmbeddedDbJpaPerfWorkload.WRITE_BATCH_EVENTS_PER_DEVICE
                                        + eventIndex,
                                deviceId,
                                1_715_833_200_000L + deviceIndex * 10_000L + eventIndex * 1_000L);
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }
        });
        return System.nanoTime() - start;
    }

    private static long measureStartupMaxId(Connection connection) throws SQLException {
        long start = System.nanoTime();
        readMaxEventId(connection);
        return System.nanoTime() - start;
    }

    private static long measureRecentPage(Connection connection) throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(
                "select event_id from event where device_id = ? order by event_id desc limit ?")) {
            statement.setString(
                    1, String.format("device-%04d", EmbeddedDbJpaPerfWorkload.WRITE_BATCH_DEVICE_COUNT - 1));
            statement.setInt(2, EmbeddedDbJpaPerfWorkload.PAGE_SIZE);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    resultSet.getLong(1);
                }
            }
        }
        return System.nanoTime() - start;
    }

    private static long measureRetentionDelete(Connection connection) throws SQLException {
        long start = System.nanoTime();
        executeInTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement("delete from event where time < ?")) {
                statement.setTimestamp(1, Timestamp.valueOf(EmbeddedDbJpaPerfWorkload.RETENTION_CUTOFF));
                statement.executeUpdate();
            }
        });
        return System.nanoTime() - start;
    }

    private static long readMaxEventId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select max(event_id) from event");
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 0L;
            }
            return resultSet.getLong(1);
        }
    }

    private static void bindDevice(PreparedStatement statement, String deviceId) throws SQLException {
        statement.setString(1, deviceId);
        statement.setString(2, "NORMAL");
        statement.setLong(3, 1L);
        statement.setLong(4, 1L);
        statement.setInt(5, 0);
        statement.setInt(6, 0);
        statement.setInt(7, -60);
        statement.setDouble(8, 3.7d);
        statement.setDouble(9, 25.0d);
        statement.setInt(10, 0);
        statement.setInt(11, 100);
        statement.setDouble(12, 0.0d);
        statement.setDouble(13, 0.0d);
    }

    private static void bindEvent(PreparedStatement statement, long eventId, String deviceId, long timestampMillis)
            throws SQLException {
        statement.setLong(1, eventId);
        statement.setString(2, deviceId);
        statement.setString(3, "MESSAGE_RECEIVING");
        statement.setTimestamp(4, new Timestamp(timestampMillis));
        statement.setString(5, EVENT_DETAILS);
    }

    private static long computeOperationsPerSecond(long operations, long elapsedNanos) {
        if (elapsedNanos <= 0L) {
            return operations;
        }
        return Math.max(1L, (operations * 1_000_000_000L) / elapsedNanos);
    }

    private static void executeInTransaction(Connection connection, SqlWork work) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            work.run();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }
}
