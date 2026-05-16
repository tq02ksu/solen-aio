package top.fengpingtech.solen.app.persistence.sqlite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteDialectTest {
    @Test
    void supportsLimitAndTimestampSelection() {
        SQLiteDialect dialect = new SQLiteDialect();

        assertTrue(dialect.supportsLimit());
        assertEquals("select current_timestamp", dialect.getCurrentTimestampSelectString());
        assertEquals("select * from event limit ? offset ?", dialect.getLimitString("select * from event", true));
    }
}
