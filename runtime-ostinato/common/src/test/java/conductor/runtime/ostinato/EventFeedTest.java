package conductor.runtime.ostinato;

import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.type.EventState;
import conductor.contract.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the event feed that need no Minecraft classes: commands, chunks, death, cross-thread hand-over. */
class EventFeedTest {

    private final OstinatoEventFeed feed = new OstinatoEventFeed(new FakeGameAccess());

    @Test
    void prefixedChatIsKeptFromTheServerAndBecomesACommand() {
        feed.interceptCommands("+");
        ChatEvent command = new ChatEvent("+kit check");
        ChatEvent normal = new ChatEvent("hello");
        ChatEvent bare = new ChatEvent("+");
        feed.onSendChatMessage(command);
        feed.onSendChatMessage(normal);
        feed.onSendChatMessage(bare);
        assertTrue(command.isCancelled());
        assertFalse(normal.isCancelled());
        assertFalse(bare.isCancelled(), "a lone prefix is ordinary chat");
        assertEquals(List.of(new GameEvent.CommandLine("+kit check")), feed.drain());
    }

    @Test
    void withoutAPrefixNothingIsIntercepted() {
        ChatEvent e = new ChatEvent("+kit");
        feed.onSendChatMessage(e);
        assertFalse(e.isCancelled());
        assertTrue(feed.drain().isEmpty());
    }

    @Test
    void chunksAndDeathAreReportedOncePerEvent() {
        feed.onChunkEvent(new ChunkEvent(EventState.PRE, ChunkEvent.Type.POPULATE_FULL, 1, 2));
        feed.onChunkEvent(new ChunkEvent(EventState.POST, ChunkEvent.Type.POPULATE_FULL, 1, 2));
        feed.onChunkEvent(new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, 1, 2));
        feed.onPlayerDeath();
        assertEquals(List.of(new GameEvent.ChunkChange(1, 2, true), new GameEvent.ChunkChange(1, 2, false),
                new GameEvent.PlayerDeath()), feed.drain());
        assertTrue(feed.drain().isEmpty());
    }

    @Test
    void eventsFromAnotherThreadAreHandedOverByDrain() throws InterruptedException {
        Thread network = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                feed.onChunkEvent(new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, i, 0));
            }
        });
        network.start();
        network.join();
        assertEquals(1000, feed.drain().size());
    }
}
