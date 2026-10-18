package com.farmguardian.farmguardian.config.jwt;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh Token 원문 대신 저장·조회할 SHA-256 해시를 생성합니다.
 * 해시 생성 자체는 JWT의 서명·용도·만료 검증을 대신하지 않습니다.
 */
@Component
public class RefreshTokenHasher {

    public String hash(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalArgumentException("Refresh Token must not be blank");
        }

        try {
            // MessageDigest는 mutable이므로 singleton Bean의 호출 간 공유하지 않습니다.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
