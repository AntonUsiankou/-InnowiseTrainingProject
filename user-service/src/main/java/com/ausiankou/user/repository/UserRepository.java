package com.ausiankou.user.repository;

import com.ausiankou.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    // named query method
    Optional<User> findByEmail(String email);

    // native SQL example
    @Query(value = "SELECT * FROM users WHERE active = true AND email = :email", nativeQuery = true)
    Optional<User> findActiveUserByEmailNative(String email);

    // JPQL example
    @Query("select u from User u where u.active = true")
    java.util.List<User> findAllActive();

    /**
     * Row-level lock (SELECT ... FOR UPDATE) used only when adding a card.
     * Without this, two concurrent "add card" requests for the SAME user can
     * both read "4 active cards" before either writes, and both proceed to
     * insert - exceeding the 5-card limit (classic check-then-act race).
     * The lock only blocks other writers to this ONE user's row, so overall
     * concurrency across different users is unaffected.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(UUID id);
}
