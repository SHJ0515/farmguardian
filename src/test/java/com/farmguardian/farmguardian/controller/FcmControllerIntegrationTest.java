package com.farmguardian.farmguardian.controller;

import tools.jackson.databind.ObjectMapper;
import com.farmguardian.farmguardian.domain.Platform;
import com.farmguardian.farmguardian.dto.request.*;
import com.farmguardian.farmguardian.repository.FcmTokenRepository;
import com.farmguardian.farmguardian.repository.RefreshTokenRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class FcmControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    private String accessToken;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        fcmTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // 회원가입
        SignUpRequestDto signUpRequest = new SignUpRequestDto();
        signUpRequest.setEmail("test@example.com");
        signUpRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest)));

        // 로그인
        SignInRequestDto signInRequest = new SignInRequestDto();
        signInRequest.setEmail("test@example.com");
        signInRequest.setPassword("password123");
        signInRequest.setClientUuid("test-client-uuid");

        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInRequest)))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(responseBody).get("accessToken").asText();

        // JWT에서 userId 추출 (실제로는 토큰을 파싱해야 하지만 테스트에서는 간단히)
        userId = 1L;
    }

    @Test
    @DisplayName("FCM 토큰 등록 성공")
    void registerToken_Success() throws Exception {
        // given
        FcmTokenRegisterRequestDto request = new FcmTokenRegisterRequestDto(
                "test-fcm-token-123456789",
                Platform.ANDROID
        );

        // when & then
        mockMvc.perform(post("/api/fcm/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FCM 토큰 등록 실패 - 토큰 누락")
    void registerToken_Fail_MissingToken() throws Exception {
        // given
        String invalidRequest = "{\"platform\": \"ANDROID\"}";

        // when & then
        mockMvc.perform(post("/api/fcm/token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("FCM 토큰 삭제 성공")
    void deleteToken_Success() throws Exception {
        // given - 토큰 등록
        FcmTokenRegisterRequestDto registerRequest = new FcmTokenRegisterRequestDto(
                "test-fcm-token-to-delete",
                Platform.ANDROID
        );

        mockMvc.perform(post("/api/fcm/token")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // when & then - 토큰 삭제
        mockMvc.perform(delete("/api/fcm/token")
                        .param("token", "test-fcm-token-to-delete"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("특정 사용자에게 푸시 알림 전송 성공")
    void sendNotification_Success() throws Exception {
        // given - FCM 토큰 등록
        FcmTokenRegisterRequestDto tokenRequest = new FcmTokenRegisterRequestDto(
                "test-fcm-token-for-notification",
                Platform.ANDROID
        );

        mockMvc.perform(post("/api/fcm/token")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)));

        // when
        FcmSendRequestDto sendRequest = new FcmSendRequestDto(
                "테스트 제목",
                "테스트 내용입니다"
        );

        // then - 실제 FCM 전송은 실패할 수 있지만 API 호출 자체는 성공해야 함
        mockMvc.perform(post("/api/fcm/send/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("전체 사용자에게 푸시 알림 브로드캐스트 성공")
    void broadcastNotification_Success() throws Exception {
        // given
        FcmSendRequestDto sendRequest = new FcmSendRequestDto(
                "전체 공지",
                "모든 사용자에게 전송되는 메시지입니다"
        );

        // when & then
        mockMvc.perform(post("/api/fcm/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FCM 알림 전송 실패 - 제목 누락")
    void sendNotification_Fail_MissingTitle() throws Exception {
        // given
        String invalidRequest = "{\"body\": \"내용만 있음\"}";

        // when & then
        mockMvc.perform(post("/api/fcm/send/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().is4xxClientError());
    }
}
