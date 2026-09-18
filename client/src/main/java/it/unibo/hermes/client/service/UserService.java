package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Service responsible for searching users by username
 * by communicating with the backend REST API.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final WebClient webClient;
    private final AppProperties props;

    public UserService(WebClient webClient, AppProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    /**
     * Searches for a user by username by sending a GET request to the backend.
     *
     * @param username    the username to search for
     * @param bearerToken the JWT token for authorisation
     * @return a Mono emitting the UserDto if found, or an error if not found
     */
    public Mono<UserDto> searchUser(String username, String bearerToken) {
        return webClient.get()
                .uri(props.getUserSearchPath() + "?username=" + username)
                .header("Authorization", bearerToken)
                .retrieve()
                .onStatus(s -> s.value() == HttpStatus.NOT_FOUND.value(),
                        r -> Mono.error(new RuntimeException("User not found: " + username)))
                .bodyToMono(UserDto.class)
                .doOnError(e -> log.warn("User search error: {}", e.getMessage()));
    }
}
