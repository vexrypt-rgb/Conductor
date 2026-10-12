package conductor.runtime.ostinato;

import baritone.api.utils.IPlayerContext;
import conductor.contract.BlockPoint;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    @Override
    public BlockPoint playerBlock() {
        if (ctx.player() == null) {
            return null;
        }
        BlockPos p = ctx.player().blockPosition();
        return new BlockPoint(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public Boolean useKeyDown() {
        // PvpProcess/PveProcess hold shields and eat through this key directly (Stage 5 audit).
        return ctx.minecraft().options == null ? null : ctx.minecraft().options.keyUse.isDown();
    }

    @Override
    public ContentResult.Presence registryPresence(ContentId.Kind kind, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        Registry<?> registry = switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM;
            case BLOCK -> BuiltInRegistries.BLOCK;
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE;
            case EFFECT -> BuiltInRegistries.MOB_EFFECT;
            default -> null;
        };
        if (registry == null) {
            return null;
        }
        return registry.containsKey(key) ? ContentResult.Presence.PRESENT : ContentResult.Presence.ABSENT;
    }

    private static String dimension(Level level) {
        // Ostinato 1.21.4 reads dimensions the same way (WorldProvider: world.dimension().location()).
        return level.dimension().location().toString();
    }
}
