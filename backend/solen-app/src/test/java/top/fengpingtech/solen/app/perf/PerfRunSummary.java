package top.fengpingtech.solen.app.perf;

import java.util.LinkedHashMap;
import java.util.Map;

final class PerfRunSummary {
    private final EmbeddedDbVariant variant;
    private final Map<String, String> values = new LinkedHashMap<>();

    PerfRunSummary(EmbeddedDbVariant variant) {
        this.variant = variant;
    }

    void add(String scenario, long elapsedNanos, long operationsPerSecond) {
        values.put(scenario, elapsedNanos + "ns " + operationsPerSecond + "ops/s");
    }

    String toSummaryLine() {
        StringBuilder builder = new StringBuilder(variant.name());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }
}
