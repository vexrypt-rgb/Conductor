package conductor.servercheck;

import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import conductor.contract.ServerControl;
import conductor.contract.ServerModule;

import java.util.List;

/**
 * Harness only (live runs of server modules): a server module that throws when a player joins, to show that it is
 * turned off and the server and the other modules keep running. Its {@code /time} clashes with vanilla's.
 */
public final class Crashy implements ServerModule {

    @Override
    public List<ServerCommand> commands() {
        return List.of(ServerCommand.of("crashy", "answers if crashy is still on", (s, c) -> "crashy is on"),
                ServerCommand.of("time", "clashes with vanilla /time", (s, c) -> "never"));
    }

    @Override
    public void joined(ServerControl server, OnlinePlayer p) {
        throw new IllegalStateException("crashy throws on purpose when " + p.name() + " joins");
    }
}
