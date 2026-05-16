package top.fengpingtech.solen.app.persistence.event;

import java.util.Date;

public class EventJdbcRow {
    private final Long eventId;
    private final String deviceId;
    private final String type;
    private final Date time;
    private final String details;

    public EventJdbcRow(Long eventId, String deviceId, String type, Date time, String details) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.type = type;
        this.time = time;
        this.details = details;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getType() {
        return type;
    }

    public Date getTime() {
        return time;
    }

    public String getDetails() {
        return details;
    }
}
