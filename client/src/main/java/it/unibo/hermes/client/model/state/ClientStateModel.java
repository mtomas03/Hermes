package it.unibo.hermes.client.model.state;

import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central observable state for the client.
 */
@Component
public class ClientStateModel {

    private static final String PROP_AUTH_STATE = "authState";
    private static final String PROP_CURRENT_USER = "currentUser";
    private static final String PROP_AUTH_TOKEN = "authToken";
    private static final String PROP_CONNECTION_STATE = "connectionState";
    private static final String PROP_CONVERSATIONS = "conversations";
    private static final String PROP_SELECTED_CONVERSATION = "selectedConversation";
    private static final String PROP_ACTIVE_MESSAGES = "activeMessages";
    private static final String PROP_SYNCING = "syncing";
    private static final String PROP_STATUS_MESSAGE = "statusMessage";
    private static final String PROP_ERROR_MESSAGE = "errorMessage";

    private static final Comparator<Message> MESSAGE_COMPARATOR =
            Comparator.comparingLong(Message::getLogicalTimestamp)
                    .thenComparing(Message::getMessageId);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final AtomicReference<List<Conversation>> conversations = new AtomicReference<>(List.of());
    private final AtomicReference<List<Message>> activeMessages = new AtomicReference<>(List.of());
    private final AtomicReference<AuthState> authState = new AtomicReference<>(AuthState.UNAUTHENTICATED);
    private final AtomicReference<User> currentUser = new AtomicReference<>();
    private final AtomicReference<AuthToken> authToken = new AtomicReference<>();
    private final AtomicReference<ConnectionState> connectionState =
            new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicReference<Conversation> selectedConversation = new AtomicReference<>();
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicReference<String> statusMessage = new AtomicReference<>("Ready");
    private final AtomicReference<String> errorMessage = new AtomicReference<>();

