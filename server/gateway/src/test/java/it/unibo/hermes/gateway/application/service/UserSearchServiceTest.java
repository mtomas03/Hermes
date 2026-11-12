package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.exception.UserNotFoundException;
import it.unibo.hermes.gateway.domain.model.UserAccount;
import it.unibo.hermes.gateway.domain.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock
    private UserAccountPort userAccounts;

    private UserSearchService userSearchService;

    @BeforeEach
    void setUp() {
        userSearchService = new UserSearchService(userAccounts);
    }

    @Test
    void shouldReturnProfileWhenUserExists() {
        when(userAccounts.findByUsername("alice")).thenReturn(Optional.of(new UserAccount("alice", "hashed-password")));

        UserProfile result = userSearchService.searchUserByUsername("alice");

        assertThat(result).isEqualTo(new UserProfile("alice"));
        verify(userAccounts).findByUsername("alice");
    }

    @Test
    void shouldThrowUserNotFoundWhenUserDoesNotExist() {
        when(userAccounts.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSearchService.searchUserByUsername("ghost"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found: ghost");

        verify(userAccounts).findByUsername("ghost");
    }
}
