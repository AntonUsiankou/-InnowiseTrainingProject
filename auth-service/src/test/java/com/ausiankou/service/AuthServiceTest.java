package com.ausiankou.service;

import com.ausiankou.dto.*;
import com.ausiankou.entity.Credential;
import com.ausiankou.entity.Role;
import com.ausiankou.exception.AuthException;
import com.ausiankou.repository.CredentialRepository;
import com.ausiankou.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class  AuthServiceTest {

    @Mock
    private CredentialRepository credentialRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void saveCredentials_throwsWhenLoginTaken() {
        RegisterCredentialsRequest request = new RegisterCredentialsRequest("anton", "pw", Role.USER, null);
        when(credentialRepository.existsByLogin("anton")).thenReturn(true);

        assertThatThrownBy(() -> authService.saveCredentials(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already registered");

        verify(credentialRepository, never()).save(any());
    }

    @Test
    void saveCredentials_hashesPasswordAndSaves() {
        RegisterCredentialsRequest request = new RegisterCredentialsRequest("anton", "raw-pw", Role.USER, null);
        when(credentialRepository.existsByLogin("anton")).thenReturn(false);
        when(passwordEncoder.encode("raw-pw")).thenReturn("hashed-pw");
        UUID savedId = UUID.randomUUID();
        when(credentialRepository.save(any(Credential.class))).thenAnswer(inv -> {
            Credential c = inv.getArgument(0);
            c.setId(savedId);
            return c;
        });

        UUID result = authService.saveCredentials(request);

        assertThat(result).isEqualTo(savedId);
        verify(credentialRepository).save(argThat(c -> c.getPasswordHash().equals("hashed-pw")));
    }

    @Test
    void login_throwsInvalidCredentials_whenLoginNotFound() {
        when(credentialRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "pw")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid login or password");
    }

    @Test
    void login_throwsInvalidCredentials_whenPasswordWrong() {
        Credential credential = Credential.builder()
                .id(UUID.randomUUID()).login("anton").passwordHash("hashed").role(Role.USER).enabled(true).build();
        when(credentialRepository.findByLogin("anton")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong-pw", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("anton", "wrong-pw")))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void login_throwsUserDisabled_whenAccountDisabled() {
        Credential credential = Credential.builder()
                .id(UUID.randomUUID()).login("anton").passwordHash("hashed").role(Role.USER).enabled(false).build();
        when(credentialRepository.findByLogin("anton")).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> authService.login(new LoginRequest("anton", "pw")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_returnsTokens_whenCredentialsValid() {
        UUID userId = UUID.randomUUID();
        Credential credential = Credential.builder()
                .id(UUID.randomUUID()).userId(userId).login("anton").passwordHash("hashed").role(Role.USER).enabled(true).build();
        when(credentialRepository.findByLogin("anton")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(userId, Role.USER)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId, Role.USER)).thenReturn("refresh-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        TokenResponse result = authService.login(new LoginRequest("anton", "pw"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void validate_returnsInvalid_whenTokenParsingFails() {
        when(jwtService.parseAndValidate("bad-token")).thenThrow(new io.jsonwebtoken.JwtException("bad"));

        ValidateTokenResponse result = authService.validate(new ValidateTokenRequest("bad-token"));

        assertThat(result.valid()).isFalse();
    }

    @Test
    void refresh_throwsInvalidToken_whenNotARefreshToken() {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAndValidate("access-token-used-as-refresh")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("access-token-used-as-refresh")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid or expired token");
    }
}
