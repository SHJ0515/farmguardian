package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.config.jwt.JwtTokenProvider;
import com.farmguardian.farmguardian.config.jwt.RefreshTokenHasher;
import com.farmguardian.farmguardian.config.jwt.ValidatedRefreshToken;
import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.RefreshSession;
import com.farmguardian.farmguardian.domain.RefreshTokenHistory;
import com.farmguardian.farmguardian.domain.User;
import com.farmguardian.farmguardian.dto.request.SignInRequestDto;
import com.farmguardian.farmguardian.dto.response.TokenResponseDto;
import com.farmguardian.farmguardian.exception.auth.ClientUuidMismatchException;
import com.farmguardian.farmguardian.exception.auth.InvalidPasswordException;
import com.farmguardian.farmguardian.exception.auth.InvalidTokenException;
import com.farmguardian.farmguardian.exception.auth.UserNotFoundException;
import com.farmguardian.farmguardian.repository.DeviceRepository;
import com.farmguardian.farmguardian.repository.RefreshSessionRepository;
import com.farmguardian.farmguardian.repository.RefreshTokenHistoryRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionTransactionService {

    private static final String GRANT_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshTokenHistoryRepository refreshTokenHistoryRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;

    @Transactional
    public TokenResponseDto signIn(SignInRequestDto request) {
        User candidate = userRepository.findByEmail(request.getEmail())
                .orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(request.getPassword(), candidate.getPassword())) {
            throw new InvalidPasswordException();
        }

        //해당 user에 lock 획득
        User user = userRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(UserNotFoundException::new);
        LocalDateTime eventAt = nowUtc();

        //해당 활성 session lock 획득
        refreshSessionRepository.findActiveByUserIdAndClientUuidForUpdate(user.getId(), request.getClientUuid())
                .ifPresent(existingSession -> {
                    existingSession.revoke(notBefore(eventAt, existingSession.getCreatedAt()));
                    refreshSessionRepository.flush(); // 기존 active_slot=NULL UPDATE를 먼저 실행해 새 Session INSERT의 UNIQUE 충돌 방지
                });

        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRole().name(), user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());
        ValidatedRefreshToken validated = jwtTokenProvider.parseAndValidateRefreshToken(refreshToken);

        RefreshSession session = refreshSessionRepository.save(new RefreshSession(
                user,
                request.getClientUuid(),
                UUID.randomUUID().toString(),
                toUtc(validated.getIssuedAt())));
        refreshTokenHistoryRepository.save(createHistory(session, validated, refreshToken));

        return new TokenResponseDto(GRANT_TYPE, accessToken, refreshToken);
    }

    @Transactional
    public RefreshTokenRotationResult rotate(ValidatedRefreshToken validated, String tokenHash, String clientUuid) {
        User user = userRepository.findByEmailForUpdate(validated.getEmail())
                .orElseThrow(InvalidTokenException::new);
        LockedRefreshState state = lockRefreshState(user, validated, tokenHash, clientUuid);
        LocalDateTime eventAt = nowUtc();

        if (!state.session().isActive()) {
            throw new InvalidTokenException();
        }

        if (!state.history().isCurrent()) {
            LocalDateTime reuseAt = notBefore(eventAt, state.history().getRotatedAt(), state.session().getRevokedAt());
            state.history().markReuseDetected(reuseAt);
            state.session().markReuseDetected(reuseAt);
            return RefreshTokenRotationResult.reuseRejected();
        }

        //위에서 검증 통과 후 새로운 RefreshToken 생성 및 rotate 진행
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getEmail(), user.getRole().name(), user.getId());
        ValidatedRefreshToken replacement = jwtTokenProvider.parseAndValidateRefreshToken(newRefreshToken);
        LocalDateTime rotatedAt = notBefore(eventAt, state.history().getIssuedAt());

        state.history().rotateTo(replacement.getJti(), rotatedAt);
        refreshTokenHistoryRepository.flush(); // 기존 current_slot=NULL UPDATE를 먼저 실행해 새 History INSERT의 UNIQUE 충돌 방지
        refreshTokenHistoryRepository.save(createHistory(state.session(), replacement, newRefreshToken));

        return RefreshTokenRotationResult.issued(new TokenResponseDto(GRANT_TYPE, newAccessToken, newRefreshToken));
    }

    @Transactional
    public void signOut(Long userId, ValidatedRefreshToken validated, String tokenHash, String clientUuid) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);
        if (!user.getEmail().equals(validated.getEmail())) {
            throw new InvalidTokenException();
        }

        LockedRefreshState state = lockRefreshState(user, validated, tokenHash, clientUuid);
        if (!state.session().isActive() || !state.history().isCurrent()) {
            throw new InvalidTokenException();
        }

        state.session().revoke(notBefore(nowUtc(), state.session().getCreatedAt()));
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new);
        LocalDateTime eventAt = nowUtc();

        refreshSessionRepository.findAllActiveByUserIdForUpdateOrderById(userId)
                .forEach(session -> session.revoke(notBefore(eventAt, session.getCreatedAt())));

        deviceRepository.findAllByUserId(userId)
                .forEach(Device::disconnectFromUser);

        if (userRepository.softDeleteById(user.getId()) != 1) {
            throw new UserNotFoundException();
        }
    }

    private LockedRefreshState lockRefreshState(User user, ValidatedRefreshToken validated, String tokenHash, String clientUuid) {
        Long sessionId = refreshTokenHistoryRepository
                .findRefreshSessionIdByJtiAndTokenHash(validated.getJti(), tokenHash)
                .orElseThrow(InvalidTokenException::new);
        RefreshSession session = refreshSessionRepository
                .findByIdAndUserIdForUpdate(sessionId, user.getId())
                .orElseThrow(InvalidTokenException::new);

        if (!session.getClientUuid().equals(clientUuid)) {
            throw new ClientUuidMismatchException();
        }

        RefreshTokenHistory history = refreshTokenHistoryRepository
                .findBySessionIdAndJtiAndTokenHashForUpdate(sessionId, validated.getJti(), tokenHash)
                .orElseThrow(InvalidTokenException::new);
        validateStoredMetadata(user, validated, history);
        return new LockedRefreshState(session, history);
    }

    private void validateStoredMetadata(User user, ValidatedRefreshToken validated, RefreshTokenHistory history) {
        if (!user.getEmail().equals(validated.getEmail())
                || !history.getIssuedAt().equals(toUtc(validated.getIssuedAt()))
                || !history.getExpiresAt().equals(toUtc(validated.getExpiresAt()))) {
            throw new InvalidTokenException();
        }
    }

    private RefreshTokenHistory createHistory(RefreshSession session, ValidatedRefreshToken validated, String rawRefreshToken) {
        return new RefreshTokenHistory(
                session,
                validated.getJti(),
                refreshTokenHasher.hash(rawRefreshToken),
                toUtc(validated.getIssuedAt()),
                toUtc(validated.getExpiresAt()));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private LocalDateTime notBefore(LocalDateTime candidate, LocalDateTime... lowerBounds) {
        LocalDateTime result = Objects.requireNonNull(candidate, "candidate must not be null");
        for (LocalDateTime lowerBound : lowerBounds) {
            if (lowerBound != null && result.isBefore(lowerBound)) {
                result = lowerBound;
            }
        }
        return result;
    }

    private record LockedRefreshState(
            RefreshSession session,
            RefreshTokenHistory history) {
    }
}
