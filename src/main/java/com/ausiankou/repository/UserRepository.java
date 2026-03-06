package com.ausiankou.repository;

import com.ausiankou.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>,
                                        JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    List<User> findByActiveTrue();
    List<User> findByNameAndSurname(String name, String surname);
    @Query("SELECT u, SIZE(u.paymentCards) FROM User u WHERE u.active = :active")
    List<Object[]> findUsersWithCardCount(@Param("active") Boolean active);
    @Modifying
    @Query("UPDATE User u SET u.active = :active WHERE u.id = :id")
    int updateUserStatus(@Param("id") Long id,
                         @Param("active") Boolean active);
    @Query(value = "SELECT * FROM users where concat(name, ' ', surname) ilike %:fullName%", nativeQuery = true)
    List<User> searchByFullNameNative(@Param("fullName") String fullName);
}