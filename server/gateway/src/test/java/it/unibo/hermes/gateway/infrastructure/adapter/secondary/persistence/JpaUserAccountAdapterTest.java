package it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence;

import it.unibo.hermes.gateway.domain.model.UserAccount;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.jpa.User;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.jpa.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaUserAccountAdapterTest {

    @Mock
    private UserRepository userRepository;

    private JpaUserAccountAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaUserAccountAdapter(userRepository);
    }

    @Test
    void shouldDelegateExistenceCheck() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThat(adapter.existsByUsername("alice")).isTrue();
    }

    @Test
    void shouldSaveAccountAsJpaEntity() {
        adapter.save(new UserAccount("alice", "hash"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void shouldMapFoundEntityToDomainAccount() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User("alice", "hash")));

        assertThat(adapter.findByUsername("alice")).contains(new UserAccount("alice", "hash"));
    }

    @Test
    void shouldReturnEmptyWhenUserIsUnknown() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(adapter.findByUsername("ghost")).isEmpty();
    }
}
