package top.fengpingtech.solen.app.perf;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;

final class EmbeddedDbJpaPerfContext implements AutoCloseable {
    private final ConfigurableApplicationContext applicationContext;
    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    EmbeddedDbJpaPerfContext(
            ConfigurableApplicationContext applicationContext,
            EventRepository eventRepository,
            DeviceRepository deviceRepository,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.applicationContext = applicationContext;
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    EventRepository eventRepository() {
        return eventRepository;
    }

    ConfigurableApplicationContext applicationContext() {
        return applicationContext;
    }

    DeviceRepository deviceRepository() {
        return deviceRepository;
    }

    JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    TransactionTemplate transactionTemplate() {
        return transactionTemplate;
    }

    @Override
    public void close() {
        applicationContext.close();
    }
}
