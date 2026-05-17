package top.fengpingtech.solen.app.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PerfResourceSmokeTest {
    @Test
    void loadsEvaluationResources() {
        assertTrue(new ClassPathResource("perf/application-perf-base.properties").exists());
        assertTrue(new ClassPathResource("perf/schema-sqlite.sql").exists());
    }
}
