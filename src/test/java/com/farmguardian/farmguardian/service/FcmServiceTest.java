package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.domain.Platform;
import com.farmguardian.farmguardian.domain.Role;
import com.farmguardian.farmguardian.domain.User;
import com.farmguardian.farmguardian.dto.request.FcmSendRequestDto;
import com.farmguardian.farmguardian.repository.FcmTokenRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * FCM 통합 테스트
 *
 * 실행 조건:
 * 1. 테스트 전용 Firebase 프로젝트와 자격증명 필요
 * 2. firebase.enabled=true 및 Application Default Credentials 또는 firebase.credentials-path 설정 필요
 * 3. 실제 수신 테스트용 FCM 토큰 필요
 *
 * 테스트 방법:
 * - 실제 FCM 토큰으로 테스트하려면 REAL_FCM_TOKEN 값을 변경
 * - Firebase Console에서 발급받은 토큰 사용
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("FcmService 통합 테스트")
class FcmServiceTest {

    @Autowired
    private FcmService fcmService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    private User testUser;

    // 테스트용 FCM 토큰
    private static final String REAL_FCM_TOKEN = "YOUR_REAL_FCM_TOKEN_HERE";

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = new User(
                "integration@test.com",
                "test1234",
                Role.USER
        );
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("FCM 토큰 등록 및 조회")
    void registerAndFindToken() {
        // given
        String tokenValue = "test-integration-token-" + System.currentTimeMillis();

        // when
        fcmService.registerToken(testUser.getId(), tokenValue, Platform.ANDROID);

        // then
        var tokens = fcmTokenRepository.findByUserId(testUser.getId());
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenValue()).isEqualTo(tokenValue);
        assertThat(tokens.get(0).getPlatform()).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("FCM 토큰 업데이트")
    void updateToken() {
        // given
        String tokenValue = "update-test-token-" + System.currentTimeMillis();
        fcmService.registerToken(testUser.getId(), tokenValue, Platform.ANDROID);

        // when - 같은 토큰으로 다시 등록
        fcmService.registerToken(testUser.getId(), tokenValue, Platform.ANDROID);

        // then - 중복 생성되지 않음
        var tokens = fcmTokenRepository.findByUserId(testUser.getId());
        assertThat(tokens).hasSize(1);
    }

    @Test
    @DisplayName("FCM 토큰 삭제")
    void deleteToken() {
        // given
        String tokenValue = "delete-test-token-" + System.currentTimeMillis();
        fcmService.registerToken(testUser.getId(), tokenValue, Platform.ANDROID);

        // when
        fcmService.deleteToken(tokenValue);

        // then
        var token = fcmTokenRepository.findByTokenValue(tokenValue);
        assertThat(token).isEmpty();
    }

    @Test
    @DisplayName("실제 FCM 푸시 전송 테스트 (수동 실행용)")
    void sendRealPushNotification() {
        // 실제 FCM 토큰이 설정되어 있지 않으면 테스트 스킵
        if (REAL_FCM_TOKEN.equals("YOUR_REAL_FCM_TOKEN_HERE")) {
            System.out.println("️ 실제 FCM 토큰을 설정하지 않아 테스트를 스킵합니다.");
            System.out.println("Firebase Console에서 토큰을 발급받아 REAL_FCM_TOKEN 상수에 설정하세요.");
            return;
        }

        // given
        fcmService.registerToken(testUser.getId(), REAL_FCM_TOKEN, Platform.ANDROID);

        FcmSendRequestDto request = new FcmSendRequestDto(
                "통합 테스트 알림",
                "FCM 푸시 알림이 정상적으로 동작합니다"
        );

        // when - 실제로 푸시 전송
        fcmService.sendNotificationToUser(testUser.getId(), request);

        // then
        System.out.println("✅ FCM 푸시가 전송되었습니다. 기기에서 알림을 확인하세요.");
    }

    @Test
    @DisplayName("존재하지 않는 사용자에게 알림 전송 시도")
    void sendNotificationToNonExistentUser() {
        // given
        Long nonExistentUserId = 99999L;
        FcmSendRequestDto request = new FcmSendRequestDto(
                "테스트",
                "내용"
        );

        // when
        fcmService.sendNotificationToUser(nonExistentUserId, request);

        // then - 예외 없이 처리됨 (로그만 남음)
        System.out.println("✅ 존재하지 않는 사용자에 대한 알림은 조용히 무시됩니다.");
    }
}
