package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.UserDto;
import it.unibo.hermes.gateway.service.UserService;
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
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService);
    }

    @Test
    void searchUserShouldReturnUserDtoWhenFound() {
        UserDto expected = new UserDto("bob");
        when(userService.searchUserByUsername("bob")).thenReturn(expected);

        ResponseEntity<UserDto> response = controller.searchUser("bob");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(userService).searchUserByUsername("bob");
    }

    @Test
    void searchUserShouldPropagateExceptionWhenUserNotFound() {
        when(userService.searchUserByUsername("ghost"))
                .thenThrow(new IllegalArgumentException("User not found: ghost"));

        assertThatThrownBy(() -> controller.searchUser("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }
}
