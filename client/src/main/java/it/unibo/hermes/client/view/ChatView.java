package it.unibo.hermes.client.view;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.ChatController;
import it.unibo.hermes.client.controller.NavigationController;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.model.state.ConnectionState;

import javax.swing.*;
import java.awt.*;

/**
 * Main chat screen created by {@link NavigationController}.
 */
public class ChatView {

    private final ClientStateModel stateModel;
    private final ChatController chatController;
    private final AuthController authController;

    private DefaultListModel<Message> messageListModel;
    private JLabel chatTitleLabel;
    private JTextField searchField;
    private JTextArea inputArea;
    private JButton sendBtn;
    private JLabel statusLabel;
    private JList<Message> msgList;

    public ChatView(ClientStateModel stateModel,
                    ChatController chatController,
                    AuthController authController) {
        this.stateModel = stateModel;
        this.chatController = chatController;
        this.authController = authController;
    }

    public JPanel build() {
        JPanel root = new JPanel(new BorderLayout());

        // Top bar
        JPanel topBar = buildTopBar();
        root.add(topBar, BorderLayout.NORTH);

        // Single Chat Area with search bar
        JPanel chatArea = buildChatArea();
        root.add(chatArea, BorderLayout.CENTER);

        // Bind state changes
        bindStateListeners();

        return root;
    }

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(21, 101, 192));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        JLabel userLabel = new JLabel();
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userLabel.setForeground(Color.WHITE);
        User user = stateModel.getCurrentUser();
        if (user != null) {
            userLabel.setText(user.username());
        }

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(Color.WHITE);
        updateConnectionStatus();

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(255, 255, 255, 38));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        logoutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> authController.logout());

        Box leftBox = Box.createHorizontalBox();
        leftBox.add(userLabel);
        leftBox.add(Box.createHorizontalStrut(10));
        leftBox.add(new JSeparator(JSeparator.VERTICAL));
        leftBox.add(Box.createHorizontalStrut(10));
        leftBox.add(statusLabel);

        topBar.add(leftBox, BorderLayout.WEST);
        topBar.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        topBar.add(logoutBtn, BorderLayout.EAST);

        return topBar;
    }

    private JPanel buildChatArea() {
        JPanel chatArea = new JPanel(new BorderLayout());

        // Header containing title and username search bar
        JPanel headerPanel = buildHeaderPanel();

        // Message list
        messageListModel = new DefaultListModel<>();
        msgList = new JList<>(messageListModel);
        msgList.setBackground(new Color(250, 250, 250));
        msgList.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                // Prevent selection
            }
        });

        JScrollPane msgScroll = new JScrollPane(msgList);
        msgScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Input area
        JPanel inputPanel = buildInputPanel();

        chatArea.add(headerPanel, BorderLayout.NORTH);
        chatArea.add(msgScroll, BorderLayout.CENTER);
        chatArea.add(inputPanel, BorderLayout.SOUTH);

        return chatArea;
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(250, 250, 250));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(224, 224, 224)));

        // Title label
        chatTitleLabel = new JLabel("Enter a username to start");
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatTitleLabel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        // Search Bar Area
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        searchPanel.setOpaque(false);

        searchField = new JTextField(12);
        searchField.setToolTipText("Search username");

        JButton searchBtn = new JButton("Search / Open");
        searchBtn.setBackground(new Color(21, 101, 192));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setFocusPainted(false);
        searchBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        searchBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Runnable startChat = () -> {
            String u = searchField.getText().trim();
            if (!u.isEmpty()) {
                searchField.setText("");
                chatController.startConversationWith(u, msg ->
                        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.WARNING_MESSAGE));
            }
        };

        searchBtn.addActionListener(e -> startChat.run());
        searchField.addActionListener(e -> startChat.run());

        searchPanel.add(new JLabel("Username:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);

        headerPanel.add(chatTitleLabel, BorderLayout.WEST);
        headerPanel.add(searchPanel, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel buildInputPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(Color.WHITE);
        inputPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        inputArea = new JTextArea(2, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setText("");
        inputArea.setEnabled(false);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        sendBtn = new JButton("Send ↑");
        sendBtn.setBackground(new Color(21, 101, 192));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sendBtn.setEnabled(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.setPreferredSize(new Dimension(80, 50));

        Runnable doSend = () -> {
            String txt = inputArea.getText();
            if (!txt.isBlank()) {
                chatController.sendMessage(txt, err ->
                        JOptionPane.showMessageDialog(null, "Cannot send: " + err, "Error", JOptionPane.ERROR_MESSAGE));
                inputArea.setText("");
            }
        };

        sendBtn.addActionListener(e -> doSend.run());
        inputArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    doSend.run();
                }
            }
        });

        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(Box.createHorizontalStrut(10), BorderLayout.EAST);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        return inputPanel;
    }

    private void bindStateListeners() {
        // Selected conversation listener
        stateModel.addSelectedConversationListener(evt ->
                SwingUtilities.invokeLater(this::updateChatTitle));

        // Active messages listener
        stateModel.addActiveMessagesListener(evt ->
                SwingUtilities.invokeLater(this::updateMessagesList));

        // Connection state listener
        stateModel.addConnectionStateListener(evt ->
                SwingUtilities.invokeLater(this::updateConnectionStatus));

        // Status message listener
        stateModel.addStatusMessageListener(evt ->
                SwingUtilities.invokeLater(() -> {
                    String msg = stateModel.getStatusMessage();
                    if (statusLabel != null && msg != null) {
                        statusLabel.setText(msg);
                    }
                }));

        // Initial state update
        updateMessagesList();
        updateChatTitle();
        updateInputState();
    }

    private void updateMessagesList() {
        messageListModel.clear();
        for (Message msg : stateModel.getActiveMessages()) {
            messageListModel.addElement(msg);
        }
        // Scroll to bottom
        if (!messageListModel.isEmpty()) {
            msgList.ensureIndexIsVisible(messageListModel.size() - 1);
        }
    }

    private void updateChatTitle() {
        Conversation selected = stateModel.getSelectedConversation();
        if (selected != null) {
            chatTitleLabel.setText("Chat with: " + selected.getOtherUser().username());
        } else {
            chatTitleLabel.setText("Enter a username to start");
        }
        updateInputState();
    }

    private void updateConnectionStatus() {
        ConnectionState cs = stateModel.getConnectionState();
        if (cs != null && statusLabel != null) {
            String text = switch (cs) {
                case CONNECTED -> "Online";
                case CONNECTING -> "Connecting";
                case RECONNECTING -> "Reconnecting";
                case DISCONNECTED -> "Offline";
                case FAILED -> "Connection failed";
            };
            Color color = switch (cs) {
                case CONNECTED -> new Color(165, 214, 167);
                case FAILED -> new Color(239, 154, 154);
                default -> new Color(255, 245, 157);
            };
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        }
    }

    private void updateInputState() {
        boolean hasConv = stateModel.getSelectedConversation() != null;
        inputArea.setEnabled(hasConv);
        sendBtn.setEnabled(hasConv);
    }
}