package com.farmguardian.farmguardian.repository;

import com.farmguardian.farmguardian.domain.RefreshSession;
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
@DisplayName("Refresh Session Repository 계약 테스트")
class RefreshSessionRepositoryTest {

    private static final String CLIENT_UUID = "repository-test-client";
    private static final String FAMILY_ID = "11111111-1111-4111-8111-111111111111";
    private static final String REPLACEMENT_FAMILY_ID = "55555555-5555-4555-8555-555555555555";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime REVOKED_AT = LocalDateTime.of(2026, 1, 1, 1, 0);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("활성 Session을 저장하고 사용자·Client 및 ID 기준으로 잠금 조회한다")
    void saveAndFindActiveSessionForUpdate() {
        User user = saveUser("refresh-session@farmguardian.test");
        RefreshSession savedSession = refreshSessionRepository.saveAndFlush(
                new RefreshSession(user, CLIENT_UUID, FAMILY_ID, CREATED_AT));
        Long sessionId = savedSession.getId();
        entityManager.clear();

        RefreshSession activeSession = refreshSessionRepository
                .findActiveByUserIdAndClientUuidForUpdate(user.getId(), CLIENT_UUID)
                .orElseThrow();
        RefreshSession lockedSession = refreshSessionRepository
                .findByIdAndUserIdForUpdate(sessionId, user.getId())
                .orElseThrow();

        assertAll(
                () -> assertThat(sessionId).isPositive(),
                () -> assertThat(activeSession.getVersion()).isZero(),
                () -> assertThat(activeSession.isActive()).isTrue(),
                () -> assertThat(activeSession.getId()).isEqualTo(sessionId),
                () -> assertThat(entityManager.getLockMode(activeSession))
                        .isEqualTo(LockModeType.PESSIMISTIC_WRITE),
                () -> assertThat(lockedSession).isSameAs(activeSession),
                () -> assertThat(refreshSessionRepository.findAllByUserId(user.getId()))
                        .extracting(RefreshSession::getId)
                        .containsExactly(sessionId)
        );
    }

    @Test
    @DisplayName("폐기된 Session은 활성 조회에서 제외한다")
    void excludeRevokedSessionFromActiveLookup() {
        User user = saveUser("revoked-session@farmguardian.test");
        RefreshSession session = refreshSessionRepository.saveAndFlush(
                new RefreshSession(user, CLIENT_UUID, FAMILY_ID, CREATED_AT));

        session.revoke(REVOKED_AT);
        refreshSessionRepository.flush();
        Long sessionId = session.getId();
        entityManager.clear();

        RefreshSession revokedSession = refreshSessionRepository.findById(sessionId).orElseThrow();

        assertAll(
                () -> assertThat(revokedSession.isActive()).isFalse(),
                () -> assertThat(revokedSession.getActiveSlot()).isNull(),
                () -> assertThat(revokedSession.getRevokedAt()).isEqualTo(REVOKED_AT),
                () -> assertThat(revokedSession.getVersion()).isEqualTo(1L),
                () -> assertThat(refreshSessionRepository
                        .findActiveByUserIdAndClientUuidForUpdate(user.getId(), CLIENT_UUID))
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("기존 Session의 활성 슬롯을 해제한 후 같은 Client의 새 Session을 저장한다")
    void saveReplacementAfterFlushingRevokedSession() {
        User user = saveUser("replacement-session@farmguardian.test");
        RefreshSession previousSession = refreshSessionRepository.saveAndFlush(
                new RefreshSession(user, CLIENT_UUID, FAMILY_ID, CREATED_AT));

        previousSession.revoke(REVOKED_AT);
        refreshSessionRepository.flush();
        RefreshSession replacementSession = refreshSessionRepository.saveAndFlush(
                new RefreshSession(user, CLIENT_UUID, REPLACEMENT_FAMILY_ID, REVOKED_AT));
        Long replacementSessionId = replacementSession.getId();
        entityManager.clear();

        RefreshSession activeSession = refreshSessionRepository
                .findActiveByUserIdAndClientUuidForUpdate(user.getId(), CLIENT_UUID)
                .orElseThrow();

        assertAll(
                () -> assertThat(activeSession.getId()).isEqualTo(replacementSessionId),
                () -> assertThat(activeSession.getFamilyId()).isEqualTo(REPLACEMENT_FAMILY_ID),
                () -> assertThat(refreshSessionRepository.findAllByUserId(user.getId()))
                        .extracting(RefreshSession::getId)
                        .containsExactlyInAnyOrder(previousSession.getId(), replacementSessionId)
        );
    }

    @Test
    @DisplayName("회원탈퇴용 조회는 활성 Session만 ID 순서로 잠근다")
    void lockOnlyActiveSessionsInDeterministicOrder() {
        User user = saveUser("withdraw-session-lock@farmguardian.test");
        RefreshSession firstActive = refreshSessionRepository.saveAndFlush(
                new RefreshSession(
                        user,
                        "client-lock-a",
                        "66666666-6666-4666-8666-666666666666",
                        CREATED_AT));
        RefreshSession revoked = refreshSessionRepository.saveAndFlush(
                new RefreshSession(
                        user,
                        "client-lock-revoked",
                        "77777777-7777-4777-8777-777777777777",
                        CREATED_AT));
        revoked.revoke(REVOKED_AT);
        refreshSessionRepository.flush();
        RefreshSession secondActive = refreshSessionRepository.saveAndFlush(
                new RefreshSession(
                        user,
                        "client-lock-b",
                        "88888888-8888-4888-8888-888888888888",
                        CREATED_AT));
        entityManager.clear();

        var activeSessions = refreshSessionRepository
                .findAllActiveByUserIdForUpdateOrderById(user.getId());

        assertAll(
                () -> assertThat(activeSessions)
                        .extracting(RefreshSession::getId)
                        .containsExactly(firstActive.getId(), secondActive.getId()),
                () -> assertThat(activeSessions)
                        .allSatisfy(session -> assertThat(entityManager.getLockMode(session))
                                .isEqualTo(LockModeType.PESSIMISTIC_WRITE))
        );
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("encoded-password")
                .role(Role.USER)
                .build());
    }
}
