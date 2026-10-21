package com.farmguardian.farmguardian.config;

import com.farmguardian.farmguardian.config.auth.RestAccessDeniedHandler;
import com.farmguardian.farmguardian.config.auth.RestAuthenticationEntryPoint;
import com.farmguardian.farmguardian.config.jwt.JwtFilter;
import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                // 네이티브 앱의 Bearer 인증만 사용하며 기본 /logout 진입점은 사용하지 않는다.
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())

                // HTTP 세션을 만들지 않고 필터 단계의 인증·인가 실패도 공통 JSON으로 응답한다.
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // 먼저 일치하는 규칙을 사용하므로 차단할 정적 경로를 동적 ID 경로보다 앞에 둔다.
                .authorizeHttpRequests(auth -> auth
                        // 내부 오류 처리만 허용한다. 외부 /error 요청과 ERROR 업무 경로는 공개하지 않는다.
                        .requestMatchers(new AndRequestMatcher(
                                new DispatcherTypeRequestMatcher(DispatcherType.ERROR),
                                PathPatternRequestMatcher.pathPattern("/error"))).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).denyAll()

                        // 관리자 이관·DEVICE 인증 전에는 ADMIN도 이 경로들을 사용할 수 없다.
                        .requestMatchers("/api/admin/**", "/api/devices/available", "/api/images/analyze",
                                "/api/fcm/send/**", "/api/fcm/broadcast", "/api/users", "/api/users/").denyAll()

                        // PUBLIC: refresh는 본문의 Refresh Token을 기존 업무 로직에서 별도로 검증한다.
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/signin", "/api/auth/refresh").permitAll()

                        // connect의 다른 메서드가 /{deviceId} 규칙으로 흡수되지 않게 한다.
                        .requestMatchers(HttpMethod.POST, "/api/devices/connect").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/devices/connect").denyAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/signout", "/api/images/mobile/analyze",
                                "/api/fcm/token", "/api/users/{userId}/devices/{deviceId}/capture").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/devices", "/api/devices/{deviceId}",
                                "/api/images", "/api/images/device/{deviceId}", "/api/images/{originImageId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/devices/{deviceId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/withdraw", "/api/devices/{deviceId}",
                                "/api/fcm/token").hasAnyRole("USER", "ADMIN")

                        // HEAD·OPTIONS·상태 점검을 포함해 명시적으로 허용하지 않은 요청은 거부한다.
                        .anyRequest().denyAll()
                )

                // 기존 Access 전용 인증과 PUBLIC의 무효 Bearer 처리 방식을 유지한다.
                .addFilterBefore(new JwtFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }


}
