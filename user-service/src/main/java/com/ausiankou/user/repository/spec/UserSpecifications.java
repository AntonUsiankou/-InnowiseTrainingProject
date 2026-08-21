package com.ausiankou.user.repository.spec;

import com.ausiankou.user.entity.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> hasName(String name) {
        return (root, query, cb) -> StringUtils.hasText(name)
                ? cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
                : cb.conjunction();
    }

    public static Specification<User> hasSurname(String surname) {
        return (root, query, cb) -> StringUtils.hasText(surname)
                ? cb.like(cb.lower(root.get("surname")), "%" + surname.toLowerCase() + "%")
                : cb.conjunction();
    }

    public static Specification<User> filterBy(String name, String surname) {
        return Specification.where(hasName(name)).and(hasSurname(surname));
    }
}
