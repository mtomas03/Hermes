package it.unibo.hermes.gateway.security;

import it.unibo.hermes.gateway.domain.User;
import it.unibo.hermes.gateway.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Service bridging Spring Security authentication mechanisms with persistent user entity storage.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Constructs the user details service with the user repository.
     *
     * @param userRepository the repository managing user persistence operations
     */
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads user authentication details from persistent storage by username.
     *
     * @param username the target username
     * @return the populated {@link UserDetails} object for Spring Security context configuration
     * @throws UsernameNotFoundException if no user matching the given username exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles("USER")
                .build();
    }
}