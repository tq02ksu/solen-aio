package top.fengpingtech.solen.app.persistence.event;

import java.sql.Timestamp;
import java.util.Date;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.fengpingtech.solen.app.persistence.sqlite.SqliteWriteCoordinator;

@Component
public class EventJdbcCleaner {
    private final JdbcTemplate jdbcTemplate;
    private final SqliteWriteCoordinator sqliteWriteCoordinator;

    public EventJdbcCleaner(JdbcTemplate jdbcTemplate, SqliteWriteCoordinator sqliteWriteCoordinator) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqliteWriteCoordinator = sqliteWriteCoordinator;
    }

    @Transactional
    public int deleteBefore(Date cutoff) {
        return sqliteWriteCoordinator.withWriteLock(
                () -> jdbcTemplate.update("delete from event where time < ?", new Timestamp(cutoff.getTime())));
    }
}
