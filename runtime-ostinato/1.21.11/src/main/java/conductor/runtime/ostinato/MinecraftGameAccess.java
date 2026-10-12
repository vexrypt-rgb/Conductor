package conductor.runtime.ostinato;

import baritone.api.utils.IPlayerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.11 (Ostinato {@code main}) implementation. Uses the same accessors Ostinato itself uses. */
final class MinecraftGameAccess implements GameAccess {

    private final IPlayerContext ctx;

    MinecraftGameAccess(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isGameThread() {
        // Same check Ostinato's BlockStateInterface uses before touching world state.
        return ctx.minecraft().isSameThread();
    }

    @Override
    public String dimensionId() {
        return dimension(ctx.world());
    }

    @Override
    public String dimensionOf(ClientLevel level) {
        return dimension(level);
    }

    @Override
    public Object connectionIdentity() {
        return ctx.minecraft().getConnection();
    }

    private static String dimension(Level level) {
        // Ostinato main reads dimensions the same way (WorldProvider: world.dimension().identifier()).
        return level.dimension().identifier().toString();
    }
}
