package com.ausiankou.orders.dto;

import java.util.UUID;

/**
 * Minimal projection of the User Service response, used to enrich order
 * responses. Kept nullable/optional-friendly: when the circuit breaker is
 * open or User Service is unavailable, this comes back as a fallback
 * placeholder rather than failing the whole order request.
 */
public record UserInfoDto(UUID id, String name, String surname, String email) {

    public static UserInfoDto unavailable() {
        return new UserInfoDto(null, "unavailable", "unavailable", null);
    }
}
