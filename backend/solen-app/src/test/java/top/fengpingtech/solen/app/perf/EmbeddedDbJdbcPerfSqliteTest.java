package top.fengpingtech.solen.app.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddedDbJdbcPerfSqliteTest {
    @Test
    void printsSqliteJdbcSummary() {
        PerfRunSummary summary = EmbeddedDbJdbcPerfSupport.runEvaluation();
        assertTrue(summary.toSummaryLine().startsWith("SQLITE_JDBC "));
        assertTrue(summary.toSummaryLine().contains("write-batch="));
        System.out.println(summary.toSummaryLine());
    }
}
