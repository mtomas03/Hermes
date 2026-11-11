package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.model.SendMessageCommand;
import it.unibo.hermes.gateway.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.gateway.application.port.primary.InboundMessagePort;
import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.AckDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.DeliveryAckDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ErrorDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageToGatewayDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompControllerTest {

    private static final Principal ALICE = () -> "alice";
    @Mock
    private InboundMessagePort inboundMessagePort;
    @Mock
    private AcknowledgementPort acknowledgementPort;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    private StompController stompController;

    @BeforeEach
    void setUp() {
        stompController = new StompController(inboundMessagePort, acknowledgementPort, messagingTemplate);
    }

    private MessageToGatewayDto payload(String messageId) {
        return new MessageToGatewayDto(messageId, "alice-bob", 
                "alice", "bob", 
                "hi", 1L);
    }

    private ErrorDto captureError() {
        ArgumentCaptor<ErrorDto> captor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), captor.capture());
        return captor.getValue();
    }

    @Test
    void validSendMessageShouldBePublishedAndAcknowledged() {
        UUID messageId = UUID.randomUUID();
        Message accepted = new Message(messageId, "alice-bob", 
                "alice", "bob", 
                "hi", 1L, MessageStatus.PENDING);
        when(inboundMessagePort.publish(any())).thenReturn(accepted);

        stompController.sendMessage(payload(messageId.toString()), ALICE);

        verify(inboundMessagePort).publish(new SendMessageCommand(
                messageId.toString(), "alice-bob", 
                "alice", "bob", 
                "hi", 1L));

        ArgumentCaptor<AckDto> ackCaptor = ArgumentCaptor.forClass(AckDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/acks"), ackCaptor.capture());
        assertThat(ackCaptor.getValue().messageId()).isEqualTo(messageId.toString());
        assertThat(ackCaptor.getValue().status()).isEqualTo("ACCEPTED");
    }

    @Test
    void senderShouldAlwaysComeFromThePrincipalNotThePayload() {
        UUID messageId = UUID.randomUUID();
        when(inboundMessagePort.publish(any())).thenReturn(
                new Message(messageId, "alice-bob", 
                        "alice", "bob", 
                        "hi", 1L, MessageStatus.PENDING));
        MessageToGatewayDto forged = new MessageToGatewayDto(
                messageId.toString(), "alice-bob",
                "charlie", "bob",
                "hi", 1L);

        stompController.sendMessage(forged, ALICE);

        ArgumentCaptor<SendMessageCommand> captor = ArgumentCaptor.forClass(SendMessageCommand.class);
        verify(inboundMessagePort).publish(captor.capture());
        assertThat(captor.getValue().senderUsername()).isEqualTo("alice");
    }

    @Test
    void businessRuleViolationShouldBeReportedWithItsCodeAndMessageId() {
        String messageId = UUID.randomUUID().toString();
        when(inboundMessagePort.publish(any()))
                .thenThrow(new InvalidMessageException("INVALID_CONVERSATION", "does not match"));

        stompController.sendMessage(payload(messageId), ALICE);

        ErrorDto error = captureError();
        assertThat(error.code()).isEqualTo("INVALID_CONVERSATION");
        assertThat(error.reason()).isEqualTo("does not match");
        assertThat(error.messageId()).isEqualTo(messageId);
        verify(messagingTemplate, never()).convertAndSendToUser(any(), eq("/queue/acks"), any());
    }

    @Test
    void deliveryRejectionShouldBeReportedWhenPersistenceUnavailable() {
        when(inboundMessagePort.publish(any()))
                .thenThrow(new PersistenceUnavailableException("down", new RuntimeException()));

        stompController.sendMessage(payload(UUID.randomUUID().toString()), ALICE);

        assertThat(captureError().code()).isEqualTo("DELIVERY_REJECTED");
    }

    @Test
    void unexpectedErrorShouldReturnInternalError() {
        when(inboundMessagePort.publish(any())).thenThrow(new RuntimeException("Unexpected NPE"));

        stompController.sendMessage(payload(UUID.randomUUID().toString()), ALICE);

        assertThat(captureError().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void ackShouldDelegateUsingAuthenticatedPrincipalAsRecipient() {
        String messageId = UUID.randomUUID().toString();

        stompController.ack(new DeliveryAckDto(messageId), ALICE);

        verify(acknowledgementPort).acknowledge(messageId, "alice");
        verifyNoInteractions(inboundMessagePort);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ackMissingMessageIdShouldBeRejectedWithStructuredErrorAndNotForwarded() {
        stompController.ack(new DeliveryAckDto(null), ALICE);

        verifyNoInteractions(acknowledgementPort);
        assertThat(captureError().code()).isEqualTo("MISSING_MESSAGE_ID");
    }

    @Test
    void ackBlankMessageIdShouldBeRejected() {
        stompController.ack(new DeliveryAckDto("   "), ALICE);

        verifyNoInteractions(acknowledgementPort);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), any(ErrorDto.class));
    }

    @Test
    void ackShouldNeverTrustAClientDeclaredIdentityBecauseThePayloadCarriesNone() {
        String messageId = UUID.randomUUID().toString();
        Principal bob = () -> "bob";

        stompController.ack(new DeliveryAckDto(messageId), bob);

        verify(acknowledgementPort).acknowledge(messageId, "bob");
    }

    @Test
    void malformedPayloadShouldProduceMalformedMessageErrorWithoutClosingSession() {
        ErrorDto result = stompController.handleException(new RuntimeException("bad payload"));

        assertThat(result.code()).isEqualTo("MALFORMED_MESSAGE");
    }
}
