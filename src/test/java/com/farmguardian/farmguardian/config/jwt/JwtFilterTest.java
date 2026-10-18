package com.farmguardian.farmguardian.config.jwt;

import com.farmguardian.farmguardian.config.auth.UserDetailsImpl;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringJUnitConfig(JwtFilterTest.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=VGVzdEp3dFNlY3JldEtleUZvckZhcm1HdWFyZGlhblRlc3RTdWl0ZTAwMDAwMDAw",
        "jwt.access-token-expiration=1800000",
        "jwt.refresh-token-expiration=604800000",
        "jwt.issuer=farmguardian-test",
        "jwt.access-token-audience=farmguardian-api-test",
        "jwt.refresh-token-audience=farmguardian-refresh-test"
})
@DisplayName("JwtFilter 보안 경계 테스트")
class JwtFilterTest {

    private static final String EMAIL = "user@farmguardian.test";
    private static final String ROLE = "USER";
    private static final Long USER_ID = 42L;

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clearSecurityContextBeforeTest() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfterTest() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Access Token만 SecurityContext에 USER 인증으로 등록한다")
    void registersAuthenticationForValidAccessToken() throws ServletException, IOException {
        String accessToken = jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID);

        FilterExecution execution = executeFilter("Bearer " + accessToken);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertAll(
                () -> assertThat(authentication).isNotNull(),
                () -> assertThat(authentication.isAuthenticated()).isTrue(),
                () -> assertThat(authentication.getPrincipal()).isInstanceOf(UserDetailsImpl.class),
                () -> assertThat(((UserDetailsImpl) authentication.getPrincipal()).getUserId())
                        .isEqualTo(USER_ID),
                () -> assertThat(authentication.getName()).isEqualTo(EMAIL),
                () -> assertThat(authentication.getAuthorities())
                        .extracting("authority")
                        .containsExactly("ROLE_" + ROLE),
                () -> assertFilterChainContinued(execution)
        );
    }

    @Test
    @DisplayName("기존 Authentication이 있으면 Access Token 인증으로 덮어쓰지 않는다")
    void preservesExistingAuthentication() throws ServletException, IOException {
        Authentication existingAuthentication = jwtTokenProvider.getAuthentication(
                jwtTokenProvider.createAccessToken(EMAIL, ROLE, USER_ID));
        SecurityContextHolder.getContext().setAuthentication(existingAuthentication);
        String anotherAccessToken = jwtTokenProvider.createAccessToken(
                "another-user@farmguardian.test", ROLE, 84L);

        FilterExecution execution = executeFilter("Bearer " + anotherAccessToken);

        assertAll(
                () -> assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isSameAs(existingAuthentication),
                () -> assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                        .isEqualTo(EMAIL),
                () -> assertFilterChainContinued(execution)
        );
    }

    @Test
    @DisplayName("Refresh Token은 SecurityContext에 사용자 인증으로 등록하지 않는다")
    void doesNotRegisterAuthenticationForRefreshToken() throws ServletException, IOException {
        String refreshToken = jwtTokenProvider.createRefreshToken(EMAIL);

        FilterExecution execution = executeFilter("Bearer " + refreshToken);

        assertAll(
                () -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(),
                () -> assertFilterChainContinued(execution)
        );
    }

    @Test
    @DisplayName("잘못된 형식의 Bearer Token은 인증으로 등록하지 않는다")
    void doesNotRegisterAuthenticationForMalformedBearerToken() throws ServletException, IOException {
        FilterExecution execution = executeFilter("Bearer not-a-jwt");

        assertAll(
                () -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(),
                () -> assertFilterChainContinued(execution)
        );
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증으로 등록하지 않는다")
    void doesNotRegisterAuthenticationWithoutAuthorizationHeader() throws ServletException, IOException {
        FilterExecution execution = executeFilter(null);

        assertAll(
                () -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(),
                () -> assertFilterChainContinued(execution)
        );
    }

    @Test
    @DisplayName("Bearer 방식이 아닌 Authorization 헤더는 인증으로 등록하지 않는다")
    void doesNotRegisterAuthenticationForUnsupportedAuthorizationScheme()
            throws ServletException, IOException {
        FilterExecution execution = executeFilter("Basic dXNlcjpwYXNzd29yZA==");

        assertAll(
                () -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(),
                () -> assertFilterChainContinued(execution)
        );
    }

    private FilterExecution executeFilter(String authorizationHeader) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }

        jwtFilter.doFilter(request, response, filterChain);
        return new FilterExecution(request, response, filterChain);
    }

    private void assertFilterChainContinued(FilterExecution execution) {
        assertAll(
                () -> assertThat(execution.filterChain().getRequest()).isSameAs(execution.request()),
                () -> assertThat(execution.filterChain().getResponse()).isSameAs(execution.response())
        );
    }

    private record FilterExecution(
            MockHttpServletRequest request,
            MockHttpServletResponse response,
            MockFilterChain filterChain) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import({JwtTokenProvider.class, JwtFilter.class})
    static class TestConfig {
    }
}
