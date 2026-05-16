package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJdbcPerfSqliteTest {
    @Test
    void printsSqliteJdbcSummary() {
        PerfRunSummary summary = EmbeddedDbJdbcPerfSupport.runEvaluation();
        assertTrue(summary.toSummaryLine().startsWith("SQLITE_JDBC "));
        assertTrue(summary.toSummaryLine().contains("write-batch="));
        System.out.println(summary.toSummaryLine());
    }
}
