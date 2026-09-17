package it.unibo.hermes.gateway.repository.cassandra;

import it.unibo.hermes.gateway.entity.cassandra.ConversationByUser;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Cassandra repository accessing the {@code conversation_by_user} table.
 */
@Repository
public interface ConversationByUserRepository extends CassandraRepository<ConversationByUser, String> {

    /**
     * Finds all conversations associated with a specific username.
     *
     * @param username      the username to search for
     * @return a list of ConversationByUser entities associated with the given username
     */
    List<ConversationByUser> findByUsername(String username);
}
