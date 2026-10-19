package com.farmguardian.farmguardian.repository;

import com.farmguardian.farmguardian.domain.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM RefreshSession session
            WHERE session.id = :sessionId
            """)
    Optional<RefreshSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM RefreshSession session
            WHERE session.id = :sessionId
              AND session.user.id = :userId
            """)
    Optional<RefreshSession> findByIdAndUserIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM RefreshSession session
            WHERE session.user.id = :userId
              AND session.clientUuid = :clientUuid
              AND session.activeSlot = 1
            """)
    Optional<RefreshSession> findActiveByUserIdAndClientUuidForUpdate(
            @Param("userId") Long userId,
            @Param("clientUuid") String clientUuid);

    List<RefreshSession> findAllByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM RefreshSession session
            WHERE session.user.id = :userId
              AND session.activeSlot = 1
            ORDER BY session.id ASC
            """)
    List<RefreshSession> findAllActiveByUserIdForUpdateOrderById(@Param("userId") Long userId);
}
