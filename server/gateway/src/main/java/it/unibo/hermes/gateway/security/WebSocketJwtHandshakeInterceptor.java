package it.unibo.hermes.gateway.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Handshake interceptor validating JWTs during WebSocket HTTP upgrade requests.
 */
@Component
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_ATTR_USERNAME = "username";

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketJwtHandshakeInterceptor.class);

    private final JwtProvider jwtProvider;

    /**
     * Creates the handshake interceptor with the JWT provider.
     *
     * @param jwtProvider the utility component verifying token validity and extracting claims
     */
    public WebSocketJwtHandshakeInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Validates the JWT parameter during the handshake, rejecting unauthenticated requests with HTTP 401.
     *
     * @param request    the incoming HTTP request
     * @param response   the outgoing HTTP response
     * @param wsHandler  the target WebSocket handler
     * @param attributes session attributes passed to the WebSocket session
     * @return {@code true} if the token is valid and the handshake may proceed, {@code false} otherwise
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {

        List<String> tokens = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");

        if (tokens == null || tokens.isEmpty()) {
            log.warn("WebSocket upgrade rejected: no token in query params");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String token = tokens.getFirst();
        if (!jwtProvider.isValid(token)) {
            log.warn("WebSocket upgrade rejected: invalid token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String username = jwtProvider.extractUsername(token);
        attributes.put(SESSION_ATTR_USERNAME, username);
        log.debug("WebSocket handshake accepted for user '{}'", username);
        return true;
    }

    /**
     * Callback executed after the handshake completes or fails.
     *
     * @param request   the HTTP request
     * @param response  the HTTP response
     * @param wsHandler the WebSocket handler
     * @param exception an exception thrown during handshake processing, if any
     */
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
    }
}