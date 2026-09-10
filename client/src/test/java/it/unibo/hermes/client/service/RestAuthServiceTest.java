package it.unibo.hermes.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.LoginResponseDto;
import it.unibo.hermes.client.dto.UserDto;
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

class RestAuthServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AppProperties props;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        ReflectionTestUtils.setField(props, "registerPath", "/api/v1/auth/register");
        ReflectionTestUtils.setField(props, "loginPath", "/api/v1/auth/login");
        ReflectionTestUtils.setField(props, "userSearchPath", "/api/v1/users/search");
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
        LoginResponseDto expected = new LoginResponseDto("jwt-abc", "alice", java.time.Instant.now());
        RestAuthService service = new RestAuthService(stubClient(HttpStatus.OK, mapper.writeValueAsString(expected)), props);

        StepVerifier.create(service.login("alice", "secret"))
                .assertNext(resp -> assertEquals("jwt-abc", resp.token()))
                .verifyComplete();
    }

    @Test
    void loginWithInvalidCredentialsShouldFailWithInvalidCredentialsMessage() {
        RestAuthService service = new RestAuthService(stubClient(HttpStatus.UNAUTHORIZED, null), props);

        StepVerifier.create(service.login("alice", "wrong"))
                .expectErrorMatches(e -> e.getMessage().contains("Invalid credentials"))
                .verify();
    }

    @Test
    void registrationSuccessShouldCompleteWithoutError() {
        RestAuthService service = new RestAuthService(stubClient(HttpStatus.CREATED, null), props);

        StepVerifier.create(service.register("newuser", "password123"))
                .verifyComplete();
    }

    @Test
    void registrationWithDuplicateUsernameShouldFail() {
        RestAuthService service = new RestAuthService(
                stubClient(HttpStatus.BAD_REQUEST, "Username 'alice' is already taken"), props);

        StepVerifier.create(service.register("alice", "password123"))
                .expectErrorMatches(e -> e.getMessage().contains("Registration failed"))
                .verify();
    }

    @Test
    void searchUserNotFoundShouldFail() {
        RestAuthService service = new RestAuthService(stubClient(HttpStatus.NOT_FOUND, null), props);

        StepVerifier.create(service.searchUser("ghost", "Bearer t"))
                .expectErrorMatches(e -> e.getMessage().contains("User not found"))
                .verify();
    }

    @Test
    void searchUserFoundShouldReturnUserDto() throws Exception {
        UserDto dto = new UserDto("bob");
        RestAuthService service = new RestAuthService(stubClient(HttpStatus.OK, mapper.writeValueAsString(dto)), props);

        StepVerifier.create(service.searchUser("bob", "Bearer t"))
                .assertNext(u -> assertEquals("bob", u.username()))
                .verifyComplete();
    }
}
