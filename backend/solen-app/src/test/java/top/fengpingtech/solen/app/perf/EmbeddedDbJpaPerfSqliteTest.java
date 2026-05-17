package top.fengpingtech.solen.app.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddedDbJpaPerfSqliteTest {
    @Test
    void printsSqliteJpaSummary() {
        PerfRunSummary summary = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.SQLITE_JPA);
        assertTrue(summary.toSummaryLine().startsWith("SQLITE_JPA "));
        System.out.println(summary.toSummaryLine());
    }
}
