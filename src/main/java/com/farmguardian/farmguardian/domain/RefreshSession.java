package com.farmguardian.farmguardian.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(name = "refresh_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {

    private static final byte ACTIVE_SLOT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            updatable = false)
    private User user;

    @Column(name = "client_uuid", updatable = false)
    private String clientUuid;

    @Column(name = "family_id", updatable = false)
    private String familyId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "reuse_detected_at")
    private LocalDateTime reuseDetectedAt;

    @Column(name = "active_slot")
    private Byte activeSlot;

    @Version
    @Column(name = "version")
    private Long version;

    public RefreshSession(
            User user,
            String clientUuid,
            String familyId,
            LocalDateTime createdAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.clientUuid = requireTextWithinLength(clientUuid, 255, "clientUuid");
        this.familyId = requireExactLength(familyId, 36, "familyId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.activeSlot = ACTIVE_SLOT;
    }

    public boolean isActive() {
        return activeSlot != null;
    }

    public void revoke(LocalDateTime revokedAt) {
        LocalDateTime requestedRevokedAt = Objects.requireNonNull(
                revokedAt, "revokedAt must not be null");
        if (!isActive()) {
            throw new IllegalStateException("Refresh Session is already revoked");
        }
        if (requestedRevokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }

        this.revokedAt = requestedRevokedAt;
        this.activeSlot = null;
    }

    public void markReuseDetected(LocalDateTime detectedAt) {
        LocalDateTime requestedDetectedAt = Objects.requireNonNull(
                detectedAt, "detectedAt must not be null");
        if (isActive()) {
            revoke(requestedDetectedAt);
        } else if (requestedDetectedAt.isBefore(revokedAt)) {
            throw new IllegalArgumentException("detectedAt must not be before revokedAt");
        }

        this.reuseDetectedAt = requestedDetectedAt;
    }

    private static String requireTextWithinLength(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static String requireExactLength(String value, int length, String fieldName) {
        if (value == null || value.length() != length) {
            throw new IllegalArgumentException(fieldName + " must be exactly " + length + " characters");
        }
        return value;
    }
}
