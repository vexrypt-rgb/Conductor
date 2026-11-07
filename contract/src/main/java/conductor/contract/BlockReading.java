package conductor.contract;

import java.util.Optional;

/**
 * The block at one position, read in one tick.
 *
 * @param block the block id (e.g. {@code minecraft:stone}); empty when the chunk is not loaded (never guessed)
 */
public final class BlockReading {
    private final RejectionReason rejection;
    private final BlockPoint at;
    private final Optional<String> block;
    private final long tick;
    private final String detail;

    public BlockReading(RejectionReason rejection, BlockPoint at, Optional<String> block, long tick, String detail) {
        this.rejection = rejection;
        this.at = at;
        this.block = block;
        this.tick = tick;
        this.detail = detail;
    }

    public RejectionReason rejection() { return rejection; }

    public BlockPoint at() { return at; }

    public Optional<String> block() { return block; }

    public long tick() { return tick; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockReading)) return false;
        BlockReading other = (BlockReading) o;
        return java.util.Arrays.deepEquals(new Object[] {this.rejection, this.at, this.block, this.tick, this.detail}, new Object[] {other.rejection, other.at, other.block, other.tick, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.rejection, this.at, this.block, this.tick, this.detail}); }

    @Override public String toString() { return "BlockReading[" + "rejection=" + rejection + ", " + "at=" + at + ", " + "block=" + block + ", " + "tick=" + tick + ", " + "detail=" + detail + "]"; }


    public boolean isAccepted() {
        return rejection == null;
    }
}
