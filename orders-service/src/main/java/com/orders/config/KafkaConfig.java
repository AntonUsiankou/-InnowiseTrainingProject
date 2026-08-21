package com.orders.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Task requirement: "make sure data is not lost". Two things Spring Boot's
 * defaults do NOT give you out of the box:
 *
 *  1. A topic with more than 1 partition - auto-created topics get a single
 *     partition, which caps consumer throughput at one thread no matter how
 *     many app instances/threads you run. We declare CREATE_PAYMENT with 6
 *     partitions explicitly so concurrent consumption is actually possible
 *     (still ordered per-orderId since the producer keys by orderId).
 *
 *  2. A dead-letter topic. Without this, Spring Kafka's default error
 *     handler retries a failing record ~9 times with backoff and then just
 *     logs and skips it - the event is gone. Here, after exhausting retries
 *     the record is published to CREATE_PAYMENT.DLT instead, so a failed
 *     payment-status update can be inspected/replayed rather than lost.
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.create-payment}")
    private String createPaymentTopic;

    @Bean
    public NewTopic createPaymentTopic() {
        return TopicBuilder.name(createPaymentTopic)
                .partitions(6)
                .replicas(1) // bump to 3 with min.insync.replicas=2 on a multi-broker cluster
                .build();
    }

    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                (KafkaOperations) kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition()));

        // 5 retries with exponential backoff (0.5s -> 8s) before giving up
        // and routing to the DLT, instead of the default silent-skip.
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(8000L);
        backOff.setMaxElapsedTime(30_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            org.springframework.kafka.core.ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // matches the topic's partition count so every partition can be
        // consumed in parallel by this instance (scale out further by
        // running more order-service replicas in the same consumer group)
        factory.setConcurrency(6);
        return factory;
    }
}
