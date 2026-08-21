package com.ausiankou.user.service;

import com.ausiankou.user.dto.PaymentCardCreateRequest;
import com.ausiankou.user.dto.PaymentCardDto;
import com.ausiankou.user.dto.UserCreateRequest;
import com.ausiankou.user.dto.UserDto;
import com.ausiankou.user.entity.PaymentCard;
import com.ausiankou.user.entity.User;
import com.ausiankou.user.exception.UserException;
import com.ausiankou.user.mapper.PaymentCardMapper;
import com.ausiankou.user.mapper.UserMapper;
import com.ausiankou.user.repository.PaymentCardRepository;
import com.ausiankou.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentCardRepository cardRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PaymentCardMapper cardMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .name("Anton")
                .surname("K")
                .email("anton@example.com")
                .active(true)
                .build();
    }

    @Test
    void createUser_savesWhenEmailFree() {
        UserCreateRequest request = new UserCreateRequest("Anton", "K", LocalDate.of(1995, 1, 1), "anton@example.com");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        UserDto dto = new UserDto(userId, "Anton", "K", null, "anton@example.com", true, List.of(), null, null);
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto result = userService.createUser(request);

        assertThat(result.email()).isEqualTo("anton@example.com");
        verify(userRepository).save(user);
    }

    @Test
    void createUser_throwsWhenEmailTaken() {
        UserCreateRequest request = new UserCreateRequest("Anton", "K", LocalDate.of(1995, 1, 1), "anton@example.com");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUser_returnsDtoWhenFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UserDto dto = new UserDto(userId, "Anton", "K", null, "anton@example.com", true, List.of(), null, null);
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto result = userService.getUser(userId);

        assertThat(result.id()).isEqualTo(userId);
    }

    @Test
    void getUser_throwsNotFoundWhenMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteUser_throwsWhenMissing() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserException.class);

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_deletesWhenPresent() {
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.deleteUser(userId);

        verify(userRepository).deleteById(userId);
    }

    @Test
    void addCard_throwsWhenLimitReached() {
        PaymentCardCreateRequest request = new PaymentCardCreateRequest("4111111111111111", "Anton K", LocalDate.now().plusYears(2));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserIdAndActiveTrue(userId)).thenReturn(5L);

        assertThatThrownBy(() -> userService.addCard(userId, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("5 cards");

        verify(cardRepository, never()).save(any());
    }

    @Test
    void addCard_savesWhenUnderLimit() {
        PaymentCardCreateRequest request = new PaymentCardCreateRequest("4111111111111111", "Anton K", LocalDate.now().plusYears(2));
        PaymentCard card = PaymentCard.builder().id(UUID.randomUUID()).user(user).build();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(cardRepository.countByUserIdAndActiveTrue(userId)).thenReturn(2L);
        when(cardMapper.toEntity(request)).thenReturn(card);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(new PaymentCardDto(card.getId(), userId, "4111111111111111", "Anton K", request.expirationDate(), true));

        PaymentCardDto result = userService.addCard(userId, request);

        assertThat(result.userId()).isEqualTo(userId);
        verify(cardRepository).save(card);
    }

    @Test
    void setActive_togglesUserFlag() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.setActive(userId, false);

        assertThat(user.isActive()).isFalse();
    }
}
