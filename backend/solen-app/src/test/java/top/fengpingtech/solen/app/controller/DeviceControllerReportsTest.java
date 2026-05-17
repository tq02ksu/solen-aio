package top.fengpingtech.solen.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import top.fengpingtech.solen.app.auth.AuthService;
import top.fengpingtech.solen.app.controller.bean.DeviceBean;
import top.fengpingtech.solen.app.domain.ConnectionStatus;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.mapper.DeviceMapper;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.DeviceService;
import top.fengpingtech.solen.server.model.EventType;

class DeviceControllerReportsTest {
    private final DeviceService deviceService = mock(DeviceService.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final DeviceMapper deviceMapper = Mappers.getMapper(DeviceMapper.class);

    private final DeviceController controller =
            new DeviceController(deviceService, deviceRepository, eventRepository, authService, deviceMapper);

    @Test
    void detailIncludesMessageAndControlReports() {
        DeviceDomain device = DeviceDomain.builder()
                .deviceId("40624120014")
                .status(ConnectionStatus.NORMAL)
                .build();
        when(deviceRepository.findById("40624120014")).thenReturn(Optional.of(device));
        when(authService.canVisit(device)).thenReturn(true);
        when(eventRepository.findTop20ByDeviceDeviceIdOrderByEventIdDesc("40624120014"))
                .thenReturn(Arrays.asList(
                        event(3L, EventType.MESSAGE_RECEIVING, new Date(3_000L), Map.of("content", "hello")),
                        event(2L, EventType.CONTROL_SENDING, new Date(2_000L), Map.of("ctrl", "1")),
                        event(1L, EventType.ATTRIBUTE_UPDATE, new Date(1_000L), Map.of("rssi", "18"))));

        DeviceBean response = controller.detail("40624120014");

        assertNotNull(response.getReports());
        assertEquals(2, response.getReports().size());
        assertEquals("hello", response.getReports().get(0).getContent());
        assertEquals("1", response.getReports().get(1).getContent());
    }

    @Test
    void detailReturnsEmptyReportsWhenNoEventsExist() {
        DeviceDomain device = DeviceDomain.builder()
                .deviceId("40624120014")
                .status(ConnectionStatus.NORMAL)
                .build();
        when(deviceRepository.findById("40624120014")).thenReturn(Optional.of(device));
        when(authService.canVisit(device)).thenReturn(true);
        when(eventRepository.findTop20ByDeviceDeviceIdOrderByEventIdDesc("40624120014"))
                .thenReturn(Collections.emptyList());

        DeviceBean response = controller.detail("40624120014");

        assertNotNull(response.getReports());
        assertEquals(0, response.getReports().size());
    }

    private EventDomain event(Long eventId, EventType type, Date time, Map<String, String> details) {
        return EventDomain.builder()
                .eventId(eventId)
                .type(type)
                .time(time)
                .details(details)
                .build();
    }
}
