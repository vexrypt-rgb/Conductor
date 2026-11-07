package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.EffectInfo;
import conductor.contract.EntityInfo;
import conductor.contract.PlayerMotion;
import conductor.contract.PlayerStatus;
import conductor.contract.SlotInfo;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.mob.player.ClientPlayerEntity;
import net.minecraft.client.entity.mob.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.effect.StatusEffectInstance;
import net.minecraft.entity.mob.monster.Monster;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.entity.mob.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Minecraft 1.0.0 implementation of {@link PlayerAccess}. What differs from newer versions: health, food and air are
 * whole numbers; a player's {@code y} is at eye level (positions here are the feet, as everywhere else); blocks, items
 * and effects are numbers ({@link LegacyIds}); there is no off hand, no sprint-flying and no infinite effect; and
 * single-player has no chat at all.
 */
final class MinecraftPlayerAccess implements PlayerAccess {

    /** The longest chat line a 1.0 server takes; a longer one gets the player kicked. */
    static final int MAX_CHAT = 100;

    private final GameAccess game;

    MinecraftPlayerAccess(GameAccess game) {
        this.game = game;
    }

    private static Minecraft minecraft() {
        return LegacyClient.minecraft();
    }

    private static ClientPlayerEntity player() {
        Minecraft mc = minecraft();
        return mc == null ? null : mc.player;
    }

    private static World world() {
        Minecraft mc = minecraft();
        return mc == null ? null : mc.world;
    }

