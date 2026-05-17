package top.fengpingtech.solen.app.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.fengpingtech.solen.app.auth.AuthService;
import top.fengpingtech.solen.app.controller.bean.DeviceBean;
import top.fengpingtech.solen.app.controller.bean.DeviceQueryRequest;
import top.fengpingtech.solen.app.controller.bean.PageableResponse;
import top.fengpingtech.solen.app.domain.DeviceDomain;
import top.fengpingtech.solen.app.domain.EventDomain;
import top.fengpingtech.solen.app.mapper.DeviceMapper;
import top.fengpingtech.solen.app.repository.DeviceRepository;
import top.fengpingtech.solen.app.repository.EventRepository;
import top.fengpingtech.solen.server.DeviceService;
import top.fengpingtech.solen.server.model.EventType;

@RestController
@RequestMapping("/api")
public class DeviceController {
    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

    private static final Set<EventType> REPORT_EVENT_TYPES =
            EnumSet.of(EventType.MESSAGE_RECEIVING, EventType.MESSAGE_SENDING, EventType.CONTROL_SENDING);

    private final DeviceService deviceService;

    private final DeviceRepository deviceRepository;

    private final EventRepository eventRepository;

    private final AuthService authService;

    private final DeviceMapper deviceMapper;

    public DeviceController(
            DeviceService deviceService,
            DeviceRepository deviceRepository,
            EventRepository eventRepository,
            AuthService authService,
            DeviceMapper deviceMapper) {
        this.deviceService = deviceService;
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
        this.authService = authService;
        this.deviceMapper = deviceMapper;
    }

    @RequestMapping("/list")
    public PageableResponse<DeviceBean> list(DeviceQueryRequest request) {
        if (request.getPageNo() == null) {
            request.setPageNo(1);
        }

        if (request.getPageSize() == null) {
            request.setPageSize(100);
        }

        PageRequest page = PageRequest.of(
                request.getPageNo() - 1, request.getPageSize(), Sort.by(Sort.Direction.DESC, "deviceId"));

        Specification<DeviceDomain> spec = (root, cq, cb) -> {
            List<Predicate> list = new ArrayList<>();

            if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
                list.add(root.get("deviceId")
                        .in(Arrays.asList(request.getDeviceId().split("[, |]"))));
            }

            if (request.getStatus() != null) {
                list.add(root.get("status").in(Arrays.asList(request.getStatus().split("[, |]"))));
            }

            authService.fillAuthPredicate(root.get("deviceId"), cb, list);

            return cb.and(list.toArray(new Predicate[0]));
        };

        Page<DeviceDomain> list = deviceRepository.findAll(spec, page);
        return PageableResponse.<DeviceBean>builder()
                .total(list.getTotalElements())
                .data(deviceMapper.mapToBean(list.getContent()))
                .build();
    }

    @GetMapping("/device/{deviceId}")
    public DeviceBean detail(@PathVariable("deviceId") String deviceId) {
        Optional<DeviceDomain> device = deviceRepository.findById(deviceId);

        if (device.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "device not found!");
        }
        DeviceDomain domain = device.get();
        if (!authService.canVisit(domain)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "can not visit the device");
        }
        return buildDeviceBean(domain);
    }

    @DeleteMapping("/device/{deviceId}")
    public Object delete(
            @PathVariable("deviceId") String deviceId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        Optional<DeviceDomain> device = deviceRepository.findById(deviceId);
        if (device.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "device not found!");
        }
        DeviceDomain domain = device.get();
        if (!authService.canVisit(domain)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "can not visit the device");
        }
        deviceRepository.deleteById(deviceId);

        return buildDeviceBean(domain);
    }

    @RequestMapping("/statByField")
    public Map<String, Long> statByField(@RequestParam String field) {
        return null;
    }

    @PostMapping("/sendControl")
    public DeviceBean sendControl(@RequestBody SendRequest request) {
        Optional<DeviceDomain> device = deviceRepository.findById(request.getDeviceId());
        if (device.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "device not found!");
        }
        DeviceDomain domain = device.get();
        if (!authService.canVisit(domain)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "can not visit the device");
        }

        deviceService.sendControl(String.valueOf(request.getDeviceId()), request.getCtrl());

        return buildDeviceBean(domain);
    }

    @PostMapping("/sendAscii")
    public DeviceBean sendAscii(@RequestBody SendRequest request) throws Exception {
        if (request.getData() == null) {
            throw new IllegalArgumentException("data can not be null");
        }

        if (request.getDeviceId() == null) {
            throw new IllegalArgumentException("deviceId can not be null");
        }
        Optional<DeviceDomain> device = deviceRepository.findById(request.getDeviceId());
        if (device.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "device not found!");
        }
        DeviceDomain domain = device.get();
        if (!authService.canVisit(domain)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "can not visit the device");
        }

        deviceService.sendMessage(String.valueOf(request.getDeviceId()), request.getData());
        return buildDeviceBean(domain);
    }

    private DeviceBean buildDeviceBean(DeviceDomain domain) {
        DeviceBean bean = deviceMapper.mapToBean(domain);
        bean.setReports(loadReports(domain.getDeviceId()));
        return bean;
    }

    private List<DeviceBean.Report> loadReports(String deviceId) {
        List<EventDomain> events = eventRepository.findTop20ByDeviceDeviceIdOrderByEventIdDesc(deviceId);
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return events.stream().map(this::toReport).flatMap(Optional::stream).collect(Collectors.toList());
    }

    private Optional<DeviceBean.Report> toReport(EventDomain event) {
        if (event == null || event.getType() == null || !REPORT_EVENT_TYPES.contains(event.getType())) {
            return Optional.empty();
        }
        Map<String, String> details = event.getDetails();
        if (details == null || details.isEmpty()) {
            return Optional.empty();
        }
        String content = Optional.ofNullable(details.get("content")).orElse(details.get("ctrl"));
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DeviceBean.Report.builder()
                .time(event.getTime())
                .content(content)
                .build());
    }
}
