package com.ausiankou.payment.kafka;

import com.ausiankou.payment.entity.OutboxEvent;
import com.ausiankou.payment.entity.OutboxStatus;
import com.ausiankou.payment.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private org.springframework.kafka.core.KafkaTemplate<Object, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxPublisher publisher() {
        OutboxPublisher p = new OutboxPublisher(outboxRepository, kafkaTemplate, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "topic", "CREATE_PAYMENT");
        return p;
    }

    private OutboxEvent pendingEvent() throws Exception {
        PaymentEvent event = new PaymentEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SUCCESS", BigDecimal.TEN);
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .status(OutboxStatus.PENDING)
                .orderId(event.orderId())
                .payload(objectMapper.writeValueAsString(event))
                .createdAt(Instant.now())
                .attempts(0)
                .build();
    }

    @Test
    void publishPending_marksPublished_onSuccessfulSend() throws Exception {
        OutboxEvent event = pendingEvent();
        when(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(event));

        CompletableFuture<SendResult<Object, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("CREATE_PAYMENT"), anyString(), any())).thenReturn(future);

        publisher().publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void publishPending_incrementsAttempts_onFailure_staysPending() throws Exception {
        OutboxEvent event = pendingEvent();
        when(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(event));

        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(eq("CREATE_PAYMENT"), anyString(), any())).thenReturn(failed);

        publisher().publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
    }

    @Test
    void publishPending_marksFailed_afterMaxAttemptsExhausted() throws Exception {
        OutboxEvent event = pendingEvent();
        event.setAttempts(9); // one more failure hits MAX_ATTEMPTS (10)
        when(outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(event));

        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka still down"));
        when(kafkaTemplate.send(eq("CREATE_PAYMENT"), anyString(), any())).thenReturn(failed);

        publisher().publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(10);
    }
}
