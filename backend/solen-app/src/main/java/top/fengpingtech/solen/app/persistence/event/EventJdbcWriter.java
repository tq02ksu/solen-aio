package top.fengpingtech.solen.app.persistence.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.fengpingtech.solen.app.domain.EventDomain;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

@Component
public class EventJdbcWriter {
    private static final String INSERT_SQL =
            "insert into event (event_id, device_id, type, time, details) values (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    private final EventJdbcMapper mapper;

    public EventJdbcWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = new EventJdbcMapper();
    }

    @Transactional
    public void insert(List<EventDomain> events) {
        if (events.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(), (PreparedStatement ps, EventDomain event) -> {
            EventJdbcRow row = mapper.toRow(event);
            ps.setLong(1, row.getEventId());
            ps.setString(2, row.getDeviceId());
            ps.setString(3, row.getType());
            ps.setTimestamp(4, new Timestamp(row.getTime().getTime()));
            ps.setString(5, row.getDetails());
        });
    }
}
