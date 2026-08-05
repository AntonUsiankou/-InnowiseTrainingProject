package com.ausiankou.payment.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("!test")  // Не создаём индексы в тестах
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        MongoCollection<Document> collection = mongoTemplate.getCollection("payments");

        // 1. Compound index для user_id + timestamp (самый частый запрос)
        collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("user_id"),
                        Indexes.descending("timestamp")
                ),
                new IndexOptions()
                        .name("idx_user_timestamp")
                        .background(true)  // Не блокирует записи
        );

        // 2. Compound index для status + timestamp (для админских запросов)
        collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("status"),
                        Indexes.descending("timestamp")
                ),
                new IndexOptions()
                        .name("idx_status_timestamp")
                        .background(true)
        );

        // 3. Unique index для order_id (избегаем дублей)
        collection.createIndex(
                Indexes.ascending("order_id"),
                new IndexOptions()
                        .name("idx_order_id_unique")
                        .unique(true)
                        .background(true)
        );

        // 4. Partial index только для успешных платежей (экономит память)
        collection.createIndex(
                Indexes.ascending("user_id"),
                new IndexOptions()
                        .name("idx_user_successful_payments")
                        .partialFilterExpression(new Document("status", "SUCCESS"))
                        .background(true)
        );

        // 5. TTL индекс для автоматического удаления старых платежей (через 90 дней)
        collection.createIndex(
                Indexes.ascending("timestamp"),
                new IndexOptions()
                        .name("idx_ttl")
                        .expireAfter(90L, java.util.concurrent.TimeUnit.DAYS)
                        .background(true)
        );

        log.info("✅ All MongoDB indexes created successfully");
    }
}
