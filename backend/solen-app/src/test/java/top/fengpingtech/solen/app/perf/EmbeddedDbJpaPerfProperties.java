package top.fengpingtech.solen.app.perf;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class EmbeddedDbJpaPerfProperties {
    private EmbeddedDbJpaPerfProperties() {}

    static Map<String, Object> forVariant(EmbeddedDbVariant variant, Path databasePath) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (variant == EmbeddedDbVariant.HSQLDB_JPA) {
            properties.put("spring.datasource.driver-class-name", "org.hsqldb.jdbc.JDBCDriver");
            properties.put("spring.datasource.url", "jdbc:hsqldb:file:" + databasePath.toString());
            properties.put("spring.datasource.username", "sa");
            properties.put("spring.datasource.password", "");
        } else {
            properties.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
            properties.put("spring.datasource.url", "jdbc:sqlite:" + databasePath.toString());
            properties.put("spring.datasource.username", "");
            properties.put("spring.datasource.password", "");
            properties.put("spring.jpa.database-platform", "top.fengpingtech.solen.app.perf.SQLiteTestDialect");
        }
        return properties;
    }
}
