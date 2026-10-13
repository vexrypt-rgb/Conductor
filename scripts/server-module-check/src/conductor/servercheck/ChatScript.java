package conductor.servercheck;

import conductor.contract.ActionResult;
import conductor.contract.ConductorModule;
import conductor.contract.LeaseResult;
import conductor.contract.ModuleContext;
import conductor.contract.ReleasePolicy;
import conductor.contract.Resources;

import java.util.List;

/**
 * Harness only (live runs of server modules): types a fixed list of chat lines and commands, as a player would, so
 * the server's modules get chat and commands from a real client. Times are seconds after the module is ready.
 */
public final class ChatScript implements ConductorModule {

    private static final System.Logger LOG = System.getLogger("server-module-check");

    private record Line(int at, String text) {
    }

    private static final List<Line> SCRIPT = List.of(
            new Line(5, "hello from the client"),
            new Line(7, "ping"),
            new Line(9, "/greet"),
            new Line(11, "/greet Dev"),
            new Line(13, "/greet Nobody"),
            new Line(15, "/whereami"),
            new Line(17, "/online"),
            new Line(19, "/blockat minecraft:overworld 0 0 0"), // not an operator yet: refused
            new Line(21, "/crashy"),
            // the server console runs "op Dev" about 30 s after the join
            new Line(36, "/blockat minecraft:overworld 0 0 0"),
            new Line(38, "/blockat minecraft:overworld 100000 64 0"),
            new Line(40, "/blockat minecraft:nether 0 64 0"),
            new Line(42, "/blockat minecraft:overworld x"),
            // the server console runs "kill Dev" about 46 s after the join
            new Line(54, "/deaths"),
            new Line(56, "bye"));

    private long ticks = -1;
    private int next;

    @Override
    public ReleasePolicy releasePolicy() {
        return ReleasePolicy.none();
    }

    @Override
    public void onReady(ModuleContext c) {
        ticks = 0;
        LOG.log(System.Logger.Level.INFO, "server-module-check: ready");
    }

    @Override
    public void onTick(ModuleContext c) {
        if (ticks < 0 || next >= SCRIPT.size()) {
            return;
        }
        ticks++;
        Line line = SCRIPT.get(next);
        if (ticks < line.at() * 20L) {
            return;
        }
        if (next == 0) {
            LeaseResult lease = c.acquire(Resources.CHAT);
            LOG.log(System.Logger.Level.INFO, "server-module-check: CHAT lease " + lease.status());
        }
        ActionResult r = c.sendChat(line.text());
        LOG.log(System.Logger.Level.INFO, "server-module-check: sent \"" + line.text() + "\": "
                + (r.isAccepted() ? "accepted" : r.rejection() + " (" + r.detail() + ")"));
        next++;
    }
}
