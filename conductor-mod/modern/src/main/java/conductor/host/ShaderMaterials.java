package conductor.host;

import conductor.runtime.ContentInstaller;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * Shader packs (Iris) give blocks a material id from the pack's {@code block.properties}, which lists blocks by name.
 * Module blocks are not listed, so a pack would draw module glass and leaves as plain solid blocks (no refraction, no
 * waving). This copies the id the pack gave the vanilla lookalike ({@link ContentInstaller#shaderLookalikes()}) to
 * each module block state, matching properties by name ({@code waterlogged}, {@code distance}, ...). A state the pack
 * already lists keeps the pack's id. Called from {@code IrisBlockIdsMixin}; does nothing without Iris.
 */
public final class ShaderMaterials {

    private static final Logger LOG = LogManager.getLogger("conductor");

    private ShaderMaterials() {}

    /** Returns {@code ids} with module block states added, or {@code ids} itself if there is nothing to add or anything fails. */
    public static Object2IntMap<BlockState> addContentBlocks(Object2IntMap<BlockState> ids) {
        try {
            Map<Block, Block> lookalikes = ContentInstaller.shaderLookalikes();
            if (ids == null || lookalikes.isEmpty()) {
                return ids;
            }
            Object2IntOpenHashMap<BlockState> out = new Object2IntOpenHashMap<>(ids);
            out.defaultReturnValue(ids.defaultReturnValue());
            int added = 0;
            for (Map.Entry<Block, Block> e : lookalikes.entrySet()) {
                for (BlockState state : e.getKey().getStateDefinition().getPossibleStates()) {
                    if (out.containsKey(state)) {
                        continue; // the pack names this block itself
                    }
                    BlockState like = counterpart(state, e.getValue());
                    if (ids.containsKey(like)) {
                        out.put(state, ids.getInt(like));
                        added++;
                    }
                }
            }
            LOG.info("Conductor content: shader pack materials for {} module block state(s) ({} block(s) like vanilla glass, leaves or panes)",
                    added, lookalikes.size());
            return out;
        } catch (Throwable t) {
            LOG.warn("Conductor could not give module blocks shader pack materials; they are drawn as plain blocks", t);
            return ids;
        }
    }

    /** The lookalike's state with the same values for the properties both blocks have. */
    private static BlockState counterpart(BlockState state, Block lookalike) {
        BlockState like = lookalike.defaultBlockState();
        for (Property<?> p : state.getProperties()) {
            like = copy(state, p, like);
        }
        return like;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, Property<T> p, BlockState to) {
        String value = p.getName(from.getValue(p));
        for (Property<?> q : to.getProperties()) {
            if (q.getName().equals(p.getName())) {
                return set(to, q, value);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState to, Property<T> q, String value) {
        Optional<T> v = q.getValue(value);
        return v.isPresent() ? to.setValue(q, v.get()) : to;
    }
}
