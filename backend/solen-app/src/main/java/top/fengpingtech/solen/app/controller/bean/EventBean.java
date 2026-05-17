package top.fengpingtech.solen.app.controller.bean;

import java.util.Date;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.fengpingtech.solen.server.model.EventType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventBean {
    private Long eventId;
    private String deviceId;
    private EventType type;
    private Date time;
    private Map<String, String> details;
}
