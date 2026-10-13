package conductor.contract;

import java.util.List;

/**
 * Game events from the runtime. The runtime may collect them on any thread (network events arrive off the game
 * thread) but hands them over only through {@link #drain()}, on the game thread, as plain data.
 */
public interface EventPort {

    /** Everything collected since the last call, oldest first. Game thread only. */
    List<GameEvent> drain();

    /**
     * Lines the player sends that start with {@code prefix} are kept from the server and delivered as
     * {@link GameEvent.CommandLine}. Null turns this off.
     */
    void interceptCommands(String prefix);
}
