package it.unibo.hermes.worker.application.port.secondary;

import it.unibo.hermes.worker.domain.model.PresenceInfo;

import java.util.Optional;

/**
 * Secondary port interface for looking up the presence of a user.
 */
public interface PresenceLookupPort {

    Optional<PresenceInfo> findPresence(String username);
}
