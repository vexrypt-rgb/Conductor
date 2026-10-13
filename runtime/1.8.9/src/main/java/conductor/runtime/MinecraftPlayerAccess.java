package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.EffectInfo;
import conductor.contract.EntityInfo;
import conductor.contract.PlayerMotion;
import conductor.contract.PlayerStatus;
import conductor.contract.SlotInfo;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Minecraft 1.8.9 implementation of {@link PlayerAccess}: the same reads and actions as the other versions, with this
 * version's names. Legacy Yarn leaves a few methods unnamed; they are called by their {@code method_} names.
 */
final class MinecraftPlayerAccess implements PlayerAccess {

    private final GameAccess game;

    MinecraftPlayerAccess(GameAccess game) {
        this.game = game;
    }

    private static MinecraftClient minecraft() {
        return MinecraftClient.getInstance();
    }

    private static ClientPlayerEntity player() {
        return minecraft().player;
    }

    private static ClientWorld level() {
        return minecraft().world;
    }

    @Override
    public PlayerStatus status() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        MinecraftClient mc = minecraft();
        String mode = mc.interactionManager == null ? "unknown" : mc.interactionManager.getCurrentGameMode().getName();
        return new PlayerStatus(p.getHealth(), p.getAbsorption(), p.getMaxHealth(), p.getHungerManager().getFoodLevel(),
                p.getHungerManager().getSaturationLevel(), p.getAir(), p.onGround, p.isSprinting(), p.isSneaking(),
                p.isTouchingWater(), false, mode);
    }

    @Override
    public PlayerMotion motion() {
        ClientPlayerEntity p = player();
        if (p == null) {
            return null;
        }
        return new PlayerMotion(p.x, p.y, p.z, p.velocityX, p.velocityY, p.velocityZ, p.yaw, p.pitch);
    }

    @Override
    public List<EntityInfo> entities(double radius, int max) {
        ClientPlayerEntity p = player();
        ClientWorld level = level();
        if (p == null || level == null) {
            return null;
        }
        List<EntityInfo> found = new ArrayList<>();
        for (Entity e : level.getEntitiesIn(p, p.getBoundingBox().expand(radius, radius, radius))) {
            if (e == p) {
                continue;
            }
            double distance = p.distanceTo(e);
            if (distance > radius) {
                continue;
            }
            OptionalDouble health = e instanceof LivingEntity living ? OptionalDouble.of(living.getHealth()) : OptionalDouble.empty();
            found.add(new EntityInfo(e.getEntityId(), String.valueOf(EntityType.getIdByEntity(e)), e.getName().asUnformattedString(),
                    e.x, e.y, e.z, distance, health, e instanceof PlayerEntity, e instanceof HostileEntity));
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
        for (int i = 0; i < inventory.getInvSize(); i++) {
            ItemStack stack = inventory.getInvStack(i);
            if (stack != null && stack.count > 0) {
                slots.add(new SlotInfo(i, String.valueOf(Item.REGISTRY.getIdentifier(stack.getItem())), stack.count,
                        stack.getDamage(), stack.isDamageable() ? stack.getMaxDamage() : 0));
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
        for (StatusEffectInstance effect : p.getStatusEffectInstances()) {
            // 1.8.9 effects have numbers, not registry ids: the translation key stands in for the name
            effects.add(new EffectInfo(StatusEffect.STATUS_EFFECTS[effect.getEffectId()].getTranslationKey(),
                    effect.getAmplifier(), effect.getDuration()));
        }
        effects.sort(Comparator.comparing(EffectInfo::effect));
        return List.copyOf(effects);
    }

    // ---- WorldPort ----

    @Override
    public Optional<String> blockAt(BlockPoint at) {
        ClientWorld level = level();
        if (level == null || !loaded(level, at.x() >> 4, at.z() >> 4)) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(Block.REGISTRY.getIdentifier(level.getBlockState(new BlockPos(at.x(), at.y(), at.z())).getBlock())));
    }

    @Override
    public List<BlockPoint> findBlocks(Set<String> blockIds, BlockPoint center, int radius, int limit) {
        ClientWorld level = level();
        if (level == null) {
            return List.of();
        }
        Map<Block, Boolean> matches = new HashMap<>();
        List<BlockPoint> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.x() + dx;
                int z = center.z() + dz;
                if (!loaded(level, x >> 4, z >> 4)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    Block block = level.getBlockState(new BlockPos(x, center.y() + dy, z)).getBlock();
                    if (matches.computeIfAbsent(block, b -> blockIds.contains(String.valueOf(Block.REGISTRY.getIdentifier(b))))) {
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

    private static boolean loaded(ClientWorld level, int chunkX, int chunkZ) {
        return level.getChunkProvider().chunkExists(chunkX, chunkZ);
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
        p.setHeadYaw((float) yaw);
        return Optional.empty();
    }

    @Override
    public Optional<String> attack(int entityId) {
        ClientPlayerEntity p = player();
        ClientWorld level = level();
        MinecraftClient mc = minecraft();
        if (p == null || level == null || mc.interactionManager == null) {
            return Optional.of("no player");
        }
        Entity target = level.getEntityById(entityId);
        if (target == null || target == p || !target.isAlive()) {
            return Optional.of("no living entity " + entityId);
        }
        if (!game.canReach(target)) {
            return Optional.of("entity " + entityId + " is out of reach");
        }
        mc.interactionManager.attackEntity(p, target);
        p.swingHand();
        return Optional.empty();
    }

    @Override
    public Optional<String> useItem(boolean offhand) {
        ClientPlayerEntity p = player();
        MinecraftClient mc = minecraft();
        if (p == null || mc.interactionManager == null) {
            return Optional.of("no player");
        }
        if (offhand) {
            return Optional.of("1.8.9 has no off hand");
        }
        mc.interactionManager.interactItem(p, level(), p.getMainHandStack());
        return Optional.empty();
    }

    @Override
    public Optional<String> showMessage(String text) {
        MinecraftClient mc = minecraft();
        if (mc.inGameHud == null) {
            return Optional.of("no game window");
        }
        mc.inGameHud.getChatHud().addMessage(new LiteralText(text));
        return Optional.empty();
    }

    @Override
    public Optional<String> sendChat(String message) {
        ClientPlayerEntity p = player();
        if (p == null || minecraft().getNetworkHandler() == null) {
            return Optional.of("not connected");
        }
        p.sendChatMessage(message); // chat lines and /commands are sent the same way
        return Optional.empty();
    }
}
