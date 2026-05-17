package top.fengpingtech.solen.app.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddedDbJpaPerfHsqldbTest {
    @Test
    void printsHsqldbJpaSummary() {
        PerfRunSummary summary = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.HSQLDB_JPA);
        assertTrue(summary.toSummaryLine().startsWith("HSQLDB_JPA "));
        System.out.println(summary.toSummaryLine());
    }
}
