package com.farmguardian.farmguardian.controller;

import com.farmguardian.farmguardian.dto.request.RefreshTokenRequestDto;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth Refresh Controller 통합 테스트")
class AuthRefreshControllerIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String CLIENT_UUID = "controller-refresh-client";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Refresh API는 저장된 현재 Refresh Token을 회전 발급한다")
    void refreshApiRotatesCurrentRefreshToken() throws Exception {
        JsonNode issued = registerAndSignIn();
        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken(issued.get("refreshToken").asText());
        request.setClientUuid(CLIENT_UUID);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode rotated = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(rotated.get("refreshToken").asText())
                .isNotEqualTo(issued.get("refreshToken").asText());
    }

    @Test
    @DisplayName("Refresh API는 Access Token 제출을 401로 거부한다")
    void refreshApiRejectsAccessToken() throws Exception {
        JsonNode issued = registerAndSignIn();
        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken(issued.get("accessToken").asText());
        request.setClientUuid(CLIENT_UUID);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    private JsonNode registerAndSignIn() throws Exception {
        String email = "refresh-controller-" + UUID.randomUUID() + "@farmguardian.test";
        SignUpRequestDto signUp = new SignUpRequestDto();
        signUp.setEmail(email);
        signUp.setPassword(PASSWORD);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUp)))
                .andExpect(status().isOk());

        SignInRequestDto signIn = new SignInRequestDto();
        signIn.setEmail(email);
        signIn.setPassword(PASSWORD);
        signIn.setClientUuid(CLIENT_UUID);
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signIn)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
