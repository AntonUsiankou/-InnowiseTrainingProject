package com.ausiankou.client;

import com.ausiankou.dto.AuthUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public AuthUserDto getUserByEmail(String email) {
        try {
            String url = userServiceUrl + "/api/internal/auth/user/" + email;
            ResponseEntity<AuthUserDto> response = restTemplate.getForEntity(url, AuthUserDto.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Пользователь не найден: {}", email);
            return null;
        } catch (Exception ex) {
            log.error("Ошибка захвата пользователя из СЕРВИСА Пользователей: {}", ex.getMessage());
            throw new RuntimeException("User service unavailable", ex);
        }
    }
    public AuthUserDto getUserById(Long id){
        try{
            String url = userServiceUrl + "/api/internal/auth/user/id" + id;
            ResponseEntity<AuthUserDto> response = restTemplate.getForEntity(url, AuthUserDto.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", id);
            return null;
        } catch (Exception e) {
            log.error("Error fetching user from UserService: {}", e.getMessage());
            throw new RuntimeException("User service unavailable", e);
        }
    }
}

