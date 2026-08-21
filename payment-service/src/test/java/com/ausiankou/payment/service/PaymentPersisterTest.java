package com.ausiankou.payment.service;

import com.ausiankou.payment.entity.OutboxEvent;
import com.ausiankou.payment.entity.OutboxStatus;
import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.entity.PaymentStatus;
import com.ausiankou.payment.repository.OutboxRepository;
import com.ausiankou.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentPersisterTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private PaymentPersister paymentPersister;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void persist_savesBothPaymentAndOutboxEvent() throws Exception {
        // wire the real ObjectMapper since @InjectMocks would otherwise mock it
        var field = PaymentPersister.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(paymentPersister, objectMapper);

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentPersister.persist(orderId, userId, BigDecimal.valueOf(50), PaymentStatus.SUCCESS);

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getOrderId()).isEqualTo(orderId);
        assertThat(outbox.getPayload()).contains(orderId.toString()).contains("SUCCESS");
    }
}
