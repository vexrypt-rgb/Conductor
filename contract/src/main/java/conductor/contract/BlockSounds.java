package conductor.contract;

/**
 * A block's own sounds, as sound event ids: vanilla's ({@code "minecraft:block.glass.break"}) or the module's, from
 * its {@code sounds.json}. Used instead of a {@link BlockSound} set when a block mixes sounds.
 */
public record BlockSounds(String breakSound, String step, String place, String hit, String fall, float volume, float pitch) {
}
