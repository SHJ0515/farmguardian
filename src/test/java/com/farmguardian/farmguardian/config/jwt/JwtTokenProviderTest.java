package com.farmguardian.farmguardian.config.jwt;

import com.farmguardian.farmguardian.config.auth.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringJUnitConfig(JwtTokenProviderTest.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=VGVzdEp3dFNlY3JldEtleUZvckZhcm1HdWFyZGlhblRlc3RTdWl0ZTAwMDAwMDAw",
        "jwt.access-token-expiration=1800000",
        "jwt.refresh-token-expiration=604800000",
        "jwt.issuer=farmguardian-test",
        "jwt.access-token-audience=farmguardian-api-test",
        "jwt.refresh-token-audience=farmguardian-refresh-test"
})
@DisplayName("JwtTokenProvider 보안 경계 테스트")
class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "VGVzdEp3dFNlY3JldEtleUZvckZhcm1HdWFyZGlhblRlc3RTdWl0ZTAwMDAwMDAw";
    private static final String OTHER_SECRET =
            "QW5vdGhlclRlc3RLZXlGb3JKd3RTaWduYXR1cmVWZXJpZmljYXRpb24wMDAwMDAw";
    private static final String ISSUER = "farmguardian-test";
    private static final String ACCESS_AUDIENCE = "farmguardian-api-test";
    private static final String REFRESH_AUDIENCE = "farmguardian-refresh-test";
    private static final String EMAIL = "user@farmguardian.test";
    private static final String ROLE = "USER";
    private static final Long USER_ID = 42L;
    private static final long ACCESS_TOKEN_EXPIRATION = 1_800_000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604_800_000L;

    private static final Key SIGNING_KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Test
    @DisplayName("Access Token은 용도와 신뢰 경계를 나타내는 필수 Claim을 포함한다")
    void createAccessTokenContainsRequiredBoundaryClaims() {
        String token = jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID);

        Claims claims = parseClaims(token, SIGNING_KEY);

        assertAll(
                () -> assertThat(claims.getSubject()).isEqualTo(EMAIL),
                () -> assertThat(claims.getIssuer()).isEqualTo(ISSUER),
                () -> assertThat(claims.getAudience()).isEqualTo(ACCESS_AUDIENCE),
                () -> assertThat(claims.getId()).isNotBlank(),
                () -> assertThatCodeIsUuid(claims.getId()),
                () -> assertThat(claims.get("token_type", String.class)).isEqualTo("ACCESS"),
                () -> assertThat(claims.getIssuedAt()).isNotNull(),
                () -> assertThat(claims.getExpiration()).isNotNull(),
                () -> assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                        .isEqualTo(ACCESS_TOKEN_EXPIRATION),
                () -> assertThat(claims.get("role", String.class)).isEqualTo(ROLE),
                () -> assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(USER_ID)
        );
    }

    @Test
    @DisplayName("Refresh Token은 별도 용도를 표시하고 사용자 권한 Claim을 포함하지 않는다")
    void createRefreshTokenContainsRequiredBoundaryClaims() {
        String token = jwtTokenProvider.createRefreshToken(EMAIL);

        Claims claims = parseClaims(token, SIGNING_KEY);

        assertAll(
                () -> assertThat(claims.getSubject()).isEqualTo(EMAIL),
                () -> assertThat(claims.getIssuer()).isEqualTo(ISSUER),
                () -> assertThat(claims.getAudience()).isEqualTo(REFRESH_AUDIENCE),
                () -> assertThat(claims.getId()).isNotBlank(),
                () -> assertThatCodeIsUuid(claims.getId()),
                () -> assertThat(claims.get("token_type", String.class)).isEqualTo("REFRESH"),
                () -> assertThat(claims.getIssuedAt()).isNotNull(),
                () -> assertThat(claims.getExpiration()).isNotNull(),
                () -> assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                        .isEqualTo(REFRESH_TOKEN_EXPIRATION),
                () -> assertThat(claims).doesNotContainKeys("role", "userId")
        );
    }

    @Test
    @DisplayName("발급되는 JWT마다 서로 다른 jti를 사용한다")
    void issuedTokensHaveUniqueJwtIds() {
        Claims firstAccessClaims = parseClaims(
                jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID), SIGNING_KEY);
        Claims secondAccessClaims = parseClaims(
                jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID), SIGNING_KEY);
        Claims refreshClaims = parseClaims(jwtTokenProvider.createRefreshToken(EMAIL), SIGNING_KEY);

        assertAll(
                () -> assertThat(firstAccessClaims.getId()).isNotBlank(),
                () -> assertThat(secondAccessClaims.getId()).isNotBlank(),
                () -> assertThat(refreshClaims.getId()).isNotBlank(),
                () -> assertThat(firstAccessClaims.getId()).isNotEqualTo(secondAccessClaims.getId()),
                () -> assertThat(firstAccessClaims.getId()).isNotEqualTo(refreshClaims.getId()),
                () -> assertThat(secondAccessClaims.getId()).isNotEqualTo(refreshClaims.getId())
        );
    }

    @Test
    @DisplayName("Access 검증은 Refresh Token을 거부한다")
    void validateTokenRejectsRefreshToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);

        assertThat(jwtTokenProvider.validateToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("Refresh 검증은 Refresh Token만 허용한다")
    void validateRefreshTokenAcceptsOnlyRefreshToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);
        String accessToken = jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID);

        assertAll(
                () -> assertThat(jwtTokenProvider.validateRefreshToken(refreshToken)).isTrue(),
                () -> assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse(),
                () -> assertRefreshResultRejected(accessToken)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRefreshTokens")
    @DisplayName("잘못된 Refresh 입력은 boolean 검증에서 false, 파싱 API에서 예외를 반환한다")
    void rejectInvalidRefreshTokens(String scenario, String token, Class<? extends RuntimeException> expectedException) {
        assertAll(
                () -> assertThat(jwtTokenProvider.validateRefreshToken(token)).isFalse(),
                () -> assertThatThrownBy(() -> jwtTokenProvider.parseAndValidateRefreshToken(token))
                        .isInstanceOf(expectedException)
        );
    }

    @Test
    @DisplayName("Refresh Token에서만 이메일을 추출한다")
    void getEmailFromRefreshTokenAcceptsOnlyRefreshToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);
        String accessToken = jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID);

        assertAll(
                () -> assertThat(jwtTokenProvider.getEmailFromRefreshToken(refreshToken)).isEqualTo(EMAIL),
                () -> assertThatThrownBy(() -> jwtTokenProvider.getEmailFromRefreshToken(accessToken))
                        .isInstanceOf(JwtException.class)
        );
    }

    @Test
    @DisplayName("검증된 Refresh 결과는 이메일과 jti 및 JWT의 실제 발급·만료 시간을 반환한다")
    void parseAndValidateRefreshTokenReturnsImmutableMetadata() {
        String token = jwtTokenProvider.createRefreshToken(EMAIL);
        Claims claims = parseClaims(token, SIGNING_KEY);

        ValidatedRefreshToken result = jwtTokenProvider.parseAndValidateRefreshToken(token);

        assertAll(
                () -> assertThat(result.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(result.getJti()).isEqualTo(claims.getId()),
                () -> assertThat(result.getIssuedAt()).isEqualTo(claims.getIssuedAt().toInstant()),
                () -> assertThat(result.getExpiresAt()).isEqualTo(claims.getExpiration().toInstant()),
                () -> assertThat(result.getExpiresAt()).isAfter(result.getIssuedAt())
        );
    }

    @Test
    @DisplayName("Access와 Refresh 검증은 DB에 저장할 수 없는 축약 UUID jti를 거부한다")
    void tokenValidationRejectsNonCanonicalUuid() {
        String shortRefreshId = createRefreshCandidate(builder -> builder.setId("1-1-1-1-1"));
        String shortAccessId = createAccessCandidate(builder -> builder.setId("1-1-1-1-1"));

        assertAll(
                () -> assertThat(jwtTokenProvider.validateRefreshToken(shortRefreshId)).isFalse(),
                () -> assertThat(jwtTokenProvider.validateAccessToken(shortAccessId)).isFalse(),
                () -> assertRefreshResultRejected(shortRefreshId)
        );
    }

    @Test
    @DisplayName("표준 UUID jti의 대소문자를 바꾸거나 정규화하지 않고 원문 Claim을 반환한다")
    void parseAndValidateRefreshTokenPreservesCanonicalUuid() {
        String jti = "ABCDEFAB-1234-5678-9ABC-ABCDEFABCDEF";
        String token = createRefreshCandidate(builder -> builder.setId(jti));

        assertThat(jwtTokenProvider.parseAndValidateRefreshToken(token).getJti()).isEqualTo(jti);
    }

    @Test
    @DisplayName("실제 발급한 Refresh Token의 해시는 고정 길이이고 서로 다른 토큰마다 다르다")
    void issuedRefreshTokensProduceDistinctStorageHashes() {
        String firstToken = jwtTokenProvider.createRefreshToken(EMAIL);
        String secondToken = jwtTokenProvider.createRefreshToken(EMAIL);

        assertAll(
                () -> assertThat(refreshTokenHasher.hash(firstToken)).matches("[0-9a-f]{64}"),
                () -> assertThat(refreshTokenHasher.hash(firstToken))
                        .isEqualTo(refreshTokenHasher.hash(firstToken)),
                () -> assertThat(refreshTokenHasher.hash(firstToken))
                        .isNotEqualTo(refreshTokenHasher.hash(secondToken)),
                () -> assertThat(jwtTokenProvider.parseAndValidateRefreshToken(firstToken).getJti())
                        .isNotEqualTo(jwtTokenProvider.parseAndValidateRefreshToken(secondToken).getJti())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAccessTokens")
    @DisplayName("Access 검증은 신뢰 경계·필수 Claim·서명·유효기간 위반을 거부한다")
    void rejectInvalidAccessTokens(String scenario, String token) {
        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Access Token으로 올바른 사용자 Authentication을 생성한다")
    void getAuthenticationCreatesAuthenticatedUserFromAccessToken() {
        String accessToken = jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID);

        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        assertAll(
                () -> assertThat(authentication.isAuthenticated()).isTrue(),
                () -> assertThat(authentication.getPrincipal()).isInstanceOf(UserDetailsImpl.class),
                () -> assertThat(((UserDetailsImpl) authentication.getPrincipal()).getUserId())
                        .isEqualTo(USER_ID),
                () -> assertThat(authentication.getName()).isEqualTo(EMAIL),
                () -> assertThat(authentication.getAuthorities())
                        .extracting("authority")
                        .containsExactly("ROLE_" + ROLE)
        );
    }

    @Test
    @DisplayName("Refresh Token으로 사용자 Authentication을 생성할 수 없다")
    void getAuthenticationRejectsRefreshToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Access 인증 조회는 유효한 Access Token만 Authentication으로 반환한다")
    void getAccessAuthenticationAcceptsOnlyValidAccessToken() {
        String accessToken = jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID);
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);
        Date now = new Date();
        String expiredAccessToken = createAccessCandidate(builder -> builder
                .setIssuedAt(new Date(now.getTime() - 120_000L))
                .setExpiration(new Date(now.getTime() - 60_000L)));
        String wrongIssuerAccessToken = createAccessCandidate(
                builder -> builder.setIssuer("another-issuer"));

        Authentication authentication = jwtTokenProvider.getAccessAuthentication(accessToken)
                .orElseThrow();

        assertAll(
                () -> assertThat(authentication.isAuthenticated()).isTrue(),
                () -> assertThat(authentication.getName()).isEqualTo(EMAIL),
                () -> assertThat(jwtTokenProvider.getAccessAuthentication(refreshToken)).isEmpty(),
                () -> assertThat(jwtTokenProvider.getAccessAuthentication("not-a-jwt")).isEmpty(),
                () -> assertThat(jwtTokenProvider.getAccessAuthentication(expiredAccessToken)).isEmpty(),
                () -> assertThat(jwtTokenProvider.getAccessAuthentication(wrongIssuerAccessToken)).isEmpty()
        );
    }

    private static Stream<Arguments> invalidRefreshTokens() {
        Date futureIssuedAt = new Date(System.currentTimeMillis() + 300_000L);
        Date futureExpiration = new Date(futureIssuedAt.getTime() + REFRESH_TOKEN_EXPIRATION);
        Key otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));

        return Stream.of(
                Arguments.of("Access Token 제출", createAccessCandidate(builder -> builder), JwtException.class),
                invalidRefresh("issuer 불일치", builder -> builder.setIssuer("untrusted-service")),
                invalidRefresh("audience 불일치", builder -> builder.setAudience("untrusted-client")),
                invalidRefresh("token_type 불일치", builder -> builder.claim("token_type", "ACCESS")),
                invalidRefresh("token_type 누락", builder -> builder.claim("token_type", null)),
                invalidRefresh("subject 누락", builder -> builder.setSubject(null)),
                invalidRefresh("jti 누락", builder -> builder.setId(null)),
                invalidRefresh("UUID 형식이 아닌 jti", builder -> builder.setId("not-a-uuid")),
                invalidRefresh("iat 누락", builder -> builder.setIssuedAt(null)),
                invalidRefresh("exp 누락", builder -> builder.setExpiration(null)),
                invalidRefresh("Refresh에 role 포함", builder -> builder.claim("role", ROLE)),
                invalidRefresh("Refresh에 userId 포함", builder -> builder.claim("userId", USER_ID)),
                invalidRefresh("미래 발급 시각", builder -> builder
                        .setIssuedAt(futureIssuedAt).setExpiration(futureExpiration)),
                invalidRefresh("발급보다 이른 만료 시각", builder -> builder
                        .setIssuedAt(futureExpiration).setExpiration(futureIssuedAt)),
                invalidRefresh("동일한 발급·만료 시각", builder -> builder
                        .setIssuedAt(futureExpiration).setExpiration(futureExpiration)),
                invalidRefresh("만료된 토큰", JwtTokenProviderTest::makeExpired),
                Arguments.of("다른 키의 서명", validRefreshTokenBuilder()
                        .signWith(otherKey, SignatureAlgorithm.HS256).compact(), JwtException.class),
                Arguments.of("비허용 서명 알고리즘", validRefreshTokenBuilder()
                        .signWith(SIGNING_KEY, SignatureAlgorithm.HS512).compact(), JwtException.class),
                Arguments.of("null 입력", null, IllegalArgumentException.class),
                Arguments.of("공백 입력", " ", IllegalArgumentException.class),
                Arguments.of("JWT 형식이 아닌 문자열", "not-a-jwt", JwtException.class)
        );
    }

    private static Stream<Arguments> invalidAccessTokens() {
        Date futureIssuedAt = new Date(System.currentTimeMillis() + 300_000L);
        Date futureExpiration = new Date(futureIssuedAt.getTime() + ACCESS_TOKEN_EXPIRATION);
        Key otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));

        return Stream.of(
                invalidAccess("issuer 불일치", builder -> builder.setIssuer("untrusted-service")),
                invalidAccess("audience 불일치", builder -> builder.setAudience("untrusted-client")),
                invalidAccess("token_type 불일치", builder -> builder.claim("token_type", "REFRESH")),
                invalidAccess("subject 누락", builder -> builder.setSubject(null)),
                invalidAccess("jti 누락", builder -> builder.setId(null)),
                invalidAccess("iat 누락", builder -> builder.setIssuedAt(null)),
                invalidAccess("exp 누락", builder -> builder.setExpiration(null)),
                invalidAccess("role 누락", builder -> builder.claim("role", null)),
                invalidAccess("userId 누락", builder -> builder.claim("userId", null)),
                invalidAccess("UUID 형식이 아닌 jti", builder -> builder.setId("not-a-uuid")),
                invalidAccess("미래 발급 시각", builder -> builder
                        .setIssuedAt(futureIssuedAt).setExpiration(futureExpiration)),
                invalidAccess("지원하지 않는 role", builder -> builder.claim("role", "SUPER_ADMIN")),
                invalidAccess("정수가 아닌 userId", builder -> builder.claim("userId", 1.5D)),
                Arguments.of("비허용 서명 알고리즘", validAccessTokenBuilder()
                        .signWith(SIGNING_KEY, SignatureAlgorithm.HS512).compact()),
                invalidAccess("만료된 토큰", JwtTokenProviderTest::makeExpired),
                Arguments.of("다른 키의 서명", validAccessTokenBuilder()
                        .signWith(otherKey, SignatureAlgorithm.HS256).compact())
        );
    }

    private static Arguments invalidRefresh(String scenario, UnaryOperator<JwtBuilder> customizer) {
        return Arguments.of(scenario, createRefreshCandidate(customizer), JwtException.class);
    }

    private static Arguments invalidAccess(String scenario, UnaryOperator<JwtBuilder> customizer) {
        return Arguments.of(scenario, createAccessCandidate(customizer));
    }

    private void assertRefreshResultRejected(String token) {
        assertThatThrownBy(() -> jwtTokenProvider.parseAndValidateRefreshToken(token))
                .isInstanceOf(JwtException.class);
    }

    private static String createAccessCandidate(UnaryOperator<JwtBuilder> customizer) {
        return customizer.apply(validAccessTokenBuilder())
                .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    private static String createRefreshCandidate(UnaryOperator<JwtBuilder> customizer) {
        return customizer.apply(validRefreshTokenBuilder())
                .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    private static JwtBuilder validAccessTokenBuilder() {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(EMAIL)
                .setIssuer(ISSUER)
                .setAudience(ACCESS_AUDIENCE)
                .setId(UUID.randomUUID().toString())
                .claim("token_type", "ACCESS")
                .claim("role", ROLE)
                .claim("userId", USER_ID)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION));
    }

    private static JwtBuilder validRefreshTokenBuilder() {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(EMAIL)
                .setIssuer(ISSUER)
                .setAudience(REFRESH_AUDIENCE)
                .setId(UUID.randomUUID().toString())
                .claim("token_type", "REFRESH")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRATION));
    }

    private static JwtBuilder makeExpired(JwtBuilder builder) {
        Date expiredAt = new Date(System.currentTimeMillis() - 60_000L);
        Date issuedAt = new Date(expiredAt.getTime() - 60_000L);
        return builder
                .setIssuedAt(issuedAt)
                .setExpiration(expiredAt);
    }

    private Claims parseClaims(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({JwtTokenProvider.class, RefreshTokenHasher.class})
    static class TestConfig {
    }
}
