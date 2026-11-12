package it.unibo.hermes.gateway.domain.model;

import java.util.Optional;

/**
 * Domain rules defining the canonical conversation identifier of two participants.
 */
public final class ConversationIds {

    private static final char SEPARATOR = '-';

    private ConversationIds() {
    }

    /**
     * Creates a canonical conversation identifier from two usernames.
     *
     * @param userA the first participant
     * @param userB the second participant
     * @return the canonical conversation identifier
     */
    public static String canonical(String userA, String userB) {
        String a = userA.trim().toLowerCase();
        String b = userB.trim().toLowerCase();
        return a.compareTo(b) <= 0 ? a + SEPARATOR + b : b + SEPARATOR + a;
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
        return conversationId != null && canonical(sender, recipient).equals(conversationId);
    }

    /**
     * Splits a conversation identifier into its two participants.
     *
     * @param conversationId the conversation identifier
     * @return the two lowercase participants, or empty if the identifier is not of the form
     * {@code <canonical-username>-<canonical-username>}
     */
    public static Optional<String[]> participantsOf(String conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        String[] parts = conversationId.split(String.valueOf(SEPARATOR), -1);
        if (parts.length != 2 || !Username.isCanonical(parts[0]) || !Username.isCanonical(parts[1])) {
            return Optional.empty();
        }
        return Optional.of(parts);
    }

    /**
     * Checks if a user is a participant in a conversation.
     *
     * @param conversationId the conversation identifier
     * @param username       a username to check
     * @return true if the identifier is well-formed and names the user as one of its two participants
     * false otherwise
     */
    public static boolean isParticipant(String conversationId, String username) {
        if (username == null || conversationId == null) {
            return false;
        }
        String normalised = username.trim().toLowerCase();
        return participantsOf(conversationId)
                .map(p -> p[0].equals(normalised) || p[1].equals(normalised))
                .orElse(false);
    }
}
