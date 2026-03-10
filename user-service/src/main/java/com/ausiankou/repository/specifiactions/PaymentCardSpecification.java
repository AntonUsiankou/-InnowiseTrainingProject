package com.ausiankou.repository.specifiactions;

import com.ausiankou.entity.PaymentCard;
import org.springframework.data.jpa.domain.Specification;

public class PaymentCardSpecification {
    public static Specification<PaymentCard> byUserId(Long userId){
        return (root, query, cb)->
                userId == null ? null :
                cb.equal(root.get("user").get("id"), userId);
    }
    public static Specification<PaymentCard> isActive(Boolean active){
        return (root, query, cb) ->
                active == null ? null :
                cb.equal(root.get("active"), active);
    }
}