    /**
     * Replaces the active message list (thread-safe, lock-free).
     */
    public void replaceMessages(List<Message> msgs) {
        List<Message> next = msgs == null ? List.of() : List.copyOf(msgs);
        List<Message> old = activeMessages.getAndSet(next);
        runOnSwing(() -> pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, old, next));
    }

    /**
     * Merges a persisted message snapshot into the active message list without
     * dropping messages appended concurrently while the snapshot was loading.
     */
    public void mergeMessages(List<Message> msgs) {
        List<Message> old = activeMessages.get();
        List<Message> updated = activeMessages.updateAndGet(current -> {
            Map<String, Message> byId = new LinkedHashMap<>();
            for (Message existing : current) {
                byId.put(existing.getMessageId(), existing);
            }
            if (msgs != null) {
                for (Message incoming : msgs) {
                    Message existing = byId.get(incoming.getMessageId());
                    if (existing == null) {
                        byId.put(incoming.getMessageId(), incoming);
                    } else if (existing.getStatus().canTransitionTo(incoming.getStatus())) {
                        existing.setStatus(incoming.getStatus());
                        byId.put(incoming.getMessageId(), incoming);
                    }
                }
            }
            List<Message> sorted = new ArrayList<>(byId.values());
            sorted.sort(MESSAGE_COMPARATOR);
            return List.copyOf(sorted);
        });
        runOnSwing(() -> pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, old, updated));
    }

    /**
     * Appends a message to the active list if it is not already present,
     * then re-sorts by logical timestamp and message ID as tie-breaker.
     */
    public void appendMessage(Message msg) {
        if (msg == null) {
            return;
        }
        boolean[] added = {false};
        List<Message> updated = activeMessages.updateAndGet(current -> {
            added[0] = false;
            boolean exists = current.stream()
                    .anyMatch(m -> m.getMessageId().equals(msg.getMessageId()));
            if (exists) {
                return current;
            }
            added[0] = true;
            List<Message> next = new ArrayList<>(current.size() + 1);
            next.addAll(current);
            next.add(msg);
            next.sort(MESSAGE_COMPARATOR);
            return List.copyOf(next);
        });
        if (added[0]) {
            runOnSwing(() -> pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, null, updated));
        }
    }

    /**
     * Clears all session-related state (call on logout).
     */
    public void clearSession() {
        AuthState oldAuthState = authState.getAndSet(AuthState.UNAUTHENTICATED);
        User oldUser = currentUser.getAndSet(null);
        AuthToken oldToken = authToken.getAndSet(null);
        Conversation oldSelected = selectedConversation.getAndSet(null);
        String oldError = errorMessage.getAndSet(null);
        String oldStatus = statusMessage.getAndSet("Logged out");
        conversations.set(List.of());
        activeMessages.set(List.of());

        runOnSwing(() -> {
            pcs.firePropertyChange(PROP_AUTH_STATE, oldAuthState, AuthState.UNAUTHENTICATED);
            pcs.firePropertyChange(PROP_CURRENT_USER, oldUser, null);
            pcs.firePropertyChange(PROP_AUTH_TOKEN, oldToken, null);
            pcs.firePropertyChange(PROP_SELECTED_CONVERSATION, oldSelected, null);
            pcs.firePropertyChange(PROP_ERROR_MESSAGE, oldError, null);
            pcs.firePropertyChange(PROP_STATUS_MESSAGE, oldStatus, "Logged out");
            pcs.firePropertyChange(PROP_CONVERSATIONS, null, List.of());
            pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, null, List.of());
        });
    }

    public void addAuthStateListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_AUTH_STATE, l);
    }

    public void removeAuthStateListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_AUTH_STATE, l);
    }

    public void addCurrentUserListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_CURRENT_USER, l);
    }

    public void removeCurrentUserListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_CURRENT_USER, l);
    }

    public void addConnectionStateListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_CONNECTION_STATE, l);
    }

    public void removeConnectionStateListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_CONNECTION_STATE, l);
    }

    public void addConversationsListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_CONVERSATIONS, l);
    }

    public void removeConversationsListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_CONVERSATIONS, l);
    }

    public void addSelectedConversationListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_SELECTED_CONVERSATION, l);
    }

    public void removeSelectedConversationListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_SELECTED_CONVERSATION, l);
    }

    public void addActiveMessagesListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_ACTIVE_MESSAGES, l);
    }

    public void removeActiveMessagesListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_ACTIVE_MESSAGES, l);
    }

    public void addStatusMessageListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_STATUS_MESSAGE, l);
    }

    public void removeStatusMessageListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_STATUS_MESSAGE, l);
    }

    public void addSyncingListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(PROP_SYNCING, l);
    }

    public void removeSyncingListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(PROP_SYNCING, l);
    }

    public List<Conversation> getConversations() {
        return conversations.get();
    }

    /**
     * Replaces the full conversation list (thread-safe, lock-free).
     */
    public void setConversations(List<Conversation> list) {
        List<Conversation> next = list == null ? List.of() : List.copyOf(list);
        List<Conversation> old = conversations.getAndSet(next);
        runOnSwing(() -> pcs.firePropertyChange(PROP_CONVERSATIONS, old, next));
    }

    public List<Message> getActiveMessages() {
        return activeMessages.get();
    }

    public AuthToken getAuthToken() {
        return authToken.get();
    }

    public void setAuthToken(AuthToken t) {
        AuthToken old = authToken.getAndSet(t);
        runOnSwing(() -> pcs.firePropertyChange(PROP_AUTH_TOKEN, old, t));
    }

    public User getCurrentUser() {
        return currentUser.get();
    }

    public void setCurrentUser(User u) {
        User old = currentUser.getAndSet(u);
        runOnSwing(() -> pcs.firePropertyChange(PROP_CURRENT_USER, old, u));
    }

    public AuthState getAuthState() {
        return authState.get();
    }

    public void setAuthState(AuthState s) {
        AuthState old = authState.getAndSet(s);
        runOnSwing(() -> pcs.firePropertyChange(PROP_AUTH_STATE, old, s));
    }

    public ConnectionState getConnectionState() {
        return connectionState.get();
    }

    public void setConnectionState(ConnectionState s) {
        ConnectionState old = connectionState.getAndSet(s);
        runOnSwing(() -> pcs.firePropertyChange(PROP_CONNECTION_STATE, old, s));
    }

    public Conversation getSelectedConversation() {
        return selectedConversation.get();
    }

    public void setSelectedConversation(Conversation c) {
        Conversation old = selectedConversation.getAndSet(c);
        runOnSwing(() -> pcs.firePropertyChange(PROP_SELECTED_CONVERSATION, old, c));
    }

    public boolean isSyncing() {
        return syncing.get();
    }

    public void setSyncing(boolean b) {
        boolean old = syncing.getAndSet(b);
        runOnSwing(() -> pcs.firePropertyChange(PROP_SYNCING, old, b));
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public void setStatusMessage(String m) {
        String old = statusMessage.getAndSet(m);
        runOnSwing(() -> pcs.firePropertyChange(PROP_STATUS_MESSAGE, old, m));
    }

    public String getErrorMessage() {
        return errorMessage.get();
    }

    public void setErrorMessage(String m) {
        String old = errorMessage.getAndSet(m);
        runOnSwing(() -> pcs.firePropertyChange(PROP_ERROR_MESSAGE, old, m));
    }

    private void runOnSwing(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
