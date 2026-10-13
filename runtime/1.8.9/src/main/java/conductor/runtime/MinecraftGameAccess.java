package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.dimension.TheEndDimension;
import net.minecraft.world.dimension.TheNetherDimension;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Minecraft 1.8.9 (Legacy Fabric; legacy Yarn names) implementation of {@link GameAccess}. */
final class MinecraftGameAccess implements GameAccess {

    /** Survival reach of a player in 1.8.9: 3 blocks from the eyes to the target's box. */
    private static final double REACH = 3.0;

    private static MinecraftClient minecraft() {
        return MinecraftClient.getInstance();
    }

    private static ClientPlayerEntity player() {
        return minecraft().player;
    }

    @Override
    public boolean isGameThread() {
        return minecraft().isOnThread();
    }

    @Override
    public String dimensionId() {
        return dimensionName(minecraft().world.dimension);
    }

    /**
     * The namespaced id of a dimension, from its class (1.8.9 dimensions have no id of their own in this API):
     * {@code minecraft:overworld}, {@code minecraft:the_nether} or {@code minecraft:the_end}.
     */
    static String dimensionName(Dimension dimension) {
        if (dimension instanceof TheNetherDimension) {
            return "minecraft:the_nether";
        }
        if (dimension instanceof TheEndDimension) {
            return "minecraft:the_end";
        }
        return "minecraft:overworld";
    }

    @Override
    public Object levelIdentity() {
        return minecraft().world;
    }

    @Override
    public boolean hasPlayer() {
        return player() != null;
    }

    @Override
    public Object connectionIdentity() {
        return minecraft().getNetworkHandler();
    }

    @Override
    public BlockPoint playerBlock() {
        if (player() == null) {
            return null;
        }
        BlockPos p = player().getBlockPos();
        return new BlockPoint(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public String playerName(UUID id) {
        var connection = minecraft().getNetworkHandler();
        var info = connection == null ? null : connection.getPlayerListEntry(id);
        return info == null ? null : info.getProfile().getName();
    }

    @Override
    public boolean canReach(Object game) {
        Entity target = (Entity) game;
        // 1.8.9 has no reach query: the distance from the eyes to the nearest point of the target's box
        Vec3d eye = player().getCameraPosVec(1.0F);
        Box box = target.getBoundingBox();
        double dx = Math.max(box.minX - eye.x, Math.max(0, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0, eye.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
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
        if (player() == null) {
            return null;
        }
        Map<String, Integer> items = new TreeMap<>();
        PlayerInventory inventory = player().inventory;
        for (int slot = 0; slot < inventory.getInvSize(); slot++) {
            ItemStack stack = inventory.getInvStack(slot);
            if (stack != null && stack.count > 0) {
                items.merge(String.valueOf(Item.REGISTRY.getIdentifier(stack.getItem())), stack.count, Integer::sum);
            }
        }
        return items;
    }

    @Override
    public Boolean useKeyDown() {
        return minecraft().options == null ? null : minecraft().options.useKey.isPressed();
    }

    @Override
    public ContentResult.Presence registryPresence(ContentId.Kind kind, String id) {
        Identifier key = identifier(id);
        if (key == null) {
            return null;
        }
        Boolean present = switch (kind) {
            case ITEM -> Item.REGISTRY.containsKey(key);
            case BLOCK -> Block.REGISTRY.containsKey(key);
            case ENTITY -> EntityType.getEntityNames().contains(key.getPath());
            case EFFECT -> StatusEffect.get(key.getPath()) != null;
            default -> null;
        };
        if (present == null) {
            return null;
        }
        return present ? ContentResult.Presence.PRESENT : ContentResult.Presence.ABSENT;
    }

    /** The identifier for {@code id}, or null when it is malformed. */
    static Identifier identifier(String id) {
        try {
            return new Identifier(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
