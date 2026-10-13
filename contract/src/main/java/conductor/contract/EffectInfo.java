package conductor.contract;

/** One active status effect: effect id (e.g. {@code minecraft:speed}), amplifier (0 = level I), ticks left (-1 = infinite). */
public final class EffectInfo {
    private final String effect;
    private final int amplifier;
    private final int durationTicks;

    public EffectInfo(String effect, int amplifier, int durationTicks) {
        this.effect = effect;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    public String effect() { return effect; }

    public int amplifier() { return amplifier; }

    public int durationTicks() { return durationTicks; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EffectInfo)) return false;
        EffectInfo other = (EffectInfo) o;
        return java.util.Arrays.deepEquals(new Object[] {this.effect, this.amplifier, this.durationTicks}, new Object[] {other.effect, other.amplifier, other.durationTicks});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.effect, this.amplifier, this.durationTicks}); }

    @Override public String toString() { return "EffectInfo[" + "effect=" + effect + ", " + "amplifier=" + amplifier + ", " + "durationTicks=" + durationTicks + "]"; }
}
