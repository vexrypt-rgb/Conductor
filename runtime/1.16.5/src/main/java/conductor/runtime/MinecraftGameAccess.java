package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Minecraft 1.16.5 implementation (Mojang names; no Parchment for this version). */
final class MinecraftGameAccess implements GameAccess {

    /** Vanilla 1.16.5's attack reach for a survival player: 3 blocks from the eyes to the target's box. */
    private static final double REACH = 3.0;

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer player() {
        return minecraft().player;
    }

    @Override
    public boolean isGameThread() {
        return minecraft().isSameThread();
    }

    @Override
    public String dimensionId() {
        return minecraft().level.dimension().location().toString();
    }

    @Override
    public Object levelIdentity() {
        return minecraft().level;
    }

    @Override
    public boolean hasPlayer() {
        return player() != null;
    }

    @Override
    public Object connectionIdentity() {
        return minecraft().getConnection();
    }

    @Override
    public BlockPoint playerBlock() {
        if (player() == null) {
            return null;
        }
        BlockPos p = player().blockPosition();
        return new BlockPoint(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public String playerName(UUID id) {
        var connection = minecraft().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        return info == null ? null : info.getProfile().getName();
    }

    @Override
    public boolean canReach(Entity target) {
        // 1.16.5 has no reach query: the distance from the eyes to the nearest point of the target's box
        Vec3 eye = player().getEyePosition(1.0F);
        AABB box = target.getBoundingBox();
        double dx = Math.max(box.minX - eye.x, Math.max(0, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0, eye.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    @Override
    public int selectedSlot() {
        return player().inventory.selected;
    }

    @Override
    public void setSelectedSlot(int slot) {
        player().inventory.selected = slot;
    }

    @Override
    public Map<String, Integer> inventoryItems() {
        if (player() == null) {
            return null;
        }
        Map<String, Integer> items = new TreeMap<>();
        Inventory inventory = player().inventory;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(Registry.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return items;
    }

    @Override
    public Boolean useKeyDown() {
        return minecraft().options == null ? null : minecraft().options.keyUse.isDown();
    }

    @Override
    public ContentResult.Presence registryPresence(ContentId.Kind kind, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        Registry<?> registry = switch (kind) {
            case ITEM -> Registry.ITEM;
            case BLOCK -> Registry.BLOCK;
            case ENTITY -> Registry.ENTITY_TYPE;
            case EFFECT -> Registry.MOB_EFFECT;
            default -> null;
        };
        if (registry == null) {
            return null;
        }
        return registry.containsKey(key) ? ContentResult.Presence.PRESENT : ContentResult.Presence.ABSENT;
    }
}
