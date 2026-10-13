package conductor.contract;

import java.util.Optional;

/**
 * One-shot player actions, implemented by a runtime adapter. Game thread only. Each method returns empty when the
 * game was told to do it, or a reason it could not be done.
 */
public interface ActionPort {

    Optional<String> selectHotbar(int slot);

    Optional<String> look(double yaw, double pitch);

    /** Attack the entity with this id, if it exists and is in reach. */
    Optional<String> attack(int entityId);

    Optional<String> useItem(boolean offhand);

    /** A message, or a command when it starts with {@code /}. */
    Optional<String> sendChat(String message);
}
