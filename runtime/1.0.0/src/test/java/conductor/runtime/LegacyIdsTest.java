package conductor.runtime;

import conductor.contract.ContentId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minecraft 1.0's numbers turned into today's ids, and its chat lines. */
class LegacyIdsTest {

    @Test
    void blocks() {
        assertEquals("minecraft:stone", LegacyIds.block(1, 0));
        assertEquals("minecraft:diamond_ore", LegacyIds.block(56, 0));
        assertEquals("minecraft:red_wool", LegacyIds.block(35, 14));
        assertEquals("minecraft:birch_log", LegacyIds.block(17, 2));
        assertEquals("minecraft:birch_log", LegacyIds.block(17, 6)); // the upper bits are not the wood
        assertEquals("minecraft:wall_torch", LegacyIds.block(50, 3));
        assertEquals("minecraft:torch", LegacyIds.block(50, 5));
        assertEquals("minecraft:dragon_egg", LegacyIds.block(122, 0)); // 1.0's newest block
        assertEquals("minecraft:legacy_123", LegacyIds.block(123, 0)); // 1.2's redstone lamp: not in 1.0
    }

    @Test
    void items() {
        assertEquals("minecraft:iron_shovel", LegacyIds.item(256, 0));
        assertEquals("minecraft:charcoal", LegacyIds.item(263, 1));
        assertEquals("minecraft:bone_meal", LegacyIds.item(351, 15));
        assertEquals("minecraft:glistering_melon_slice", LegacyIds.item(382, 0)); // 1.0's newest item
        assertEquals("minecraft:legacy_383", LegacyIds.item(383, 0));
        assertEquals("minecraft:music_disc_13", LegacyIds.item(2256, 0));
        assertEquals("minecraft:music_disc_11", LegacyIds.item(2266, 0));
        assertEquals("minecraft:diamond_ore", LegacyIds.item(56, 0)); // a block's item
    }

    @Test
    void effectsEntitiesAndDimensions() {
        assertEquals("minecraft:speed", LegacyIds.effect(1));
        assertEquals("minecraft:legacy_effect_99", LegacyIds.effect(99));
        assertEquals("minecraft:magma_cube", LegacyIds.entity("LavaSlime"));
        assertEquals("minecraft:unknown", LegacyIds.entity(null));
        assertEquals("minecraft:the_nether", LegacyIds.dimension(-1));
        assertEquals(-1, LegacyIds.dimensionNumber("minecraft:the_nether"));
        assertNull(LegacyIds.dimensionNumber("minecraft:the_moon"));
    }

    @Test
    void knownIds() {
        assertTrue(LegacyIds.known(ContentId.Kind.BLOCK).contains("minecraft:red_wool"));
        assertFalse(LegacyIds.known(ContentId.Kind.BLOCK).contains("minecraft:copper_ore"));
        assertTrue(LegacyIds.known(ContentId.Kind.ITEM).contains("minecraft:music_disc_13"));
        assertTrue(LegacyIds.known(ContentId.Kind.ITEM).contains("minecraft:stone")); // a block is its own item
        assertTrue(LegacyIds.known(ContentId.Kind.ENTITY).contains("minecraft:player"));
        assertTrue(LegacyIds.known(ContentId.Kind.EFFECT).contains("minecraft:speed"));
    }

    @Test
    void offlineIdsMatchTodaysOfflineServers() {
        // what a modern offline-mode server gives "Dev" (UUID.nameUUIDFromBytes("OfflinePlayer:Dev"))
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Dev".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                LegacyIds.offlineId("Dev"));
        assertEquals(3, LegacyIds.offlineId("Dev").version());
    }

    @Test
    void commandsInTodaysSyntax() {
        assertEquals("give Dev 297 3", LegacyIds.command("give Dev minecraft:bread 3"));
        assertEquals("give Dev 297 1", LegacyIds.command("give Dev bread"));
        assertEquals("give Dev 35 2 14", LegacyIds.command("give Dev minecraft:red_wool 2"));
        assertEquals("give Dev 17 1", LegacyIds.command("give Dev minecraft:oak_log"));
        assertEquals("give Dev 2256 1", LegacyIds.command("give Dev minecraft:music_disc_13"));
        assertEquals("give Dev 351 1 15", LegacyIds.command("give Dev minecraft:bone_meal"));
        assertEquals("give Dev 297 3", LegacyIds.command("give Dev 297 3")); // already 1.0's
        assertEquals("give Dev minecraft:netherite_ingot 1", LegacyIds.command("give Dev minecraft:netherite_ingot 1"));
        assertEquals("time set 0", LegacyIds.command("time set 0"));
    }

    @Test
    void playerChatLines() {
        assertArrayEquals(new String[] {"Dev", "hello <there>"}, LegacyEvents.playerLine("<Dev> hello <there>"));
        assertNull(LegacyEvents.playerLine("* Dev waves"));
        assertNull(LegacyEvents.playerLine("§eDev joined the game."));
        assertNull(LegacyEvents.playerLine("<a name far too long> hi"));
    }

    @Test
    void longMessagesAreSplit() {
        assertEquals(List.of("a", "", "b"), LegacyEvents.chatLines("a\n\nb"));
        assertEquals(List.of(""), LegacyEvents.chatLines(""));
        String text = "x".repeat(250);
        List<String> lines = LegacyEvents.chatLines(text);
        assertEquals(List.of(119, 119, 12), lines.stream().map(String::length).toList());
        assertEquals(text, String.join("", lines));
    }
}
