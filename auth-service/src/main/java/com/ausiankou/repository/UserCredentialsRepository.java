package com.ausiankou.repository;

import com.ausiankou.entity.UserCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, Long> {
    Optional<UserCredentials> findByEmail(String email);
    Optional<UserCredentials> findUserId(Long userId);
    boolean existsByEmail(String email);

    @Modifying
    @Transactional
    @Query("UPDATE UserCredentials uc SET uc.enabled = :enabled WHERE uc.userId = :userId")
    void updateEnabledStatus(@Param("userId") Long userId, @Param("enabled") boolean enabled);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}
