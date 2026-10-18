package com.farmguardian.farmguardian.repository;

import com.farmguardian.farmguardian.domain.RefreshTokenHistory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenHistoryRepository extends JpaRepository<RefreshTokenHistory, Long> {

    Optional<RefreshTokenHistory> findByJtiAndTokenHash(String jti, String tokenHash);

    @Query("""
            SELECT history.refreshSession.id
            FROM RefreshTokenHistory history
            WHERE history.jti = :jti
              AND history.tokenHash = :tokenHash
            """)
    Optional<Long> findRefreshSessionIdByJtiAndTokenHash(
            @Param("jti") String jti,
            @Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT history
            FROM RefreshTokenHistory history
            WHERE history.refreshSession.id = :refreshSessionId
              AND history.jti = :jti
              AND history.tokenHash = :tokenHash
            """)
    Optional<RefreshTokenHistory> findBySessionIdAndJtiAndTokenHashForUpdate(
            @Param("refreshSessionId") Long refreshSessionId,
            @Param("jti") String jti,
            @Param("tokenHash") String tokenHash);

    @Query("""
            SELECT history
            FROM RefreshTokenHistory history
            WHERE history.refreshSession.id = :refreshSessionId
              AND history.currentSlot IS NOT NULL
            """)
    Optional<RefreshTokenHistory> findCurrentByRefreshSessionId(
            @Param("refreshSessionId") Long refreshSessionId);

    List<RefreshTokenHistory> findAllByRefreshSessionIdOrderByIssuedAtAsc(Long refreshSessionId);
}
