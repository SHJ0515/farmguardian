package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.config.jwt.RefreshTokenHasher;
import com.farmguardian.farmguardian.config.jwt.ValidatedRefreshToken;
import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.Role;
import com.farmguardian.farmguardian.domain.User;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.*;
import com.farmguardian.farmguardian.repository.DeviceRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증, 인가에 대한 내용
 * 회원가입, 로그인, 로그아웃, 회원탈퇴
 * 자체로그인만 구현, 소셜로그인 x
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final DeviceRepository deviceRepository;
    private final RefreshSessionTransactionService refreshSessionTransactionService;

    @Transactional
    public Long signUp(SignUpRequestDto request) {
        // 이메일 중복 검증
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }

        // 비밀번호 암호화 및 사용자 생성
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getEmail(), encodedPassword, Role.USER);
        User saved = userRepository.save(user);

        // 모바일 디바이스 자동 생성 및 연결
        createMobileDevice(saved);

        return saved.getId();
    }

    private void createMobileDevice(User user) {
        String mobileDeviceUuid = "mobile-user-" + user.getId();
        Device mobileDevice = Device.builder()
                .deviceUuid(mobileDeviceUuid)
                .build();
        mobileDevice.connectToUser(user, "직접 촬영", null, null, null);
        deviceRepository.save(mobileDevice);
    }

    @Transactional
    public TokenResponseDto signIn(SignInRequestDto request) {
        return refreshSessionTransactionService.signIn(request);
    }

    @Transactional
    public void signOut(Long userId, String refreshTokenValue, String clientUuid) {
        ValidatedRefreshToken validated = validateRefreshToken(refreshTokenValue);
        String tokenHash = hashRefreshToken(refreshTokenValue);
        refreshSessionTransactionService.signOut(userId, validated, tokenHash, clientUuid);
    }

    @Transactional
    public void withdraw(Long userId) {
        refreshSessionTransactionService.withdraw(userId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TokenResponseDto refreshAccessToken(String refreshTokenValue, String clientUuid) {
        ValidatedRefreshToken validated = validateRefreshToken(refreshTokenValue);
        String tokenHash = hashRefreshToken(refreshTokenValue);
        RefreshTokenRotationResult result = refreshSessionTransactionService.rotate(validated, tokenHash, clientUuid);

        if (!result.isIssued()) {
            // Transaction Service가 재사용 기록을 commit한 다음 실패 응답으로 변환합니다.
            throw new InvalidTokenException();
        }
        return result.tokenResponse();
    }

    private ValidatedRefreshToken validateRefreshToken(String refreshTokenValue) {
        try {
            return jwtTokenProvider.parseAndValidateRefreshToken(refreshTokenValue);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    private String hashRefreshToken(String refreshTokenValue) {
        try {
            return refreshTokenHasher.hash(refreshTokenValue);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }
}
