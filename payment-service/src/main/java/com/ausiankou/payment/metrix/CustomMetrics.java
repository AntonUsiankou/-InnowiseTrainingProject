package com.ausiankou.payment.metrix;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Gauge;

import com.github.benmanes.caffeine.cache.Cache;
import com.ausiankou.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;

@Component
@RequiredArgsConstructor
public class CustomMetrics {

    private final MeterRegistry meterRegistry;
    private final PaymentRepository paymentRepository; // Внедряем для подсчета очереди
    private final Cache<String, Integer> cache;        // Внедряем для метрик кэша

    // Сохраняем ссылки на счетчики, чтобы их можно было вызывать из других сервисов
    private Counter totalPaymentsCounter;
    private Counter successPaymentsCounter;
    private Counter failedPaymentsCounter;

    @PostConstruct
    public void registerCustomMetrics() {
        // 1. Инициализируем и регистрируем счетчики
        totalPaymentsCounter = meterRegistry.counter("payments.total", "service", "payment-service");
        successPaymentsCounter = meterRegistry.counter("payments.success", "service", "payment-service");
        failedPaymentsCounter = meterRegistry.counter("payments.failed", "service", "payment-service");

        // 2. Регистрируем динамические метрики (Gauge)
        // Каждые несколько секунд Prometheus будет дергать эти лямбды и забирать свежие цифры
        Gauge.builder("payments.pending.queue.size", this, CustomMetrics::getPendingPaymentCount)
                .register(meterRegistry);

        Gauge.builder("cache.hit.ratio", this, CustomMetrics::getCacheHitRatio)
                .register(meterRegistry);
    }

    // --- Методы для вызова из ваших сервисов ---

    public void incrementTotal() {
        totalPaymentsCounter.increment();
    }

    public void incrementSuccess() {
        successPaymentsCounter.increment();
    }

    public void incrementFailed() {
        failedPaymentsCounter.increment();
    }

    // --- Методы подсчета значений для Gauge ---

    private long getPendingPaymentCount() {
        // Теперь возвращаем реальное количество документов со статусом PENDING из MongoDB
        return paymentRepository != null ? paymentRepository.countByStatus("PENDING") : 0;
    }

    private double getCacheHitRatio() {
        // Возвращаем реальный коэффициент попадания в кэш из статистики Caffeine
        if (cache != null && cache.stats() != null) {
            return cache.stats().hitRate(); // Метод Caffeine, возвращает double от 0.0 до 1.0
        }
        return 0.0;
    }
}