package it.unibo.hermes.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AuthResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AppProperties props;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        ReflectionTestUtils.setField(props, "registerPath", "/api/v1/auth/register");
        ReflectionTestUtils.setField(props, "loginPath", "/api/v1/auth/login");
    }

    private WebClient stubClient(HttpStatus status, String jsonBody) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(status)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body(jsonBody == null ? "" : jsonBody)
                                .build()))
                .build();
    }

    @Test
    void loginWithValidCredentialsShouldReturnLoginResponse() throws Exception {
        AuthResponseDto expected = new AuthResponseDto("jwt-abc", "alice", 3_600_000);
        AuthService service = new AuthService(stubClient(HttpStatus.OK, mapper.writeValueAsString(expected)), props);

        StepVerifier.create(service.login("alice", "secret"))
                .assertNext(resp -> assertEquals("jwt-abc", resp.token()))
                .verifyComplete();
    }

    @Test
    void loginWithInvalidCredentialsShouldFailWithInvalidCredentialsMessage() {
        AuthService service = new AuthService(stubClient(HttpStatus.UNAUTHORIZED, null), props);

        StepVerifier.create(service.login("alice", "wrong"))
                .expectErrorMatches(e -> e.getMessage().contains("Invalid credentials"))
                .verify();
    }

    @Test
    void registrationSuccessShouldCompleteWithoutError() {
        AuthService service = new AuthService(stubClient(HttpStatus.CREATED, null), props);

        StepVerifier.create(service.register("newuser", "password123"))
                .verifyComplete();
    }

    @Test
    void registrationWithDuplicateUsernameShouldFail() {
        AuthService service = new AuthService(
                stubClient(HttpStatus.BAD_REQUEST, "Username 'alice' is already taken"), props);

        StepVerifier.create(service.register("alice", "password123"))
                .expectErrorMatches(e -> e.getMessage().contains("Registration failed"))
                .verify();
    }
}
