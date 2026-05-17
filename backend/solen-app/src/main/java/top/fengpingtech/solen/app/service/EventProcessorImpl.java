package top.fengpingtech.solen.app.service;

import static top.fengpingtech.solen.app.domain.CoordinateSystem.WGS84;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.fengpingtech.solen.app.domain.*;
import top.fengpingtech.solen.app.persistence.event.EventJdbcWriter;
import top.fengpingtech.solen.app.persistence.sqlite.SqliteWriteCoordinator;
import top.fengpingtech.solen.app.repository.ConnectionRepository;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.server.EventProcessor;
import top.fengpingtech.solen.server.model.*;

@Service
public class EventProcessorImpl implements EventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EventProcessorImpl.class);
    private static final int SQLITE_BUSY_MAX_ATTEMPTS = 5;
    private static final long SQLITE_BUSY_RETRY_SLEEP_MS = 25L;

    private final DeviceRepository deviceRepository;

    private final ConnectionRepository connectionRepository;

    private final EventJdbcWriter eventJdbcWriter;

    private final TransactionTemplate transactionTemplate;

    private final SqliteWriteCoordinator sqliteWriteCoordinator;

    public EventProcessorImpl(
            DeviceRepository deviceRepository,
            ConnectionRepository connectionRepository,
            EventJdbcWriter eventJdbcWriter,
            TransactionTemplate transactionTemplate,
            SqliteWriteCoordinator sqliteWriteCoordinator) {
        this.deviceRepository = deviceRepository;
        this.connectionRepository = connectionRepository;
        this.eventJdbcWriter = eventJdbcWriter;
        this.transactionTemplate = transactionTemplate;
        this.sqliteWriteCoordinator = sqliteWriteCoordinator;
    }

    @Override
    public void processEvents(List<Event> events) {
        try {
            List<EventDomain> list = executeWithSqliteRetry(events);
            if (list != null && !list.isEmpty()) {
                eventJdbcWriter.enqueue(list);
            }
        } catch (Throwable e) {
            logger.error("error while process events: {}", events, e);
        }
    }

    private List<EventDomain> executeWithSqliteRetry(List<Event> events) {
        for (int attempt = 1; ; attempt++) {
            try {
                return sqliteWriteCoordinator.withWriteLock(
                        () -> transactionTemplate.execute(status -> processEventsInternal(events)));
            } catch (Throwable e) {
                if (!isSqliteBusy(e) || attempt >= SQLITE_BUSY_MAX_ATTEMPTS) {
                    throw e;
                }

                logger.warn(
                        "sqlite busy while processing events, attempt={}/{}, retrying",
                        attempt,
                        SQLITE_BUSY_MAX_ATTEMPTS,
                        e);
                sleepBeforeRetry();
            }
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(SQLITE_BUSY_RETRY_SLEEP_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while retrying sqlite event processing", interruptedException);
        }
    }

    private boolean isSqliteBusy(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_BUSY") || message.contains("database is locked"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<EventDomain> processEventsInternal(List<Event> events) {
        List<EventDomain> list = new ArrayList<>();

        for (Event event : events) {
            switch (event.getType()) {
                case CONNECT:
                    EventDomain eventDomain = processConnect((ConnectionEvent) event);
                    Optional.ofNullable(eventDomain).ifPresent(list::add);
                    break;
                case DISCONNECT:
                    eventDomain = processDisconnect(event);
                    Optional.ofNullable(eventDomain).ifPresent(list::add);
                    break;
                case ATTRIBUTE_UPDATE:
                    eventDomain = processAttributeUpdate((AttributeEvent) event);
                    Optional.ofNullable(eventDomain).ifPresent(list::add);
                    break;
                case CONTROL_SENDING:
                case MESSAGE_RECEIVING:
                case MESSAGE_SENDING:
                    eventDomain = processMessageEvent((MessageEvent) event);
                    Optional.ofNullable(eventDomain).ifPresent(list::add);
                    break;
                case LOCATION_CHANGE:
                    eventDomain = processLocationChange((LocationEvent) event);
                    Optional.ofNullable(eventDomain).ifPresent(list::add);
                    break;
                default:
                    throw new IllegalStateException("unknown event type");
            }
        }

        return list;
    }

    private EventDomain processLocationChange(LocationEvent event) {
        Optional<DeviceDomain> optionalDeviceDomain = deviceRepository.findById(event.getDeviceId());

        if (optionalDeviceDomain.isPresent()) {
            Coordinate coordinate = new Coordinate(WGS84, event.getLng(), event.getLat());
            DeviceDomain device = optionalDeviceDomain.get();
            if (!Objects.equals(device.getLat(), event.getLat()) || !Objects.equals(device.getLng(), event.getLng())) {
                device.setLng(event.getLng());
                device.setLat(event.getLat());
                deviceRepository.save(device);
            }

            Map<String, String> details = new HashMap<>();
            details.put("lat", String.valueOf(event.getLat()));
            details.put("lng", String.valueOf(event.getLng()));
            CoordinateTransformationService transform = new CoordinateTransformationService();
            Coordinate bd09 = transform.wgs84ToBd09(coordinate);
            details.put("bd09Lat", String.valueOf(bd09.getLat()));
            details.put("bd09Lng", String.valueOf(bd09.getLng()));
            Coordinate gcj02 = transform.wgs84ToGcj02(coordinate);
            details.put("gcj02Lat", String.valueOf(gcj02.getLat()));
            details.put("gcj02Lng", String.valueOf(gcj02.getLng()));

            return EventDomain.builder()
                    .eventId(event.getEventId())
                    .device(device)
                    .time(event.getTime())
                    .type(event.getType())
                    .details(details)
                    .build();
        }

        return null;
    }

    private EventDomain processAttributeUpdate(AttributeEvent event) {
        Optional<DeviceDomain> device = deviceRepository.findById(event.getDeviceId());

        if (!device.isPresent()) {
            return null;
        }

        DeviceDomain deviceDomain = device.get();

        Map<String, String> details = new HashMap<>();

        if (!event.getInputStat().equals(deviceDomain.getInputStat())) {
            details.put("inputStat", String.valueOf(event.getInputStat()));
            deviceDomain.setInputStat(event.getInputStat());
        }

        if (!event.getOutputStat().equals(deviceDomain.getOutputStat())) {
            details.put("outputStat", String.valueOf(event.getOutputStat()));
            deviceDomain.setOutputStat(event.getOutputStat());
        }

        if (!event.getRssi().equals(deviceDomain.getRssi())) {
            details.put("rssi", String.valueOf(event.getRssi()));
            deviceDomain.setRssi(event.getRssi());
        }

        if (!event.getVoltage().equals(deviceDomain.getVoltage())) {
            details.put("voltage", String.valueOf(event.getVoltage()));
            deviceDomain.setVoltage(event.getVoltage());
        }

        if (!event.getTemperature().equals(deviceDomain.getTemperature())) {
            details.put("temperature", String.valueOf(event.getTemperature()));
            deviceDomain.setTemperature(event.getTemperature());
        }

        if (!event.getGravity().equals(deviceDomain.getGravity())) {
            details.put("gravity", String.valueOf(event.getGravity()));
            deviceDomain.setGravity(event.getGravity());
        }

        if (!event.getUptime().equals(deviceDomain.getUptime())) {
            details.put("uptime", String.valueOf(event.getUptime()));
            deviceDomain.setUptime(event.getUptime());
        }

        if (details.isEmpty()) {
            return null;
        }

        deviceRepository.save(deviceDomain);

        return EventDomain.builder()
                .eventId(event.getEventId())
                .time(event.getTime())
                .type(event.getType())
                .details(details)
                .device(deviceDomain)
                .build();
    }

    private EventDomain processMessageEvent(MessageEvent event) {
        Optional<DeviceDomain> device = deviceRepository.findById(event.getDeviceId());
        String key = event.getType() == EventType.CONTROL_SENDING ? "ctrl" : "content";
        return device.map(deviceDomain -> EventDomain.builder()
                        .eventId(event.getEventId())
                        .time(event.getTime())
                        .type(event.getType())
                        .device(deviceDomain)
                        .details(Collections.singletonMap(key, event.getMessage()))
                        .build())
                .orElse(null);
    }

    private EventDomain processDisconnect(Event event) {
        Optional<DeviceDomain> device = deviceRepository.findById(event.getDeviceId());

        if (!device.isPresent()) {
            return null;
        }

        List<ConnectionDomain> connections = connectionRepository.findByDevice(device.get());
        // delete current connection
        connections.stream()
                .filter(c -> c.getConnectionId().equals(event.getConnectionId()))
                .forEach(connectionRepository::delete);
        boolean statusChanged =
                connections.stream().noneMatch(c -> c.getConnectionId().equals(event.getConnectionId()));
        DeviceDomain deviceDomain = device.get();
        if (statusChanged) {
            deviceDomain.setStatus(ConnectionStatus.DISCONNECTED);
            deviceRepository.save(deviceDomain);
        }

        return statusChanged
                ? EventDomain.builder()
                        .device(deviceDomain)
                        .type(event.getType())
                        .time(event.getTime())
                        .eventId(event.getEventId())
                        .build()
                : null;

        //                    EventDomain.builder()
        //                            .device(deviceDomain)
        //                            .type(EventType.ATTRIBUTE_UPDATE)
        //                            .time(event.getTime())
        //                            .eventId(event.getEventId())
        //                            .details(Collections.singletonMap("status", ConnectionStatus.DISCONNECTED.name()))
        //                            .build()
    }

    private EventDomain processConnect(ConnectionEvent event) {
        String connectionId = event.getConnectionId();
        Optional<ConnectionDomain> connection = connectionRepository.findById(connectionId);

        if (!connection.isPresent()) {
            connection = Optional.of(ConnectionDomain.builder().build());
        }

        Optional<DeviceDomain> device = deviceRepository.findById(event.getDeviceId());
        DeviceDomain deviceDomain = device.orElseGet(() -> DeviceDomain.builder()
                .deviceId(event.getDeviceId())
                .status(ConnectionStatus.NORMAL)
                .build());
        deviceDomain.setStatus(ConnectionStatus.NORMAL);
        deviceDomain.setLac(event.getLac());
        deviceDomain.setCi(event.getCi());
        deviceRepository.save(deviceDomain);
        // connection
        ConnectionDomain domain = connection.get();
        domain.setConnectionId(event.getConnectionId());
        domain.setDevice(deviceDomain);
        connectionRepository.save(domain);

        return EventDomain.builder()
                .eventId(event.getEventId())
                .device(deviceDomain)
                .type(event.getType())
                .time(event.getTime())
                .build();
        //                EventDomain.builder()
        //                        .eventId(event.getEventId())
        //                        .device(deviceDomain)
        //                        .type(EventType.ATTRIBUTE_UPDATE)
        //                        .time(event.getTime())
        //                        .details(Collections.singletonMap("status", ConnectionStatus.NORMAL.name()))
        //                        .build()
    }
}
