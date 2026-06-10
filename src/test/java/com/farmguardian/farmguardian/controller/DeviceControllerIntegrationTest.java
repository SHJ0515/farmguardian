package com.farmguardian.farmguardian.controller;

import tools.jackson.databind.ObjectMapper;
import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.TargetCrop;
import com.farmguardian.farmguardian.dto.request.DeviceConnectRequestDto;
import com.farmguardian.farmguardian.dto.request.DeviceUpdateRequestDto;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.repository.DeviceRepository;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class DeviceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        deviceRepository.deleteAll();
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

        // 테스트용 디바이스 생성 (AVAILABLE 상태)
        Device device = Device.builder()
                .deviceUuid("device-001")
                .build();
        deviceRepository.save(device);
    }

    @Test
    @DisplayName("디바이스 연결 성공")
    void connectDevice_Success() throws Exception {
        // given
        DeviceConnectRequestDto request = new DeviceConnectRequestDto();
        request.setDeviceUuid("device-001");
        request.setTargetCrop(TargetCrop.POTATO);
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));

        // when & then
        mockMvc.perform(post("/api/devices/connect")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceUuid").value("device-001"));
    }

    @Test
    @DisplayName("내 디바이스 목록 조회 성공")
    void getMyDevices_Success() throws Exception {
        // given - 디바이스 연결
        DeviceConnectRequestDto request = new DeviceConnectRequestDto();
        request.setDeviceUuid("device-001");
        request.setTargetCrop(TargetCrop.POTATO);
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));

        mockMvc.perform(post("/api/devices/connect")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // when & then
        mockMvc.perform(get("/api/devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("연결 가능한 디바이스 목록 조회 성공")
    void getAvailableDevices_Success() throws Exception {
        mockMvc.perform(get("/api/devices/available")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("디바이스 상세 조회 성공")
    void getDevice_Success() throws Exception {
        // given
        DeviceConnectRequestDto request = new DeviceConnectRequestDto();
        request.setDeviceUuid("device-001");
        request.setTargetCrop(TargetCrop.POTATO);
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));

        MvcResult connectResult = mockMvc.perform(post("/api/devices/connect")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String connectResponse = connectResult.getResponse().getContentAsString();
        Long deviceId = objectMapper.readTree(connectResponse).get("id").asLong();

        // when & then
        mockMvc.perform(get("/api/devices/" + deviceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceUuid").value("device-001"));
    }

    @Test
    @DisplayName("디바이스 정보 수정 성공")
    void updateDevice_Success() throws Exception {
        // given
        DeviceConnectRequestDto connectRequest = new DeviceConnectRequestDto();
        connectRequest.setDeviceUuid("device-001");
        connectRequest.setTargetCrop(TargetCrop.POTATO);
        connectRequest.setLatitude(new BigDecimal("37.5665"));
        connectRequest.setLongitude(new BigDecimal("126.9780"));

        MvcResult connectResult = mockMvc.perform(post("/api/devices/connect")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connectRequest)))
                .andReturn();

        String connectResponse = connectResult.getResponse().getContentAsString();
        Long deviceId = objectMapper.readTree(connectResponse).get("id").asLong();

        // when
        DeviceUpdateRequestDto updateRequest = new DeviceUpdateRequestDto();
        updateRequest.setTargetCrop(TargetCrop.CHILI_PEPPER);
        updateRequest.setLatitude(new BigDecimal("37.5000"));
        updateRequest.setLongitude(new BigDecimal("127.0000"));

        // then
        mockMvc.perform(patch("/api/devices/" + deviceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCrop").value("CHILI_PEPPER"));
    }

    @Test
    @DisplayName("디바이스 연결 해제 성공")
    void disconnectDevice_Success() throws Exception {
        // given
        DeviceConnectRequestDto request = new DeviceConnectRequestDto();
        request.setDeviceUuid("device-001");
        request.setTargetCrop(TargetCrop.POTATO);
        request.setLatitude(new BigDecimal("37.5665"));
        request.setLongitude(new BigDecimal("126.9780"));

        MvcResult connectResult = mockMvc.perform(post("/api/devices/connect")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String connectResponse = connectResult.getResponse().getContentAsString();
        Long deviceId = objectMapper.readTree(connectResponse).get("id").asLong();

        // when & then
        mockMvc.perform(delete("/api/devices/" + deviceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
