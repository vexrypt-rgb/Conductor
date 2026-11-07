package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.mob.player.ClientPlayerEntity;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Minecraft 1.0.0 implementation (Ornithe's Feather names). 1.0 numbers its blocks, items, effects and dimensions;
 * {@link LegacyIds} turns them into today's ids. It has no player UUIDs: a player is known by name, and by the id
 * {@link LegacyIds#offlineId} gives the name.
 */
final class MinecraftGameAccess implements GameAccess {

    private static Minecraft minecraft() {
        return LegacyClient.minecraft();
    }

    private static ClientPlayerEntity player() {
        Minecraft mc = minecraft();
        return mc == null ? null : mc.player;
    }

    @Override
    public boolean isGameThread() {
        return LegacyClient.isGameThread();
    }

    @Override
    public String dimensionId() {
        return LegacyIds.dimension(minecraft().world.dimension.id);
    }

    @Override
    public Object levelIdentity() {
        Minecraft mc = minecraft();
        return mc == null ? null : mc.world;
    }

    @Override
    public boolean hasPlayer() {
        return player() != null;
    }

    @Override
    public Object connectionIdentity() {
        return minecraft().getNetworkHandler(); // null in single-player
    }

    @Override
    public BlockPoint playerBlock() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        // 1.0 keeps a player's y at eye level; the feet are the bottom of the box
        return new BlockPoint(floor(p.x), floor(p.shape.minY), floor(p.z));
    }

    static int floor(double v) {
        return (int) Math.floor(v);
    }

    @Override
    public String playerName(UUID id) {
        ClientPlayerEntity p = player();
        if (p != null && LegacyIds.offlineId(p.name).equals(id)) {
            return p.name;
        }
        ClientNetworkHandler connection = minecraft().getNetworkHandler();
        if (connection != null) {
            for (Object o : connection.onlinePlayers) {
                String name = ((PlayerInfo) o).name;
                if (LegacyIds.offlineId(name).equals(id)) {
                    return name;
                }
            }
        }
        return null;
    }

    @Override
    public boolean canReach(Object entity) {
        Entity target = (Entity) entity;
        ClientPlayerEntity p = player();
        // the reach 1.0 itself picks entities with (GameRenderer): 6 blocks with creative's extended reach, else 3;
        // measured from the eyes to the nearest point of the target's box
        double reach = minecraft().interactionManager.hasExtendedReach() ? 6.0 : 3.0;
        Box box = target.shape;
        double eyeY = p.y; // 1.0 keeps the local player's y at eye level
        double dx = Math.max(box.minX - p.x, Math.max(0, p.x - box.maxX));
        double dy = Math.max(box.minY - eyeY, Math.max(0, eyeY - box.maxY));
        double dz = Math.max(box.minZ - p.z, Math.max(0, p.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    @Override
    public int selectedSlot() {
        return player().inventory.selectedSlot;
    }

    @Override
    public void setSelectedSlot(int slot) {
        player().inventory.selectedSlot = slot;
    }

    @Override
    public Map<String, Integer> inventoryItems() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        Map<String, Integer> items = new TreeMap<>();
        PlayerInventory inventory = p.inventory;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.size > 0) {
                items.merge(LegacyIds.item(stack.id, stack.getMetadata()), stack.size, Integer::sum);
            }
        }
        return items;
    }

    @Override
    public Boolean useKeyDown() {
        Minecraft mc = minecraft();
        return mc == null || mc.options == null ? null : mc.options.useKey.pressed;
    }

    @Override
    public ContentResult.Presence registryPresence(ContentId.Kind kind, String id) {
        Set<String> known = LegacyIds.known(kind);
        if (known == null || id.indexOf(':') < 0) {
            return null;
        }
        return known.contains(id) ? ContentResult.Presence.PRESENT : ContentResult.Presence.ABSENT;
    }
}
