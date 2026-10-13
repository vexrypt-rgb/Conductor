package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.EffectInfo;
import conductor.contract.EntityInfo;
import conductor.contract.PlayerMotion;
import conductor.contract.PlayerStatus;
import conductor.contract.SlotInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Minecraft 1.16.1 implementation of {@link PlayerAccess}: the 1.21.x one (runtime/modern) with this version's names.
 * The differences: the inventory and rotations are fields, the registries are {@code Registry.*}, effects have no
 * infinite duration, chat is one call for lines and commands, and messages are {@code TextComponent}s.
 */
final class MinecraftPlayerAccess implements PlayerAccess {

    private final GameAccess game;

    MinecraftPlayerAccess(GameAccess game) {
        this.game = game;
    }

    /** Plain Minecraft, no engine. */
    private static final class Ctx {
        static Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        static LocalPlayer player() {
            return minecraft().player;
        }
    }


    private LocalPlayer player() {
        return Ctx.player();
    }

    private ClientLevel level() {
        return Ctx.minecraft().level;
    }

    @Override
    public PlayerStatus status() {
        LocalPlayer p = player();
        if (p == null) {
            return null;
        }
        Minecraft mc = Ctx.minecraft();
        String mode = mc.gameMode == null ? "unknown" : mc.gameMode.getPlayerMode().getName();
        return new PlayerStatus(p.getHealth(), p.getAbsorptionAmount(), p.getMaxHealth(), p.getFoodData().getFoodLevel(),
                p.getFoodData().getSaturationLevel(), p.getAirSupply(), p.isOnGround(), p.isSprinting(), p.isShiftKeyDown(),
                p.isInWater(), p.isFallFlying(), mode);
    }

    @Override
    public PlayerMotion motion() {
        LocalPlayer p = player();
        if (p == null) {
            return null;
        }
        return new PlayerMotion(p.getX(), p.getY(), p.getZ(), p.getDeltaMovement().x, p.getDeltaMovement().y,
                p.getDeltaMovement().z, p.yRot, p.xRot);
    }

    @Override
    public List<EntityInfo> entities(double radius, int max) {
        LocalPlayer p = player();
        ClientLevel level = level();
        if (p == null || level == null) {
            return null;
        }
        List<EntityInfo> found = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e == p) {
                continue;
            }
            double distance = p.distanceTo(e);
            if (distance > radius) {
                continue;
            }
            OptionalDouble health = e instanceof LivingEntity living ? OptionalDouble.of(living.getHealth()) : OptionalDouble.empty();
            found.add(new EntityInfo(e.getId(), String.valueOf(EntityType.getKey(e.getType())), e.getName().getString(),
                    e.getX(), e.getY(), e.getZ(), distance, health, e instanceof Player, e instanceof Enemy));
        }
        found.sort(Comparator.comparingDouble(EntityInfo::distance));
        return found.size() > max ? List.copyOf(found.subList(0, max)) : List.copyOf(found);
    }

    @Override
    public List<SlotInfo> slots() {
        LocalPlayer p = player();
        if (p == null) {
            return null;
        }
        Inventory inventory = p.inventory;
        List<SlotInfo> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                slots.add(new SlotInfo(i, String.valueOf(Registry.ITEM.getKey(stack.getItem())), stack.getCount(),
                        stack.getDamageValue(), stack.isDamageableItem() ? stack.getMaxDamage() : 0));
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
        LocalPlayer p = player();
        if (p == null) {
            return null;
        }
        List<EffectInfo> effects = new ArrayList<>();
        for (MobEffectInstance effect : p.getActiveEffects()) {
            effects.add(new EffectInfo(String.valueOf(Registry.MOB_EFFECT.getKey(effect.getEffect())),
                    effect.getAmplifier(), effect.getDuration()));
        }
        effects.sort(Comparator.comparing(EffectInfo::effect));
        return List.copyOf(effects);
    }

    // ---- WorldPort ----

    @Override
    public Optional<String> blockAt(BlockPoint at) {
        ClientLevel level = level();
        if (level == null || !loaded(level, at.x() >> 4, at.z() >> 4)) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(Registry.BLOCK.getKey(level.getBlockState(new BlockPos(at.x(), at.y(), at.z())).getBlock())));
    }

    @Override
    public List<BlockPoint> findBlocks(Set<String> blockIds, BlockPoint center, int radius, int limit) {
        ClientLevel level = level();
        if (level == null) {
            return List.of();
        }
        Map<Block, Boolean> matches = new HashMap<>();
        List<BlockPoint> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.x() + dx;
                int z = center.z() + dz;
                if (!loaded(level, x >> 4, z >> 4)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    pos.set(x, center.y() + dy, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (matches.computeIfAbsent(block, b -> blockIds.contains(String.valueOf(Registry.BLOCK.getKey(b))))) {
                        found.add(new BlockPoint(x, center.y() + dy, z));
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

    private static boolean loaded(ClientLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
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
        LocalPlayer p = player();
        if (p == null) {
            return Optional.of("no player");
        }
        p.yRot = (float) yaw;
        p.xRot = (float) pitch;
        p.setYHeadRot((float) yaw);
        return Optional.empty();
    }

    @Override
    public Optional<String> attack(int entityId) {
        LocalPlayer p = player();
        ClientLevel level = level();
        Minecraft mc = Ctx.minecraft();
        if (p == null || level == null || mc.gameMode == null) {
            return Optional.of("no player");
        }
        Entity target = level.getEntity(entityId);
        if (target == null || target == p || !target.isAlive()) {
            return Optional.of("no living entity " + entityId);
        }
        if (!game.canReach(target)) {
            return Optional.of("entity " + entityId + " is out of reach");
        }
        mc.gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);
        return Optional.empty();
    }

    @Override
    public Optional<String> useItem(boolean offhand) {
        LocalPlayer p = player();
        Minecraft mc = Ctx.minecraft();
        if (p == null || mc.gameMode == null) {
            return Optional.of("no player");
        }
        mc.gameMode.useItem(p, p.level, offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        return Optional.empty();
    }

    @Override
    public Optional<String> showMessage(String text) {
        Minecraft mc = Ctx.minecraft();
        if (mc.gui == null) {
            return Optional.of("no game window");
        }
        mc.gui.getChat().addMessage(new TextComponent(text));
        return Optional.empty();
    }

    @Override
    public Optional<String> sendChat(String message) {
        LocalPlayer p = player();
        if (p == null || p.connection == null) {
            return Optional.of("not connected");
        }
        p.chat(message); // 1.16.1 sends chat lines and /commands the same way
        return Optional.empty();
    }
}
