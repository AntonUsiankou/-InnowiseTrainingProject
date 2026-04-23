package com.ausiankou;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@Sql(statements = "TRUNCATE TABLE users, payment_cards RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@EnableCaching
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class UserIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled ", () -> false);
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    private LocalDate birthDate;
    private RequestPostProcessor testUser;
    private RequestPostProcessor adminUser;

    @BeforeEach
    void setUp() {
        birthDate = LocalDate.of(1990, 1, 1);
        userRepository.deleteAll();

        // СОЗДАЕМ ТЕСТОВЫХ ПОЛЬЗОВАТЕЛЕЙ ДЛЯ АВТОРИЗАЦИИ
        testUser = user("testuser").password("password").roles("USER");
        adminUser = user("admin").password("password").roles("USER", "ADMIN");
    }
    @BeforeEach
    void logTestName(TestInfo testInfo) {
        log.info("-------------------------------------------------------");
        log.info("СТАРТ ТЕСТА: {}", testInfo.getDisplayName());
        log.info("-------------------------------------------------------");
    }

    private UserCreateDto createUniqueUserDto() {
        UserCreateDto dto = new UserCreateDto();
        dto.setName("Иван");
        dto.setSurname("Петров");
        dto.setBirthDate(birthDate);
        dto.setEmail("ivan." + UUID.randomUUID() + "@mail.com");
        dto.setPassword("Test123!@#");
        return dto;
    }

    private PaymentCardDto createCardDto() {
        PaymentCardDto dto = new PaymentCardDto();
        dto.setNumber("1234567890123456");
        dto.setHolder("IVAN PETROV");
        dto.setExpirationDate(LocalDate.now().plusYears(2));
        dto.setActive(true);
        return dto;
    }

    private UserDto createUserAndReturn(UserCreateDto userDto) throws Exception {
        String response = mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(response, UserDto.class);
    }

    @Test
    @Order(1)
    @WithMockUser
    void createUser_ShouldReturnCreatedUser() throws Exception {
        log.info("Method: createUser_ShouldReturnCreatedUser()");
        UserCreateDto userDto = createUniqueUserDto();

        mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(userDto.getEmail()))
                .andExpect(jsonPath("$.name").value(userDto.getName()))
                .andExpect(jsonPath("$.surname").value(userDto.getSurname()));
    }

    @Test
    @Order(2)
    void createUser_DuplicateEmail_ShouldThrowException() throws Exception {
        log.info("Method: createUser_DuplicateEmail_ShouldThrowException");
        UserCreateDto userDto = createUniqueUserDto();

        // Первое создание - успешно
        mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isCreated());

        // Второе создание с тем же email - конфликт
        mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void getUserById_ShouldReturnUser() throws Exception {
        log.info("Method: getUserById_ShouldReturnUser");
        UserCreateDto userDto = createUniqueUserDto();
        UserDto createdUser = createUserAndReturn(userDto);
        Long userId = createdUser.getId();

        mockMvc.perform(get("/api/users/{id}", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf()))  // ДОБАВЛЯЕМ АВТОРИЗАЦИЮ (для GET csrf не нужен)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(userDto.getEmail()))
                .andExpect(jsonPath("$.name").value(userDto.getName()));
    }

    @Test
    @Order(4)
    void getUserById_NotFound_ShouldThrowException() throws Exception {
        log.info("Method: getUserById_NotFound_ShouldThrowException");

        mockMvc.perform(get("/api/users/999")
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf()))
                .andExpect(status().isNotFound());  // или isBadRequest(), зависит от вашего API
    }

    @Test
    @Order(5)
    void createCard_ShouldReturnCreatedCard() throws Exception {
        log.info("Method: createCard_ShouldReturnCreatedCard()");
        UserCreateDto userDto = createUniqueUserDto();
        UserDto createdUser = createUserAndReturn(userDto);
        Long userId = createdUser.getId();

        PaymentCardDto cardDto = createCardDto();

        mockMvc.perform(post("/api/users/{userId}/cards", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.number").value(cardDto.getNumber()))
                .andExpect(jsonPath("$.holder").value(cardDto.getHolder()))
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @Order(6)
    void createCard_MaxCardsExceeded_ShouldThrowException() throws Exception {
        log.info("Method: createCard_MaxCardsExceeded_ShouldThrowException");
        UserCreateDto userDto = createUniqueUserDto();
        UserDto createdUser = createUserAndReturn(userDto);
        Long userId = createdUser.getId();

        // Создаем 5 карт (максимум)
        for (int i = 0; i < 5; i++) {
            PaymentCardDto newCard = new PaymentCardDto();
            newCard.setNumber(String.format("%016d", i + 1));
            newCard.setHolder("IVAN PETROV");
            newCard.setExpirationDate(LocalDate.now().plusYears(2));
            newCard.setActive(true);

            mockMvc.perform(post("/api/users/{userId}/cards", userId)
                            .with(user("admin").roles("ADMIN")) // Эмулируем вход
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newCard)))
                    .andExpect(status().isCreated());
        }

        // Пытаемся создать 6-ю карту
        PaymentCardDto sixthCard = new PaymentCardDto();
        sixthCard.setNumber("9999999999999999");
        sixthCard.setHolder("IVAN PETROV");
        sixthCard.setExpirationDate(LocalDate.now().plusYears(2));
        sixthCard.setActive(true);

        mockMvc.perform(post("/api/users/{userId}/cards", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sixthCard)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void updateUser_ShouldReturnUpdatedUser() throws Exception {
        log.info("Method: updateUser_ShouldReturnUpdatedUser()");
        UserCreateDto userDto = createUniqueUserDto();
        UserDto createdUser = createUserAndReturn(userDto);
        Long userId = createdUser.getId();

        UserDto updateDto = new UserDto();
        updateDto.setId(userId);
        updateDto.setName("Петр");
        updateDto.setSurname("Иванов");
        updateDto.setBirthDate(birthDate);
        updateDto.setEmail("petr." + UUID.randomUUID() + "@mail.com");
        updateDto.setActive(true);

        // Обновляем пользователя
        mockMvc.perform(put("/api/users/{id}", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // Проверяем обновленные данные
        mockMvc.perform(get("/api/users/{id}", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Петр"))
                .andExpect(jsonPath("$.surname").value("Иванов"))
                .andExpect(jsonPath("$.email").value(updateDto.getEmail()));
    }

    @Test
    @Order(8)
    void deleteUser_ShouldRemoveUser() throws Exception {
        log.info("Method: deleteUser_ShouldRemoveUser()");
        UserCreateDto userDto = createUniqueUserDto();
        UserDto createdUser = createUserAndReturn(userDto);
        Long userId = createdUser.getId();

        // Удаляем пользователя
        mockMvc.perform(delete("/api/users/{id}", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Проверяем, что пользователь удален
        mockMvc.perform(get("/api/users/{id}", userId)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    void searchByFullName_ShouldReturnMatchingUsers() throws Exception {
        log.info("Method: searchByFullName_ShouldReturnMatchingUsers()");
        UserCreateDto userDto = createUniqueUserDto();
        createUserAndReturn(userDto);

        String fullName = userDto.getName() + " " + userDto.getSurname();

        mockMvc.perform(get("/api/users/search")
                        .param("fullName", fullName)
                        .with(user("admin").roles("ADMIN")) // Эмулируем вход
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(userDto.getName()))
                .andExpect(jsonPath("$[0].surname").value(userDto.getSurname()));
    }
}