    @Override
    public PlayerStatus status() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        String mode = p.abilities.creativeMode ? "creative" : "survival";
        return new PlayerStatus(p.getHealth(), 0, p.getMaxHealth(), p.getHungerManager().getFoodLevel(),
                p.getHungerManager().getSaturationLevel(), p.getBreath(), p.onGround, p.isSprinting(), p.isSneaking(),
                p.isInWater(), false, mode);
    }

    @Override
    public PlayerMotion motion() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        return new PlayerMotion(p.x, p.shape.minY, p.z, p.velocityX, p.velocityY, p.velocityZ, p.yaw, p.pitch);
    }

    @Override
    public List<EntityInfo> entities(double radius, int max) {
        ClientPlayerEntity p = player();
        World world = world();
        if (p == null || world == null) {
            return null;
        }
        List<EntityInfo> found = new ArrayList<>();
        for (Object o : world.entities) {
            Entity e = (Entity) o;
            if (e == p || e.removed) {
                continue;
            }
            double distance = p.distanceTo(e);
            if (distance > radius) {
                continue;
            }
            OptionalDouble health = e instanceof MobEntity mob ? OptionalDouble.of(mob.getHealth()) : OptionalDouble.empty();
            boolean player = e instanceof PlayerEntity;
            String key = player ? null : Entities.getKey(e);
            String type = player ? "minecraft:player" : LegacyIds.entity(key);
            String name = player ? ((PlayerEntity) e).name : key != null ? key : type;
            found.add(new EntityInfo(e.networkId, type, name, e.x, e.shape.minY, e.z, distance, health, player,
                    e instanceof Monster));
        }
        found.sort(Comparator.comparingDouble(EntityInfo::distance));
        return found.size() > max ? List.copyOf(found.subList(0, max)) : List.copyOf(found);
    }

    @Override
    public List<SlotInfo> slots() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        PlayerInventory inventory = p.inventory;
        List<SlotInfo> slots = new ArrayList<>();
        // 0-35 the main inventory (0-8 the hotbar), 36-39 armour: the numbering newer versions use
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.size > 0) {
                boolean damageable = stack.isDamageable();
                slots.add(new SlotInfo(i, LegacyIds.item(stack.id, damageable ? 0 : stack.getMetadata()), stack.size,
                        damageable ? stack.getDamage() : 0, damageable ? stack.getMaxDamage() : 0));
            }
        }
        return List.copyOf(slots);
    }

    @Override
    public Integer selectedSlot() {
        return player() == null ? null : game.selectedSlot();
    }

    @Override
    public List<EffectInfo> effects() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        List<EffectInfo> effects = new ArrayList<>();
        for (Object o : p.getStatusEffects()) {
            StatusEffectInstance effect = (StatusEffectInstance) o;
            effects.add(new EffectInfo(LegacyIds.effect(effect.getId()), effect.getAmplifier(), effect.getDuration()));
        }
        effects.sort(Comparator.comparing(EffectInfo::effect));
        return List.copyOf(effects);
    }

    // ---- WorldPort ----

    @Override
    public Optional<String> blockAt(BlockPoint at) {
        World world = world();
        if (world == null || !loaded(world, at.x(), at.z())) {
            return Optional.empty();
        }
        return Optional.of(LegacyIds.block(world.getBlock(at.x(), at.y(), at.z()), world.getBlockMetadata(at.x(), at.y(), at.z())));
    }

    @Override
    public List<BlockPoint> findBlocks(Set<String> blockIds, BlockPoint center, int radius, int limit) {
        World world = world();
        if (world == null) {
            return List.of();
        }
        Map<Integer, Boolean> matches = new HashMap<>(); // block number << 4 | metadata
        List<BlockPoint> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.x() + dx;
                int z = center.z() + dz;
                if (!loaded(world, x, z)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    int y = center.y() + dy;
                    int id = world.getBlock(x, y, z);
                    if (id == 0) {
                        continue;
                    }
                    int key = id << 4 | (world.getBlockMetadata(x, y, z) & 15);
                    if (matches.computeIfAbsent(key, k -> blockIds.contains(LegacyIds.block(k >> 4, k & 15)))) {
                        found.add(new BlockPoint(x, y, z));
                    }
                }
            }
        }
        found.sort(Comparator.comparingLong(b -> {
            long dx = b.x() - center.x(), dy = b.y() - center.y(), dz = b.z() - center.z();
            return dx * dx + dy * dy + dz * dz;
        }));
        return found.size() > limit ? List.copyOf(found.subList(0, limit)) : List.copyOf(found);
    }

    /**
     * Whether the chunk holding block x, z has arrived, without loading it. In single-player 1.0 asks its chunk cache;
     * a multiplayer world claims every chunk and hands out an empty one for those the server has not sent.
     */
    static boolean loaded(World world, int x, int z) {
        return world.isChunkLoaded(x, 64, z) && !(world.getChunkAt(x >> 4, z >> 4) instanceof EmptyChunk);
    }

    // ---- ActionPort ----

    @Override
    public Optional<String> selectHotbar(int slot) {
        if (player() == null) {
            return Optional.of("no player");
        }
        game.setSelectedSlot(slot); // the game sends the change to the server on its next tick
        return Optional.empty();
    }

    @Override
    public Optional<String> look(double yaw, double pitch) {
        ClientPlayerEntity p = player();
        if (p == null) {
            return Optional.of("no player");
        }
        p.yaw = (float) yaw;
        p.pitch = (float) pitch;
        return Optional.empty();
    }

    @Override
    public Optional<String> attack(int entityId) {
        ClientPlayerEntity p = player();
        World world = world();
        Minecraft mc = minecraft();
        if (p == null || world == null || mc.interactionManager == null) {
            return Optional.of("no player");
        }
        Entity target = null;
        for (Object o : world.entities) {
            if (((Entity) o).networkId == entityId) {
                target = (Entity) o;
                break;
            }
        }
        if (target == null || target == p || !target.isAlive()) {
            return Optional.of("no living entity " + entityId);
        }
        if (!game.canReach(target)) {
            return Optional.of("entity " + entityId + " is out of reach");
        }
        p.swingArm();
        mc.interactionManager.attackEntity(p, target);
        return Optional.empty();
    }

    @Override
    public Optional<String> useItem(boolean offhand) {
        ClientPlayerEntity p = player();
        Minecraft mc = minecraft();
        if (p == null || mc.interactionManager == null) {
            return Optional.of("no player");
        }
        if (offhand) {
            return Optional.of("Minecraft 1.0 has no off hand");
        }
        ItemStack stack = p.inventory.getSelectedItem();
        if (stack == null) {
            return Optional.of("nothing in hand");
        }
        mc.interactionManager.useItem(p, mc.world, stack);
        return Optional.empty();
    }

    @Override
    public Optional<String> showMessage(String text) {
        Minecraft mc = minecraft();
        if (mc == null || mc.gui == null) {
            return Optional.of("no game window");
        }
        mc.gui.addChatMessage(text);
        return Optional.empty();
    }

    @Override
    public Optional<String> sendChat(String message) {
        ClientPlayerEntity p = player();
        if (!(p instanceof LocalClientPlayerEntity local) || local.networkHandler == null) {
            return Optional.of(p == null ? "not connected" : "single-player Minecraft 1.0 has no chat");
        }
        // a 1.0 server disconnects a player for a longer line or a character outside its font
        if (message.length() > MAX_CHAT) {
            return Optional.of("longer than " + MAX_CHAT + " characters (Minecraft 1.0's limit)");
        }
        for (int i = 0; i < message.length(); i++) {
            if (SharedConstants.VALID_CHAT_CHARACTERS.indexOf(message.charAt(i)) < 0) {
                return Optional.of("character '" + message.charAt(i) + "' is not allowed in Minecraft 1.0 chat");
            }
        }
        local.sendChat(message); // 1.0 sends chat lines and /commands the same way
        return Optional.empty();
    }
}
