package it.unibo.hermes.client.model.service;

import it.unibo.hermes.client.infrastructure.config.AppProperties;
import it.unibo.hermes.client.infrastructure.exception.AuthException;
import it.unibo.hermes.client.infrastructure.exception.AuthException.Reason;
import it.unibo.hermes.client.model.dto.AuthResponseDto;
import it.unibo.hermes.client.model.dto.LoginRequestDto;
import it.unibo.hermes.client.model.dto.RegisterRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * Service responsible for handling user authentication and registration
 * by communicating with the backend REST API.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final WebClient webClient;
    private final AppProperties props;

    public AuthService(WebClient webClient, AppProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    /**
     * Registers a new user account by sending a POST request to the backend.
     *
     * @param username the desired username for the new account
     * @param password the desired password for the new account
     * @return a Mono that completes when the registration is successful or emits an error if it fails
     */
    public Mono<Void> register(String username, String password) {
        return webClient.post()
                .uri(props.getRegisterPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequestDto(username, password))
                .retrieve()
                .onStatus(s -> s.value() == HttpStatus.CONFLICT.value(),
                        r -> r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AuthException(
                                        Reason.USERNAME_TAKEN,
                                        HttpStatus.CONFLICT.value(),
                                        formatMessage("Registration failed", body)))))
                .onStatus(HttpStatusCode::is4xxClientError,
                        r -> r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AuthException(
                                        Reason.BAD_REQUEST,
                                        r.statusCode().value(),
                                        formatMessage("Registration failed", body)))))
                .onStatus(s -> s.value() == HttpStatus.SERVICE_UNAVAILABLE.value(),
                        r -> Mono.error(new AuthException(
                                Reason.SERVICE_UNAVAILABLE,
                                HttpStatus.SERVICE_UNAVAILABLE.value(),
                                "Registration failed: service unavailable")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        r -> r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AuthException(
                                        Reason.SERVER_ERROR,
                                        r.statusCode().value(),
                                        formatMessage("Registration failed", body)))))
                .bodyToMono(Void.class)
                .onErrorMap(WebClientRequestException.class,
                        e -> new AuthException(Reason.NETWORK_ERROR, 0, "Cannot reach the server", e))
                .doOnSuccess(v -> log.info("Registration successful for {}", username))
                .doOnError(e -> log.warn("Registration error: {}", e.getMessage()));
    }

    /**
     * Authenticates user credentials by sending a POST request to the backend.
     *
     * @param username the username of the account to authenticate
     * @param password the password of the account to authenticate
     * @return a Mono emitting the authentication response containing the JWT token if successful,
     * or an error if authentication fails
     */
    public Mono<AuthResponseDto> login(String username, String password) {
        return webClient.post()
                .uri(props.getLoginPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequestDto(username, password))
                .retrieve()
                .onStatus(s -> s.value() == HttpStatus.UNAUTHORIZED.value(),
                        r -> Mono.error(new AuthException(
                                Reason.INVALID_CREDENTIALS,
                                HttpStatus.UNAUTHORIZED.value(),
                                "Invalid credentials")))
                .onStatus(s -> s.value() == HttpStatus.SERVICE_UNAVAILABLE.value(),
                        r -> Mono.error(new AuthException(
                                Reason.SERVICE_UNAVAILABLE,
                                HttpStatus.SERVICE_UNAVAILABLE.value(),
                                "Server error: service unavailable")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        r -> r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AuthException(
                                        Reason.SERVER_ERROR,
                                        r.statusCode().value(),
                                        formatMessage("Server error", body)))))
                .bodyToMono(AuthResponseDto.class)
                .onErrorMap(WebClientRequestException.class,
                        e -> new AuthException(Reason.NETWORK_ERROR, 0, "Cannot reach the server", e))
                .doOnSuccess(r -> log.info("Login successful, username={}", r.username()))
                .doOnError(e -> log.warn("Login error: {}", e.getMessage()));
    }

    private String formatMessage(String prefix, String body) {
        return (body == null || body.isBlank()) ? prefix : prefix + ": " + body;
    }
}
