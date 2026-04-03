package com.ausiankou.client;

import com.ausiankou.dto.AuthUserDto;
import com.ausiankou.dto.RegistrationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user.service.url::http://localhost:8082}")
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

    public AuthUserDto createUser(RegistrationRequest request){
        try {
            String url = userServiceUrl + "/api/internal/auth/users";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RegistrationRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<AuthUserDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    AuthUserDto.class
            );

            log.info("User created in UserService: {}", request.getEmail());
            return response.getBody();
        } catch (HttpClientErrorException.Conflict ex) {
            log.warn("User already exists: {}", request.getEmail());
            throw new RuntimeException("User already exists with email: " + request.getEmail());
        } catch (Exception ex) {
            log.error("Error creating user in UserService: {}", ex.getMessage());
            throw new RuntimeException("User service unavailable", ex);
        }
    }
    public boolean validateCredentials(String email, String password) {
        try {
            String url = userServiceUrl + "/api/internal/auth/validate?email=" + email + "&password=" + password;
            ResponseEntity<Boolean> response = restTemplate.getForEntity(url, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception ex) {
            log.error("Error validating credentials: {}", ex.getMessage());
            return false;
        }
    }
}