package top.fengpingtech.solen.app.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import top.fengpingtech.solen.app.SolenApplication;

@SpringBootTest(classes = SolenApplication.class)
class SqliteApplicationConfigTest {
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.sql.init.schema-locations[0]}")
    private String schemaLocation;

    @Test
    void usesSqliteDatasourceAndSchema() {
        assertTrue(datasourceUrl.startsWith("jdbc:sqlite:"));
        assertTrue(schemaLocation.contains("schema-sqlite.sql"));
    }
}
