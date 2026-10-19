package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.config.jwt.RefreshTokenHasher;
import com.farmguardian.farmguardian.config.jwt.ValidatedRefreshToken;
import com.farmguardian.farmguardian.domain.RefreshSession;
import com.farmguardian.farmguardian.domain.RefreshTokenHistory;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.ClientUuidMismatchException;
import com.farmguardian.farmguardian.exception.auth.InvalidTokenException;
import com.farmguardian.farmguardian.repository.RefreshSessionRepository;
import com.farmguardian.farmguardian.repository.RefreshTokenHistoryRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("Auth Refresh Session 트랜잭션 통합 테스트")
class AuthRefreshSessionIntegrationTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private RefreshTokenHistoryRepository refreshTokenHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("로그인은 원문 대신 jti와 해시를 가진 활성 Session을 저장한다")
    void signInPersistsHashedRefreshSession() {
        Account account = register();

        TokenResponseDto response = signIn(account, "client-a");
        ValidatedRefreshToken validated = jwtTokenProvider.parseAndValidateRefreshToken(
                response.getRefreshToken());
        RefreshSession session = onlySession(account.userId());
        RefreshTokenHistory history = refreshTokenHistoryRepository
                .findByJtiAndTokenHash(
                        validated.getJti(),
                        refreshTokenHasher.hash(response.getRefreshToken()))
                .orElseThrow();

        assertAll(
                () -> assertThat(session.isActive()).isTrue(),
                () -> assertThat(session.getClientUuid()).isEqualTo("client-a"),
                () -> assertThat(session.getFamilyId()).hasSize(36),
                () -> assertThat(history.getRefreshSession().getId()).isEqualTo(session.getId()),
                () -> assertThat(history.getTokenHash()).hasSize(64),
                () -> assertThat(history.getTokenHash()).isNotEqualTo(response.getRefreshToken()),
                () -> assertThat(history.getIssuedAt()).isEqualTo(LocalDateTime.ofInstant(
                        validated.getIssuedAt(), ZoneOffset.UTC)),
                () -> assertThat(history.getExpiresAt()).isEqualTo(LocalDateTime.ofInstant(
                        validated.getExpiresAt(), ZoneOffset.UTC)),
                () -> assertThat(history.getExpiresAt()).isAfter(history.getIssuedAt())
        );
    }

    @Test
    @DisplayName("같은 client로 다시 로그인하면 이전 Session을 폐기하고 새 family를 만든다")
    void repeatedSignInRevokesPreviousSession() {
        Account account = register();
        TokenResponseDto first = signIn(account, "client-repeat");

        TokenResponseDto second = signIn(account, "client-repeat");
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(account.userId());

        assertAll(
                () -> assertThat(second.getRefreshToken()).isNotEqualTo(first.getRefreshToken()),
                () -> assertThat(sessions).hasSize(2),
                () -> assertThat(sessions).filteredOn(RefreshSession::isActive).hasSize(1),
                () -> assertThat(sessions).filteredOn(session -> !session.isActive()).hasSize(1),
                () -> assertThat(sessions).extracting(RefreshSession::getFamilyId)
                        .doesNotHaveDuplicates(),
                () -> assertThatThrownBy(() -> authService.refreshAccessToken(
                        first.getRefreshToken(), "client-repeat"))
                        .isInstanceOf(InvalidTokenException.class),
                () -> assertThat(authService.refreshAccessToken(
                        second.getRefreshToken(), "client-repeat").getRefreshToken())
                        .isNotBlank()
        );
    }

    @Test
    @DisplayName("정상 재발급은 현재 세대를 회전시키고 새 세대를 저장한다")
    void refreshRotatesHistory() {
        Account account = register();
        TokenResponseDto issued = signIn(account, "client-rotate");
        ValidatedRefreshToken oldClaims = jwtTokenProvider.parseAndValidateRefreshToken(
                issued.getRefreshToken());

        TokenResponseDto rotated = authService.refreshAccessToken(
                issued.getRefreshToken(), "client-rotate");
        ValidatedRefreshToken newClaims = jwtTokenProvider.parseAndValidateRefreshToken(
                rotated.getRefreshToken());
        RefreshSession session = onlySession(account.userId());
        List<RefreshTokenHistory> histories = refreshTokenHistoryRepository
                .findAllByRefreshSessionIdOrderByIssuedAtAsc(session.getId());

        assertAll(
                () -> assertThat(jwtTokenProvider.validateAccessToken(rotated.getAccessToken())).isTrue(),
                () -> assertThat(histories).hasSize(2),
                () -> assertThat(histories).filteredOn(history -> history.getJti().equals(oldClaims.getJti()))
                        .singleElement()
                        .satisfies(history -> {
                            assertThat(history.isCurrent()).isFalse();
                            assertThat(history.getReplacedByJti()).isEqualTo(newClaims.getJti());
                            assertThat(history.getRotatedAt()).isNotNull();
                        }),
                () -> assertThat(histories).filteredOn(history -> history.getJti().equals(newClaims.getJti()))
                        .singleElement()
                        .satisfies(history -> assertThat(history.isCurrent()).isTrue()),
                () -> assertThat(session.isActive()).isTrue()
        );
    }

    @Test
    @DisplayName("다른 Refresh Token으로는 같은 user와 client의 Session을 로그아웃할 수 없다")
    void signOutRejectsDifferentRefreshToken() {
        Account account = register();
        TokenResponseDto issued = signIn(account, "client-signout-mismatch");
        String differentToken = jwtTokenProvider.createRefreshToken(account.email());

        assertThatThrownBy(() -> authService.signOut(
                account.userId(), differentToken, "client-signout-mismatch"))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(onlySession(account.userId()).isActive()).isTrue();
        assertThat(authService.refreshAccessToken(
                issued.getRefreshToken(), "client-signout-mismatch").getRefreshToken())
                .isNotBlank();
    }

    @Test
    @DisplayName("잘못된 clientUuid 재발급 요청은 Session을 훼손하지 않는다")
    void refreshRejectsWrongClientWithoutRevokingSession() {
        Account account = register();
        TokenResponseDto issued = signIn(account, "client-correct");

        assertThatThrownBy(() -> authService.refreshAccessToken(
                issued.getRefreshToken(), "client-wrong"))
                .isInstanceOf(ClientUuidMismatchException.class);
        assertThat(authService.refreshAccessToken(
                issued.getRefreshToken(), "client-correct").getRefreshToken())
                .isNotBlank();
    }

    @Test
    @DisplayName("다른 사용자의 Principal은 피해자 Refresh Session을 로그아웃할 수 없다")
    void signOutRejectsAnotherUsersPrincipalWithoutRevokingVictim() {
        Account attacker = register();
        Account victim = register();
        TokenResponseDto victimToken = signIn(victim, "client-victim");

        assertThatThrownBy(() -> authService.signOut(
                attacker.userId(), victimToken.getRefreshToken(), "client-victim"))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(authService.refreshAccessToken(
                victimToken.getRefreshToken(), "client-victim").getRefreshToken())
                .isNotBlank();
    }

    @Test
    @DisplayName("올바른 Refresh Token 로그아웃은 해당 Session만 폐기한다")
    void signOutRevokesOnlyPresentedSession() {
        Account account = register();
        TokenResponseDto firstClient = signIn(account, "client-signout-a");
        TokenResponseDto secondClient = signIn(account, "client-signout-b");

        authService.signOut(account.userId(), firstClient.getRefreshToken(), "client-signout-a");

        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(account.userId());
        assertAll(
                () -> assertThat(sessions).filteredOn(
                        session -> session.getClientUuid().equals("client-signout-a"))
                        .singleElement().satisfies(session -> assertThat(session.isActive()).isFalse()),
                () -> assertThat(sessions).filteredOn(
                        session -> session.getClientUuid().equals("client-signout-b"))
                        .singleElement().satisfies(session -> assertThat(session.isActive()).isTrue()),
                () -> assertThatThrownBy(() -> authService.refreshAccessToken(
                        firstClient.getRefreshToken(), "client-signout-a"))
                        .isInstanceOf(InvalidTokenException.class),
                () -> assertThat(authService.refreshAccessToken(
                        secondClient.getRefreshToken(), "client-signout-b").getRefreshToken())
                        .isNotBlank()
        );
    }

    @Test
    @DisplayName("회전된 Token 재사용은 실패 응답과 별개로 family 폐기를 커밋한다")
    void reusedRefreshTokenRevocationCommitsBeforeFailureResponse() {
        Account account = register();
        TokenResponseDto first = signIn(account, "client-reuse");
        ValidatedRefreshToken firstClaims = jwtTokenProvider.parseAndValidateRefreshToken(
                first.getRefreshToken());
        TokenResponseDto second = authService.refreshAccessToken(
                first.getRefreshToken(), "client-reuse");

        assertThatThrownBy(() -> authService.refreshAccessToken(
                first.getRefreshToken(), "client-reuse"))
                .isInstanceOf(InvalidTokenException.class);

        RefreshSession session = onlySession(account.userId());
        RefreshTokenHistory reusedHistory = refreshTokenHistoryRepository
                .findByJtiAndTokenHash(
                        firstClaims.getJti(),
                        refreshTokenHasher.hash(first.getRefreshToken()))
                .orElseThrow();

        assertAll(
                () -> assertThat(session.isActive()).isFalse(),
                () -> assertThat(session.getReuseDetectedAt()).isNotNull(),
                () -> assertThat(reusedHistory.getReuseDetectedAt()).isNotNull(),
                () -> assertThatThrownBy(() -> authService.refreshAccessToken(
                        second.getRefreshToken(), "client-reuse"))
                        .isInstanceOf(InvalidTokenException.class)
        );
    }

    @Test
    @DisplayName("회원탈퇴는 사용자의 모든 활성 Refresh Session을 폐기한다")
    void withdrawRevokesAllRefreshSessions() {
        Account account = register();
        signIn(account, "client-withdraw-a");
        signIn(account, "client-withdraw-b");

        authService.withdraw(account.userId());

        assertAll(
                () -> assertThat(userRepository.findById(account.userId())).isEmpty(),
                () -> assertThat(refreshSessionRepository.findAllByUserId(account.userId()))
                        .allSatisfy(session -> assertThat(session.isActive()).isFalse())
        );
    }

    private Account register() {
        String email = "refresh-" + UUID.randomUUID() + "@farmguardian.test";
        SignUpRequestDto request = new SignUpRequestDto();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return new Account(email, authService.signUp(request));
    }

    private TokenResponseDto signIn(Account account, String clientUuid) {
        SignInRequestDto request = new SignInRequestDto();
        request.setEmail(account.email());
        request.setPassword(PASSWORD);
        request.setClientUuid(clientUuid);
        return authService.signIn(request);
    }

    private RefreshSession onlySession(Long userId) {
        return refreshSessionRepository.findAllByUserId(userId)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private record Account(String email, Long userId) {
    }
}
