package conductor.contract;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only block access, implemented by a runtime adapter. Game thread only. */
public interface WorldPort {

    /** The block id at {@code at}; empty when its chunk is not loaded. */
    Optional<String> blockAt(BlockPoint at);

    /** Matching blocks within {@code radius} (a cube) of {@code center}, nearest first, at most {@code limit}; loaded chunks only. */
    List<BlockPoint> findBlocks(Set<String> blockIds, BlockPoint center, int radius, int limit);
}
