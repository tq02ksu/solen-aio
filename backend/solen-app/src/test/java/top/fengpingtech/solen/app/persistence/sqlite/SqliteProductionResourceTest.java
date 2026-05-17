package top.fengpingtech.solen.app.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SqliteProductionResourceTest {
    @Test
    void loadsProductionSqliteSchema() {
        assertTrue(new ClassPathResource("schema-sqlite.sql").exists());
    }
}
