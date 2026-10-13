package conductor.contract;

import java.util.List;

/**
 * Positions of matching blocks around the player, nearest first, from loaded chunks only. Unloaded positions are
 * skipped, so "not found" means "not found where the game could see".
 */
public record BlockSearch(RejectionReason rejection, List<BlockPoint> found, long tick, String detail) {

    public BlockSearch {
        found = List.copyOf(found);
    }

    public boolean isAccepted() {
        return rejection == null;
    }
}
