package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.config.jwt.RefreshTokenHasher;
import com.farmguardian.farmguardian.config.jwt.ValidatedRefreshToken;
import com.farmguardian.farmguardian.domain.RefreshSession;
import com.farmguardian.farmguardian.domain.RefreshTokenHistory;
import com.farmguardian.farmguardian.dto.request.RefreshTokenRequestDto;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.InvalidTokenException;
import com.farmguardian.farmguardian.repository.RefreshSessionRepository;
import com.farmguardian.farmguardian.repository.RefreshTokenHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:farmguardian-refresh-rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@DisplayName("Flyway 기반 Refresh Session rollback 및 실패 응답 검증")
class AuthRefreshRollbackIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String CLIENT_UUID = "rollback-client";

    @Autowired
    private AuthService authService;

    @MockitoSpyBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoSpyBean
    private RefreshTokenHistoryRepository refreshTokenHistoryRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void resetFailureInjection() {
        reset(jwtTokenProvider, refreshTokenHistoryRepository);
    }

    @Test
    @DisplayName("재로그인 발급 실패는 이미 flush된 기존 Session 폐기를 rollback한다")
    void failedReloginIssuanceRestoresPreviousSession() {
        Account account = register();
        TokenResponseDto original = signIn(account);
        Long sessionId = onlySession(account.userId()).getId();
        doAnswer(invocation -> {
            assertSessionRevokedInCurrentTransaction(sessionId);
            throw new InjectedFailure();
        }).when(jwtTokenProvider).createRefreshToken(account.email());

        assertThatThrownBy(() -> signIn(account)).isInstanceOf(InjectedFailure.class);
        reset(jwtTokenProvider);

        assertOriginalState(account, sessionId, original.getRefreshToken());
        assertThat(authService.refreshAccessToken(original.getRefreshToken(), CLIENT_UUID).getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("재로그인 History 저장 실패는 기존 Session 폐기와 신규 Session INSERT를 모두 rollback한다")
    void failedReloginStorageRestoresPreviousSessionAndRemovesReplacement() {
        Account account = register();
        TokenResponseDto original = signIn(account);
        Long sessionId = onlySession(account.userId()).getId();
        doAnswer(invocation -> {
            assertSessionRevokedInCurrentTransaction(sessionId);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE user_id = ?", Long.class, account.userId())).isEqualTo(2L);
            throw new DataIntegrityViolationException("Injected replacement History storage failure");
        }).when(refreshTokenHistoryRepository).save(any(RefreshTokenHistory.class));

        assertThatThrownBy(() -> signIn(account)).isInstanceOf(DataIntegrityViolationException.class);
        reset(refreshTokenHistoryRepository);

        assertOriginalState(account, sessionId, original.getRefreshToken());
        TokenResponseDto replacement = signIn(account);
        assertThat(refreshSessionRepository.findAllByUserId(account.userId())).hasSize(2).filteredOn(RefreshSession::isActive).hasSize(1);
        assertThat(authService.refreshAccessToken(replacement.getRefreshToken(), CLIENT_UUID).getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("Rotation 후속 History 저장 실패는 flush된 기존 History를 현재 세대로 복구한다")
    void failedRotationStorageRestoresCurrentHistory() {
        Account account = register();
        TokenResponseDto original = signIn(account);
        Long sessionId = onlySession(account.userId()).getId();
        doAnswer(invocation -> {
            assertHistoryRotatedInCurrentTransaction(sessionId);
            throw new DataIntegrityViolationException("Injected successor History storage failure");
        }).when(refreshTokenHistoryRepository).save(any(RefreshTokenHistory.class));

        assertThatThrownBy(() -> authService.refreshAccessToken(original.getRefreshToken(), CLIENT_UUID)).isInstanceOf(DataIntegrityViolationException.class);
        reset(refreshTokenHistoryRepository);

        assertOriginalState(account, sessionId, original.getRefreshToken());
        TokenResponseDto rotated = authService.refreshAccessToken(original.getRefreshToken(), CLIENT_UUID);
        assertThat(rotated.getRefreshToken()).isNotEqualTo(original.getRefreshToken());
        assertThat(refreshTokenHistoryRepository.findAllByRefreshSessionIdOrderByIssuedAtAsc(sessionId)).hasSize(2).filteredOn(RefreshTokenHistory::isCurrent).hasSize(1);
    }

    @Test
    @DisplayName("일시적 DB 잠금 오류는 현재 정책의 500 COMMON_999를 반환하고 Rotation을 rollback한다")
    void transientDatabaseFailureReturnsCurrentHttpPolicyAndRollsBack() throws Exception {
        Account account = register();
        TokenResponseDto original = signIn(account);
        Long sessionId = onlySession(account.userId()).getId();
        doAnswer(invocation -> {
            assertHistoryRotatedInCurrentTransaction(sessionId);
            throw new CannotAcquireLockException("Injected transient database failure");
        }).when(refreshTokenHistoryRepository).save(any(RefreshTokenHistory.class));

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshRequest(original.getRefreshToken())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_999"));
        reset(refreshTokenHistoryRepository);

        assertOriginalState(account, sessionId, original.getRefreshToken());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshRequest(original.getRefreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        assertThat(refreshTokenHistoryRepository.findAllByRefreshSessionIdOrderByIssuedAtAsc(sessionId)).hasSize(2).filteredOn(RefreshTokenHistory::isCurrent).hasSize(1);
    }

    @Test
    @DisplayName("이미 폐기된 Session의 회전 토큰 replay는 401이며 재사용 감사 timestamp를 추가하지 않는다")
    void replayOnRevokedSessionPreservesCurrentAuditPolicy() throws Exception {
        Account account = register();
        TokenResponseDto original = signIn(account);
        TokenResponseDto rotated = authService.refreshAccessToken(original.getRefreshToken(), CLIENT_UUID);
        authService.signOut(account.userId(), rotated.getRefreshToken(), CLIENT_UUID);
        RefreshSession revoked = onlySession(account.userId());
        LocalDateTime revokedAt = revoked.getRevokedAt();

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshRequest(original.getRefreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        RefreshSession afterReplay = onlySession(account.userId());
        assertThat(afterReplay.isActive()).isFalse();
        assertThat(afterReplay.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(afterReplay.getReuseDetectedAt()).isNull();
        assertThat(refreshTokenHistoryRepository.findAllByRefreshSessionIdOrderByIssuedAtAsc(revoked.getId()))
                .hasSize(2).allSatisfy(history -> assertThat(history.getReuseDetectedAt()).isNull());
        assertThatThrownBy(() -> authService.refreshAccessToken(rotated.getRefreshToken(), CLIENT_UUID)).isInstanceOf(InvalidTokenException.class);

        TokenResponseDto freshLogin = signIn(account);
        assertThat(authService.refreshAccessToken(freshLogin.getRefreshToken(), CLIENT_UUID).getRefreshToken()).isNotBlank();
    }

    private void assertOriginalState(Account account, Long sessionId, String originalToken) {
        RefreshSession restored = onlySession(account.userId());
        assertThat(restored.getId()).isEqualTo(sessionId);
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.getRevokedAt()).isNull();
        assertThat(restored.getReuseDetectedAt()).isNull();
        ValidatedRefreshToken claims = jwtTokenProvider.parseAndValidateRefreshToken(originalToken);
        List<RefreshTokenHistory> histories = refreshTokenHistoryRepository.findAllByRefreshSessionIdOrderByIssuedAtAsc(sessionId);
        assertThat(histories).singleElement().satisfies(history -> {
            assertThat(history.isCurrent()).isTrue();
            assertThat(history.getJti()).isEqualTo(claims.getJti());
            assertThat(history.getTokenHash()).isEqualTo(refreshTokenHasher.hash(originalToken));
            assertThat(history.getRotatedAt()).isNull();
            assertThat(history.getReplacedByJti()).isNull();
            assertThat(history.getReuseDetectedAt()).isNull();
        });
    }

    private void assertSessionRevokedInCurrentTransaction(Long sessionId) {
        assertThat(jdbcTemplate.queryForObject("SELECT active_slot FROM refresh_sessions WHERE refresh_session_id = ?", Integer.class, sessionId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT revoked_at FROM refresh_sessions WHERE refresh_session_id = ?", LocalDateTime.class, sessionId)).isNotNull();
    }

    private void assertHistoryRotatedInCurrentTransaction(Long sessionId) {
        assertThat(jdbcTemplate.queryForObject("SELECT current_slot FROM refresh_token_history WHERE refresh_session_id = ?", Integer.class, sessionId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT rotated_at FROM refresh_token_history WHERE refresh_session_id = ?", LocalDateTime.class, sessionId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token_history WHERE refresh_session_id = ?", Long.class, sessionId)).isEqualTo(1L);
    }

    private String refreshRequest(String refreshToken) throws Exception {
        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken(refreshToken);
        request.setClientUuid(CLIENT_UUID);
        return objectMapper.writeValueAsString(request);
    }

    private Account register() {
        String email = "rollback-" + UUID.randomUUID() + "@farmguardian.test";
        SignUpRequestDto request = new SignUpRequestDto();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return new Account(email, authService.signUp(request));
    }

    private TokenResponseDto signIn(Account account) {
        SignInRequestDto request = new SignInRequestDto();
        request.setEmail(account.email());
        request.setPassword(PASSWORD);
        request.setClientUuid(CLIENT_UUID);
        return authService.signIn(request);
    }

    private RefreshSession onlySession(Long userId) {
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(userId);
        assertThat(sessions).hasSize(1);
        return sessions.getFirst();
    }

    private record Account(String email, Long userId) {
    }

    private static class InjectedFailure extends RuntimeException {
    }
}
