package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.UserDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.jpa.User;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.jpa.UserRepository;
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
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserSearchService userSearchService;

    @BeforeEach
    void setUp() {
        userSearchService = new UserSearchService(userRepository);
    }

    @Test
    void shouldReturnUserDtoWhenUserExists() {
        User user = new User("alice", "hashed-password");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDto result = userSearchService.searchUserByUsername("alice");

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("alice");
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void shouldThrowResponseStatusExceptionWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSearchService.searchUserByUsername("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(userRepository).findByUsername("ghost");
    }
}