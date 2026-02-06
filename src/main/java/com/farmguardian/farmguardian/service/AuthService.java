package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.RefreshToken;
import com.farmguardian.farmguardian.domain.Role;
import com.farmguardian.farmguardian.domain.User;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.request.SignUpRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.*;
import com.farmguardian.farmguardian.repository.DeviceRepository;
import com.farmguardian.farmguardian.repository.RefreshTokenRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final DeviceRepository deviceRepository;

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
        // 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(UserNotFoundException::new);

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException();
        }

        // 토큰 생성 (userId 포함)
        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRole().name(), user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        // 디바이스별 리프레시 토큰 저장 또는 갱신
        refreshTokenRepository.findByUserAndClientUuid(user, request.getClientUuid())
                .ifPresentOrElse(
                        token -> token.updateToken(refreshToken),
                        () -> refreshTokenRepository.save(new RefreshToken(user, refreshToken, request.getClientUuid()))
                );

        return new TokenResponseDto("Bearer", accessToken, refreshToken);
    }

    @Transactional
    public void signOut(Long userId, String refreshTokenValue, String clientUuid) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 디바이스별 리프레시 토큰 검증 및 삭제
        RefreshToken refreshToken = refreshTokenRepository.findByUserAndClientUuid(user, clientUuid)
                .orElseThrow(RefreshTokenNotFoundException::new);
        refreshTokenRepository.delete(refreshToken);
    }

    @Transactional
    public void withdraw(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 사용자의 모든 디바이스 연결 해제 (Device는 남기고 연결 정보만 초기화)
        deviceRepository.findAllByUserId(userId)
                .forEach(Device::disconnectFromUser);

        // 모든 디바이스의 리프레시 토큰 삭제
        refreshTokenRepository.deleteByUser(user);

        // 사용자 soft delete
        userRepository.delete(user);
    }

    @Transactional
    public TokenResponseDto refreshAccessToken(String refreshTokenValue, String clientUuid) {
        // Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshTokenValue)) {
            throw new InvalidTokenException();
        }

        // DB에서 Refresh Token 조회
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(RefreshTokenNotFoundException::new);

        // Client UUID 일치 확인 (디바이스 검증)
        if (!refreshToken.getClientUuid().equals(clientUuid)) {
            throw new ClientUuidMismatchException();
        }

        // 사용자 조회
        User user = refreshToken.getUser();

        // 새로운 Access Token 생성
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId()
        );

        // RTR 패턴: 새로운 Refresh Token 생성 및 DB 갱신
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());
        refreshToken.updateToken(newRefreshToken);

        return new TokenResponseDto("Bearer", newAccessToken, newRefreshToken);
    }
}