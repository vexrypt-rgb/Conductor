package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.EventPort;
import conductor.contract.GameEvent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game events from Conductor's own hooks ({@link Hooks}), handed to modules as plain data through {@link #drain()}
 * on the game thread. The hooks may fire on any thread; only plain values (strings, ids, coordinates) are queued.
 * The one lookup that needs game state (a chat sender's name) is done in {@link #drain()}.
 */
final class HookEventFeed implements EventPort {

    private static final Logger LOG = LogManager.getLogger("conductor");
    static final int MAX_QUEUED = 50_000;

    /** A player chat line whose sender name is resolved on the game thread. */
    private record PendingChat(String text, UUID sender) {}

    private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();
    private final GameAccess game;
    private volatile String prefix;

    HookEventFeed(GameAccess game) {
        this.game = game;
    }

    private void offer(Object event) {
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            if (dropped.getAndIncrement() == 0) {
                LOG.warn("Conductor event queue full ({}); dropping events until it drains", MAX_QUEUED);
            }
            return;
        }
        queue.add(event);
    }

    // ---- EventPort (game thread) ----

    @Override
    public List<GameEvent> drain() {
        List<GameEvent> out = new ArrayList<>();
        Object next;
        while ((next = queue.poll()) != null) {
            queued.decrementAndGet();
            if (next instanceof PendingChat chat) {
                out.add(new GameEvent.ChatMessage(chat.text(), Optional.ofNullable(game.playerName(chat.sender()))));
            } else {
                out.add((GameEvent) next);
            }
        }
        int lost = dropped.getAndSet(0);
        if (lost > 0) {
            LOG.warn("Conductor dropped {} game events (queue full)", lost);
        }
        return out;
    }

    @Override
    public void interceptCommands(String prefix) {
        this.prefix = prefix;
    }

    // ---- from the hooks ----

    /** True when the line is a Conductor command: it is queued and must not be sent to the server. */
    boolean outgoingChat(String message) {
        String p = prefix;
        if (p != null && message != null && message.startsWith(p) && message.length() > p.length()) {
            offer(new GameEvent.CommandLine(message));
            return true;
        }
        return false;
    }

    void blockChanged(int x, int y, int z, String block) {
        offer(new GameEvent.BlockChange(new BlockPoint(x, y, z), block));
    }

    void chunk(int chunkX, int chunkZ, boolean loaded) {
        offer(new GameEvent.ChunkChange(chunkX, chunkZ, loaded));
    }

    void chat(String text, UUID sender) {
        offer(sender == null ? new GameEvent.ChatMessage(text, Optional.empty()) : new PendingChat(text, sender));
    }

    void death() {
        offer(new GameEvent.PlayerDeath());
    }
}
