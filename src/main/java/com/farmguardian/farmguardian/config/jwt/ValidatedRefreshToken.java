package com.farmguardian.farmguardian.config.jwt;

import java.time.Instant;

/**
 * Provider가 검증한 Refresh JWT의 불변 메타데이터입니다.
 * JWT 검증 결과일 뿐, DB 세션의 활성 상태까지 보장하지는 않습니다.
 */
public final class ValidatedRefreshToken {

    private final String email;
    private final String jti;
    private final Instant issuedAt;
    private final Instant expiresAt;

    ValidatedRefreshToken(String email, String jti, Instant issuedAt, Instant expiresAt) {
        this.email = email;
        this.jti = jti;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getEmail() {
        return email;
    }

    public String getJti() {
        return jti;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
