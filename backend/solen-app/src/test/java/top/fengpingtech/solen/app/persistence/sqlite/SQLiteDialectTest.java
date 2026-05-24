package top.fengpingtech.solen.app.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.engine.spi.RowSelection;
import org.junit.jupiter.api.Test;

class SQLiteDialectTest {
    @Test
    void supportsLimitAndTimestampSelection() {
        SQLiteDialect dialect = new SQLiteDialect();

        assertTrue(dialect.supportsLimit());
        assertEquals("select current_timestamp", dialect.getCurrentTimestampSelectString());
        RowSelection selection = new RowSelection();
        selection.setFirstRow(18);
        selection.setMaxRows(2);

        assertEquals(
                "select * from event limit ? offset ?",
                dialect.getLimitHandler().processSql("select * from event", selection));
    }

    @Test
    void shouldBindLimitBeforeOffsetForSqliteSyntax() throws Exception {
        SQLiteDialect dialect = new SQLiteDialect();
        RowSelection selection = new RowSelection();
        selection.setFirstRow(18);
        selection.setMaxRows(2);
        List<Integer> boundValues = new ArrayList<>();

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    if ("setInt".equals(method.getName())) {
                        boundValues.add((Integer) args[1]);
                        return null;
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    if (returnType.equals(float.class)) {
                        return 0f;
                    }
                    if (returnType.equals(double.class)) {
                        return 0d;
                    }
                    return null;
                });

        dialect.getLimitHandler().bindLimitParametersAtEndOfQuery(selection, statement, 1);

        assertEquals(List.of(2, 18), boundValues);
    }
}
