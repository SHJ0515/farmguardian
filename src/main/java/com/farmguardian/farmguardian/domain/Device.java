package com.farmguardian.farmguardian.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE devices SET deleted_at = CURRENT_TIMESTAMP WHERE device_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Device extends BaseDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @Column(name = "device_uuid", unique = true, nullable = false, updatable = false)
    private String deviceUuid;

    @Column(name = "alias", length = 10)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeviceStatus status;

    @Enumerated(EnumType.STRING)
    private TargetCrop targetCrop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Builder
    public Device(String deviceUuid, TargetCrop targetCrop, User user, BigDecimal latitude, BigDecimal longitude) {
        this.deviceUuid = deviceUuid != null ? deviceUuid : UUID.randomUUID().toString();
        this.status = DeviceStatus.AVAILABLE;
        this.targetCrop = targetCrop;
        this.user = user;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void connectToUser(User user, String alias, TargetCrop targetCrop, BigDecimal latitude, BigDecimal longitude) {
        this.user = user;
        this.alias = alias;
        this.status = DeviceStatus.CONNECTED;
        this.targetCrop = targetCrop;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void disconnectFromUser() {
        this.user = null;
        this.alias = null;
        this.status = DeviceStatus.AVAILABLE;
        this.targetCrop = null;
        this.latitude = null;
        this.longitude = null;
    }

    public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
    }

    public void updateTargetCrop(TargetCrop targetCrop) {
        if (targetCrop != null) {
            this.targetCrop = targetCrop;
        }
    }

    public void updateAlias(String alias) {
        if (alias != null) {
            this.alias = alias;
        }
    }
}
