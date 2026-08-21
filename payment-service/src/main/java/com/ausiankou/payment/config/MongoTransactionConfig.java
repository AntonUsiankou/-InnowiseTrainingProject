package com.ausiankou.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables real multi-document Mongo transactions (requires a replica set -
 * see docker-compose.yml / k8s payment-db, which now run with --replSet).
 * Needed so PaymentService can write the Payment document AND its outbox
 * event atomically: either both are persisted, or neither is - no window
 * where a payment exists but no event was ever recorded for it to publish.
 */
@Configuration
@EnableTransactionManagement
public class MongoTransactionConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
