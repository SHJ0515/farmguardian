package com.farmguardian.farmguardian.repository;

import com.farmguardian.farmguardian.domain.RefreshSession;
import com.farmguardian.farmguardian.domain.RefreshTokenHistory;
import com.farmguardian.farmguardian.domain.Role;
import com.farmguardian.farmguardian.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Transactional
@DisplayName("Refresh Token History Repository 계약 테스트")
class RefreshTokenHistoryRepositoryTest {

    private static final String FAMILY_ID = "22222222-2222-4222-8222-222222222222";
    private static final String FIRST_JTI = "33333333-3333-4333-8333-333333333333";
    private static final String NEXT_JTI = "44444444-4444-4444-8444-444444444444";
    private static final String FIRST_HASH = "a".repeat(64);
    private static final String NEXT_HASH = "b".repeat(64);
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime ROTATED_AT = LocalDateTime.of(2026, 1, 1, 1, 0);
    private static final LocalDateTime REUSE_DETECTED_AT = LocalDateTime.of(2026, 1, 1, 2, 0);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 1, 8, 0, 0);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private RefreshTokenHistoryRepository refreshTokenHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Token 원문 대신 Hash·JTI로 현재 이력을 조회한다")
    void saveAndFindCurrentTokenHistoryByHashAndJti() {
        RefreshSession session = saveSession();
        RefreshTokenHistory history = refreshTokenHistoryRepository.saveAndFlush(
                new RefreshTokenHistory(
                        session, FIRST_JTI, FIRST_HASH, ISSUED_AT, EXPIRES_AT));
        Long historyId = history.getId();
        entityManager.clear();

        RefreshTokenHistory storedHistory = refreshTokenHistoryRepository
                .findBySessionIdAndJtiAndTokenHashForUpdate(
                        session.getId(), FIRST_JTI, FIRST_HASH)
                .orElseThrow();

        assertAll(
                () -> assertThat(historyId).isPositive(),
                () -> assertThat(storedHistory.getId()).isEqualTo(historyId),
                () -> assertThat(storedHistory.isCurrent()).isTrue(),
                () -> assertThat(entityManager.getLockMode(storedHistory))
                        .isEqualTo(LockModeType.PESSIMISTIC_WRITE),
                () -> assertThat(refreshTokenHistoryRepository
                        .findRefreshSessionIdByJtiAndTokenHash(FIRST_JTI, FIRST_HASH))
                        .contains(session.getId()),
                () -> assertThat(refreshTokenHistoryRepository
                        .findCurrentByRefreshSessionId(session.getId()))
                        .contains(storedHistory)
        );
    }

    @Test
    @DisplayName("회전 시 이전 이력을 보존하고 새 이력만 현재 Token으로 조회한다")
    void preserveRotatedHistoryAndSelectOnlySuccessorAsCurrent() {
        RefreshSession session = saveSession();
        RefreshTokenHistory firstHistory = refreshTokenHistoryRepository.saveAndFlush(
                new RefreshTokenHistory(
                        session, FIRST_JTI, FIRST_HASH, ISSUED_AT, EXPIRES_AT));

        firstHistory.rotateTo(NEXT_JTI, ROTATED_AT);
        refreshTokenHistoryRepository.flush();
        RefreshTokenHistory successor = refreshTokenHistoryRepository.saveAndFlush(
                new RefreshTokenHistory(
                        session, NEXT_JTI, NEXT_HASH, ROTATED_AT, EXPIRES_AT));
        firstHistory.markReuseDetected(REUSE_DETECTED_AT);
        session.markReuseDetected(REUSE_DETECTED_AT);
        refreshSessionRepository.flush();
        entityManager.clear();

        RefreshTokenHistory storedFirstHistory = refreshTokenHistoryRepository
                .findByJtiAndTokenHash(FIRST_JTI, FIRST_HASH)
                .orElseThrow();
        RefreshTokenHistory storedSuccessor = refreshTokenHistoryRepository
                .findCurrentByRefreshSessionId(session.getId())
                .orElseThrow();
        RefreshSession storedSession = refreshSessionRepository
                .findById(session.getId())
                .orElseThrow();

        assertAll(
                () -> assertThat(storedFirstHistory.isCurrent()).isFalse(),
                () -> assertThat(storedFirstHistory.getReplacedByJti()).isEqualTo(NEXT_JTI),
                () -> assertThat(storedFirstHistory.getReuseDetectedAt())
                        .isEqualTo(REUSE_DETECTED_AT),
                () -> assertThat(storedSession.isActive()).isFalse(),
                () -> assertThat(storedSession.getReuseDetectedAt())
                        .isEqualTo(REUSE_DETECTED_AT),
                () -> assertThat(storedSuccessor.getId()).isEqualTo(successor.getId()),
                () -> assertThat(refreshTokenHistoryRepository
                        .findAllByRefreshSessionIdOrderByIssuedAtAsc(session.getId()))
                        .extracting(RefreshTokenHistory::getJti)
                        .containsExactly(FIRST_JTI, NEXT_JTI)
        );
    }

    private RefreshSession saveSession() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("refresh-history@farmguardian.test")
                .password("encoded-password")
                .role(Role.USER)
                .build());
        return refreshSessionRepository.saveAndFlush(
                new RefreshSession(user, "history-test-client", FAMILY_ID, ISSUED_AT));
    }
}
