package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.service.InboundMessageService;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.AckDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.DeliveryAckDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ErrorDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageToGatewayDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer.MessageAckProducer;
import it.unibo.hermes.gateway.infrastructure.event.MessageAckEvent;
import it.unibo.hermes.gateway.infrastructure.event.MessageEvent;
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
    private InboundMessageService publisherService;
    @Mock
    private MessageAckProducer messageAckProducer;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    private StompController stompController;

    @BeforeEach
    void setUp() {
        stompController = new StompController(publisherService, messageAckProducer, messagingTemplate);
    }

    @Test
    void validSendMessageShouldBePublishedAndAcknowledged() {
        UUID messageId = UUID.randomUUID();
        MessageEvent accepted = new MessageEvent(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L);
        when(publisherService.publish(any(), eq("alice"))).thenReturn(accepted);

        MessageToGatewayDto payload = new MessageToGatewayDto(
                messageId.toString(), "alice-bob",
                "alice", "bob", "hi",
                1L);
        stompController.sendMessage(payload, ALICE);

        verify(publisherService).publish(payload, "alice");

        ArgumentCaptor<AckDto> ackCaptor = ArgumentCaptor.forClass(AckDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/acks"), ackCaptor.capture());
        assertThat(ackCaptor.getValue().messageId()).isEqualTo(messageId.toString());
        assertThat(ackCaptor.getValue().status()).isEqualTo("ACCEPTED");
    }

    @Test
    void sendMessageWithConversationIdOfOtherUsersShouldBeRejectedAndNeverPublished() {
        MessageToGatewayDto forged = new MessageToGatewayDto(
                UUID.randomUUID().toString(), "bob-carol",
                "alice", "dave", "hi",
                1L);

        stompController.sendMessage(forged, ALICE);

        verifyNoInteractions(publisherService);
        ArgumentCaptor<ErrorDto> error = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("alice"), eq("/queue/errors"), error.capture());
        assertThat(error.getValue().code()).isEqualTo("INVALID_CONVERSATION");
    }

    @Test
    void conversationIdIsCanonicalRegardlessOfParticipantOrderAndCase() {
        assertThat(stompController.isCanonicalConversationId(
                "alice-bob", "alice", "bob")).isTrue();
        assertThat(stompController.isCanonicalConversationId(
                "alice-bob", "bob", "alice")).isTrue();
        assertThat(stompController.isCanonicalConversationId(
                "alice-bob", " Alice ", "BOB")).isTrue();
        assertThat(stompController.isCanonicalConversationId(
                "bob-alice", "alice", "bob")).isFalse();
        assertThat(stompController.isCanonicalConversationId(
                null, "alice", "bob")).isFalse();
    }

    @Test
    void sendMessageMissingRecipientShouldBeRejected() {
        String messageId = UUID.randomUUID().toString();
        MessageToGatewayDto payload = new MessageToGatewayDto(
                messageId, "alice-bob",
                "alice", null, "hi",
                1L);

        stompController.sendMessage(payload, ALICE);

        verifyNoInteractions(publisherService);
        ArgumentCaptor<ErrorDto> errCaptor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errCaptor.capture());
        assertThat(errCaptor.getValue().code()).isEqualTo("MISSING_RECIPIENT");
    }

    @Test
    void sendMessageMissingContentShouldBeRejected() {
        String messageId = UUID.randomUUID().toString();
        MessageToGatewayDto payload = new MessageToGatewayDto(
                messageId, "alice-bob",
                "alice", "bob", null,
                1L);

        stompController.sendMessage(payload, ALICE);

        verifyNoInteractions(publisherService);
        ArgumentCaptor<ErrorDto> errCaptor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errCaptor.capture());
        assertThat(errCaptor.getValue().code()).isEqualTo("MISSING_CONTENT");
    }

    @Test
    void sendingMessageToSelfShouldBeRejected() {
        String messageId = UUID.randomUUID().toString();
        MessageToGatewayDto payload = new MessageToGatewayDto(
                messageId, "alice-bob",
                "alice", "alice", "hi",
                1L);

        stompController.sendMessage(payload, ALICE);

        verifyNoInteractions(publisherService);
        ArgumentCaptor<ErrorDto> errCaptor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errCaptor.capture());
        assertThat(errCaptor.getValue().code()).isEqualTo("SELF_SEND");
    }

    @Test
    void deliveryRejectionShouldBeReportedWhenPersistenceUnavailable() {
        String messageId = UUID.randomUUID().toString();
        when(publisherService.publish(any(), eq("alice")))
                .thenThrow(new PersistenceUnavailableException("down", new RuntimeException()));
        MessageToGatewayDto payload = new MessageToGatewayDto(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L);

        stompController.sendMessage(payload, ALICE);

        ArgumentCaptor<ErrorDto> errCaptor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errCaptor.capture());
        assertThat(errCaptor.getValue().code()).isEqualTo("DELIVERY_REJECTED");
    }

    @Test
    void unexpectedErrorShouldReturnInternalError() {
        String messageId = UUID.randomUUID().toString();
        when(publisherService.publish(any(), eq("alice"))).thenThrow(new RuntimeException("Unexpected NPE"));
        MessageToGatewayDto payload = new MessageToGatewayDto(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L);

        stompController.sendMessage(payload, ALICE);

        ArgumentCaptor<ErrorDto> errCaptor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errCaptor.capture());
        assertThat(errCaptor.getValue().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void ackShouldPublishToKafkaProducerUsingAuthenticatedPrincipalAsRecipient() {
        String messageId = UUID.randomUUID().toString();
        DeliveryAckDto payload = new DeliveryAckDto(messageId);

        stompController.ack(payload, ALICE);

        ArgumentCaptor<MessageAckEvent> captor = ArgumentCaptor.forClass(MessageAckEvent.class);
        verify(messageAckProducer).publishAck(captor.capture());
        MessageAckEvent event = captor.getValue();
        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.recipientUsername()).isEqualTo("alice");
        verifyNoInteractions(publisherService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ackMissingMessageIdShouldBeRejectedWithStructuredErrorAndNotPublished() {
        DeliveryAckDto payload = new DeliveryAckDto(null);

        stompController.ack(payload, ALICE);

        verifyNoInteractions(messageAckProducer);
        ArgumentCaptor<ErrorDto> errCaptor = ArgumentCaptor.forClass(ErrorDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), errCaptor.capture());
        assertThat(errCaptor.getValue().code()).isEqualTo("MISSING_MESSAGE_ID");
    }

    @Test
    void ackBlankMessageIdShouldBeRejected() {
        DeliveryAckDto payload = new DeliveryAckDto("   ");

        stompController.ack(payload, ALICE);

        verifyNoInteractions(messageAckProducer);
        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/errors"), any(ErrorDto.class));
    }

    @Test
    void ackShouldNeverTrustAClientDeclaredIdentityBecauseThePayloadCarriesNone() {
        DeliveryAckDto payload = new DeliveryAckDto(UUID.randomUUID().toString());
        Principal bob = () -> "bob";

        stompController.ack(payload, bob);

        ArgumentCaptor<MessageAckEvent> captor = ArgumentCaptor.forClass(MessageAckEvent.class);
        verify(messageAckProducer).publishAck(captor.capture());
        assertThat(captor.getValue().recipientUsername()).isEqualTo("bob");
    }

    @Test
    void malformedPayloadShouldProduceMalformedMessageErrorWithoutClosingSession() {
        ErrorDto result = stompController.handleException(new RuntimeException("bad payload"));

        assertThat(result.code()).isEqualTo("MALFORMED_MESSAGE");
    }
}
