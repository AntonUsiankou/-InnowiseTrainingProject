package com.ausiankou.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResponse {
    private boolean valid;
    private Long userId;
    private String email;
    private String role;
    private String message;
}
