package com.ausiankou.user.integration;

import com.ausiankou.user.dto.PaymentCardCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ausiankou.user.dto.UserCreateRequest;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test: real Postgres + real Redis in containers,
 * real Spring context (security filter, JPA, Liquibase, cache), driven
 * through MockMvc exactly like a client hitting the REST API would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class UserServiceImplIntegrationTest {

    private static final String JWT_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken() {
        return token(UUID.randomUUID(), "ADMIN");
    }

    private String userToken(UUID subjectId) {
        return token(subjectId, "USER");
    }

    private String token(UUID subject, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(subject.toString())
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    @Test
    void createThenFetchUser_asAdmin_succeeds() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Anton", "K", LocalDate.of(1995, 1, 1), "integration-" + UUID.randomUUID() + "@example.com");

        String response = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(request.email()))
                .andReturn().getResponse().getContentAsString();

        UUID createdId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/users/{id}", createdId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anton"));
    }

    @Test
    void getUser_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUser_cannotViewAnotherUsersProfile() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Bob", "B", LocalDate.of(1990, 1, 1), "bob-" + UUID.randomUUID() + "@example.com");
        String response = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID bobId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        // A different user (not admin, not Bob) must be forbidden from viewing Bob's profile
        mockMvc.perform(get("/users/{id}", bobId)
                        .header("Authorization", "Bearer " + userToken(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        // Bob himself can view his own profile
        mockMvc.perform(get("/users/{id}", bobId)
                        .header("Authorization", "Bearer " + userToken(bobId)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivate_requiresAdminRole() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Carl", "C", LocalDate.of(1992, 5, 5), "carl-" + UUID.randomUUID() + "@example.com");
        String response = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID carlId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(post("/users/{id}/deactivate", carlId)
                        .header("Authorization", "Bearer " + userToken(carlId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/users/{id}/deactivate", carlId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void createUser_duplicateEmail_returnsConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest("Dan", "D", LocalDate.of(1988, 3, 3), email);

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentAddCard_neverExceedsLimit_underRace() throws Exception {
        // Real concurrency test for the pessimistic-lock fix in
        // UserRepository.findByIdForUpdate(): fire 10 concurrent "add card"
        // requests at a user who already has 4 active cards (limit is 5).
        // Without the row lock, several threads can read "4 cards" before
        // any of them commits and all proceed - exceeding the limit. With
        // the lock, exactly 1 of these 10 should succeed and 9 should be
        // rejected with 400, leaving the user at exactly 5 cards.
        UserCreateRequest request = new UserCreateRequest("Race", "Condition", LocalDate.of(1990, 1, 1),
                "race-" + UUID.randomUUID() + "@example.com");
        String response = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        String token = userToken(userId);

        // pre-fill 4 cards sequentially so we start exactly 1 slot under the limit
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/users/{id}/cards", userId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PaymentCardCreateRequest(
                                    "4111111111111" + i, "Race Condition", LocalDate.now().plusYears(2)))))
                    .andExpect(status().isCreated());
        }

        int threadCount = 10;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                var result = mockMvc.perform(post("/users/{id}/cards", userId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new PaymentCardCreateRequest(
                                        "422222222222" + idx, "Race Condition", LocalDate.now().plusYears(2)))))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        ready.await();
        go.countDown(); // release all threads at once to maximize the race window
        long successCount = 0;
        for (var future : futures) {
            if (future.get() == 201) successCount++;
        }
        pool.shutdown();

        assertThat(successCount).isEqualTo(1); // exactly one of the 10 concurrent requests should win the last slot

        mockMvc.perform(get("/users/{id}/cards", userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5)); // never exceeds the limit
    }
}

