package com.ausiankou.payment.integration;

import com.ausiankou.payment.dto.PaymentCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test: real MongoDB + real Kafka broker in containers. The
 * external random-number API is NOT stubbed here on purpose - Resilience4j's
 * fallback (SecureRandom) is expected to kick in when that call fails in a
 * sandboxed CI runner without internet egress, so either outcome (SUCCESS or
 * FAILED payment) is valid; the test only asserts the payment was created
 * and an event was attempted, not which branch the coin flip took.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class PaymentServiceIntegrationTest {

    private static final String JWT_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("spring.liquibase.enabled", () -> "false"); // no auth on the Testcontainers Mongo instance
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String userToken(UUID subject) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(subject.toString())
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    @Test
    void createPayment_persistsAndReturnsStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(UUID.randomUUID(), userId, BigDecimal.valueOf(49.99));

        mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.orderId").value(request.orderId().toString()));
    }

    @Test
    void createPayment_withoutToken_isUnauthorized() throws Exception {
        PaymentCreateRequest request = new PaymentCreateRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPayment_returnsCreatedPayment() throws Exception {
        UUID userId = UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(UUID.randomUUID(), userId, BigDecimal.valueOf(20));

        String response = mockMvc.perform(post("/payments")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/payments/{id}", id)
                        .header("Authorization", "Bearer " + userToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }
}
