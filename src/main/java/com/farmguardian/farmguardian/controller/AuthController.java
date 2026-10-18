package com.farmguardian.farmguardian.controller;

import com.farmguardian.farmguardian.config.auth.UserDetailsImpl;
import com.farmguardian.farmguardian.dto.request.RefreshTokenRequestDto;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignOutRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequestDto request) {
        authService.signUp(request);
        return ResponseEntity.ok().build();
    }

    // 로그인
    @PostMapping("/signin")
    public ResponseEntity<TokenResponseDto> signIn(@Valid @RequestBody SignInRequestDto request) {
        TokenResponseDto response = authService.signIn(request);
        return ResponseEntity.ok(response);
    }

    // 로그아웃
    @PostMapping("/signout")
    public ResponseEntity<Void> signOut(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody SignOutRequestDto request) {
        Long userId = userDetails.getUserId();
        authService.signOut(userId, request.getRefreshToken(), request.getClientUuid());
        return ResponseEntity.ok().build();
    }

    // 회원탈퇴
    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUserId();
        authService.withdraw(userId);
        return ResponseEntity.ok().build();
    }

    // 토큰 갱신
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        TokenResponseDto response = authService.refreshAccessToken(request.getRefreshToken(), request.getClientUuid());
        return ResponseEntity.ok(response);
    }
}
