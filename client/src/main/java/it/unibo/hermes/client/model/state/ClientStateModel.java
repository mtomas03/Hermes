package it.unibo.hermes.client.model.state;

import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final List<Conversation> conversations = new ArrayList<>();
    private final List<Message> activeMessages = new ArrayList<>();
    private AuthState authState = AuthState.UNAUTHENTICATED;
    private User currentUser;
    private AuthToken authToken;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private Conversation selectedConversation;
    private boolean syncing;
    private String statusMessage = "Ready";
    private String errorMessage;

    /**
     * Replaces the active message list (thread-safe).
     */
    public void replaceMessages(List<Message> msgs) {
        runOnSwing(() -> {
            List<Message> old = new ArrayList<>(this.activeMessages);
            this.activeMessages.clear();
            this.activeMessages.addAll(msgs);
            pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, old, new ArrayList<>(msgs));
        });
    }

    /**
     * Appends a message to the active list if it is not already present,
     * then re-sorts by logical timestamp and message ID as tie-breaker.
     */
    public void appendMessage(Message msg) {
        runOnSwing(() -> {
            boolean exists = activeMessages.stream()
                    .anyMatch(m -> m.getMessageId().equals(msg.getMessageId()));
            if (!exists) {
                activeMessages.add(msg);
                activeMessages.sort(
                        Comparator.comparingLong(Message::getLogicalTimestamp)
                                .thenComparing(Message::getMessageId)
                );
                pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, null, new ArrayList<>(activeMessages));
            }
        });
    }

    /**
     * Clears all session-related state (call on logout).
     */
    public void clearSession() {
        runOnSwing(() -> {
            setAuthState(AuthState.UNAUTHENTICATED);
            setCurrentUser(null);
            setAuthToken(null);
            setSelectedConversation(null);
            conversations.clear();
            activeMessages.clear();
            setErrorMessage(null);
            setStatusMessage("Logged out");
            pcs.firePropertyChange(PROP_CONVERSATIONS, null, new ArrayList<>());
            pcs.firePropertyChange(PROP_ACTIVE_MESSAGES, null, new ArrayList<>());
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
        return new ArrayList<>(conversations);
    }

    /**
     * Replaces the full conversation list (thread-safe).
     */
    public void setConversations(List<Conversation> list) {
        runOnSwing(() -> {
            List<Conversation> old = new ArrayList<>(this.conversations);
            this.conversations.clear();
            this.conversations.addAll(list);
            pcs.firePropertyChange(PROP_CONVERSATIONS, old, new ArrayList<>(list));
        });
    }

    public List<Message> getActiveMessages() {
        return new ArrayList<>(activeMessages);
    }

    public AuthToken getAuthToken() {
        return authToken;
    }

    public void setAuthToken(AuthToken t) {
        runOnSwing(() -> {
            AuthToken old = this.authToken;
            this.authToken = t;
            pcs.firePropertyChange(PROP_AUTH_TOKEN, old, t);
        });
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User u) {
        runOnSwing(() -> {
            User old = this.currentUser;
            this.currentUser = u;
            pcs.firePropertyChange(PROP_CURRENT_USER, old, u);
        });
    }

    public AuthState getAuthState() {
        return authState;
    }

    public void setAuthState(AuthState s) {
        runOnSwing(() -> {
            AuthState old = this.authState;
            this.authState = s;
            pcs.firePropertyChange(PROP_AUTH_STATE, old, s);
        });
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public void setConnectionState(ConnectionState s) {
        runOnSwing(() -> {
            ConnectionState old = this.connectionState;
            this.connectionState = s;
            pcs.firePropertyChange(PROP_CONNECTION_STATE, old, s);
        });
    }

    public Conversation getSelectedConversation() {
        return selectedConversation;
    }

    public void setSelectedConversation(Conversation c) {
        runOnSwing(() -> {
            Conversation old = this.selectedConversation;
            this.selectedConversation = c;
            pcs.firePropertyChange(PROP_SELECTED_CONVERSATION, old, c);
        });
    }

    public boolean isSyncing() {
        return syncing;
    }

    public void setSyncing(boolean b) {
        runOnSwing(() -> {
            boolean old = this.syncing;
            this.syncing = b;
            pcs.firePropertyChange(PROP_SYNCING, old, b);
        });
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String m) {
        runOnSwing(() -> {
            String old = this.statusMessage;
            this.statusMessage = m;
            pcs.firePropertyChange(PROP_STATUS_MESSAGE, old, m);
        });
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String m) {
        runOnSwing(() -> {
            String old = this.errorMessage;
            this.errorMessage = m;
            pcs.firePropertyChange(PROP_ERROR_MESSAGE, old, m);
        });
    }

    private void runOnSwing(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
