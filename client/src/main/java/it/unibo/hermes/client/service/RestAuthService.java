package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.LoginRequestDto;
import it.unibo.hermes.client.dto.LoginResponseDto;
import it.unibo.hermes.client.dto.RegisterRequestDto;
import it.unibo.hermes.client.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Handles HTTP calls to the auth endpoints on the backend.
 * All methods return Mono and are non-blocking; callers subscribe on a scheduler.
 */
@Service
public class RestAuthService {

    private static final Logger log = LoggerFactory.getLogger(RestAuthService.class);

    private final WebClient webClient;
    private final AppProperties props;

    public RestAuthService(WebClient webClient, AppProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    public Mono<Void> register(String username, String password) {
        return webClient.post()
                .uri(props.getRegisterPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequestDto(username, password))
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                        r -> r.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("Registration failed: " + body))))
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Registration successful for {}", username))
                .doOnError(e -> log.warn("Registration error: {}", e.getMessage()));
    }

    public Mono<LoginResponseDto> login(String username, String password) {
        return webClient.post()
                .uri(props.getLoginPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequestDto(username, password))
                .retrieve()
                .onStatus(s -> s.value() == HttpStatus.UNAUTHORIZED.value(),
                        r -> Mono.error(new RuntimeException("Invalid credentials")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        r -> r.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(new RuntimeException("Server error: " + b))))
                .bodyToMono(LoginResponseDto.class)
                .doOnSuccess(r -> log.info("Login successful, username={}", r.username()))
                .doOnError(e -> log.warn("Login error: {}", e.getMessage()));
    }

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
