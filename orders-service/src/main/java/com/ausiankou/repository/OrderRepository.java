package com.ausiankou.repository;

import com.ausiankou.entity.Order;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByIdAndDeletedFalse(Long id);
    Page<Order> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);
    List<Order> findByUserIdAndDeletedFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.deleted = true WHERE o.id = :id")
    void softDeleteById(@Param("id") Long id);
    boolean existsByIdAndDeletedFalse(Long id);
}
