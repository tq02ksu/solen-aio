package top.fengpingtech.solen.app.persistence.event;

import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.domain.support.MapConverter;

public class EventJdbcMapper {
    private final MapConverter mapConverter = new MapConverter();

    public EventJdbcRow toRow(EventDomain event) {
        return new EventJdbcRow(
                event.getEventId(),
                event.getDevice().getDeviceId(),
                event.getType().name(),
                event.getTime(),
                mapConverter.convertToDatabaseColumn(event.getDetails())
        );
    }
}
