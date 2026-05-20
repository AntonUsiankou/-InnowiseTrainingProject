package com.ausiankou.repository.specification;

import com.ausiankou.entity.Order;
import com.ausiankou.entity.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderSpecification {

    public static Specification<Order> filterByStatuses(List<OrderStatus> statuses){
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()){
                return cb.conjunction();
            }
            return root.get("status").in(statuses);
        };
    }
    public static Specification<Order> filterByDateRange(LocalDateTime from, LocalDateTime to){
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if(from != null){
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if(to != null){
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    public static Specification<Order> notDeleted(){
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

}
