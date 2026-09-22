package it.unibo.hermes.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Channel interceptor validating the JWT, carried in the {@code Authorization} header of the
 * STOMP CONNECT frame, and attaching an authenticated {@link StompPrincipal} to the session.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtProvider jwtProvider;

    /**
     * Creates the STOMP authentication interceptor.
     *
     * @param jwtProvider the utility component verifying token validity and extracting claims
     */
    public StompAuthChannelInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Validates the JWT on every CONNECT frame before it reaches the broker,
     * rejecting the session if the token is missing or invalid.
     * All other STOMP commands pass through untouched.
     *
     * @param message   the inbound STOMP message
     * @param channel   the channel the message is being sent to
     * @return the message unchanged, if authentication succeeds or is not applicable
     * @throws MessagingException if the CONNECT frame carries no valid JWT
     */
    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractBearerToken(accessor.getFirstNativeHeader(AUTH_HEADER));

            if (token == null || !jwtProvider.isValid(token)) {
                log.warn("STOMP CONNECT rejected: missing or invalid JWT");
                throw new MessagingException("Invalid or missing JWT in STOMP CONNECT frame");
            }

            String username = jwtProvider.extractUsername(token);
            accessor.setUser(new StompPrincipal(username));
            log.debug("STOMP CONNECT authenticated for user '{}'", username);
        }

        return message;
    }

    private String extractBearerToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}
