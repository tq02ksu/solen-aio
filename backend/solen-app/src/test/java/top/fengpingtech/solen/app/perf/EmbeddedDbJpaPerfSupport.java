package top.fengpingtech.solen.app.perf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.model.EventType;

final class EmbeddedDbJpaPerfSupport {
    private EmbeddedDbJpaPerfSupport() {}

    static Path createDatabasePath(String prefix, String suffix) throws IOException {
        Path directory = Files.createTempDirectory(prefix);
        Path path = directory.resolve("perf" + suffix);
        Files.deleteIfExists(path);
        return path;
    }

    static EmbeddedDbJpaPerfContext startContext(EmbeddedDbVariant variant) {
        try {
            Path databasePath = createDatabasePath(
                    variant.name().toLowerCase(), variant == EmbeddedDbVariant.HSQLDB_JPA ? "" : ".sqlite");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.putAll(EmbeddedDbJpaPerfProperties.forVariant(variant, databasePath));
            properties.put("perf.variant", variant.name());
            properties.put("spring.sql.init.mode", "never");
            properties.put("spring.jpa.hibernate.ddl-auto", "none");
            properties.put("debug", "false");
            properties.put("logging.level.root", "WARN");

            ConfigurableApplicationContext applicationContext = new SpringApplicationBuilder(TestJpaApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(toArgs(properties));

            return new EmbeddedDbJpaPerfContext(
                    applicationContext,
                    applicationContext.getBean(EventRepository.class),
                    applicationContext.getBean(DeviceRepository.class),
                    applicationContext.getBean(JdbcTemplate.class),
                    applicationContext.getBean(org.springframework.transaction.support.TransactionTemplate.class));
        } catch (Exception e) {
            throw new IllegalStateException("failed to start JPA perf context", e);
        }
    }

    static PerfRunSummary runEvaluation(EmbeddedDbVariant variant) {
        EmbeddedDbJpaPerfContext context = startContext(variant);
        try {
            seedDataset(context);

            PerfRunSummary summary = new PerfRunSummary(variant);
            long writeBatchNanos = measureWriteBatch(context);
            summary.add(
                    "write-batch",
                    writeBatchNanos,
                    computeOperationsPerSecond(
                            EmbeddedDbJpaPerfWorkload.WRITE_BATCH_DEVICE_COUNT
                                    * EmbeddedDbJpaPerfWorkload.WRITE_BATCH_EVENTS_PER_DEVICE,
                            writeBatchNanos));
            long startupMaxIdNanos = measureStartupMaxId(context);
            summary.add("startup-max-id", startupMaxIdNanos, computeOperationsPerSecond(1, startupMaxIdNanos));
            long recentPageNanos = measureRecentPage(context);
            summary.add(
                    "page-recent",
                    recentPageNanos,
                    computeOperationsPerSecond(EmbeddedDbJpaPerfWorkload.PAGE_SIZE, recentPageNanos));
            long retentionDeleteNanos = measureRetentionDelete(context);
            summary.add(
                    "cleanup-retention",
                    retentionDeleteNanos,
                    computeOperationsPerSecond(EmbeddedDbJpaPerfWorkload.DEVICE_COUNT * 170L, retentionDeleteNanos));
            return summary;
        } finally {
            context.close();
        }
    }

    static void seedDataset(EmbeddedDbJpaPerfContext context) {
        seedDataset(context, EmbeddedDbJpaPerfWorkload.DEVICE_COUNT, EmbeddedDbJpaPerfWorkload.EVENTS_PER_DEVICE);
    }

    static void seedDataset(EmbeddedDbJpaPerfContext context, int deviceCount, int eventsPerDevice) {
        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            String deviceId = String.format("device-%04d", deviceIndex);
            context.jdbcTemplate()
                    .update(
                            "insert into device (device_id, status, lac, ci, input_stat, output_stat, rssi, voltage, temperature, gravity, uptime, lat, lng) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            deviceId,
                            ConnectionStatus.NORMAL.name(),
                            1L,
                            1L,
                            0,
                            0,
                            -60,
                            3.7d,
                            25.0d,
                            0,
                            100,
                            0.0d,
                            0.0d);

            DeviceDomain device = context.deviceRepository()
                    .findById(deviceId)
                    .orElseThrow(() -> new IllegalStateException("missing seeded device " + deviceId));

            for (int eventIndex = 0; eventIndex < eventsPerDevice; eventIndex++) {
                context.eventRepository()
                        .save(EventDomain.builder()
                                .eventId((long) deviceIndex * eventsPerDevice + eventIndex + 1)
                                .device(device)
                                .type(EventType.MESSAGE_RECEIVING)
                                .time(new Date(1_715_831_200_000L + eventIndex * 1_000L))
                                .details(Collections.singletonMap("content", "payload"))
                                .build());
            }
        }
    }

    static List<EventDomain> queryRecentPage(EmbeddedDbJpaPerfContext context, String deviceId) {
        return queryRecentPage(context, deviceId, EmbeddedDbJpaPerfWorkload.PAGE_SIZE);
    }

