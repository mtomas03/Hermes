package it.unibo.hermes.client.view.chat;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.ChatController;
import it.unibo.hermes.client.controller.NavigationController;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.ClientStateModel;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Main chat screen created by {@link NavigationController}.
 */
public class ChatView {

    private static final Color COLOR_PRIMARY = new Color(21, 101, 192);
    private static final Color COLOR_BG_LIGHT = new Color(250, 250, 250);
    private static final Color COLOR_BORDER = new Color(224, 224, 224);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font FONT_BOLD_13 = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_PLAIN_13 = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_PLAIN_12 = new Font("Segoe UI", Font.PLAIN, 12);

    private final ClientStateModel stateModel;
    private final ChatController chatController;
    private final AuthController authController;

    private DefaultListModel<Message> messageListModel;
    private JLabel chatTitleLabel;
    private JTextField searchField;
    private JTextArea inputArea;
    private JButton sendBtn;
    private JList<Message> msgList;

    public ChatView(ClientStateModel stateModel,
                    ChatController chatController,
                    AuthController authController) {
        this.stateModel = stateModel;
        this.chatController = chatController;
        this.authController = authController;
    }

    /**
     * Builds and returns the main chat panel.
     */
    public JPanel build() {
        JPanel root = new JPanel(new BorderLayout());

        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildChatArea(), BorderLayout.CENTER);

        bindStateListeners();

        return root;
    }

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(COLOR_PRIMARY);
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        JLabel userLabel = new JLabel();
        userLabel.setFont(FONT_BOLD_13);
        userLabel.setForeground(Color.WHITE);

        User user = stateModel.getCurrentUser();
        if (user != null) {
            userLabel.setText(user.username());
        }

        JButton logoutBtn = createPrimaryButton("Logout", FONT_PLAIN_12);
        logoutBtn.addActionListener(e -> authController.logout());

        Box leftBox = Box.createHorizontalBox();
        leftBox.add(userLabel);

        topBar.add(leftBox, BorderLayout.WEST);
        topBar.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        topBar.add(logoutBtn, BorderLayout.EAST);

        return topBar;
    }

    private JPanel buildChatArea() {
        JPanel chatArea = new JPanel(new BorderLayout());

        messageListModel = new DefaultListModel<>();
        msgList = new JList<>(messageListModel);
        msgList.setBackground(COLOR_BG_LIGHT);
        msgList.setSelectionModel(new DisabledSelectionModel());
        msgList.setCellRenderer(new MessageListCellRenderer());

        JScrollPane msgScroll = new JScrollPane(msgList);
        msgScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        chatArea.add(buildHeaderPanel(), BorderLayout.NORTH);
        chatArea.add(msgScroll, BorderLayout.CENTER);
        chatArea.add(buildInputPanel(), BorderLayout.SOUTH);

        return chatArea;
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(COLOR_BG_LIGHT);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER));

        chatTitleLabel = new JLabel("Enter a username to start chatting");
        chatTitleLabel.setFont(FONT_TITLE);
        chatTitleLabel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        searchPanel.setOpaque(false);

        searchField = new JTextField(12);
        searchField.setToolTipText("Search username");

        JButton searchBtn = createPrimaryButton("Search / Open", FONT_PLAIN_12);

        Runnable startChat = () -> {
            String username = searchField.getText().trim();
            if (!username.isEmpty()) {
                searchField.setText("");
                chatController.startConversationWith(username, msg ->
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
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        inputArea = new JTextArea(2, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setEnabled(false);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        sendBtn = createPrimaryButton("Send ↑", FONT_PLAIN_13);
        sendBtn.setEnabled(false);
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
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    doSend.run();
                }
            }
        });

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        rightPanel.add(sendBtn);

        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(rightPanel, BorderLayout.EAST);

        return inputPanel;
    }

    private void bindStateListeners() {
        stateModel.addSelectedConversationListener(evt ->
                SwingUtilities.invokeLater(this::updateChatTitle));

        stateModel.addActiveMessagesListener(evt ->
                SwingUtilities.invokeLater(this::updateMessagesList));

        updateMessagesList();
        updateChatTitle();
        updateInputState();
    }

    private void updateMessagesList() {
        messageListModel.clear();
        for (Message msg : stateModel.getActiveMessages()) {
            messageListModel.addElement(msg);
        }
        if (!messageListModel.isEmpty()) {
            msgList.ensureIndexIsVisible(messageListModel.size() - 1);
        }
    }

    private void updateChatTitle() {
        Conversation selected = stateModel.getSelectedConversation();
        if (selected != null) {
            chatTitleLabel.setText("Chat with: " + selected.recipientUsername().username());
        } else {
            chatTitleLabel.setText("Enter a username to start");
        }
        updateInputState();
    }

    private void updateInputState() {
        boolean hasConv = stateModel.getSelectedConversation() != null;
        inputArea.setEnabled(hasConv);
        sendBtn.setEnabled(hasConv);
    }

    private JButton createPrimaryButton(String text, Font font) {
        JButton btn = new JButton(text);
        btn.setBackground(COLOR_PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(font);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static class MessageListCellRenderer extends DefaultListCellRenderer {
        private static final Border CELL_PADDING = BorderFactory
                .createEmptyBorder(6, 10, 6, 10);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Message msg) {
                label.setText(msg.getSenderUsername() + ": " + msg.getContent());
            }
            label.setBorder(CELL_PADDING);
            label.setFont(FONT_PLAIN_13);
            return label;
        }
    }

    private static class DisabledSelectionModel extends DefaultListSelectionModel {
        @Override
        public void setSelectionInterval(int index0, int index1) {
            // Prevents visual item selection
        }
    }
}
