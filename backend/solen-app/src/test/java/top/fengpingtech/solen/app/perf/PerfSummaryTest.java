package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfSummaryTest {
    @Test
    void formatsStableSummaryLine() {
        PerfRunSummary summary = new PerfRunSummary(EmbeddedDbVariant.SQLITE_JPA);
        summary.add("write-batch", 1000L, 500L);
        summary.add("startup-max-id", 1L, 100L);

        assertEquals(
                "SQLITE_JPA write-batch=1000ns 500ops/s startup-max-id=1ns 100ops/s",
                summary.toSummaryLine());
    }
}
