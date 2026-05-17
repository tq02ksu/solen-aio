package top.fengpingtech.solen.app.controller.bean;

import java.util.Date;
import lombok.Data;

@Data
public class EventQueryRequest {
    private Date startTime;

    private Date endTime;

    private String deviceId;

    private String startId;

    private String type;

    private Integer pageNo;

    private Integer pageSize;
}
