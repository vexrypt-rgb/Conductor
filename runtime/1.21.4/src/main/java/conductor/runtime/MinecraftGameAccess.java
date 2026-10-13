package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.4 implementation (Mojang names via Parchment). */
final class MinecraftGameAccess implements GameAccess {

    /** Plain Minecraft, no engine: the same calls work with or without Ostinato installed. */
    private static final class Ctx {
        net.minecraft.client.Minecraft minecraft() {
            return net.minecraft.client.Minecraft.getInstance();
        }

        net.minecraft.client.player.LocalPlayer player() {
            return minecraft().player;
        }

        net.minecraft.client.multiplayer.ClientLevel world() {
            return minecraft().level;
        }
    }

    private final Ctx ctx = new Ctx();

    MinecraftGameAccess() {
    }

    @Override
    public boolean isGameThread() {
        return ctx.minecraft().isSameThread();
    }

    @Override
    public String dimensionId() {
        return dimension(ctx.world());
    }

    @Override
    public Object levelIdentity() {
        return ctx.world();
    }

    @Override
    public boolean hasPlayer() {
        return ctx.player() != null;
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
    public String playerName(java.util.UUID id) {
        var connection = ctx.minecraft().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        return info == null ? null : info.getProfile().getName();
    }

    @Override
    public boolean canReach(net.minecraft.world.entity.Entity target) {
        return ctx.player().canInteractWithEntity(target, 0);
    }

    @Override
    public int selectedSlot() {
        return ctx.player().getInventory().selected;
    }

    @Override
    public void setSelectedSlot(int slot) {
        ctx.player().getInventory().selected = slot;
    }

    @Override
    public java.util.Map<String, Integer> inventoryItems() {
        if (ctx.player() == null) {
            return null;
        }
        java.util.Map<String, Integer> items = new java.util.TreeMap<>();
        net.minecraft.world.entity.player.Inventory inventory = ctx.player().getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return items;
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
