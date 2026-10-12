package conductor.runtime.ostinato;

import baritone.api.utils.IPlayerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.4 (Ostinato branch {@code 1.21.4}) implementation. Uses the same accessors Ostinato itself uses. */
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
        // Ostinato 1.21.4 reads dimensions the same way (WorldProvider: world.dimension().location()).
        return level.dimension().location().toString();
    }
}
