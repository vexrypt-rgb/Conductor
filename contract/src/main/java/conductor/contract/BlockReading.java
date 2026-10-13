package conductor.contract;

import java.util.Optional;

/**
 * The block at one position, read in one tick.
 *
 * @param block the block id (e.g. {@code minecraft:stone}); empty when the chunk is not loaded (never guessed)
 */
public record BlockReading(RejectionReason rejection, BlockPoint at, Optional<String> block, long tick, String detail) {

    public boolean isAccepted() {
        return rejection == null;
    }
}
