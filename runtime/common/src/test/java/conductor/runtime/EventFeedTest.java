package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Events from Conductor's own hooks: commands, block changes, chunks, chat, death, cross-thread hand-over. */
class EventFeedTest {

    private final FakeGameAccess game = new FakeGameAccess();
    private final HookEventFeed feed = new HookEventFeed(game);

    @Test
    void prefixedChatIsKeptFromTheServerAndBecomesACommand() {
        feed.interceptCommands("+");
        assertTrue(feed.outgoingChat("+kit check"));
        assertFalse(feed.outgoingChat("hello"));
        assertFalse(feed.outgoingChat("+"), "a lone prefix is ordinary chat");
        assertEquals(Jdk8.listOf(new GameEvent.CommandLine("+kit check")), feed.drain());
    }

    @Test
    void withoutAPrefixNothingIsIntercepted() {
        assertFalse(feed.outgoingChat("+kit"));
        assertTrue(feed.drain().isEmpty());
    }

    @Test
    void eachHookIsReportedOncePerCall() {
        feed.blockChanged(1, 64, 2, "minecraft:stone");
        feed.chunk(1, 2, true);
        feed.chunk(1, 2, false);
        feed.death();
        assertEquals(Jdk8.listOf(new GameEvent.BlockChange(new BlockPoint(1, 64, 2), "minecraft:stone"),
                new GameEvent.ChunkChange(1, 2, true), new GameEvent.ChunkChange(1, 2, false),
                new GameEvent.PlayerDeath()), feed.drain());
        assertTrue(feed.drain().isEmpty());
    }

    @Test
    void chatSenderNamesAreResolvedOnTheGameThreadAndUnknownSendersStayUnknown() {
        UUID alex = UUID.randomUUID();
        feed.chat("hi", alex);
        game.names.put(alex, "Alex");                    // learned before the game thread drains
        feed.chat("who?", UUID.randomUUID());
        feed.chat("[Server] restart", null);
        assertEquals(Jdk8.listOf(new GameEvent.ChatMessage("hi", Optional.of("Alex")),
                new GameEvent.ChatMessage("who?", Optional.empty()),
                new GameEvent.ChatMessage("[Server] restart", Optional.empty())), feed.drain());
    }

    @Test
    void eventsFromAnotherThreadAreHandedOverByDrain() throws InterruptedException {
        Thread network = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                feed.chunk(i, 0, false);
            }
        });
        network.start();
        network.join();
        assertEquals(1000, feed.drain().size());
    }

    @Test
    void aFullQueueDropsInsteadOfGrowingForever() {
        for (int i = 0; i < HookEventFeed.MAX_QUEUED + 10; i++) {
            feed.chunk(i, 0, true);
        }
        assertEquals(HookEventFeed.MAX_QUEUED, feed.drain().size());
        feed.death();
        assertEquals(Jdk8.listOf(new GameEvent.PlayerDeath()), feed.drain(), "accepts again once drained");
    }
}
