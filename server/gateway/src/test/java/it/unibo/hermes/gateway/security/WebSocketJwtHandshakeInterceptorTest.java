package it.unibo.hermes.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketJwtHandshakeInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private WebSocketHandler wsHandler;

    private WebSocketJwtHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketJwtHandshakeInterceptor(jwtProvider);
    }

    @Test
    void shouldAcceptHandshakeWithValidToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws?token=good-token"));
        when(jwtProvider.isValid("good-token")).thenReturn(true);
        when(jwtProvider.extractUsername("good-token")).thenReturn("alice");
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get(WebSocketJwtHandshakeInterceptor.SESSION_ATTR_USERNAME)).isEqualTo("alice");
    }

    @Test
    void shouldRejectHandshakeWithMissingToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    void shouldRejectHandshakeWithInvalidToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws?token=bad-token"));
        when(jwtProvider.isValid("bad-token")).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
