package conductor.contract;

/**
 * A block's own sounds, as sound event ids: vanilla's ({@code "minecraft:block.glass.break"}) or the module's, from
 * its {@code sounds.json}. Used instead of a {@link BlockSound} set when a block mixes sounds.
 */
public final class BlockSounds {
    private final String breakSound;
    private final String step;
    private final String place;
    private final String hit;
    private final String fall;
    private final float volume;
    private final float pitch;

    public BlockSounds(String breakSound, String step, String place, String hit, String fall, float volume, float pitch) {
        this.breakSound = breakSound;
        this.step = step;
        this.place = place;
        this.hit = hit;
        this.fall = fall;
        this.volume = volume;
        this.pitch = pitch;
    }

    public String breakSound() { return breakSound; }

    public String step() { return step; }

    public String place() { return place; }

    public String hit() { return hit; }

    public String fall() { return fall; }

    public float volume() { return volume; }

    public float pitch() { return pitch; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockSounds)) return false;
        BlockSounds other = (BlockSounds) o;
        return java.util.Arrays.deepEquals(new Object[] {this.breakSound, this.step, this.place, this.hit, this.fall, this.volume, this.pitch}, new Object[] {other.breakSound, other.step, other.place, other.hit, other.fall, other.volume, other.pitch});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.breakSound, this.step, this.place, this.hit, this.fall, this.volume, this.pitch}); }

    @Override public String toString() { return "BlockSounds[" + "breakSound=" + breakSound + ", " + "step=" + step + ", " + "place=" + place + ", " + "hit=" + hit + ", " + "fall=" + fall + ", " + "volume=" + volume + ", " + "pitch=" + pitch + "]"; }
}
