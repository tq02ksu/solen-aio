package top.fengpingtech.solen.app.perf;

final class EmbeddedDbJpaPerfWorkload {
    static final int DEVICE_COUNT = 100;
    static final int EVENTS_PER_DEVICE = 200;
    static final int WRITE_BATCH_DEVICE_COUNT = 20;
    static final int WRITE_BATCH_EVENTS_PER_DEVICE = 50;
    static final int PAGE_SIZE = 100;
    static final String RETENTION_CUTOFF = "2026-05-16 12:30:00";

    private EmbeddedDbJpaPerfWorkload() {}
}
