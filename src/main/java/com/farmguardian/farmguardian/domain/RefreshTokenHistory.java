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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(name = "refresh_token_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenHistory {

    private static final byte CURRENT_SLOT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "refresh_session_id",
            updatable = false)
    private RefreshSession refreshSession;

    @Column(name = "jti", updatable = false)
    private String jti;

    @Column(name = "token_hash", updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "replaced_by_jti")
    private String replacedByJti;

    @Column(name = "reuse_detected_at")
    private LocalDateTime reuseDetectedAt;

    @Column(name = "current_slot")
    private Byte currentSlot;

    public RefreshTokenHistory(
            RefreshSession refreshSession,
            String jti,
            String tokenHash,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        this.refreshSession = Objects.requireNonNull(
                refreshSession, "refreshSession must not be null");
        this.jti = requireExactLength(jti, 36, "jti");
        this.tokenHash = requireExactLength(tokenHash, 64, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!this.expiresAt.isAfter(this.issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.currentSlot = CURRENT_SLOT;
    }

    public boolean isCurrent() {
        return currentSlot != null;
    }

    public void rotateTo(String replacementJti, LocalDateTime rotatedAt) {
        if (!isCurrent()) {
            throw new IllegalStateException("Refresh Token is already rotated");
        }

        String requestedReplacementJti = requireExactLength(
                replacementJti, 36, "replacementJti");
        if (jti.equals(requestedReplacementJti)) {
            throw new IllegalArgumentException("replacementJti must be different from jti");
        }

        LocalDateTime requestedRotatedAt = Objects.requireNonNull(
                rotatedAt, "rotatedAt must not be null");
        if (requestedRotatedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("rotatedAt must not be before issuedAt");
        }

        this.rotatedAt = requestedRotatedAt;
        this.replacedByJti = requestedReplacementJti;
        this.currentSlot = null;
    }

    public void markReuseDetected(LocalDateTime detectedAt) {
        if (isCurrent() || rotatedAt == null) {
            throw new IllegalStateException("Only a rotated Refresh Token can be marked as reused");
        }

        LocalDateTime requestedDetectedAt = Objects.requireNonNull(
                detectedAt, "detectedAt must not be null");
        if (requestedDetectedAt.isBefore(rotatedAt)) {
            throw new IllegalArgumentException("detectedAt must not be before rotatedAt");
        }

        this.reuseDetectedAt = requestedDetectedAt;
    }

    private static String requireExactLength(String value, int length, String fieldName) {
        if (value == null || value.length() != length) {
            throw new IllegalArgumentException(fieldName + " must be exactly " + length + " characters");
        }
        return value;
    }
}
