package com.farmguardian.farmguardian.repository;

import com.farmguardian.farmguardian.domain.Role;
import com.farmguardian.farmguardian.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Transactional
@DisplayName("User Repository 인증 세션 잠금 계약 테스트")
class UserRepositoryLockTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("이메일과 ID 인증 조회는 User 행을 비관적 쓰기 잠금한다")
    void lockUserForAuthenticationSessionMutation() {
        User first = saveUser("user-lock-email@farmguardian.test");
        User second = saveUser("user-lock-id@farmguardian.test");
        entityManager.clear();

        User byEmail = userRepository.findByEmailForUpdate(first.getEmail()).orElseThrow();
        User byId = userRepository.findByIdForUpdate(second.getId()).orElseThrow();

        assertAll(
                () -> assertThat(entityManager.getLockMode(byEmail))
                        .isEqualTo(LockModeType.PESSIMISTIC_WRITE),
                () -> assertThat(entityManager.getLockMode(byId))
                        .isEqualTo(LockModeType.PESSIMISTIC_WRITE)
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
