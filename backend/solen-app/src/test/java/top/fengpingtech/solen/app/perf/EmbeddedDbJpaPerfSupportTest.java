package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfSupportTest {
    @Test
    void buildsVariantSpecificDatasourceProperties() throws Exception {
        Path sqlitePath = EmbeddedDbJpaPerfSupport.createDatabasePath("sqlite-jpa", ".sqlite");
        Map<String, Object> sqlite = EmbeddedDbJpaPerfProperties.forVariant(EmbeddedDbVariant.SQLITE_JPA, sqlitePath);
        assertEquals("org.sqlite.JDBC", sqlite.get("spring.datasource.driver-class-name"));
        assertTrue(sqlite.get("spring.datasource.url").toString().startsWith("jdbc:sqlite:"));
    }

    @Test
    void startsEachVariantWithAnEmptyDeviceTable() {
        assertEmptyDeviceTable(EmbeddedDbVariant.HSQLDB_JPA);
        assertEmptyDeviceTable(EmbeddedDbVariant.SQLITE_JPA);
    }

    @Test
    void startsEachVariantWithItsOwnDatasourceDriver() {
        assertDatasourceDriver(EmbeddedDbVariant.HSQLDB_JPA, "org.hsqldb.jdbc.JDBCDriver");
        assertDatasourceDriver(EmbeddedDbVariant.SQLITE_JPA, "org.sqlite.JDBC");
    }

    @Test
    void startsEachVariantWithItsOwnDatasourceUrlPrefix() {
        assertDatasourceUrlPrefix(EmbeddedDbVariant.HSQLDB_JPA, "jdbc:hsqldb:file:");
        assertDatasourceUrlPrefix(EmbeddedDbVariant.SQLITE_JPA, "jdbc:sqlite:");
    }

    private static void assertEmptyDeviceTable(EmbeddedDbVariant variant) {
        EmbeddedDbJpaPerfContext context = EmbeddedDbJpaPerfSupport.startContext(variant);
        try {
            assertEquals(0, context.jdbcTemplate().queryForObject("select count(*) from device", Integer.class));
        } finally {
            context.close();
        }
    }

    private static void assertDatasourceDriver(EmbeddedDbVariant variant, String expectedDriverClassName) {
        EmbeddedDbJpaPerfContext context = EmbeddedDbJpaPerfSupport.startContext(variant);
        try {
            assertEquals(expectedDriverClassName,
                    context.applicationContext().getEnvironment().getProperty("spring.datasource.driver-class-name"));
        } finally {
            context.close();
        }
    }

    private static void assertDatasourceUrlPrefix(EmbeddedDbVariant variant, String expectedPrefix) {
        EmbeddedDbJpaPerfContext context = EmbeddedDbJpaPerfSupport.startContext(variant);
        try {
            DriverManagerDataSource dataSource = context.applicationContext().getBean(DriverManagerDataSource.class);
            assertTrue(dataSource.getUrl().startsWith(expectedPrefix));
        } finally {
            context.close();
        }
    }

    @Test
    void reportsPreloadedDevicesForHsqldbContext() {
        EmbeddedDbJpaPerfContext context = EmbeddedDbJpaPerfSupport.startContext(EmbeddedDbVariant.HSQLDB_JPA);
        try {
            List<String> deviceIds = context.jdbcTemplate().queryForList(
                    "select device_id from device order by device_id limit 5",
                    String.class);
            assertEquals(Collections.emptyList(), deviceIds);
        } finally {
            context.close();
        }
    }
}
