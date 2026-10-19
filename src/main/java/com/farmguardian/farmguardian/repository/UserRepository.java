package com.farmguardian.farmguardian.repository;

import com.farmguardian.farmguardian.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Modifying
    @Query("""
            UPDATE User user
            SET user.deletedAt = CURRENT_TIMESTAMP
            WHERE user.id = :userId AND user.deletedAt IS NULL
            """)
    int softDeleteById(@Param("userId") Long userId);

    boolean existsByEmail(String email);

}
