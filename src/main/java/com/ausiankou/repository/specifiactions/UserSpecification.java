package com.ausiankou.repository.specifiactions;

import com.ausiankou.entity.User;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecification {
    public static Specification<User> hasName(String name){
        return (root, query, cb) ->
                name == null ? null :
                        cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<User> hasSurname(String surname){
        return (root, query, cb) ->
                surname == null ? null :
                        cb.like(cb.lower(root.get("surname")), "%" + surname.toLowerCase() + "%");
    }

    public static Specification<User> isActive(Boolean active){
        return (root, query, cb) ->
                active == null ? null :
                        cb.equal(root.get("active"), active);
    }
    public static Specification<User> nameAndSurname(String name, String surname){
        return Specification.where(hasName(name)).and(hasSurname(surname));
    }
}
