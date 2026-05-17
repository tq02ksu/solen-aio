package top.fengpingtech.solen.server;

import java.util.List;
import top.fengpingtech.solen.server.model.Event;

public interface EventProcessor {
    void processEvents(List<Event> events);
}
