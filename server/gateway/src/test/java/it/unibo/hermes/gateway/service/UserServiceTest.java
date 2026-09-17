package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.dto.UserDto;
import it.unibo.hermes.gateway.entity.jpa.User;
import it.unibo.hermes.gateway.repository.jpa.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void shouldReturnUserDtoWhenUserExists() {
        User user = new User("alice", "hashed-password");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDto result = userService.searchUserByUsername("alice");

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("alice");
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void shouldThrowResponseStatusExceptionWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.searchUserByUsername("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(userRepository).findByUsername("ghost");
    }
}