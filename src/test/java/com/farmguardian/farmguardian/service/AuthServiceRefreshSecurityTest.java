package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.config.jwt.RefreshTokenHasher;
import com.farmguardian.farmguardian.config.jwt.ValidatedRefreshToken;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.InvalidTokenException;
import com.farmguardian.farmguardian.repository.DeviceRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AuthService Refresh Token 보안 경계 테스트")
class AuthServiceRefreshSecurityTest {

    private static final String TEST_SECRET =
            "VGVzdEp3dFNlY3JldEtleUZvckZhcm1HdWFyZGlhblRlc3RTdWl0ZTAwMDAwMDAw";
    private static final String EMAIL = "refresh-user@farmguardian.test";
    private static final String CLIENT_UUID = "refresh-security-client";
    private static final Long USER_ID = 42L;

    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenHasher refreshTokenHasher;
    private RefreshSessionTransactionService transactionService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        transactionService = mock(RefreshSessionTransactionService.class);
        refreshTokenHasher = new RefreshTokenHasher();
        jwtTokenProvider = new JwtTokenProvider(
                TEST_SECRET,
                1_800_000L,
                604_800_000L,
                "farmguardian-test",
                "farmguardian-api-test",
                "farmguardian-refresh-test");
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenHasher,
                deviceRepository,
                transactionService);
    }

    @Test
    @DisplayName("Access Token은 DB 조회 전에 Refresh 요청에서 거부한다")
    void rejectsAccessTokenAtRefreshBoundary() {
        String accessToken = jwtTokenProvider.createAccessToken(EMAIL, "USER", USER_ID);

        assertThatThrownBy(() -> authService.refreshAccessToken(accessToken, CLIENT_UUID))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("유효한 Refresh Token의 검증 결과와 해시를 트랜잭션 서비스에 한 번 전달한다")
    void passesValidatedRefreshMetadataToRotationTransaction() {
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);
        TokenResponseDto expected = new TokenResponseDto(
                "Bearer",
                jwtTokenProvider.createAccessToken(EMAIL, "USER", USER_ID),
                jwtTokenProvider.createRefreshToken(EMAIL));
        when(transactionService.rotate(any(ValidatedRefreshToken.class), anyString(), eq(CLIENT_UUID)))
                .thenReturn(RefreshTokenRotationResult.issued(expected));

        TokenResponseDto actual = authService.refreshAccessToken(refreshToken, CLIENT_UUID);

        ArgumentCaptor<ValidatedRefreshToken> claimsCaptor =
                ArgumentCaptor.forClass(ValidatedRefreshToken.class);
        verify(transactionService).rotate(
                claimsCaptor.capture(),
                eq(refreshTokenHasher.hash(refreshToken)),
                eq(CLIENT_UUID));
        assertThat(actual).isSameAs(expected);
        assertThat(claimsCaptor.getValue().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("로그아웃은 검증된 제출 Token이 저장 세션과 다르면 실패를 전파한다")
    void signOutPropagatesStoredSessionMismatch() {
        String presentedRefreshToken = jwtTokenProvider.createRefreshToken(EMAIL);
        doThrow(new InvalidTokenException()).when(transactionService).signOut(
                eq(USER_ID),
                any(ValidatedRefreshToken.class),
                eq(refreshTokenHasher.hash(presentedRefreshToken)),
                eq(CLIENT_UUID));

        assertThatThrownBy(() -> authService.signOut(
                USER_ID, presentedRefreshToken, CLIENT_UUID))
                .isInstanceOf(InvalidTokenException.class);
        verify(transactionService).signOut(
                eq(USER_ID),
                any(ValidatedRefreshToken.class),
                eq(refreshTokenHasher.hash(presentedRefreshToken)),
                eq(CLIENT_UUID));
    }
}
