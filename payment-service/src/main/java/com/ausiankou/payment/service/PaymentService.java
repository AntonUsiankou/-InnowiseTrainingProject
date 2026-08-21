package com.ausiankou.payment.service;

import com.ausiankou.payment.client.RandomNumberClient;
import com.ausiankou.payment.dto.PageResponse;
import com.ausiankou.payment.dto.PaymentCreateRequest;
import com.ausiankou.payment.dto.PaymentDto;
import com.ausiankou.payment.dto.TotalSumResponse;
import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.entity.PaymentStatus;
import com.ausiankou.payment.exception.PaymentException;
import com.ausiankou.payment.mapper.PaymentMapper;
import com.ausiankou.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomNumberClient randomNumberClient;
    private final PaymentPersister paymentPersister;

    /**
     * The external random-number call happens here, OUTSIDE any DB
     * transaction, so a slow/retrying 3rd-party call never holds a Mongo
     * transaction open. Only the actual persistence (Payment + outbox event,
     * written atomically) is transactional - see PaymentPersister.
     */
    public PaymentDto createPayment(PaymentCreateRequest request) {
        // even -> SUCCESS, odd -> FAILED, per Task 3
        int randomNumber = randomNumberClient.getRandomNumber();
        PaymentStatus status = randomNumber % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        Payment saved = paymentPersister.persist(request.orderId(), request.userId(), request.paymentAmount(), status);
        return paymentMapper.toDto(saved);
    }

    public PaymentDto getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> PaymentException.notFound("Payment"));
        return paymentMapper.toDto(payment);
    }

    /** "by user_id or order_id or status (it could be user_id or order_id or status)" - only one filter applies at a time. */
    public PageResponse<PaymentDto> search(UUID userId, UUID orderId, PaymentStatus status, Pageable pageable) {
        Page<Payment> page;
        if (userId != null) {
            page = paymentRepository.findAllByUserId(userId, pageable);
        } else if (orderId != null) {
            page = paymentRepository.findAllByOrderId(orderId, pageable);
        } else if (status != null) {
            page = paymentRepository.findAllByStatus(status, pageable);
        } else {
            page = paymentRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(paymentMapper::toDto));
    }

    public TotalSumResponse totalForUser(UUID userId, Instant from, Instant to) {
        List<Payment> payments = paymentRepository.findAllByUserIdAndTimestampBetween(userId, from, to);
        BigDecimal sum = sumSuccessful(payments);
        return new TotalSumResponse(from, to, sum);
    }

    /** Admin-only aggregate across all users. */
    public TotalSumResponse totalForAllUsers(Instant from, Instant to) {
        List<Payment> payments = paymentRepository.findAllByTimestampBetween(from, to);
        BigDecimal sum = sumSuccessful(payments);
        return new TotalSumResponse(from, to, sum);
    }

    private BigDecimal sumSuccessful(List<Payment> payments) {
        return payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
