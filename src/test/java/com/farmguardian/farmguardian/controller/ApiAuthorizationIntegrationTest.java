package com.farmguardian.farmguardian.controller;

import com.farmguardian.farmguardian.config.SecurityConfig;
import com.farmguardian.farmguardian.config.jwt.JwtFilter;
import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.domain.Platform;
import com.farmguardian.farmguardian.dto.request.DeviceConnectRequestDto;
import com.farmguardian.farmguardian.dto.request.DeviceUpdateRequestDto;
import com.farmguardian.farmguardian.dto.request.MobileImageUploadRequestDto;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.response.DeviceResponseDto;
import com.farmguardian.farmguardian.dto.response.ImageAnalysisResponseDto;
import com.farmguardian.farmguardian.dto.response.ImageDetailResponseDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.ErrorCode;
import com.farmguardian.farmguardian.exception.GlobalExceptionHandler;
import com.farmguardian.farmguardian.gateway.MqttGateway;
import com.farmguardian.farmguardian.service.AuthService;
import com.farmguardian.farmguardian.service.DeviceService;
import com.farmguardian.farmguardian.service.FcmService;
import com.farmguardian.farmguardian.service.ImageAnalyzeService;
import com.farmguardian.farmguardian.service.ImageService;
import com.farmguardian.farmguardian.service.MqttService;
import com.google.firebase.FirebaseApp;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3-2B의 URL 경계만 검증한다. 업무 Service는 mock이며 실제 DB/외부 전송은 실행하지 않는다.
 * Refresh 본문의 업무 검증은 기존 AuthRefreshControllerIntegrationTest가 담당한다.
 * Image/FCM/capture 소유권과 관리자 개방/현재 역할/권한 회수 정책은 이 테스트의 범위가 아니다.
 */
@WebMvcTest
@ContextConfiguration(classes = ApiAuthorizationIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=VGVzdEp3dFNlY3JldEtleUZvckZhcm1HdWFyZGlhblRlc3RTdWl0ZTAwMDAwMDAw",
        "jwt.access-token-expiration=1800000",
        "jwt.refresh-token-expiration=604800000",
        "jwt.issuer=farmguardian-test",
        "jwt.access-token-audience=farmguardian-api-test",
        "jwt.refresh-token-audience=farmguardian-refresh-test"
})
@DisplayName("API URL 인증·인가 경계 통합 테스트")
class ApiAuthorizationIntegrationTest {

    // 기존 JWT 테스트와 같은 합성 키이며 실제 환경에서는 사용하지 않는다.
    private static final String TEST_SECRET =
            "VGVzdEp3dFNlY3JldEtleUZvckZhcm1HdWFyZGlhblRlc3RTdWl0ZTAwMDAwMDAw";
    private static final String EMAIL = "url-auth@farmguardian.test";
    private static final String PASSWORD = "test-password";
    private static final String CLIENT_UUID = "url-auth-client";
    private static final String FCM_TOKEN = "url-auth-synthetic-fcm-token";
    private static final Long USER_ID = 42L;
    private static final Long DEVICE_ID = 7L;
    private static final Long IMAGE_ID = 11L;
    private static final String REFRESH_BODY = "{\"refreshToken\":\"%s\",\"clientUuid\":\"url-auth-client\"}";
    private static final String NOTIFICATION_BODY = "{\"title\":\"test title\",\"body\":\"test body\"}";

