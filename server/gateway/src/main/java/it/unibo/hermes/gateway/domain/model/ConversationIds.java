package it.unibo.hermes.gateway.domain.model;

/**
 * Utility class for checking the canonical format of conversation identifiers.
 */
public final class ConversationIds {

    private ConversationIds() {
    }

    /**
     * Checks whether the given conversation identifier is the canonical one
     * for the two participants.
     *
     * @param conversationId the conversation identifier to check
     * @param sender         the username of the sender
     * @param recipient      the username of the recipient
     * @return true if the conversation identifier is canonical, false otherwise
     */
    public static boolean isCanonical(String conversationId, String sender, String recipient) {
        if (conversationId == null) {
            return false;
        }
        String a = sender.trim().toLowerCase();
        String b = recipient.trim().toLowerCase();
        String expected = a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a;
        return expected.equals(conversationId);
    }
}
