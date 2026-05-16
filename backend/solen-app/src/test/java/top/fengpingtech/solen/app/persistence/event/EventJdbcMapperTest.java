package top.fengpingtech.solen.app.persistence.event;

import org.junit.jupiter.api.Test;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.server.model.EventType;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventJdbcMapperTest {
    @Test
    void mapsEventDomainToJdbcRow() {
        EventJdbcMapper mapper = new EventJdbcMapper();
        EventDomain event = EventDomain.builder()
                .eventId(42L)
                .device(DeviceDomain.builder().deviceId("device-1").build())
                .type(EventType.MESSAGE_RECEIVING)
                .time(new Date(1_700_000_000_000L))
                .details(Collections.singletonMap("content", "hello"))
                .build();

        EventJdbcRow row = mapper.toRow(event);

        assertEquals(Long.valueOf(42L), row.getEventId());
        assertEquals("device-1", row.getDeviceId());
        assertEquals("MESSAGE_RECEIVING", row.getType());
        assertEquals("{\"content\":\"hello\"}", row.getDetails());
    }
}
