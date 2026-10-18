package com.farmguardian.farmguardian.config.jwt;

import com.farmguardian.farmguardian.config.auth.UserDetailsImpl;
import com.farmguardian.farmguardian.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ROLE_CLAIM = "role";
    private static final String USER_ID_CLAIM = "userId";
    private static final long ALLOWED_FUTURE_ISSUED_AT_MILLIS = 60_000L;

    private final Key secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;
    private final String accessTokenAudience;
    private final String refreshTokenAudience;
    private final JwtParser accessTokenParser;
    private final JwtParser refreshTokenParser;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.access-token-audience}") String accessTokenAudience,
            @Value("${jwt.refresh-token-audience}") String refreshTokenAudience) {
        String configuredSecret = requireConfigurationText(secret, "jwt.secret");
        this.secretKey = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = requirePositiveExpiration(
                accessTokenExpiration, "jwt.access-token-expiration");
        this.refreshTokenExpiration = requirePositiveExpiration(
                refreshTokenExpiration, "jwt.refresh-token-expiration");
        this.issuer = requireConfigurationText(issuer, "jwt.issuer");
        this.accessTokenAudience = requireConfigurationText(
                accessTokenAudience, "jwt.access-token-audience");
        this.refreshTokenAudience = requireConfigurationText(
                refreshTokenAudience, "jwt.refresh-token-audience");
        this.accessTokenParser = createParser(this.accessTokenAudience, JwtTokenType.ACCESS);
        this.refreshTokenParser = createParser(this.refreshTokenAudience, JwtTokenType.REFRESH);
    }

    public String createAccessToken(String email, String role, Long userId) {
        requireCreationText(email, "email");
        requireCreationText(role, ROLE_CLAIM);
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be a positive number");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return createTokenBuilder(
                email, accessTokenAudience, JwtTokenType.ACCESS, now, expiryDate)
                .claim(ROLE_CLAIM, role)
                .claim(USER_ID_CLAIM, userId)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(String email) {
        requireCreationText(email, "email");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return createTokenBuilder(
                email, refreshTokenAudience, JwtTokenType.REFRESH, now, expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseAccessClaims(token).getSubject();
    }

    /**
     * 기존 호출부 호환을 위한 Access Token 전용 검증 메서드입니다.
     */
    public boolean validateToken(String token) {
        return validateAccessToken(token);
    }

    public boolean validateAccessToken(String token) {
        try {
            parseAccessClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            parseAndValidateRefreshToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getEmailFromRefreshToken(String token) {
        return parseAndValidateRefreshToken(token).getEmail();
    }

    /**
     * Refresh JWT를 한 번 파싱·검증하고 불변 메타데이터를 반환합니다.
     * DB 세션의 활성 상태와 회전·재사용 여부는 Service에서 별도로 검증해야 합니다.
     */
    public ValidatedRefreshToken parseAndValidateRefreshToken(String token) {
        Claims claims = parseRefreshClaims(token);
        return new ValidatedRefreshToken(
                claims.getSubject(),
                claims.getId(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant());
    }

    public String getRoleFromToken(String token) {
        return getRequiredRole(parseAccessClaims(token));
    }

    public Long getUserIdFromToken(String token) {
        return getRequiredUserId(parseAccessClaims(token));
    }

    /**
     * 유효한 Access Token만 사용자 인증으로 변환합니다.
     * Refresh Token이나 유효하지 않은 토큰은 인증을 만들지 않습니다.
     */
    public Optional<Authentication> getAccessAuthentication(String token) {
        try {
            Claims claims = parseAccessClaims(token);
            return Optional.of(createAuthentication(claims));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Authentication getAuthentication(String token) {
        return createAuthentication(parseAccessClaims(token));
    }

    private Authentication createAuthentication(Claims claims) {
        String email = claims.getSubject();
        String role = getRequiredRole(claims);
        Long userId = getRequiredUserId(claims);

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role));

        UserDetailsImpl userDetails = new UserDetailsImpl(userId, email, authorities);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    private JwtBuilder createTokenBuilder(
            String email,
            String audience,
            JwtTokenType tokenType,
            Date issuedAt,
            Date expiresAt) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuer(issuer)
                .setAudience(audience)
                .setId(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt);
    }

    private JwtParser createParser(String audience, JwtTokenType tokenType) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .require(TOKEN_TYPE_CLAIM, tokenType.name())
                .build();
    }

    private Claims parseAccessClaims(String token) {
        Claims claims = parseClaims(accessTokenParser, token);
        validateAccessClaims(claims);
        return claims;
    }

    private Claims parseRefreshClaims(String token) {
        Claims claims = parseClaims(refreshTokenParser, token);
        validateRefreshClaims(claims);
        return claims;
    }

    private Claims parseClaims(JwtParser parser, String token) {
        Jws<Claims> parsedToken = parser.parseClaimsJws(token);
        if (!SignatureAlgorithm.HS256.getValue().equals(parsedToken.getHeader().getAlgorithm())) {
            throw new MalformedJwtException("Unsupported JWT signing algorithm");
        }

        Claims claims = parsedToken.getBody();
        validateCommonClaims(claims);
        return claims;
    }

    private void validateCommonClaims(Claims claims) {
        requireClaimText(claims.getSubject(), Claims.SUBJECT);
        String jwtId = requireClaimText(claims.getId(), Claims.ID);
        try {
            UUID parsedId = UUID.fromString(jwtId);
            if (!parsedId.toString().equalsIgnoreCase(jwtId)) {
                throw new MalformedJwtException("JWT jti must use the canonical UUID format");
            }
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("JWT jti must be a UUID", e);
        }

        Date issuedAt = claims.getIssuedAt();
        Date expiresAt = claims.getExpiration();
        if (issuedAt == null) {
            throw new MalformedJwtException("JWT iat claim is required");
        }
        if (expiresAt == null) {
            throw new MalformedJwtException("JWT exp claim is required");
        }
        if (!expiresAt.after(issuedAt)) {
            throw new MalformedJwtException("JWT exp must be after iat");
        }
        if (issuedAt.getTime() > System.currentTimeMillis() + ALLOWED_FUTURE_ISSUED_AT_MILLIS) {
            throw new MalformedJwtException("JWT iat claim is in the future");
        }
    }

    private void validateAccessClaims(Claims claims) {
        getRequiredRole(claims);
        getRequiredUserId(claims);
    }

    private void validateRefreshClaims(Claims claims) {
        if (claims.containsKey(ROLE_CLAIM) || claims.containsKey(USER_ID_CLAIM)) {
            throw new MalformedJwtException("Refresh Token must not contain user authority claims");
        }
    }

    private String getRequiredRole(Claims claims) {
        String role = requireClaimText(claims.get(ROLE_CLAIM, String.class), ROLE_CLAIM);
        try {
            Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("JWT role claim is not supported", e);
        }
        return role;
    }

    private Long getRequiredUserId(Claims claims) {
        Object value = claims.get(USER_ID_CLAIM);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new MalformedJwtException("JWT userId claim must be an integer");
        }

        long userId = ((Number) value).longValue();
        if (userId <= 0) {
            throw new MalformedJwtException("JWT userId claim must be a positive number");
        }
        return userId;
    }

    private String requireClaimText(String value, String claimName) {
        if (!StringUtils.hasText(value)) {
            throw new MalformedJwtException("JWT " + claimName + " claim is required");
        }
        return value;
    }

    private String requireConfigurationText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        return value;
    }

    private void requireCreationText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private long requirePositiveExpiration(long expiration, String propertyName) {
        if (expiration <= 0) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
        return expiration;
    }
}