    static List<EventDomain> queryRecentPage(EmbeddedDbJpaPerfContext context, String deviceId, int pageSize) {
        Specification<EventDomain> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("device").get("deviceId"), deviceId);
        return context.eventRepository()
                .findAll(specification, PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "eventId")))
                .getContent();
    }

    static long deleteRetentionSlice(EmbeddedDbJpaPerfContext context) {
        long before = context.eventRepository().count();
        context.transactionTemplate().execute(status -> {
            context.eventRepository().deleteByTimeLessThan(new Date(1_715_833_000_000L));
            return null;
        });
        return before - context.eventRepository().count();
    }

    private static long measureWriteBatch(EmbeddedDbJpaPerfContext context) {
        Long maxId = context.eventRepository().getMaxId();
        List<EventDomain> batch = new ArrayList<>();
        long nextEventId = maxId == null ? 1L : maxId + 1L;
        for (int deviceIndex = 0; deviceIndex < EmbeddedDbJpaPerfWorkload.WRITE_BATCH_DEVICE_COUNT; deviceIndex++) {
            DeviceDomain device = context.deviceRepository()
                    .findById(String.format("device-%04d", deviceIndex))
                    .orElseThrow(() -> new IllegalStateException("missing seeded device for write batch"));
            for (int eventIndex = 0;
                    eventIndex < EmbeddedDbJpaPerfWorkload.WRITE_BATCH_EVENTS_PER_DEVICE;
                    eventIndex++) {
                batch.add(EventDomain.builder()
                        .eventId(nextEventId++)
                        .device(device)
                        .type(EventType.MESSAGE_RECEIVING)
                        .time(new Date(1_715_833_200_000L + deviceIndex * 10_000L + eventIndex * 1_000L))
                        .details(Collections.singletonMap("content", "payload"))
                        .build());
            }
        }

        long start = System.nanoTime();
        context.transactionTemplate()
                .execute(status -> context.eventRepository().saveAll(batch));
        return System.nanoTime() - start;
    }

    private static long measureStartupMaxId(EmbeddedDbJpaPerfContext context) {
        long start = System.nanoTime();
        context.eventRepository().getMaxId();
        return System.nanoTime() - start;
    }

    private static long measureRecentPage(EmbeddedDbJpaPerfContext context) {
        String deviceId = String.format("device-%04d", EmbeddedDbJpaPerfWorkload.WRITE_BATCH_DEVICE_COUNT - 1);
        long start = System.nanoTime();
        queryRecentPage(context, deviceId);
        return System.nanoTime() - start;
    }

    private static long measureRetentionDelete(EmbeddedDbJpaPerfContext context) {
        long start = System.nanoTime();
        deleteRetentionSlice(context);
        return System.nanoTime() - start;
    }

    private static long computeOperationsPerSecond(long operations, long elapsedNanos) {
        if (elapsedNanos <= 0L) {
            return operations;
        }
        return Math.max(1L, (operations * 1_000_000_000L) / elapsedNanos);
    }

    private static String[] toArgs(Map<String, Object> properties) {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.toList())
                .toArray(new String[0]);
    }

    @SpringBootApplication
    @ConditionalOnProperty(name = "perf.variant")
    @Import(TestJpaConfiguration.class)
    static class TestJpaApplication {}

    @TestConfiguration
    @ConditionalOnProperty(name = "perf.variant")
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "top.fengpingtech.solen.app.repository")
    static class TestJpaConfiguration {
        @Bean
        @Primary
        DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(DataAccessUtils.singleResult(
                    Collections.singletonList(environment.getProperty("spring.datasource.driver-class-name"))));
            dataSource.setUrl(environment.getProperty("spring.datasource.url"));
            dataSource.setUsername(environment.getProperty("spring.datasource.username"));
            dataSource.setPassword(environment.getProperty("spring.datasource.password"));
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            if ("SQLITE_JPA".equals(environment.getProperty("perf.variant"))) {
                populator.addScript(new ClassPathResource("perf/schema-sqlite.sql"));
            } else {
                populator.addScript(new ClassPathResource("schema.sql"));
            }
            populator.execute(dataSource);
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                DataSource dataSource, org.springframework.core.env.Environment environment) {
            LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                    new LocalContainerEntityManagerFactoryBean();
            entityManagerFactoryBean.setDataSource(dataSource);
            entityManagerFactoryBean.setPackagesToScan("top.fengpingtech.solen.app.domain");
            entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("hibernate.hbm2ddl.auto", "none");
            properties.put("hibernate.show_sql", "false");
            properties.put("hibernate.jdbc.batch_size", "200");
            properties.put("hibernate.order_inserts", "true");
            properties.put("hibernate.order_updates", "true");
            properties.put(
                    "hibernate.physical_naming_strategy",
                    "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy");
            properties.put(
                    "hibernate.implicit_naming_strategy",
                    "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
            String databasePlatform = environment.getProperty("spring.jpa.database-platform");
            if (databasePlatform != null) {
                properties.put("hibernate.dialect", databasePlatform);
            }
            entityManagerFactoryBean.setJpaPropertyMap(properties);
            return entityManagerFactoryBean;
        }

        @Bean
        PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
            JpaTransactionManager transactionManager = new JpaTransactionManager();
            transactionManager.setEntityManagerFactory(entityManagerFactoryBean.getObject());
            return transactionManager;
        }
    }
}
