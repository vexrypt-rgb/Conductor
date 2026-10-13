package conductor.runtime.ostinato;

import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Pair;
import conductor.contract.BlockPoint;
import conductor.contract.EventPort;
import conductor.contract.GameEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game events from Ostinato's event bus, handed to Conductor as plain data.
 * <p>
 * Ostinato fires packet events on the network thread. This feed never touches game state there: it copies plain
 * values (strings, ids, coordinates) out of the event into a queue, and {@link #drain()} hands them over on the game
 * thread. The one lookup that needs game state (a chat sender's name) is done in {@link #drain()}.
 */
final class OstinatoEventFeed implements EventPort, AbstractGameEventListener {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");
    static final int MAX_QUEUED = 50_000;

    /** A player chat line whose sender name is resolved on the game thread. */
    private record PendingChat(String text, UUID sender) {}

    private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();
    private final GameAccess game;
    private volatile String prefix;

    OstinatoEventFeed(GameAccess game) {
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

    // ---- Ostinato events (any thread) ----

    @Override
    public void onSendChatMessage(ChatEvent event) {
        String p = prefix;
        if (p != null && event.getMessage().startsWith(p) && event.getMessage().length() > p.length()) {
            event.cancel(); // a Conductor command: never sent to the server
            offer(new GameEvent.CommandLine(event.getMessage()));
        }
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        for (Pair<BlockPos, BlockState> change : event.getBlocks()) {
            BlockPos pos = change.first();
            offer(new GameEvent.BlockChange(new BlockPoint(pos.getX(), pos.getY(), pos.getZ()),
                    String.valueOf(BuiltInRegistries.BLOCK.getKey(change.second().getBlock()))));
        }
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (event.getState() != EventState.POST) {
            return;
        }
        if (event.getType() == ChunkEvent.Type.UNLOAD) {
            offer(new GameEvent.ChunkChange(event.getX(), event.getZ(), false));
        } else if (event.getType() == ChunkEvent.Type.POPULATE_FULL) {
            offer(new GameEvent.ChunkChange(event.getX(), event.getZ(), true));
        }
    }

    @Override
    public void onPlayerDeath() {
        offer(new GameEvent.PlayerDeath());
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        if (event.getState() != EventState.PRE) {
            return;
        }
        Object packet = event.getPacket();
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            // Ostinato reports multi-block section updates as BlockChangeEvent but not single-block updates
            // (its hook for those is commented out; live run 2026-10-13), so single updates come from the packet.
            BlockPos pos = update.getPos();
            offer(new GameEvent.BlockChange(new BlockPoint(pos.getX(), pos.getY(), pos.getZ()),
                    String.valueOf(BuiltInRegistries.BLOCK.getKey(update.getBlockState().getBlock()))));
        } else if (packet instanceof ClientboundSystemChatPacket system && !system.overlay()) {
            offer(new GameEvent.ChatMessage(system.content().getString(), Optional.empty()));
        } else if (packet instanceof ClientboundPlayerChatPacket chat) {
            offer(new PendingChat(chat.body().content(), chat.sender()));
        }
    }
}
