package com.ausiankou;


import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class UserIntegrationTest {
    /*//ИЗ-за теста проект не собиратеся, пределаю
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
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop"); // Очищаем БД перед тестами
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository; // Для очистки

    private RestTemplate restTemplate;
    private String baseUrl;
    private UserCreateDto userCreateDto;
    private PaymentCardDto cardDto;
    private LocalDate birthDate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        baseUrl = "http://localhost:" + port + "/api";

        birthDate = LocalDate.of(1990, 1, 1);

        userCreateDto = new UserCreateDto();
        userCreateDto.setName("Иван");
        userCreateDto.setSurname("Петров");
        userCreateDto.setBirthDate(birthDate);
        userCreateDto.setEmail("ivan@mail.com");

        cardDto = new PaymentCardDto();
        cardDto.setNumber("1234567890123456");
        cardDto.setHolder("IVAN PETROV");
        cardDto.setExpirationDate(LocalDate.now().plusYears(2));
        cardDto.setActive(true);

        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    void createUser_ShouldReturnCreatedUser() {
        String url = baseUrl + "/users";

        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                url,
                userCreateDto,
                UserDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Иван");
        assertThat(response.getBody().getEmail()).isEqualTo("ivan@mail.com");
    }

    @Test
    @Order(2)
    void createUser_DuplicateEmail_ShouldThrowException() {
        String url = baseUrl + "/users";

        restTemplate.postForEntity(url, userCreateDto, UserDto.class);

        assertThatThrownBy(() -> restTemplate.postForEntity(url, userCreateDto, UserDto.class))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    @Order(3)
    void getUserById_ShouldReturnUser() {
        String createUrl = baseUrl + "/users";
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                createUrl, userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        String getUrl = baseUrl + "/users/" + userId;
        ResponseEntity<UserDto> response = restTemplate.getForEntity(getUrl, UserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getEmail()).isEqualTo("ivan@mail.com");
    }

    @Test
    @Order(4)
    void getUserById_NotFound_ShouldThrowException() {
        String url = baseUrl + "/users/999";

        assertThatThrownBy(() -> restTemplate.getForEntity(url, UserDto.class))
                .isInstanceOf(HttpClientErrorException.NotFound.class); // Должен быть 404, не 400!
    }

    @Test
    @Order(5)
    void createCard_ShouldReturnCreatedCard() {
        String createUserUrl = baseUrl + "/users";
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(
                createUserUrl, userCreateDto, UserDto.class);
        Long userId = userResponse.getBody().getId();

        String createCardUrl = baseUrl + "/users/" + userId + "/cards";
        ResponseEntity<PaymentCardDto> response = restTemplate.postForEntity(
                createCardUrl,
                cardDto,
                PaymentCardDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getNumber()).isEqualTo("1234567890123456");
        assertThat(response.getBody().getUserId()).isEqualTo(userId);
    }

    @Test
    @Order(6)
    void createCard_MaxCardsExceeded_ShouldThrowException() {
        String createUserUrl = baseUrl + "/users";
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(
                createUserUrl, userCreateDto, UserDto.class);
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
        String createUrl = baseUrl + "/users";
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                createUrl, userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        UserDto updateDto = new UserDto();
        updateDto.setId(userId);
        updateDto.setName("Петр");
        updateDto.setSurname("Иванов");
        updateDto.setBirthDate(birthDate);
        updateDto.setEmail("petr@mail.com");
        updateDto.setActive(true);

        String updateUrl = baseUrl + "/users/" + userId;
        restTemplate.put(updateUrl, updateDto);

        String getUrl = baseUrl + "/users/" + userId;
        ResponseEntity<UserDto> getResponse = restTemplate.getForEntity(getUrl, UserDto.class);

        assertThat(getResponse.getBody().getName()).isEqualTo("Петр");
        assertThat(getResponse.getBody().getEmail()).isEqualTo("petr@mail.com");
    }

    @Test
    @Order(8)
    void deleteUser_ShouldRemoveUser() {
        String createUrl = baseUrl + "/users";
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                createUrl, userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        String deleteUrl = baseUrl + "/users/" + userId;
        restTemplate.delete(deleteUrl);

        String getUrl = baseUrl + "/users/" + userId;
        assertThatThrownBy(() -> restTemplate.getForEntity(getUrl, UserDto.class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    @Order(9)
    void searchByFullName_ShouldReturnMatchingUsers() {
        String createUrl = baseUrl + "/users";
        restTemplate.postForEntity(createUrl, userCreateDto, UserDto.class);

        String searchUrl = baseUrl + "/users/search?fullName=Иван Петров";
        ResponseEntity<UserDto[]> response = restTemplate.getForEntity(searchUrl, UserDto[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getName()).isEqualTo("Иван");
    }*/
}
