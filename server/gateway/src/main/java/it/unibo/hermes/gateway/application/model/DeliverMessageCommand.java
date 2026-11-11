package it.unibo.hermes.gateway.application.model;

/**
 * Use-case input for pushing a stored message to a connected recipient.
 *
 * @param messageId         the message identifier
 * @param conversationId    the conversation identifier
 * @param senderUsername    the sender username
 * @param recipientUsername the recipient username to deliver the message to
 * @param targetGatewayId   the Gateway instance the recipient is registered on
 * @param content           the body of the message
 * @param logicalTimestamp  the logical timestamp
 */
public record DeliverMessageCommand(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String targetGatewayId,
        String content,
        Long logicalTimestamp
) {
}
