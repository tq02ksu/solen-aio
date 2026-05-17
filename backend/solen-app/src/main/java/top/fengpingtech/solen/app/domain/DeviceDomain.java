package top.fengpingtech.solen.app.domain;

import java.util.List;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.fengpingtech.solen.app.domain.support.MinusOneIntegerNotNullConverter;
import top.fengpingtech.solen.app.domain.support.MinusOneLongNotNullConverter;
import top.fengpingtech.solen.app.domain.support.ZeroDoubleNotNullConverter;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "device")
public class DeviceDomain {
    @Id
    private String deviceId;

    @Enumerated(value = EnumType.STRING)
    private ConnectionStatus status;

    @Column(nullable = false)
    @Convert(converter = MinusOneLongNotNullConverter.class)
    private Long lac;

    @Column(nullable = false)
    @Convert(converter = MinusOneLongNotNullConverter.class)
    private Long ci;

    @Column(nullable = false)
    @Convert(converter = MinusOneIntegerNotNullConverter.class)
    private Integer inputStat;

    @Column(nullable = false)
    @Convert(converter = MinusOneIntegerNotNullConverter.class)
    private Integer outputStat;

    @Column(nullable = false)
    @Convert(converter = MinusOneIntegerNotNullConverter.class)
    private Integer rssi;

    @Column(nullable = false)
    @Convert(converter = ZeroDoubleNotNullConverter.class)
    private Double voltage;

    @Column(nullable = false)
    @Convert(converter = ZeroDoubleNotNullConverter.class)
    private Double temperature;

    @Column(nullable = false)
    @Convert(converter = MinusOneIntegerNotNullConverter.class)
    private Integer gravity;

    @Column(nullable = false)
    @Convert(converter = MinusOneIntegerNotNullConverter.class)
    private Integer uptime;

    // longitude
    @Column(nullable = false)
    @Convert(converter = ZeroDoubleNotNullConverter.class)
    private Double lng;

    // latitude
    @Column(nullable = false)
    @Convert(converter = ZeroDoubleNotNullConverter.class)
    private Double lat;

    @OneToMany(mappedBy = "device")
    private List<ConnectionDomain> connection;
}
