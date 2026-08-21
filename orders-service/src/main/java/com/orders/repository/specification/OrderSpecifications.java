package com.orders.repository.specification;

import com.orders.entity.Order;
import com.orders.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null) return cb.lessThanOrEqualTo(root.get("createdAt"), to);
            if (to == null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            return cb.between(root.get("createdAt"), from, to);
        };
    }

    public static Specification<Order> hasStatuses(List<OrderStatus> statuses) {
        return (root, query, cb) -> (statuses == null || statuses.isEmpty())
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }

    public static Specification<Order> filterBy(Instant from, Instant to, List<OrderStatus> statuses) {
        return Specification.where(createdBetween(from, to)).and(hasStatuses(statuses));
    }
}
