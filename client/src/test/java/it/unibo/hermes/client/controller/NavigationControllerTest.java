package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.view.navigator.ViewNavigator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NavigationControllerTest {

    @Mock
    private ClientStateModel stateModel;
    @Mock
    private ViewNavigator viewNavigator;

    @Captor
    private ArgumentCaptor<PropertyChangeListener> listenerCaptor;

    private NavigationController controller;

    @BeforeEach
    void setUp() {
        controller = new NavigationController(stateModel, viewNavigator);
    }

    @Test
    void bindToAuthStateShouldRouteToChatWhenAuthenticated() {
        controller.bindToAuthState();
        verify(stateModel).addAuthStateListener(listenerCaptor.capture());

        listenerCaptor.getValue().propertyChange(
                new PropertyChangeEvent(stateModel, "authState", null, AuthState.AUTHENTICATED)
        );

        verify(viewNavigator).showChat();
    }

    @Test
    void bindToAuthStateShouldRouteToLoginWhenUnauthenticated() {
        controller.bindToAuthState();
        verify(stateModel).addAuthStateListener(listenerCaptor.capture());

        listenerCaptor.getValue().propertyChange(
                new PropertyChangeEvent(stateModel, "authState", null, AuthState.UNAUTHENTICATED)
        );

        verify(viewNavigator).showLogin();
    }

    @Test
    void bindToAuthStateShouldTriggerExpiredDialogWhenTokenExpires() {
        controller.bindToAuthState();
        verify(stateModel).addAuthStateListener(listenerCaptor.capture());

        listenerCaptor.getValue().propertyChange(
                new PropertyChangeEvent(stateModel, "authState", null, AuthState.TOKEN_EXPIRED)
        );

        verify(viewNavigator).showSessionExpiredDialog();
    }

    @Test
    void directShowMethodsShouldDelegateToViewNavigator() {
        controller.showLogin();
        verify(viewNavigator).showLogin();

        controller.showRegister();
        verify(viewNavigator).showRegister();

        controller.showChat();
        verify(viewNavigator).showChat();
    }
}