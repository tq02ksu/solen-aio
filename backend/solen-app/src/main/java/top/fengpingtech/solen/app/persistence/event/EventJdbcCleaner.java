package top.fengpingtech.solen.app.persistence.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Date;

@Component
public class EventJdbcCleaner {
    private final JdbcTemplate jdbcTemplate;

    public EventJdbcCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int deleteBefore(Date cutoff) {
        return jdbcTemplate.update("delete from event where time < ?", new Timestamp(cutoff.getTime()));
    }
}
