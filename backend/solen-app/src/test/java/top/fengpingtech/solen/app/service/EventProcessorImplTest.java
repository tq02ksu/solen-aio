package top.fengpingtech.solen.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.persistence.event.EventJdbcWriter;
import top.fengpingtech.solen.app.persistence.sqlite.SqliteWriteCoordinator;
import top.fengpingtech.solen.app.repository.ConnectionRepository;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.server.model.Event;
import top.fengpingtech.solen.server.model.EventType;
import top.fengpingtech.solen.server.model.LocationEvent;

class EventProcessorImplTest {
    @Test
    void processLocationChangeUpdatesDeviceWhenStoredCoordinatesAreNull() throws Exception {
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
        EventJdbcWriter eventJdbcWriter = mock(EventJdbcWriter.class);
        EventProcessorImpl processor = new EventProcessorImpl(
                deviceRepository, connectionRepository, eventJdbcWriter, null, new SqliteWriteCoordinator());
        DeviceDomain device = DeviceDomain.builder()
                .deviceId("40623100019")
                .status(ConnectionStatus.NORMAL)
                .lat(null)
                .lng(null)
                .build();
        LocationEvent event = new LocationEvent();
        event.setEventId(1116L);
        event.setDeviceId("40623100019");
        event.setType(EventType.LOCATION_CHANGE);
        event.setTime(new Date(1_700_000_000_000L));
        event.setLat(24.293282d);
        event.setLng(116.113869d);
        when(deviceRepository.findById("40623100019")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(DeviceDomain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<top.fengpingtech.solen.app.domain.EventDomain> writtenEvents =
                invokeProcessEventsInternal(processor, Collections.singletonList(event));

        assertEquals(Double.valueOf(24.293282d), device.getLat());
        assertEquals(Double.valueOf(116.113869d), device.getLng());
        assertNotNull(writtenEvents);
        assertEquals(1, writtenEvents.size());
        assertEquals(Long.valueOf(1116L), writtenEvents.get(0).getEventId());
        assertEquals(EventType.LOCATION_CHANGE, writtenEvents.get(0).getType());
        assertEquals(device, writtenEvents.get(0).getDevice());
        verify(deviceRepository).save(device);
        verify(eventJdbcWriter, never()).enqueue(any());
    }

    @SuppressWarnings("unchecked")
    private List<top.fengpingtech.solen.app.domain.EventDomain> invokeProcessEventsInternal(
            EventProcessorImpl processor, List<Event> events) throws Exception {
        Method method = EventProcessorImpl.class.getDeclaredMethod("processEventsInternal", List.class);
        method.setAccessible(true);
        return (List<top.fengpingtech.solen.app.domain.EventDomain>) method.invoke(processor, events);
    }
}
