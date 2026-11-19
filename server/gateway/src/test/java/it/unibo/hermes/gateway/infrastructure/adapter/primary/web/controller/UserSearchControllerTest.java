package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.UserSearchPort;
import it.unibo.hermes.gateway.domain.exception.UserNotFoundException;
import it.unibo.hermes.gateway.domain.model.UserProfile;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSearchControllerTest {

    @Mock
    private UserSearchPort userSearchService;

    private UserSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new UserSearchController(userSearchService);
    }

    @Test
    void searchUserShouldReturnUserDtoWhenFound() {
        UserDto expected = new UserDto("bob");
        when(userSearchService.searchUserByUsername("bob")).thenReturn(new UserProfile("bob"));

        ResponseEntity<UserDto> response = controller.searchUser("bob");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(userSearchService).searchUserByUsername("bob");
    }

    @Test
    void searchUserShouldPropagateExceptionWhenUserNotFound() {
        when(userSearchService.searchUserByUsername("ghost"))
                .thenThrow(new UserNotFoundException("User not found: ghost"));

        assertThatThrownBy(() -> controller.searchUser("ghost"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
