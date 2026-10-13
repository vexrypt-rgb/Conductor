package conductor.contract;

/** One active status effect: effect id (e.g. {@code minecraft:speed}), amplifier (0 = level I), ticks left (-1 = infinite). */
public record EffectInfo(String effect, int amplifier, int durationTicks) {
}
