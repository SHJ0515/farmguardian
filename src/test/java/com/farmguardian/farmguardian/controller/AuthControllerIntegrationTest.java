package com.farmguardian.farmguardian.controller;

import tools.jackson.databind.ObjectMapper;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignOutRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
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
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 성공")
    void signUp_Success() throws Exception {
        // given
        SignUpRequestDto request = new SignUpRequestDto();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 성공")
    void signIn_Success() throws Exception {
        // given - 회원가입
        SignUpRequestDto signUpRequest = new SignUpRequestDto();
        signUpRequest.setEmail("test@example.com");
        signUpRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest)));

        // when - 로그인
        SignInRequestDto signInRequest = new SignInRequestDto();
        signInRequest.setEmail("test@example.com");
        signInRequest.setPassword("password123");
        signInRequest.setClientUuid("test-client-uuid");

        // then
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("로그아웃 성공")
    void signOut_Success() throws Exception {
        // given - 회원가입 및 로그인
        SignUpRequestDto signUpRequest = new SignUpRequestDto();
        signUpRequest.setEmail("test@example.com");
        signUpRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest)));

        SignInRequestDto signInRequest = new SignInRequestDto();
        signInRequest.setEmail("test@example.com");
        signInRequest.setPassword("password123");
        signInRequest.setClientUuid("test-client-uuid");

        MvcResult signInResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInRequest)))
                .andReturn();

        String responseBody = signInResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).get("accessToken").asText();
        String refreshToken = objectMapper.readTree(responseBody).get("refreshToken").asText();

        // when - 로그아웃
        SignOutRequestDto signOutRequest = new SignOutRequestDto();
        signOutRequest.setRefreshToken(refreshToken);
        signOutRequest.setClientUuid("test-client-uuid");

        // then
        mockMvc.perform(post("/api/auth/signout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signOutRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원탈퇴 성공")
    void withdraw_Success() throws Exception {
        // given
        SignUpRequestDto signUpRequest = new SignUpRequestDto();
        signUpRequest.setEmail("test@example.com");
        signUpRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest)));

        SignInRequestDto signInRequest = new SignInRequestDto();
        signInRequest.setEmail("test@example.com");
        signInRequest.setPassword("password123");
        signInRequest.setClientUuid("test-client-uuid");

        MvcResult signInResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signInRequest)))
                .andReturn();

        String responseBody = signInResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).get("accessToken").asText();

        // when & then
        mockMvc.perform(delete("/api/auth/withdraw")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
