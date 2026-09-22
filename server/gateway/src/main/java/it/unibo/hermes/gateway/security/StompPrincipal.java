package it.unibo.hermes.gateway.security;

import java.security.Principal;

/**
 * {@link Principal} implementation carrying the username extracted from a
 * validated JWT, during the STOMP CONNECT frame, used to identify the user
 * for the lifetime of the STOMP session.
 *
 * @param username  the username of the authenticated user
 */
public record StompPrincipal(String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
