package it.unibo.hermes.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Data transfer object representing a WebSocket message exchanged between the client and the gateway.
 *
 * <p> Fields, that are not applicable to a specific message type, are omitted
 * during JSON serialization by excluding null values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsMessage {

    private WsMessageType type;

    private String recipientUsername;
    private String content;
    private String clientMessageId; // client-assigned idempotency key
    private String messageId; // server-assigned UUID
    private String senderUsername;
    private Long logicalTimestamp;
    private Instant physicalTimestamp;
    private String errorCode;
    private String reason;

    public WsMessage() {}

    /**
     * Creates a heartbeat PONG response message.
     *
     * @return a new WebSocket message configured as PONG
     */
    public static WsMessage pong() {
        WsMessage m = new WsMessage();
        m.type = WsMessageType.PONG;
        return m;
    }

    /**
     * Creates an ERROR message carrying failure details.
     *
     * @param errorCode the error classification code
     * @param reason    the explanation of the error condition
     * @return a new WebSocket message envelope configured as an ERROR frame
     */
    public static WsMessage error(String errorCode, String reason) {
        WsMessage m = new WsMessage();
        m.type = WsMessageType.ERROR;
        m.errorCode = errorCode;
        m.reason = reason;
        return m;
    }

    /**
     * Creates a FORCE_RECONNECT message instructing immediate client session re-establishment.
     *
     * @param reason the operational reason requiring the client to reconnect
     * @return a new WebSocket message configured as FORCE_RECONNECT
     */
    public static WsMessage forceReconnect(String reason) {
        WsMessage m = new WsMessage();
        m.type = WsMessageType.FORCE_RECONNECT;
        m.reason = reason;
        return m;
    }

    public WsMessageType getType() {
        return type;
    }

    public void setType(WsMessageType t) {
        this.type = t;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String c) {
        this.content = c;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String c) {
        this.clientMessageId = c;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String id) {
        this.messageId = id;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public Long getLogicalTimestamp() {
        return logicalTimestamp;
    }

    public void setLogicalTimestamp(Long t) {
        this.logicalTimestamp = t;
    }

    public Instant getPhysicalTimestamp() {
        return physicalTimestamp;
    }

    public void setPhysicalTimestamp(Instant t) {
        this.physicalTimestamp = t;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String c) {
        this.errorCode = c;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String r) {
        this.reason = r;
    }
}