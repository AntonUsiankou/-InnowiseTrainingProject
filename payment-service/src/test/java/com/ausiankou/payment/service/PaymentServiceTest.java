package com.ausiankou.payment.service;

import com.ausiankou.payment.client.RandomNumberClient;
import com.ausiankou.payment.dto.PaymentCreateRequest;
import com.ausiankou.payment.dto.PaymentDto;
import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.entity.PaymentStatus;
import com.ausiankou.payment.mapper.PaymentMapper;
import com.ausiankou.payment.repository.PaymentRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private RandomNumberClient randomNumberClient;
    @Mock
    private PaymentPersister paymentPersister;

    @InjectMocks
    private PaymentService paymentService;

    private Payment fakeSaved(UUID orderId, UUID userId, BigDecimal amount, PaymentStatus status) {
        return Payment.builder().id(UUID.randomUUID()).orderId(orderId).userId(userId)
                .status(status).paymentAmount(amount).timestamp(java.time.Instant.now()).build();
    }

    @Test
    void createPayment_marksSuccess_whenRandomNumberIsEven() {
        PaymentCreateRequest request = new PaymentCreateRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        when(randomNumberClient.getRandomNumber()).thenReturn(42); // even -> SUCCESS
        Payment saved = fakeSaved(request.orderId(), request.userId(), request.paymentAmount(), PaymentStatus.SUCCESS);
        when(paymentPersister.persist(request.orderId(), request.userId(), request.paymentAmount(), PaymentStatus.SUCCESS))
                .thenReturn(saved);
        when(paymentMapper.toDto(saved)).thenReturn(
                new PaymentDto(saved.getId(), saved.getOrderId(), saved.getUserId(), saved.getStatus(), saved.getTimestamp(), saved.getPaymentAmount()));

        PaymentDto result = paymentService.createPayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void createPayment_marksFailed_whenRandomNumberIsOdd() {
        PaymentCreateRequest request = new PaymentCreateRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        when(randomNumberClient.getRandomNumber()).thenReturn(7); // odd -> FAILED
        Payment saved = fakeSaved(request.orderId(), request.userId(), request.paymentAmount(), PaymentStatus.FAILED);
        when(paymentPersister.persist(request.orderId(), request.userId(), request.paymentAmount(), PaymentStatus.FAILED))
                .thenReturn(saved);
        when(paymentMapper.toDto(saved)).thenReturn(
                new PaymentDto(saved.getId(), saved.getOrderId(), saved.getUserId(), saved.getStatus(), saved.getTimestamp(), saved.getPaymentAmount()));

        PaymentDto result = paymentService.createPayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void createPayment_delegatesPersistenceWithCorrectArgs() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(orderId, userId, BigDecimal.valueOf(99.99));
        when(randomNumberClient.getRandomNumber()).thenReturn(2);
        Payment saved = fakeSaved(orderId, userId, request.paymentAmount(), PaymentStatus.SUCCESS);
        when(paymentPersister.persist(any(), any(), any(), any())).thenReturn(saved);
        when(paymentMapper.toDto(any())).thenReturn(
                new PaymentDto(saved.getId(), orderId, userId, PaymentStatus.SUCCESS, saved.getTimestamp(), request.paymentAmount()));

        paymentService.createPayment(request);

        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(paymentPersister).persist(orderIdCaptor.capture(), eq(userId), eq(request.paymentAmount()), eq(PaymentStatus.SUCCESS));
        assertThat(orderIdCaptor.getValue()).isEqualTo(orderId);
    }

    @Test
    void totalForUser_sumsOnlySuccessfulPayments() {
        UUID userId = UUID.randomUUID();
        java.time.Instant from = java.time.Instant.now().minusSeconds(3600);
        java.time.Instant to = java.time.Instant.now();

        Payment success1 = Payment.builder().id(UUID.randomUUID()).userId(userId).status(PaymentStatus.SUCCESS).paymentAmount(BigDecimal.valueOf(10)).build();
        Payment success2 = Payment.builder().id(UUID.randomUUID()).userId(userId).status(PaymentStatus.SUCCESS).paymentAmount(BigDecimal.valueOf(5)).build();
        Payment failed = Payment.builder().id(UUID.randomUUID()).userId(userId).status(PaymentStatus.FAILED).paymentAmount(BigDecimal.valueOf(100)).build();

        when(paymentRepository.findAllByUserIdAndTimestampBetween(userId, from, to))
                .thenReturn(java.util.List.of(success1, success2, failed));

        var result = paymentService.totalForUser(userId, from, to);

        assertThat(result.total()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }
}
