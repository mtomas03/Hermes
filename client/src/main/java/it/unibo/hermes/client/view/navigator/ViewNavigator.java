package it.unibo.hermes.client.view.navigator;

/**
 * Interface for navigating between different views in the application.
 */
public interface ViewNavigator {

    /**
     * Show the login view.
     */
    void showLogin();

    /**
     * Show the registration view.
     */
    void showRegister();

    /**
     * Show the chat view.
     */
    void showChat();

    /**
     * Show a dialog indicating that the session has expired.
     */
    void showSessionExpiredDialog();

    /**
     * Initialise the primary frame of the application.
     */
    void initPrimaryFrame();

    /**
     * Set the visibility of the primary frame.
     *
     * @param b true to make the frame visible, false to hide it
     */
    void setPrimaryFrameVisible(boolean b);
}
