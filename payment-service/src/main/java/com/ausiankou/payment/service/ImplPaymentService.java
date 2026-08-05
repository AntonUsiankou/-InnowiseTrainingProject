package com.ausiankou.payment.service;

import com.ausiankou.payment.dto.*;
import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.exception.CustomExceptions;
import com.ausiankou.payment.mapper.PaymentMapper;
import com.ausiankou.payment.repository.PaymentRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
@EnableAsync
public class ImplPaymentService implements IPaymentService{

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomNumberClient randomNumberClient;
    private final KafkaProducerService kafkaProducerService;
    private final MeterRegistry meterRegistry;

    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(50);

    /**
     * Create payment asynchronously with bulkhead isolation
     */
    @Bulkhead(name = "paymentCreation", type = Bulkhead.Type.SEMAPHORE)
    @RateLimiter(name = "paymentApi")
    @Transactional
    public CompletableFuture<PaymentResponse> createPaymentAsync(PaymentRequest request) {
        long startTime = System.nanoTime();

        return CompletableFuture.supplyAsync(() -> {
            try {
                PaymentResponse response = createPaymentSync(request);

                long duration = System.nanoTime() - startTime;
                meterRegistry.timer("payment.create.duration").record(Duration.ofNanos(duration));
                meterRegistry.counter("payment.created", "status", "success").increment();

                return response;
            } catch (Exception e) {
                meterRegistry.counter("payment.created", "status", "error", "error", e.getClass().getSimpleName()).increment();
                throw new RuntimeException("Failed to create payment", e);
            }
        }, asyncExecutor);
    }

    /**
     * Synchronous payment creation with retry logic
     */
    @Override
    public PaymentResponse createPaymentSync(PaymentRequest request) {
        log.info("Creating payment for orderId: {}, userId: {}", request.getOrderId(), request.getUserId());

        // Check for duplicate payments with retry
        int retries = 3;
        while (retries > 0) {
            if (paymentRepository.findByOrderId(request.getOrderId()).isPresent()) {
                throw new CustomExceptions.ConflictException("Payment already exists for order: " + request.getOrderId());
            }
            retries--;
        }

        // Generate random number and determine status
        boolean isSuccess = randomNumberClient.isPaymentSuccess();
        PaymentStatus status = isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        log.info("Payment status determined: {} for order: {}", status, request.getOrderId());

        // Create payment entity
        Payment payment = paymentMapper.toEntity(request);
        payment.setStatus(status.name());
        payment.setTimestamp(LocalDateTime.now());

        // Save with retry logic for MongoDB
        Payment savedPayment = saveWithRetry(payment);
        log.info("Payment saved with id: {}, status: {}", savedPayment.getId(), savedPayment.getStatus());

        // Send Kafka event asynchronously (non-blocking)
        CompletableFuture.runAsync(() -> {
            try {
                kafkaProducerService.sendPaymentEvent(
                        savedPayment.getId(),
                        savedPayment.getOrderId(),
                        savedPayment.getUserId(),
                        status
                );
                meterRegistry.counter("kafka.event.sent").increment();
            } catch (Exception e) {
                log.error("Failed to send Kafka event for payment: {}", savedPayment.getId(), e);
                meterRegistry.counter("kafka.event.failed").increment();
                // Store failed event in database for retry
                storeFailedEvent(savedPayment, e.getMessage());
            }
        }, asyncExecutor);

        // Invalidate cache for user payments
        invalidateUserPaymentCache(request.getUserId());

        return paymentMapper.toResponse(savedPayment);
    }

