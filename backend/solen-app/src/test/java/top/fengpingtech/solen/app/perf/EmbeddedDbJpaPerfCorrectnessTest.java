package top.fengpingtech.solen.app.perf;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbJpaPerfCorrectnessTest {
    private static final int CORRECTNESS_DEVICE_COUNT = 3;
    private static final int CORRECTNESS_EVENTS_PER_DEVICE = 10;

    @ParameterizedTest
    @EnumSource(value = EmbeddedDbVariant.class, names = {"HSQLDB_JPA", "SQLITE_JPA"})
    void loadsAndQueriesRepresentativeDataset(EmbeddedDbVariant variant) {
        EmbeddedDbJpaPerfContext context = EmbeddedDbJpaPerfSupport.startContext(variant);
        try {
            EmbeddedDbJpaPerfSupport.seedDataset(context, CORRECTNESS_DEVICE_COUNT, CORRECTNESS_EVENTS_PER_DEVICE);
            assertEquals((long) CORRECTNESS_DEVICE_COUNT * CORRECTNESS_EVENTS_PER_DEVICE,
                    context.eventRepository().count());
            assertEquals(CORRECTNESS_EVENTS_PER_DEVICE,
                    EmbeddedDbJpaPerfSupport.queryRecentPage(context, "device-0001", CORRECTNESS_EVENTS_PER_DEVICE).size());
            assertTrue(EmbeddedDbJpaPerfSupport.deleteRetentionSlice(context) > 0);
        } finally {
            context.close();
        }
    }
}
