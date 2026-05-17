package top.fengpingtech.solen.server.model;

import java.util.Date;
import lombok.Data;

@Data
public class Event {
    private String connectionId;

    private Long eventId;
    private String deviceId;
    private EventType type;
    private Date time;

    private Integer index;
}
