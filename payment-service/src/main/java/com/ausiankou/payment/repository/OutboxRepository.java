package com.ausiankou.payment.repository;

import com.ausiankou.payment.entity.OutboxEvent;
import com.ausiankou.payment.entity.OutboxStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends MongoRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