    // 번호는 기존 23개 API 권한표와 대응한다. 관리자 전환 후에도 구 경로는 허용하지 않는다.
    private static final List<Endpoint> PUBLIC_ENDPOINTS = List.of(
            new Endpoint(1, HttpMethod.GET, "/", null),
            new Endpoint(2, HttpMethod.POST, "/api/auth/signup", "{\"email\":\"url-auth@farmguardian.test\",\"password\":\"test-password\"}"),
            new Endpoint(3, HttpMethod.POST, "/api/auth/signin", "{\"email\":\"url-auth@farmguardian.test\",\"password\":\"test-password\",\"clientUuid\":\"url-auth-client\"}"),
            new Endpoint(6, HttpMethod.POST, "/api/auth/refresh", REFRESH_BODY)
    );
    private static final List<Endpoint> USER_ENDPOINTS = List.of(
            new Endpoint(4, HttpMethod.POST, "/api/auth/signout", REFRESH_BODY),
            new Endpoint(5, HttpMethod.DELETE, "/api/auth/withdraw", null),
            new Endpoint(7, HttpMethod.POST, "/api/devices/connect", "{\"deviceUuid\":\"url-auth-device\",\"alias\":\"test\"}"),
            new Endpoint(9, HttpMethod.GET, "/api/devices", null),
            new Endpoint(10, HttpMethod.GET, "/api/devices/7", null),
            new Endpoint(11, HttpMethod.PATCH, "/api/devices/7", "{\"alias\":\"test\"}"),
            new Endpoint(12, HttpMethod.DELETE, "/api/devices/7", null),
            new Endpoint(14, HttpMethod.POST, "/api/images/mobile/analyze", "{\"cloudUrl\":\"https://example.test/image.jpg\",\"width\":640,\"height\":480}"),
            new Endpoint(15, HttpMethod.GET, "/api/images", null),
            new Endpoint(16, HttpMethod.GET, "/api/images/device/7", null),
            new Endpoint(17, HttpMethod.GET, "/api/images/11", null),
            new Endpoint(18, HttpMethod.POST, "/api/fcm/token", "{\"token\":\"url-auth-synthetic-fcm-token\",\"platform\":\"ANDROID\"}"),
            new Endpoint(19, HttpMethod.DELETE, "/api/fcm/token", null),
            new Endpoint(23, HttpMethod.POST, "/api/users/42/devices/7/capture", null)
    );
    private static final List<Endpoint> BLOCKED_ENDPOINTS = List.of(
            new Endpoint(8, HttpMethod.GET, "/api/devices/available", null),
            new Endpoint(13, HttpMethod.POST, "/api/images/analyze", "{\"deviceUuid\":\"url-auth-device\",\"cloudUrl\":\"https://example.test/image.jpg\",\"width\":640,\"height\":480}"),
            new Endpoint(20, HttpMethod.POST, "/api/fcm/send/42", NOTIFICATION_BODY),
            new Endpoint(21, HttpMethod.POST, "/api/fcm/broadcast", NOTIFICATION_BODY),
            new Endpoint(22, HttpMethod.GET, "/api/users/", null)
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private ImageAnalyzeService imageAnalyzeService;

    @MockitoBean
    private FcmService fcmService;

    @MockitoBean
    private MqttService mqttService;

    private String userAccessToken;
    private String adminAccessToken;
    private String refreshToken;
    private String expiredAccessToken;

    @BeforeEach
    void setUpTokens() {
        SecurityContextHolder.clearContext();
        userAccessToken = jwtTokenProvider.createAccessToken(EMAIL, "USER", USER_ID);
        adminAccessToken = jwtTokenProvider.createAccessToken(EMAIL, "ADMIN", USER_ID);
        refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);
        long now = System.currentTimeMillis();
        expiredAccessToken = Jwts.builder()
                .setSubject(EMAIL)
                .setIssuer("farmguardian-test")
                .setAudience("farmguardian-api-test")
                .setId(UUID.randomUUID().toString())
                .claim("token_type", "ACCESS")
                .claim("role", "USER")
                .claim("userId", USER_ID)
                .setIssuedAt(new Date(now - 120_000L))
                .setExpiration(new Date(now - 60_000L))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("실제 보안 체인·JWT를 사용하며 DB와 외부 연동은 구성하지 않는다")
    void fixtureUsesProductionSecurityWithoutInfrastructure() {
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("filterChain");
        SecurityFilterChain filterChain = applicationContext.getBean(SecurityFilterChain.class);
        assertThat(filterChain.getFilters()).anyMatch(JwtFilter.class::isInstance);
        assertThat(mockingDetails(jwtTokenProvider).isMock()).isFalse();
        for (Object service : businessServices()) {
            assertThat(mockingDetails(service).isMock()).isTrue();
        }
        assertAll(
                () -> assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty(),
                () -> assertThat(applicationContext.getBeansOfType(EntityManagerFactory.class)).isEmpty(),
                () -> assertThat(applicationContext.getBeansOfType(FirebaseApp.class)).isEmpty(),
                () -> assertThat(applicationContext.getBeansOfType(MqttGateway.class)).isEmpty(),
                () -> assertThat(jwtTokenProvider.validateAccessToken(userAccessToken)).isTrue(),
                () -> assertThat(jwtTokenProvider.validateAccessToken(adminAccessToken)).isTrue(),
                () -> assertThat(jwtTokenProvider.validateAccessToken(expiredAccessToken)).isFalse(),
                () -> assertThat(jwtTokenProvider.validateAccessToken(refreshToken)).isFalse(),
                () -> assertThat(jwtTokenProvider.validateRefreshToken(refreshToken)).isTrue()
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicEndpoints")
    @DisplayName("PUBLIC 4개는 Access 없이 기존 Controller로 진입한다")
    void publicEndpointsRemainAccessibleWithoutAccessToken(Endpoint endpoint) throws Exception {
        TokenResponseDto tokens = new TokenResponseDto("Bearer", userAccessToken, refreshToken);
        if (endpoint.number() == 3) {
            when(authService.signIn(any(SignInRequestDto.class))).thenReturn(tokens);
        } else if (endpoint.number() == 6) {
            when(authService.refreshAccessToken(refreshToken, CLIENT_UUID)).thenReturn(tokens);
        }

        MvcResult result = mockMvc.perform(requestFor(endpoint)).andExpect(status().isOk()).andReturn();

        switch (endpoint.number()) {
            case 1 -> content().string("Welcome to API!").match(result);
            case 2 -> verify(authService).signUp(argThat(dto -> EMAIL.equals(dto.getEmail()) && PASSWORD.equals(dto.getPassword())));
            case 3 -> {
                verify(authService).signIn(argThat(dto -> EMAIL.equals(dto.getEmail()) && PASSWORD.equals(dto.getPassword()) && CLIENT_UUID.equals(dto.getClientUuid())));
                jsonPath("$.accessToken").value(userAccessToken).match(result);
            }
            case 6 -> {
                verify(authService).refreshAccessToken(refreshToken, CLIENT_UUID);
                jsonPath("$.refreshToken").value(refreshToken).match(result);
            }
            default -> throw new IllegalArgumentException("Unexpected PUBLIC endpoint: " + endpoint);
        }
        verifyNoMoreInteractions(businessServices());
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("unauthorizedUserRequests")
    @DisplayName("USER 14개는 토큰 없음·무효·만료·Refresh Bearer를 401로 거부한다")
    void userEndpointsRejectMissingOrInvalidAccessToken(Endpoint endpoint, Credential credential) throws Exception {
        MvcResult result = mockMvc.perform(withCredential(requestFor(endpoint), credential)).andReturn();
        assertDeniedWithoutBusinessCalls(result, 401);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("authorizedUserRequests")
    @DisplayName("유효 USER·ADMIN Access는 유지 API의 기존 Service 위임을 허용한다")
    void userEndpointsAcceptValidAccessToken(Endpoint endpoint, Credential credential) throws Exception {
        stubUserResponse(endpoint);
        mockMvc.perform(withCredential(requestFor(endpoint), credential)).andExpect(status().isOk());
        verifyUserDelegation(endpoint);
        verifyNoMoreInteractions(businessServices());
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("blockedRequests")
    @DisplayName("기존 폐기·IoT 경로는 익명·USER·ADMIN 모두 업무를 실행할 수 없다")
    void blockedEndpointsNeverInvokeBusinessServices(Endpoint endpoint, Credential credential) throws Exception {
        MvcResult result = mockMvc.perform(withCredential(requestFor(endpoint), credential)).andReturn();
        assertDeniedWithoutBusinessCalls(result, credential == Credential.NONE ? 401 : 403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callerCredentials")
    @DisplayName("새 관리자 broadcast 경로도 개방 승인 전에는 차단한다")
    void adminBroadcastRemainsBlocked(Credential credential) throws Exception {
        Endpoint endpoint = new Endpoint(0, HttpMethod.POST, "/api/admin/notifications/broadcast", NOTIFICATION_BODY);
        MvcResult result = mockMvc.perform(withCredential(requestFor(endpoint), credential)).andReturn();
        assertDeniedWithoutBusinessCalls(result, credential == Credential.NONE ? 401 : 403);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("unlistedRequests")
    @DisplayName("미정의 경로와 미허용 HTTP 메서드는 기본 거부한다")
    void unlistedRoutesAndMethodsAreDenied(Endpoint endpoint, Credential credential) throws Exception {
        MvcResult result = mockMvc.perform(withCredential(requestFor(endpoint), credential)).andReturn();
        assertDeniedWithoutBusinessCalls(result, credential == Credential.NONE ? 401 : 403);
    }

    private void assertDeniedWithoutBusinessCalls(MvcResult result, int expectedStatus) {
        // 응답 검증이 실패해도 업무 Service가 실행됐는지 함께 기록한다.
        assertAll(
                () -> assertThat(result.getResponse().getStatus()).as("HTTP status").isEqualTo(expectedStatus),
                () -> assertErrorJson(result, expectedStatus),
                () -> verifyNoInteractions(businessServices())
        );
    }

    private void assertErrorJson(MvcResult result, int expectedStatus) throws Exception {
        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
        jsonPath("$.success").value(false).match(result);
        jsonPath("$.status").value(expectedStatus).match(result);
        jsonPath("$.code").isNotEmpty().match(result);
        jsonPath("$.message").isNotEmpty().match(result);
        jsonPath("$.timestamp").isNotEmpty().match(result);
        if (expectedStatus == 401) {
            jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()).match(result);
        }
        // 403 전용 ErrorCode 이름/번호는 임의로 확정하지 않고 공통 응답 구조만 검사한다.
    }

    private MockHttpServletRequestBuilder requestFor(Endpoint endpoint) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path())
                .accept(MediaType.APPLICATION_JSON);
        if (endpoint.body() != null) {
            String body = endpoint.number() == 4 || endpoint.number() == 6
                    ? endpoint.body().formatted(refreshToken) : endpoint.body();
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (endpoint.number() == 19) {
            request.queryParam("token", FCM_TOKEN);
        }
        return request;
    }

    private MockHttpServletRequestBuilder withCredential(MockHttpServletRequestBuilder request, Credential credential) {
        String token = switch (credential) {
            case NONE -> null;
            case MALFORMED -> "not-a-jwt";
            case EXPIRED -> expiredAccessToken;
            case REFRESH -> refreshToken;
            case USER -> userAccessToken;
            case ADMIN -> adminAccessToken;
        };
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void stubUserResponse(Endpoint endpoint) {
        DeviceResponseDto device = DeviceResponseDto.builder().id(DEVICE_ID).deviceUuid("url-auth-device").build();
        switch (endpoint.number()) {
            case 7 -> when(deviceService.connectDevice(eq(USER_ID), any(DeviceConnectRequestDto.class))).thenReturn(device);
            case 9 -> when(deviceService.getDevicesByUserId(USER_ID)).thenReturn(List.of(device));
            case 10 -> when(deviceService.getDeviceById(USER_ID, DEVICE_ID)).thenReturn(device);
            case 11 -> when(deviceService.updateDevice(eq(USER_ID), eq(DEVICE_ID), any(DeviceUpdateRequestDto.class))).thenReturn(device);
            case 14 -> when(imageAnalyzeService.analyzeMobileImage(eq(USER_ID), any(MobileImageUploadRequestDto.class)))
                    .thenReturn(ImageAnalysisResponseDto.builder().originImageId(IMAGE_ID).pestDetected(false).pests(List.of()).build());
            case 15 -> when(imageService.getAllImagesByUser(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));
            case 16 -> when(imageService.getImagesByDevice(eq(DEVICE_ID), any(Pageable.class)))
                    .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));
            case 17 -> when(imageService.getImageById(IMAGE_ID)).thenReturn(ImageDetailResponseDto.builder().originImageId(IMAGE_ID).deviceId(DEVICE_ID).build());
            default -> { }
        }
    }

    private void verifyUserDelegation(Endpoint endpoint) {
        switch (endpoint.number()) {
            case 4 -> verify(authService).signOut(USER_ID, refreshToken, CLIENT_UUID);
            case 5 -> verify(authService).withdraw(USER_ID);
            case 7 -> verify(deviceService).connectDevice(eq(USER_ID), any(DeviceConnectRequestDto.class));
            case 9 -> verify(deviceService).getDevicesByUserId(USER_ID);
            case 10 -> verify(deviceService).getDeviceById(USER_ID, DEVICE_ID);
            case 11 -> verify(deviceService).updateDevice(eq(USER_ID), eq(DEVICE_ID), any(DeviceUpdateRequestDto.class));
            case 12 -> verify(deviceService).disconnectDevice(USER_ID, DEVICE_ID);
            case 14 -> verify(imageAnalyzeService).analyzeMobileImage(eq(USER_ID), any(MobileImageUploadRequestDto.class));
            case 15 -> verify(imageService).getAllImagesByUser(eq(USER_ID), any(Pageable.class));
            case 16 -> verify(imageService).getImagesByDevice(eq(DEVICE_ID), any(Pageable.class));
            case 17 -> verify(imageService).getImageById(IMAGE_ID);
            case 18 -> verify(fcmService).registerToken(USER_ID, FCM_TOKEN, Platform.ANDROID);
            case 19 -> verify(fcmService).deleteToken(FCM_TOKEN);
            case 23 -> verify(mqttService).requestCapture(USER_ID, DEVICE_ID);
            default -> throw new IllegalArgumentException("Unexpected USER endpoint: " + endpoint);
        }
    }

    private Object[] businessServices() {
        return new Object[]{authService, deviceService, imageService, imageAnalyzeService, fcmService, mqttService};
    }

    private static Stream<Endpoint> publicEndpoints() {
        return PUBLIC_ENDPOINTS.stream();
    }

    private static Stream<Arguments> unauthorizedUserRequests() {
        return requests(USER_ENDPOINTS, Credential.NONE, Credential.MALFORMED, Credential.EXPIRED, Credential.REFRESH);
    }

    private static Stream<Arguments> authorizedUserRequests() {
        return requests(USER_ENDPOINTS, Credential.USER, Credential.ADMIN);
    }

    private static Stream<Arguments> blockedRequests() {
        return requests(BLOCKED_ENDPOINTS, Credential.NONE, Credential.USER, Credential.ADMIN);
    }

    private static Stream<Credential> callerCredentials() {
        return Stream.of(Credential.NONE, Credential.USER, Credential.ADMIN);
    }

    private static Stream<Arguments> unlistedRequests() {
        return requests(List.of(
                new Endpoint(0, HttpMethod.GET, "/api/auth/not-registered", null),
                new Endpoint(0, HttpMethod.GET, "/api/admin/not-registered", null),
                new Endpoint(0, HttpMethod.GET, "/api/auth/signin", null),
                new Endpoint(0, HttpMethod.POST, "/", "{}")
        ), Credential.NONE, Credential.USER, Credential.ADMIN);
    }

    private static Stream<Arguments> requests(List<Endpoint> endpoints, Credential... credentials) {
        return endpoints.stream().flatMap(endpoint -> Arrays.stream(credentials).map(credential -> Arguments.of(endpoint, credential)));
    }

    private enum Credential {
        NONE, MALFORMED, EXPIRED, REFRESH, USER, ADMIN
    }

    private record Endpoint(int number, HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return "#" + number + " " + method + " " + path;
        }
    }

    // 메인 Application의 JPA auditing/외부 연동을 불러오지 않고 실제 MVC·보안 컴포넌트만 연결한다.
    // Boot 4 MVC slice에는 Pageable 자동설정이 없으므로 실제 Spring Data Web resolver를 등록한다.
    @Configuration(proxyBeanMethods = false)
    @EnableSpringDataWebSupport
    @Import({SecurityConfig.class, JwtTokenProvider.class, GlobalExceptionHandler.class,
            HomeController.class, AuthController.class, DeviceController.class,
            ImageController.class, FcmController.class, UserController.class})
    static class TestConfig {
    }
}
