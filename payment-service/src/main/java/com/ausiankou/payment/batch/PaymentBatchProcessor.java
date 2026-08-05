package com.ausiankou.payment.batch;

import com.ausiankou.payment.entity.Payment;
import com.ausiankou.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentBatchProcessor {

    private final PaymentRepository paymentRepository;

    /**
     * Batch process pending payments every 5 minutes
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void processPendingPayments() {
        log.info("Starting batch processing of pending payments");

        Pageable pageable = PageRequest.of(0, 100);
        List<Payment> allProcessed = new ArrayList<>();

        while (true) {
            var pendingPayments = paymentRepository.findByStatus("PENDING", pageable);
            if (pendingPayments.isEmpty()) {
                break;
            }

            // Извлекаем список элементов из страницы и запускаем параллельный стрим
            pendingPayments.getContent().parallelStream().forEach(payment -> {
                // Process each payment
                log.debug("Processing pending payment: {}", payment.getId());
                // Re-process logic here
            });

            allProcessed.addAll(pendingPayments.getContent());
            pageable = pageable.next();
        }

        log.info("Completed batch processing of {} pending payments", allProcessed.size());
    }

    /**
     * Archive old payments daily
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
    public void archiveOldPayments() {
        LocalDateTime archiveBefore = LocalDateTime.now().minusMonths(6);
        log.info("Archiving payments older than: {}", archiveBefore);

        // Implementation for archiving to cold storage
        // This would move old payments to a separate collection
    }
}
