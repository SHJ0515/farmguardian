package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.config.jwt.RefreshTokenHasher;
import com.farmguardian.farmguardian.config.jwt.ValidatedRefreshToken;
import com.farmguardian.farmguardian.domain.RefreshSession;
import com.farmguardian.farmguardian.domain.RefreshTokenHistory;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.InvalidTokenException;
import com.farmguardian.farmguardian.repository.RefreshSessionRepository;
import com.farmguardian.farmguardian.repository.RefreshTokenHistoryRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:farmguardian-refresh-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=5000",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Timeout(30)
@DisplayName("Refresh Session Flyway 기반 동시성 통합 테스트")
class AuthRefreshConcurrencyIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final int REPETITIONS = 3;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @RepeatedTest(REPETITIONS)
    @DisplayName("같은 Refresh Token의 동시 사용은 한 번 발급하고 family를 폐기한다")
    void sameTokenConcurrentRefreshRevokesFamily() throws Exception {
        Account account = register();
        String clientUuid = "same-token-client";
        TokenResponseDto first = signIn(account, clientUuid);

        List<Outcome> outcomes = race(
                new Operation("refresh-a", () -> authService.refreshAccessToken(first.getRefreshToken(), clientUuid)),
                new Operation("refresh-b", () -> authService.refreshAccessToken(first.getRefreshToken(), clientUuid)));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement()
                .satisfies(outcome -> assertThat(outcome.failure()).isInstanceOf(InvalidTokenException.class));
        RefreshSession session = onlySession(account.userId());
        List<RefreshTokenHistory> histories = histories(session);
        assertThat(session.isActive()).isFalse();
        assertThat(session.getReuseDetectedAt()).isNotNull();
        assertThat(histories).hasSize(2);
        assertThat(histories).filteredOn(RefreshTokenHistory::isCurrent).hasSize(1);
        RefreshTokenHistory original = history(first.getRefreshToken());
        assertThat(original.isCurrent()).isFalse();
        assertThat(original.getReuseDetectedAt()).isEqualTo(session.getReuseDetectedAt());
        TokenResponseDto winner = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().tokens();
        assertRefreshRejected(winner.getRefreshToken(), clientUuid);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("같은 client 동시 로그인은 두 번 성공해도 활성 Session은 하나만 남긴다")
    void sameClientConcurrentSignInLeavesOneActiveSession() throws Exception {
        Account account = register();
        String clientUuid = "same-signin-client";

        List<Outcome> outcomes = race(
                new Operation("signin-a", () -> signIn(account, clientUuid)),
                new Operation("signin-b", () -> signIn(account, clientUuid)));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(account.userId());
        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(RefreshSession::getFamilyId).doesNotHaveDuplicates();
        RefreshSession active = sessions.stream().filter(RefreshSession::isActive).findFirst().orElseThrow();
        assertThat(sessions).filteredOn(RefreshSession::isActive).hasSize(1);
        assertThat(sessions).filteredOn(session -> !session.isActive()).singleElement()
                .satisfies(session -> assertThat(session.getRevokedAt()).isNotNull());
        String activeJti = histories(active).getFirst().getJti();
        TokenResponseDto activeTokens = outcomes.stream().map(Outcome::tokens)
                .filter(tokens -> claims(tokens.getRefreshToken()).getJti().equals(activeJti))
                .findFirst().orElseThrow();
        TokenResponseDto superseded = outcomes.stream().map(Outcome::tokens)
                .filter(tokens -> !claims(tokens.getRefreshToken()).getJti().equals(activeJti))
                .findFirst().orElseThrow();
        assertRefreshRejected(superseded.getRefreshToken(), clientUuid);
        assertThat(authService.refreshAccessToken(activeTokens.getRefreshToken(), clientUuid).getRefreshToken()).isNotBlank();
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("재발급과 로그아웃 경쟁은 DB에서 먼저 처리된 현재 Token을 기준으로 결정된다")
    void refreshAndSignOutRespectSerializedTokenState() throws Exception {
        Account account = register();
        String clientUuid = "refresh-signout-client";
        TokenResponseDto first = signIn(account, clientUuid);

        List<Outcome> outcomes = race(
                new Operation("refresh", () -> authService.refreshAccessToken(first.getRefreshToken(), clientUuid)),
                new Operation("signout", () -> {
                    authService.signOut(account.userId(), first.getRefreshToken(), clientUuid);
                    return null;
                }));

        Outcome refresh = outcome(outcomes, "refresh");
        Outcome signOut = outcome(outcomes, "signout");
        RefreshSession session = onlySession(account.userId());
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        if (signOut.succeeded()) {
            assertThat(refresh.failure()).isInstanceOf(InvalidTokenException.class);
            assertThat(session.isActive()).isFalse();
            assertThat(histories(session)).hasSize(1);
        } else {
            assertThat(refresh.failure()).isNull();
            assertThat(signOut.failure()).isInstanceOf(InvalidTokenException.class);
            assertThat(session.isActive()).isTrue();
            assertThat(histories(session)).hasSize(2);
            assertThat(authService.refreshAccessToken(refresh.tokens().getRefreshToken(), clientUuid).getRefreshToken()).isNotBlank();
        }
        assertThat(session.getReuseDetectedAt()).isNull();
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("재발급과 회원탈퇴 경쟁은 어떤 순서든 모든 Refresh Session을 무효화한다")
    void refreshAndWithdrawCannotLeaveUsableSession() throws Exception {
        Account account = register();
        String clientUuid = "refresh-withdraw-client";
        TokenResponseDto first = signIn(account, clientUuid);
        TokenResponseDto otherClient = signIn(account, "withdraw-other-client");

        List<Outcome> outcomes = race(
                new Operation("refresh", () -> authService.refreshAccessToken(first.getRefreshToken(), clientUuid)),
                new Operation("withdraw", () -> {
                    authService.withdraw(account.userId());
                    return null;
                }));

        Outcome refresh = outcome(outcomes, "refresh");
        assertThat(outcome(outcomes, "withdraw").failure()).isNull();
        assertThat(userRepository.findById(account.userId())).isEmpty();
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(account.userId());
        assertThat(sessions).hasSize(2).allSatisfy(session -> {
            assertThat(session.isActive()).isFalse();
            assertThat(session.getRevokedAt()).isNotNull();
        });
        if (refresh.succeeded()) {
            assertRefreshRejected(refresh.tokens().getRefreshToken(), clientUuid);
        } else {
            assertThat(refresh.failure()).isInstanceOf(InvalidTokenException.class);
        }
        assertRefreshRejected(first.getRefreshToken(), clientUuid);
        assertRefreshRejected(otherClient.getRefreshToken(), "withdraw-other-client");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("서로 다른 client의 동시 재발급은 각 family를 유지하며 모두 성공한다")
    void distinctClientsConcurrentRefreshStayIsolated() throws Exception {
        Account account = register();
        TokenResponseDto first = signIn(account, "independent-a");
        TokenResponseDto second = signIn(account, "independent-b");

        List<Outcome> outcomes = race(
                new Operation("a", () -> authService.refreshAccessToken(first.getRefreshToken(), "independent-a")),
                new Operation("b", () -> authService.refreshAccessToken(second.getRefreshToken(), "independent-b")));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(account.userId());
        assertThat(sessions).hasSize(2).allSatisfy(session -> {
            assertThat(session.isActive()).isTrue();
            assertThat(session.getReuseDetectedAt()).isNull();
            assertThat(histories(session)).hasSize(2).filteredOn(RefreshTokenHistory::isCurrent).hasSize(1);
        });
        assertThat(authService.refreshAccessToken(outcome(outcomes, "a").tokens().getRefreshToken(), "independent-a").getRefreshToken()).isNotBlank();
        assertThat(authService.refreshAccessToken(outcome(outcomes, "b").tokens().getRefreshToken(), "independent-b").getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("여러 세대 전 Token 재사용은 최신 Token만 무효화하고 다른 family는 유지한다")
    void ancestorReuseRevokesLatestGenerationWithoutAffectingOtherFamily() {
        Account account = register();
        String clientUuid = "ancestor-client";
        TokenResponseDto first = signIn(account, clientUuid);
        TokenResponseDto independent = signIn(account, "independent-family");
        TokenResponseDto second = authService.refreshAccessToken(first.getRefreshToken(), clientUuid);
        TokenResponseDto third = authService.refreshAccessToken(second.getRefreshToken(), clientUuid);

        assertRefreshRejected(first.getRefreshToken(), clientUuid);

        RefreshTokenHistory oldest = history(first.getRefreshToken());
        RefreshSession revoked = refreshSessionRepository.findById(oldest.getRefreshSession().getId()).orElseThrow();
        assertThat(histories(revoked)).hasSize(3);
        assertThat(oldest.getReuseDetectedAt()).isNotNull();
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.getReuseDetectedAt()).isEqualTo(oldest.getReuseDetectedAt());
        assertRefreshRejected(third.getRefreshToken(), clientUuid);
        assertThat(authService.refreshAccessToken(independent.getRefreshToken(), "independent-family").getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("로그아웃과 재발급의 두 직렬 처리 순서를 각각 검증한다")
    void signOutAndRefreshBothSerializedOrders() {
        Account logoutFirst = register();
        TokenResponseDto first = signIn(logoutFirst, "logout-first");
        authService.signOut(logoutFirst.userId(), first.getRefreshToken(), "logout-first");
        assertRefreshRejected(first.getRefreshToken(), "logout-first");
        assertThat(onlySession(logoutFirst.userId()).isActive()).isFalse();

        Account refreshFirst = register();
        TokenResponseDto original = signIn(refreshFirst, "refresh-first");
        TokenResponseDto replacement = authService.refreshAccessToken(original.getRefreshToken(), "refresh-first");
        assertThatThrownBy(() -> authService.signOut(refreshFirst.userId(), original.getRefreshToken(), "refresh-first"))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(onlySession(refreshFirst.userId()).isActive()).isTrue();
        authService.signOut(refreshFirst.userId(), replacement.getRefreshToken(), "refresh-first");
        assertRefreshRejected(replacement.getRefreshToken(), "refresh-first");
    }

    @Test
    @DisplayName("회원탈퇴와 재발급의 두 직렬 처리 순서 모두 최종 Token을 무효화한다")
    void withdrawAndRefreshBothSerializedOrders() {
        Account withdrawFirst = register();
        TokenResponseDto first = signIn(withdrawFirst, "withdraw-first");
        authService.withdraw(withdrawFirst.userId());
        assertRefreshRejected(first.getRefreshToken(), "withdraw-first");
        assertThat(userRepository.findById(withdrawFirst.userId())).isEmpty();

        Account refreshFirst = register();
        TokenResponseDto original = signIn(refreshFirst, "withdraw-after-refresh");
        TokenResponseDto replacement = authService.refreshAccessToken(original.getRefreshToken(), "withdraw-after-refresh");
        authService.withdraw(refreshFirst.userId());
        assertRefreshRejected(replacement.getRefreshToken(), "withdraw-after-refresh");
        assertThat(userRepository.findById(refreshFirst.userId())).isEmpty();
        assertThat(refreshSessionRepository.findAllByUserId(refreshFirst.userId())).hasSize(1)
                .allSatisfy(session -> assertThat(session.isActive()).isFalse());
    }

    @Test
    @DisplayName("실제 User 행 잠금 타임아웃은 상태를 바꾸지 않고 잠금 해제 후 재시도가 성공한다")
    void actualLockTimeoutPreservesCurrentToken() throws InterruptedException {
        Account account = register();
        TokenResponseDto original = signIn(account, "lock-timeout-client");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Outcome blocked = new TransactionTemplate(transactionManager).execute(status -> {
                userRepository.findByIdForUpdate(account.userId()).orElseThrow();
                Future<Outcome> worker = executor.submit(() -> {
                    try {
                        return new Outcome("blocked-refresh", authService.refreshAccessToken(original.getRefreshToken(), "lock-timeout-client"), null);
                    } catch (RuntimeException failure) {
                        return new Outcome("blocked-refresh", null, failure);
                    }
                });
                try {
                    // 이 트랜잭션이 User lock을 유지한 채 다른 connection의 실제 timeout을 기다립니다.
                    return worker.get(15, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Lock timeout worker interrupted", interrupted);
                } catch (ExecutionException | TimeoutException failure) {
                    throw new IllegalStateException("Lock timeout worker did not complete", failure);
                }
            });
            assertThat(blocked).isNotNull();
            assertThat(blocked.failure()).isInstanceOf(TransientDataAccessException.class);
            RefreshSession session = onlySession(account.userId());
            assertThat(session.isActive()).isTrue();
            assertThat(session.getReuseDetectedAt()).isNull();
            assertThat(histories(session)).singleElement().satisfies(history -> {
                assertThat(history.isCurrent()).isTrue();
                assertThat(history.getRotatedAt()).isNull();
                assertThat(history.getReplacedByJti()).isNull();
            });
            assertThat(authService.refreshAccessToken(original.getRefreshToken(), "lock-timeout-client").getRefreshToken()).isNotBlank();
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Lock timeout worker did not terminate");
            }
        }
    }

    // 각 worker는 Spring proxy를 호출하여 별도 트랜잭션/connection으로 실행합니다.
    private List<Outcome> race(Operation... operations) throws Exception {
        CountDownLatch ready = new CountDownLatch(operations.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(operations.length);
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            for (Operation operation : operations) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent worker start timed out");
                    }
                    try {
                        return new Outcome(operation.name(), operation.call().call(), null);
                    } catch (RuntimeException failure) {
                        return new Outcome(operation.name(), null, failure);
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(15, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            start.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent workers did not terminate");
            }
        }
    }

    private Account register() {
        SignUpRequestDto request = new SignUpRequestDto();
        request.setEmail("concurrency-" + UUID.randomUUID() + "@farmguardian.test");
        request.setPassword(PASSWORD);
        return new Account(request.getEmail(), authService.signUp(request));
    }

    private TokenResponseDto signIn(Account account, String clientUuid) {
        SignInRequestDto request = new SignInRequestDto();
        request.setEmail(account.email());
        request.setPassword(PASSWORD);
        request.setClientUuid(clientUuid);
        return authService.signIn(request);
    }

    private RefreshSession onlySession(Long userId) {
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(userId);
        assertThat(sessions).hasSize(1);
        return sessions.getFirst();
    }

    private List<RefreshTokenHistory> histories(RefreshSession session) {
        return refreshTokenHistoryRepository.findAllByRefreshSessionIdOrderByIssuedAtAsc(session.getId());
    }

    private ValidatedRefreshToken claims(String rawToken) {
        return jwtTokenProvider.parseAndValidateRefreshToken(rawToken);
    }

    private RefreshTokenHistory history(String rawToken) {
        return refreshTokenHistoryRepository.findByJtiAndTokenHash(claims(rawToken).getJti(), refreshTokenHasher.hash(rawToken)).orElseThrow();
    }

    private void assertRefreshRejected(String rawToken, String clientUuid) {
        assertThatThrownBy(() -> authService.refreshAccessToken(rawToken, clientUuid)).isInstanceOf(InvalidTokenException.class);
    }

    private Outcome outcome(List<Outcome> outcomes, String name) {
        return outcomes.stream().filter(outcome -> outcome.name().equals(name)).findFirst().orElseThrow();
    }

    private record Account(String email, Long userId) {
    }

    private record Operation(String name, Callable<TokenResponseDto> call) {
    }

    private record Outcome(String name, TokenResponseDto tokens, RuntimeException failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}

