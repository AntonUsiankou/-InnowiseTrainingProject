package com.ausiankou.auth.apigateway.filter;

import com.ausiankou.auth.apigateway.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    private JwtAuthGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter(new JwtValidator(SECRET));
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String validToken(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    @Test
    void publicLoginPath_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/auth/login").build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void registerPath_passesThroughWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/register").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void protectedPath_withoutHeader_returns401() {
        ServerHttpRequest request = MockServerHttpRequest.get("/orders/123").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_withInvalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/orders/123")
                .header("Authorization", "Bearer not-a-real-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPath_withValidToken_forwardsUserHeadersAndCallsChain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/orders/123")
                .header("Authorization", "Bearer " + validToken("ADMIN"))
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(argThat(ex ->
                "11111111-1111-1111-1111-111111111111".equals(ex.getRequest().getHeaders().getFirst("X-User-Id"))
                        && "ADMIN".equals(ex.getRequest().getHeaders().getFirst("X-User-Role"))));
    }
}
