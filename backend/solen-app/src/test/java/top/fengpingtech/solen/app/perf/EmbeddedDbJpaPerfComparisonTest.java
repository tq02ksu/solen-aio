package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfComparisonTest {
    @Test
    void comparesHsqldbAndSqliteViaJpa() {
        PerfRunSummary hsqldb = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.HSQLDB_JPA);
        PerfRunSummary sqlite = EmbeddedDbJpaPerfSupport.runEvaluation(EmbeddedDbVariant.SQLITE_JPA);

        assertNotNull(hsqldb.toSummaryLine());
        assertNotNull(sqlite.toSummaryLine());
        assertTrue(hsqldb.toSummaryLine().contains("write-batch="));
        assertTrue(hsqldb.toSummaryLine().contains("startup-max-id="));
        assertTrue(hsqldb.toSummaryLine().contains("page-recent="));
        assertTrue(hsqldb.toSummaryLine().contains("cleanup-retention="));
        assertTrue(sqlite.toSummaryLine().contains("write-batch="));
        assertTrue(sqlite.toSummaryLine().contains("startup-max-id="));
        assertTrue(sqlite.toSummaryLine().contains("page-recent="));
        assertTrue(sqlite.toSummaryLine().contains("cleanup-retention="));

        System.out.println(hsqldb.toSummaryLine());
        System.out.println(sqlite.toSummaryLine());
    }
}
