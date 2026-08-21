package com.orders.auth.ordersservice.integration;

import com.orders.dto.OrderCreateRequest;
import com.orders.kafka.PaymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test: real Postgres + real Kafka broker. Verifies both the REST
 * flow (create order) and the async flow (a CREATE_PAYMENT event on the
 * topic gets consumed and flips the order's status), which is the part a
 * pure MockMvc/unit test can't exercise.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class OrderServiceIntegrationTest {

    private static final String JWT_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.secret", () -> JWT_SECRET);
        // no live User Service in this test - point at a dead port so calls
        // fail fast and exercise the fallback path instead of hanging
        registry.add("services.user-service.base-url", () -> "http://localhost:1");
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
    void createOrder_persists() throws Exception {
        UUID userId = UUID.randomUUID();
        OrderCreateRequest request = new OrderCreateRequest(userId,
                List.of(new OrderCreateRequest.OrderItemRequest(UUID.randomUUID(), 3)));

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void getOrder_fallsBackGracefully_whenUserServiceIsDown() throws Exception {
        UUID userId = UUID.randomUUID();
        OrderCreateRequest request = new OrderCreateRequest(userId,
                List.of(new OrderCreateRequest.OrderItemRequest(UUID.randomUUID(), 1)));

        String response = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        // User Service is unreachable (see @DynamicPropertySource) - the
        // circuit breaker's fallback must still let this request succeed.
        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + userToken(userId))
                        .param("requesterEmail", "someone@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userInfo.name").value("unavailable"));
    }

    @Test
    void paymentEvent_updatesOrderStatusToPaid() throws Exception {
        UUID userId = UUID.randomUUID();
        OrderCreateRequest request = new OrderCreateRequest(userId,
                List.of(new OrderCreateRequest.OrderItemRequest(UUID.randomUUID(), 1)));

        String response = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        publishPaymentEvent(new PaymentEvent(UUID.randomUUID(), orderId, userId, "SUCCESS", java.math.BigDecimal.TEN));

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String orderJson = mockMvc.perform(get("/orders/{id}", orderId)
                            .header("Authorization", "Bearer " + userToken(userId))
                            .param("requesterEmail", "someone@example.com"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(orderJson).get("status").asText()).isEqualTo("PAID");
        });
    }

    private void publishPaymentEvent(PaymentEvent event) {
        Map<String, Object> producerProps = Map.of(
                BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class,
                VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        );
        ProducerFactory<String, PaymentEvent> pf = new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, PaymentEvent> template = new KafkaTemplate<>(pf);
        template.send("CREATE_PAYMENT", event.orderId().toString(), event);
        template.flush();
    }
}
