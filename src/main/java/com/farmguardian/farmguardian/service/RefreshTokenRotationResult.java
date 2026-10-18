package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.dto.response.TokenResponseDto;

import java.util.Objects;

record RefreshTokenRotationResult(Status status, TokenResponseDto tokenResponse) {

    enum Status {
        ISSUED,
        REUSE_REJECTED
    }

    RefreshTokenRotationResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.ISSUED && tokenResponse == null) {
            throw new IllegalArgumentException("Issued result requires a token response");
        }
        if (status != Status.ISSUED && tokenResponse != null) {
            throw new IllegalArgumentException("Rejected result must not contain tokens");
        }
    }

    static RefreshTokenRotationResult issued(TokenResponseDto tokenResponse) {
        return new RefreshTokenRotationResult(
                Status.ISSUED,
                Objects.requireNonNull(tokenResponse, "tokenResponse must not be null"));
    }

    static RefreshTokenRotationResult reuseRejected() {
        return new RefreshTokenRotationResult(Status.REUSE_REJECTED, null);
    }

    boolean isIssued() {
        return status == Status.ISSUED;
    }
}
