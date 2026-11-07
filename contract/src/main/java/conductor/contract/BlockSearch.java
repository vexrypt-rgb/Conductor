package conductor.contract;

import java.util.List;

/**
 * Positions of matching blocks around the player, nearest first, from loaded chunks only. Unloaded positions are
 * skipped, so "not found" means "not found where the game could see".
 */
public final class BlockSearch {
    private final RejectionReason rejection;
    private final List<BlockPoint> found;
    private final long tick;
    private final String detail;

    public BlockSearch(RejectionReason rejection, List<BlockPoint> found, long tick, String detail) {
        found = Jdk8.copyList(found);

        this.rejection = rejection;
        this.found = found;
        this.tick = tick;
        this.detail = detail;
    }

    public RejectionReason rejection() { return rejection; }

    public List<BlockPoint> found() { return found; }

    public long tick() { return tick; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockSearch)) return false;
        BlockSearch other = (BlockSearch) o;
        return java.util.Arrays.deepEquals(new Object[] {this.rejection, this.found, this.tick, this.detail}, new Object[] {other.rejection, other.found, other.tick, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.rejection, this.found, this.tick, this.detail}); }

    @Override public String toString() { return "BlockSearch[" + "rejection=" + rejection + ", " + "found=" + found + ", " + "tick=" + tick + ", " + "detail=" + detail + "]"; }


    public boolean isAccepted() {
        return rejection == null;
    }
}