    /**
     * Save payment with retry logic
     */
    private Payment saveWithRetry(Payment payment) {
        int maxRetries = 3;
        int retryDelay = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++){
            try{
                return paymentRepository.save(payment);
            } catch (Exception ex) {
                log.warn("Failed to save payment (attempt {}/{}): {}", attempt, maxRetries, ex.getMessage());
                if(attempt == maxRetries) {
                    throw new RuntimeException("Failed to save payment after " + maxRetries + " attempts", ex);
                }
                try{
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying save", ie);
                }
            }
        }
        throw new RuntimeException("Failed to save payment");
    }

    /**
     * Store failed Kafka events for later retry
     */
    private void storeFailedEvent(Payment savedPayment, String errorMessage) {
        log.error("Payment {} Kafka event failed: {}", savedPayment.getId(), errorMessage);
        // Could implement retry mechanism here

    }

    /**
     * Get payment by ID with caching
     */
    @Override
    @Cacheable(value = "payments", key = "#id", unless = "#result == null")
    public PaymentResponse getPaymentById(String id) {
        long startTime = System.nanoTime();

        try {
            Payment payment = paymentRepository.findById(id)
                    .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("Payment", "id", id));

            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("payment.get.byId.duration").record(Duration.ofNanos(duration));

            return paymentMapper.toResponse(payment);
        } catch (Exception e) {
            meterRegistry.counter("payment.get.error", "error", e.getClass().getSimpleName()).increment();
            throw e;
        }
    }

    @Override
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CustomExceptions.ResourceNotFoundException("Payment", "orderId", orderId));
        return paymentMapper.toResponse(payment);
    }

    /**
     * Get payments with pagination and caching
     */
    @Cacheable(value = "paymentsList", key = "#userId + '_' + #orderId + '_' + #status + '_' + #pageable.pageNumber",
            unless = "#result == null || #result.isEmpty()")
    public Page<PaymentResponse> getPayments(Long userId, Long orderId, String status, Pageable pageable) {
        long startTime = System.nanoTime();

        try {
            Page<Payment> payments;

            if (userId != null) {
                payments = paymentRepository.findByUserId(userId, pageable);
            } else if (orderId != null) {
                Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
                payments = (payment != null)
                        ? new org.springframework.data.domain.PageImpl<>(java.util.List.of(payment), pageable, 1)
                        : org.springframework.data.domain.Page.empty(pageable);            } else if (status != null) {
                payments = paymentRepository.findByStatus(status, pageable);
            } else {
                payments = paymentRepository.findAll(pageable);
            }

            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("payment.get.list.duration").record(Duration.ofNanos(duration));
            meterRegistry.counter("payment.get.list.count").increment(payments.getNumberOfElements());

            return payments.map(paymentMapper::toResponse);
        } catch (Exception e) {
            meterRegistry.counter("payment.get.list.error").increment();
            throw e;
        }
    }

    /**
     * Get total sum for user with caching
     */
    @Cacheable(value = "paymentStats", key = "#userId + '_' + #fromDate + '_' + #toDate")
    public PaymentSummaryResponse getTotalSumForUser(Long userId, LocalDateTime fromDate, LocalDateTime toDate) {
        log.info("Calculating total sum for user: {} from {} to {}", userId, fromDate, toDate);

        long startTime = System.nanoTime();

        try {
            List<Payment> payments = paymentRepository.findByUserIdAndTimestampBetween(userId, fromDate, toDate);

            BigDecimal totalSum = payments.stream()
                    .filter(p -> PaymentStatus.SUCCESS.name().equals(p.getStatus()))
                    .map(Payment::getPaymentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("payment.summary.user.duration").record(Duration.ofNanos(duration));

            return PaymentSummaryResponse.builder()
                    .totalSum(totalSum)
                    .paymentCount((long) payments.size())
                    .period(fromDate.toLocalDate() + " to " + toDate.toLocalDate())
                    .build();
        } catch (Exception e) {
            meterRegistry.counter("payment.summary.user.error").increment();
            throw e;
        }
    }

    /**
     * Get total sum for all users with parallel processing for large datasets
     */
    public PaymentSummaryResponse getTotalSumForAllUsers(LocalDateTime fromDate, LocalDateTime toDate) {
        log.info("Calculating total sum for all users from {} to {}", fromDate, toDate);

        long startTime = System.nanoTime();

        try {
            // For large datasets, use parallel stream
            List<Payment> payments = paymentRepository.findByTimestampBetween(fromDate, toDate);

            BigDecimal totalSum = payments.parallelStream()
                    .filter(p -> PaymentStatus.SUCCESS.name().equals(p.getStatus()))
                    .map(Payment::getPaymentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("payment.summary.all.duration").record(Duration.ofNanos(duration));

            return PaymentSummaryResponse.builder()
                    .totalSum(totalSum)
                    .paymentCount((long) payments.size())
                    .period(fromDate.toLocalDate() + " to " + toDate.toLocalDate())
                    .build();
        } catch (Exception e) {
            meterRegistry.counter("payment.summary.all.error").increment();
            throw e;
        }
    }

    /**
     * Invalidate cache when payment is created
     */
    @CacheEvict(value = {"paymentsList", "paymentStats"}, allEntries = true)
    public void invalidateUserPaymentCache(Long userId) {
        log.debug("Invalidated cache for user: {}", userId);
    }
}
