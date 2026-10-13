package conductor.example.highlight;

import conductor.contract.BlockPoint;
import conductor.contract.BlockSearch;
import conductor.contract.ConductorModule;
import conductor.contract.FreshnessPolicy;
import conductor.contract.GameEvent;
import conductor.contract.ModuleContext;
import conductor.contract.ObservationKinds;
import conductor.contract.OverlayShape;
import conductor.contract.ReleasePolicy;
import conductor.contract.Resources;
import conductor.contract.Setting;
import conductor.contract.Topic;
import conductor.contract.Topics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Highlights one kind of block around the player, the way client "block ESP" modules do, in one jar for every
 * version Conductor supports. It shows the platform's player-facing parts working together:
 * <ul>
 *   <li><b>settings</b> the player changes in chat ({@code +set block-highlighter block minecraft:gold_ore});</li>
 *   <li>a <b>chat command</b> ({@code +highlight} rescans and reports);</li>
 *   <li><b>3D overlays</b>: boxes around the blocks, and tracers to them;</li>
 *   <li><b>game events</b>: rescans when the server changes a block it cares about, and reports received chat;</li>
 *   <li><b>local messages</b> to the player.</li>
 * </ul>
 * Setting {@code selftest=true} makes it drive its own command and settings through real chat, for live tests.
 */
public final class BlockHighlighter implements ConductorModule {

    static final Setting<String> BLOCK = Setting.text("block", "block id to highlight", "minecraft:diamond_ore");
    static final Setting<Integer> RADIUS = Setting.integer("radius", "search radius in blocks", 8, 1, 16);
    static final Setting<Boolean> TRACERS = Setting.bool("tracers", "draw lines to the blocks", true);
    static final Setting<Integer> COLOR = Setting.integer("color", "outline colour as RGB number", 0x00FFFF, 0, 0xFFFFFF);
    static final Setting<Boolean> SELFTEST = Setting.bool("selftest", "drive its own command and settings (live tests)", false);

    /** Other modules can listen: "found <n> <block>". */
    public static final Topic<String> FOUND = new Topic<>("block-highlighter:found", String.class);

    private static final System.Logger LOG = System.getLogger("block-highlighter");

    private boolean rescan = true;
    private int ticks;
    private List<BlockPoint> found = List.of();

    @Override
    public ReleasePolicy releasePolicy() {
        return ReleasePolicy.none(); // it never moves the bot
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(BLOCK, RADIUS, TRACERS, COLOR, SELFTEST);
    }

    @Override
    public void onReady(ModuleContext c) {
        c.command("highlight", "rescan now and say how many blocks were found", call -> {
            rescan(c);
            c.showMessage(found.size() + " x " + c.get(BLOCK) + " within " + c.get(RADIUS) + " blocks");
        });
        c.subscribe(Topics.SETTINGS, change -> {
            if (change.module().equals(c.self())) {
                log("setting %s = %s", change.name(), change.value());
                rescan = true;
            }
        });
        c.subscribe(Topics.BLOCK_CHANGES, change -> {
            if (change.block().equals(c.get(BLOCK)) || found.contains(change.at())) {
                log("block change at %s: %s", change.at(), change.block());
                rescan = true;
            }
        });
        c.subscribe(Topics.CHAT, message -> log("chat received: %s%s", message.sender().map(s -> "<" + s + "> ").orElse(""), message.text()));
        c.subscribe(Topics.CHUNKS, chunk -> {
            if (chunk.loaded()) {
                rescan = true;
            }
        });
        log("ready: highlighting %s within %d (tracers %s); %s", c.get(BLOCK), c.get(RADIUS), c.get(TRACERS),
                c.get(SELFTEST) ? "SELFTEST" : "type +highlight");
    }

    @Override
    public void onTick(ModuleContext c) {
        ticks++;
        if (rescan || ticks % 40 == 0) {
            rescan(c);
        }
        if (c.get(SELFTEST)) {
            selfTest(c);
        }
    }

    private void rescan(ModuleContext c) {
        rescan = false;
        BlockSearch search = c.findBlocks(Set.of(c.get(BLOCK)), c.get(RADIUS), 64);
        if (!search.isAccepted()) {
            return;
        }
        boolean changed = !search.found().equals(found);
        found = search.found();
        int argb = 0xFF000000 | c.get(COLOR);
        List<OverlayShape> shapes = new ArrayList<>();
        for (BlockPoint p : found) {
            shapes.add(OverlayShape.block(p, argb).throughWalls(true));
            if (c.get(TRACERS)) {
                shapes.add(OverlayShape.tracer(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, argb));
            }
        }
        c.showOverlay(shapes);
        if (changed) {
            log("found %d x %s: %s", found.size(), c.get(BLOCK), found);
            c.publish(FOUND, "found " + found.size() + " " + c.get(BLOCK));
        }
    }

    /** Drives the module through real chat, as a player would. */
    private void selfTest(ModuleContext c) {
        String me = c.self().value();
        switch (ticks) {
            case 100 -> c.acquire(Resources.CHAT);
            case 101 -> log("selftest: typed +highlight: %s", c.sendChat("+highlight").isAccepted());
            case 140 -> log("selftest: typed +set radius 12: %s", c.sendChat("+set " + me + " radius 12").isAccepted());
            case 180 -> log("selftest: typed +set radius 99 (invalid): %s", c.sendChat("+set " + me + " radius 99").isAccepted());
            case 220 -> log("selftest: typed +settings: %s", c.sendChat("+settings " + me).isAccepted());
            case 260 -> log("selftest: typed +modules: %s", c.sendChat("+modules").isAccepted());
            case 300 -> log("selftest: radius is now %d", c.get(RADIUS));
            default -> {
            }
        }
    }

    private static void log(String format, Object... args) {
        LOG.log(System.Logger.Level.INFO, String.format(format, args));
    }
}
