package com.ausiankou.payment.health;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaAdmin; // Используем АДМИНА
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentServiceHealthIndicator implements HealthIndicator {

    private final MongoTemplate mongoTemplate;
    private final KafkaAdmin kafkaAdmin; // Идеально для проверки инфраструктуры
    private final Cache<String, Integer> cache;

    public PaymentServiceHealthIndicator(MongoTemplate mongoTemplate,
                                         KafkaAdmin kafkaAdmin,
                                         Cache<String, Integer> cache) {
        this.mongoTemplate = mongoTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.cache = cache;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // 1. Честная проверка MongoDB
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            builder.withDetail("mongodb", "UP");
        } catch (Exception e) {
            builder.withDetail("mongodb", "DOWN: " + e.getMessage()).down();
        }

        // 2. Честная проверка Kafka через AdminClient
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // Запрашиваем список топиков. Таймаут 2 секунды, чтобы хелсчек не завис навсегда
            client.listTopics().names().get(2, TimeUnit.SECONDS);
            builder.withDetail("kafka", "UP");
        } catch (Exception e) {
            builder.withDetail("kafka", "DOWN: " + e.getMessage()).down();
        }

        // 3. Метрики кэша и потоков
        builder.withDetail("threadpool.size", Thread.activeCount());
        builder.withDetail("cache.size", cache != null ? cache.estimatedSize() : 0);

        return builder.build();
    }
}
