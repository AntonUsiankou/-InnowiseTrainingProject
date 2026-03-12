package com.ausiankou;

import com.ausiankou.dto.PaymentCardDto;
import com.ausiankou.dto.UserCreateDto;
import com.ausiankou.dto.UserDto;
import com.ausiankou.entity.PaymentCard;
import com.ausiankou.entity.User;
import com.ausiankou.exception.CustomExceptions;
import com.ausiankou.mapper.PaymentCardMapper;
import com.ausiankou.mapper.UserMapper;
import com.ausiankou.repository.PaymentCardRepository;
import com.ausiankou.repository.UserRepository;
import com.ausiankou.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentCardRepository cardRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    
    private PaymentCardMapper cardMapper;

    @InjectMocks
    private UserService userService;

    private User user;
    private UserDto userDto;
    private UserCreateDto createDto;
    private PaymentCard card;
    private PaymentCardDto cardDto;
    private LocalDate birthDate;

    @BeforeEach
    void setUp() {
        birthDate = LocalDate.of(1990, 1, 1);

        user = new User();
        user.setId(1L);
        user.setName("Иван");
        user.setSurname("Петров");
        user.setBirthDate(birthDate);
        user.setEmail("ivan@mail.com");
        user.setActive(true);

        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setName("Иван");
        userDto.setSurname("Петров");
        userDto.setBirthDate(birthDate);
        userDto.setEmail("ivan@mail.com");
        userDto.setActive(true);

        createDto = new UserCreateDto();
        createDto.setName("Иван");
        createDto.setSurname("Петров");
        createDto.setBirthDate(birthDate);
        createDto.setEmail("ivan@mail.com");

        card = new PaymentCard();
        card.setId(1L);
        card.setNumber("1234567890123456");
        card.setHolder("IVAN PETROV");
        card.setExpirationDate(LocalDate.now().plusYears(2));
        card.setActive(true);
        card.setUser(user);

        cardDto = new PaymentCardDto();
        cardDto.setId(1L);
        cardDto.setUserId(1L);
        cardDto.setNumber("1234567890123456");
        cardDto.setHolder("IVAN PETROV");
        cardDto.setExpirationDate(LocalDate.now().plusYears(2));
        cardDto.setActive(true);
    }

    @Test
    @DisplayName("Создание пользователя и сохранение")
    void createUser_ShouldSaveUser() {
        when(userRepository.findByEmail("ivan@mail.com")).thenReturn(Optional.empty());
        when(userMapper.toEntity(createDto)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.createUser(createDto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("ivan@mail.com");

        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Создание пользователя с существющим мылом")
    void createUser_DuplicateEmail_ShouldThrowException() {
        when(userRepository.findByEmail("ivan@mail.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.createUser(createDto))
                .isInstanceOf(CustomExceptions.DuplicateResourceException.class)
                .hasMessageContaining("уже существует");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Создание карты")
    void createCard_ShouldSaveCard() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserId(1L)).thenReturn(0L);
        when(cardRepository.findByNumber("1234567890123456")).thenReturn(Optional.empty());
        when(cardMapper.toEntityWithUser(cardDto, user)).thenReturn(card);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(cardDto);

        PaymentCardDto result = userService.createCard(1L, cardDto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getNumber()).isEqualTo("1234567890123456");
    }

    @Test
    @DisplayName("Проверка при создание карт больше 5 = искл")
    void createCard_MaxCardsExceeded_ShouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> userService.createCard(1L, cardDto))
                .isInstanceOf(CustomExceptions.BusinessRuleException.class)
                .hasMessageContaining("больше 5 карт");
    }

    @Test
    @DisplayName("Поиск юзер по ID")
    void getUserById_ShouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Поиск несуществующего юзера = искл")
    void getUserById_NotFound_ShouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(CustomExceptions.ResourceNotFoundException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Поиск карт по пользователю")
    void getCardsByUserId_ShouldReturnCards() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(cardRepository.findByUserId(1L)).thenReturn(List.of(card));
        when(cardMapper.toDtoList(anyList())).thenReturn(List.of(cardDto));

        List<PaymentCardDto> results = userService.getCardsByUserId(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNumber()).isEqualTo("1234567890123456");
    }

    @Test
    @DisplayName("Проверка обновления юзера")
    void updateUser_ShouldUpdateUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("ivan@mail.com")).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.updateUser(1L, userDto);

        assertThat(result).isNotNull();
        verify(userMapper).partialUpdate(userDto, user);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Проверка изменения активности юзера")
    void activateUser_ShouldSetActiveTrue() {
        user.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.activateUser(1L);

        assertThat(user.getActive()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Проверка дизактивации юзера")
    void deactivateUser_ShouldSetActiveFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.deactivateUser(1L);

        assertThat(user.getActive()).isFalse();
        verify(cardRepository).updateCardStatusByUserId(1L, false);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Удаление юзера")
    void deleteUser_ShouldDeleteUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doNothing().when(userRepository).delete(user);

        userService.deleteUser(1L);

        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("Поиск по полному имени")
    void searchByFullName_ShouldReturnUsers() {
        when(userRepository.searchByFullNameNative("Иван Петров")).thenReturn(List.of(user));
        when(userMapper.toDtoList(anyList())).thenReturn(List.of(userDto));

        List<UserDto> results = userService.searchByFullName("Иван Петров");

        assertThat(results).hasSize(1);
    }
}
