package conductor.contract;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Observation kinds defined by the contract: raw, read-only signals about the movement resource, plus the player's
 * position.
 * <p>
 * Each kind is one raw signal. None of them, alone or combined, is defined here as evidence that pathing stopped,
 * that overrides were cleared, or that the resource is safe. Those evidence rules belong to Stage 4. See
 * {@code docs/stage1-audit.md} §3 for why each underlying signal is insufficient alone.
 */
public final class ObservationKinds {

    private ObservationKinds() {}

    public static final ObservationKind<Boolean> PATH_SEGMENT_PRESENT = new ObservationKind<>(
            "PATH_SEGMENT_PRESENT",
            "The primary pathing executor holds a current path segment.");

    public static final ObservationKind<Boolean> NEXT_SEGMENT_PRESENT = new ObservationKind<>(
            "NEXT_SEGMENT_PRESENT",
            "The primary pathing executor holds a planned next segment.");

    public static final ObservationKind<Boolean> PATH_CALCULATION_PENDING = new ObservationKind<>(
            "PATH_CALCULATION_PENDING",
            "The engine's in-progress calculation slot is occupied. Empty does not prove that no calculation thread is "
                    + "still running (stage1-audit R1).");

    public static final ObservationKind<Optional<String>> CONTROLLING_PROCESS = new ObservationKind<>(
            "CONTROLLING_PROCESS",
            "Class name of the process that issued a pathing command this tick, or empty when none did.");

    public static final ObservationKind<Boolean> TRAVEL_GOAL_ACTIVE = new ObservationKind<>(
            "TRAVEL_GOAL_ACTIVE",
            "The engine's custom-goal travel process reports itself active.");

    public static final ObservationKind<Set<String>> FORCED_INPUTS = new ObservationKind<>(
            "FORCED_INPUTS",
            "Names of inputs currently forced down through the engine's input override handler. Does not cover "
                    + "inputs written by other means (e.g. vanilla key state).");

    public static final ObservationKind<Boolean> SECONDARY_BACKEND_BOUND = new ObservationKind<>(
            "SECONDARY_BACKEND_BOUND",
            "The secondary travel backend was found and bound.");

    public static final ObservationKind<Boolean> SECONDARY_BACKEND_PATHING = new ObservationKind<>(
            "SECONDARY_BACKEND_PATHING",
            "The secondary travel backend reports following, searching or executing.");

    public static final ObservationKind<Boolean> SECONDARY_BACKEND_STOPPING = new ObservationKind<>(
            "SECONDARY_BACKEND_STOPPING",
            "The secondary travel backend reports a cancelled search that has not yet wound down.");

    public static final ObservationKind<BlockPoint> PLAYER_POSITION = new ObservationKind<>(
            "PLAYER_POSITION",
            "The block the player is standing in.");

    public static final ObservationKind<Boolean> USE_KEY_HELD = new ObservationKind<>(
            "USE_KEY_HELD",
            "The game's use (right-click) key is down, whoever pressed it. Covers what FORCED_INPUTS cannot: engine "
                    + "combat holds shields and eats by writing this key directly.");

    public static final ObservationKind<java.util.Optional<String>> COMBAT_TARGET = new ObservationKind<>(
            "COMBAT_TARGET",
            "The entity the engine's combat is targeting: entity type id and name, or empty when none.");

    public static final ObservationKind<java.util.Map<String, Integer>> INVENTORY_ITEMS = new ObservationKind<>(
            "INVENTORY_ITEMS",
            "The player's inventory (hotbar, main, armour, offhand) as item id to total count, e.g. "
                    + "minecraft:shield -> 1. Ids are registry ids, so the same module works on every version.");

    public static final ObservationKind<PlayerStatus> PLAYER_STATUS = new ObservationKind<>(
            "PLAYER_STATUS",
            "Health, absorption, food, air and movement flags of the local player.");

    public static final ObservationKind<PlayerMotion> PLAYER_MOTION = new ObservationKind<>(
            "PLAYER_MOTION",
            "Exact position, velocity (blocks/tick), yaw and pitch of the local player.");

    public static final ObservationKind<List<EntityInfo>> NEARBY_ENTITIES = new ObservationKind<>(
            "NEARBY_ENTITIES",
            "Entities within 32 blocks of the player (not the player itself), nearest first, at most 64.");

    public static final ObservationKind<List<SlotInfo>> INVENTORY_SLOTS = new ObservationKind<>(
            "INVENTORY_SLOTS",
            "Every non-empty inventory slot with item id, count and durability (slot numbers: see SlotInfo).");

    public static final ObservationKind<Integer> SELECTED_SLOT = new ObservationKind<>(
            "SELECTED_SLOT",
            "The selected hotbar slot, 0-8.");

    public static final ObservationKind<List<EffectInfo>> ACTIVE_EFFECTS = new ObservationKind<>(
            "ACTIVE_EFFECTS",
            "The local player's active status effects.");

    public static final List<ObservationKind<?>> ALL = Jdk8.listOf(
            PATH_SEGMENT_PRESENT, NEXT_SEGMENT_PRESENT, PATH_CALCULATION_PENDING, CONTROLLING_PROCESS,
            TRAVEL_GOAL_ACTIVE, FORCED_INPUTS, SECONDARY_BACKEND_BOUND, SECONDARY_BACKEND_PATHING,
            SECONDARY_BACKEND_STOPPING, PLAYER_POSITION, USE_KEY_HELD, COMBAT_TARGET, INVENTORY_ITEMS,
            PLAYER_STATUS, PLAYER_MOTION, NEARBY_ENTITIES, INVENTORY_SLOTS, SELECTED_SLOT, ACTIVE_EFFECTS);
}
