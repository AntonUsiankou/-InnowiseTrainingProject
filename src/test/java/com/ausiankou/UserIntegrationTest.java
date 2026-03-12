package com.ausiankou;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
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
    }

    @Autowired
    private RestTemplate restTemplate;

    private UserCreateDto userCreateDto;
    private PaymentCardDto cardDto;
    private LocalDate birthDate;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void createUser_ShouldReturnCreatedUser() {
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
                "/api/users",
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
    void createUser_DuplicateEmail_ShouldReturnConflict() {
        restTemplate.postForEntity("/api/users", userCreateDto, UserDto.class);

        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/users",
                userCreateDto,
                Object.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getUserById_ShouldReturnUser() {
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        ResponseEntity<UserDto> response = restTemplate.getForEntity(
                "/api/users/" + userId,
                UserDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getEmail()).isEqualTo("ivan@mail.com");
    }

    @Test
    void getUserById_NotFound_ShouldReturn404() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                "/api/users/999",
                Object.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createCard_ShouldReturnCreatedCard() {
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = userResponse.getBody().getId();

        ResponseEntity<PaymentCardDto> response = restTemplate.postForEntity(
                "/api/users/" + userId + "/cards",
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
    void createCard_MaxCardsExceeded_ShouldReturnBadRequest() {
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = userResponse.getBody().getId();

        for (int i = 0; i < 5; i++) {
            PaymentCardDto newCard = new PaymentCardDto();
            newCard.setNumber(String.format("%016d", i + 1));
            newCard.setHolder("IVAN PETROV");
            newCard.setExpirationDate(LocalDate.now().plusYears(2));
            newCard.setActive(true);

            restTemplate.postForEntity(
                    "/api/users/" + userId + "/cards",
                    newCard,
                    PaymentCardDto.class
            );
        }

        PaymentCardDto sixthCard = new PaymentCardDto();
        sixthCard.setNumber("9999999999999999");
        sixthCard.setHolder("IVAN PETROV");
        sixthCard.setExpirationDate(LocalDate.now().plusYears(2));
        sixthCard.setActive(true);

        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/users/" + userId + "/cards",
                sixthCard,
                Object.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUserCards_ShouldReturnAllCards() {
        ResponseEntity<UserDto> userResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = userResponse.getBody().getId();

        restTemplate.postForEntity(
                "/api/users/" + userId + "/cards",
                cardDto,
                PaymentCardDto.class
        );

        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/users/" + userId + "/cards",
                List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void updateUser_ShouldReturnUpdatedUser() {
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        UserDto updateDto = createResponse.getBody();
        updateDto.setName("Петр");
        updateDto.setEmail("petr@mail.com");

        restTemplate.put("/api/users/" + userId, updateDto);

        ResponseEntity<UserDto> getResponse = restTemplate.getForEntity(
                "/api/users/" + userId,
                UserDto.class
        );

        assertThat(getResponse.getBody().getName()).isEqualTo("Петр");
        assertThat(getResponse.getBody().getEmail()).isEqualTo("petr@mail.com");
    }

    @Test
    void activateDeactivateUser_ShouldChangeStatus() {
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        restTemplate.patchForObject(
                "/api/users/" + userId + "/deactivate",
                null,
                Void.class
        );

        ResponseEntity<UserDto> deactivatedResponse = restTemplate.getForEntity(
                "/api/users/" + userId,
                UserDto.class
        );
        assertThat(deactivatedResponse.getBody().getActive()).isFalse();

        restTemplate.patchForObject(
                "/api/users/" + userId + "/activate",
                null,
                Void.class
        );

        ResponseEntity<UserDto> activatedResponse = restTemplate.getForEntity(
                "/api/users/" + userId,
                UserDto.class
        );
        assertThat(activatedResponse.getBody().getActive()).isTrue();
    }

    @Test
    void deleteUser_ShouldRemoveUser() {
        ResponseEntity<UserDto> createResponse = restTemplate.postForEntity(
                "/api/users", userCreateDto, UserDto.class);
        Long userId = createResponse.getBody().getId();

        restTemplate.delete("/api/users/" + userId);

        ResponseEntity<Object> getResponse = restTemplate.getForEntity(
                "/api/users/" + userId,
                Object.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void searchByFullName_ShouldReturnMatchingUsers() {
        restTemplate.postForEntity("/api/users", userCreateDto, UserDto.class);

        ResponseEntity<UserDto[]> response = restTemplate.getForEntity(
                "/api/users/search?fullName=Иван Петров",
                UserDto[].class
        );

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getName()).isEqualTo("Иван");
    }
}