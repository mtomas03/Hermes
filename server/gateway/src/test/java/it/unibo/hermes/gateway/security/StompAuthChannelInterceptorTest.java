package it.unibo.hermes.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private MessageChannel channel;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtProvider);
    }

    private Message<byte[]> connectFrameWithAuthHeader(String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (headerValue != null) {
            accessor.setNativeHeader("Authorization", headerValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void shouldAuthenticateConnectFrameWithValidBearerToken() {
        when(jwtProvider.isValid("good-token")).thenReturn(true);
        when(jwtProvider.extractUsername("good-token")).thenReturn("alice");
        Message<byte[]> message = connectFrameWithAuthHeader("Bearer good-token");

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("alice");
    }

    @Test
    void shouldRejectConnectFrameWithMissingAuthHeader() {
        Message<byte[]> message = connectFrameWithAuthHeader(null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldRejectConnectFrameWithMalformedAuthHeader() {
        Message<byte[]> message = connectFrameWithAuthHeader("NotBearer good-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldRejectConnectFrameWithInvalidToken() {
        when(jwtProvider.isValid("bad-token")).thenReturn(false);
        Message<byte[]> message = connectFrameWithAuthHeader("Bearer bad-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldPassThroughNonConnectFramesUnauthenticated() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }
}
