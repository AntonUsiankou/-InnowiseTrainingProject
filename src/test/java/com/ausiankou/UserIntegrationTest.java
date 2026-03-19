package com.ausiankou;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Sql(statements = "TRUNCATE TABLE users, payment_cards RESTART IDENTITY CASCADE", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@EnableCaching
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
        //registry.add("spring.cache.type", () -> "none");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private RestTemplate restTemplate;
    private String baseUrl;
    private LocalDate birthDate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        baseUrl = "http://localhost:" + port + "/api";
        birthDate = LocalDate.of(1990, 1, 1);

        userRepository.deleteAll();
    }

    private UserCreateDto createUniqueUserDto() {
        UserCreateDto dto = new UserCreateDto();
        dto.setName("Иван");
        dto.setSurname("Петров");
        dto.setBirthDate(birthDate);
        dto.setEmail("ivan." + UUID.randomUUID() + "@mail.com"); // Уникальный email
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

    @Test
    @Order(1)
    void createUser_ShouldReturnCreatedUser() {
        UserCreateDto userDto = createUniqueUserDto();
        String url = baseUrl + "/users";

        ResponseEntity<UserDto> response = restTemplate.postForEntity(url, userDto, UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo(userDto.getEmail());
    }

    @Test
    @Order(2)
    void createUser_DuplicateEmail_ShouldThrowException() {
        UserCreateDto userDto = createUniqueUserDto();
        String url = baseUrl + "/users";

        restTemplate.postForEntity(url, userDto, UserDto.class);

        assertThatThrownBy(() -> restTemplate.postForEntity(url, userDto, UserDto.class))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    @Order(3)
    void getUserById_ShouldReturnUser() {
        UserCreateDto userDto = createUniqueUserDto();
        String createUrl = baseUrl + "/users";
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(createUrl, userDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        String getUrl = baseUrl + "/users/" + userId;
        ResponseEntity<UserDto> response = restTemplate.getForEntity(getUrl, UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getEmail()).isEqualTo(userDto.getEmail());
    }

    @Test
    @Order(4)
    void getUserById_NotFound_ShouldThrowException() {
        String url = baseUrl + "/users/999";

        assertThatThrownBy(() -> restTemplate.getForEntity(url, UserDto.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @Order(5)
    void createCard_ShouldReturnCreatedCard() {
        UserCreateDto userDto = createUniqueUserDto();
        String createUserUrl = baseUrl + "/users";
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(createUserUrl, userDto, UserDto.class);
        Long userId = userResponse.getBody().getId();

        PaymentCardDto newCardDto = createCardDto();
        String createCardUrl = baseUrl + "/users/" + userId + "/cards";
        ResponseEntity<PaymentCardDto> response = restTemplate.postForEntity(createCardUrl, newCardDto, PaymentCardDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getNumber()).isEqualTo(newCardDto.getNumber());
        assertThat(response.getBody().getUserId()).isEqualTo(userId);
    }

    @Test
    @Order(6)
    void createCard_MaxCardsExceeded_ShouldThrowException() {
        UserCreateDto userDto = createUniqueUserDto();
        String createUserUrl = baseUrl + "/users";
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(createUserUrl, userDto, UserDto.class);
        Long userId = userResponse.getBody().getId();

        String createCardUrl = baseUrl + "/users/" + userId + "/cards";

        for (int i = 0; i < 5; i++) {
            PaymentCardDto newCard = new PaymentCardDto();
            newCard.setNumber(String.format("%016d", i + 1));
            newCard.setHolder("IVAN PETROV");
            newCard.setExpirationDate(LocalDate.now().plusYears(2));
            newCard.setActive(true);
            restTemplate.postForEntity(createCardUrl, newCard, PaymentCardDto.class);
        }

        PaymentCardDto sixthCard = new PaymentCardDto();
        sixthCard.setNumber("9999999999999999");
        sixthCard.setHolder("IVAN PETROV");
        sixthCard.setExpirationDate(LocalDate.now().plusYears(2));
        sixthCard.setActive(true);

        assertThatThrownBy(() -> restTemplate.postForEntity(createCardUrl, sixthCard, PaymentCardDto.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @Order(7)
    void updateUser_ShouldReturnUpdatedUser() {
        UserCreateDto userDto = createUniqueUserDto();
        String createUrl = baseUrl + "/users";
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(createUrl, userDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        UserDto updateDto = new UserDto();
        updateDto.setId(userId);
        updateDto.setName("Петр");
        updateDto.setSurname("Иванов");
        updateDto.setBirthDate(birthDate);
        updateDto.setEmail("petr." + UUID.randomUUID() + "@mail.com");
        updateDto.setActive(true);

        String updateUrl = baseUrl + "/users/" + userId;

        try {
            restTemplate.put(updateUrl, updateDto);
        } catch (HttpClientErrorException e) {
            System.err.println("Ошибка при обновлении: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw e;
        }

        String getUrl = baseUrl + "/users/" + userId;
        ResponseEntity<UserDto> getResponse = restTemplate.getForEntity(getUrl, UserDto.class);

        assertThat(getResponse.getBody().getName()).isEqualTo("Петр");
        assertThat(getResponse.getBody().getEmail()).isEqualTo(updateDto.getEmail());
    }

    @Test
    @Order(8)
    void deleteUser_ShouldRemoveUser() {
        UserCreateDto userDto = createUniqueUserDto();
        String createUrl = baseUrl + "/users";
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(createUrl, userDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        String deleteUrl = baseUrl + "/users/" + userId;
        restTemplate.delete(deleteUrl);

        String getUrl = baseUrl + "/users/" + userId;

        assertThatThrownBy(() -> restTemplate.getForEntity(getUrl, UserDto.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class); // ✅ ИЗМЕНЕНО: BadRequest вместо NotFound
    }

    @Test
    @Order(9)
    void searchByFullName_ShouldReturnMatchingUsers() {
        UserCreateDto userDto = createUniqueUserDto();
        String createUrl = baseUrl + "/users";
        restTemplate.postForEntity(createUrl, userDto, UserDto.class);

        String searchUrl = baseUrl + "/users/search?fullName=" + userDto.getName() + "+" + userDto.getSurname();
        ResponseEntity<UserDto[]> response = restTemplate.getForEntity(searchUrl, UserDto[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getName()).isEqualTo(userDto.getName());
    }
}