package conductor.contract;

/**
 * Java binding for consumers: direct calls, no serialisation.
 * <p>
 * Surface: discovery and read-only observation (Stage 2), plus single-consumer navigation delegation (Stage 3).
 * A {@code Conductor} instance is one consumer; it is not a shared service.
 * <p>
 * Every method must be called on the game thread. A call from another thread is rejected with
 * {@link RejectionReason#OFF_THREAD} before anything is read, and is recorded in the diagnostic sink.
 */
public interface Conductor {

    String CONTRACT_VERSION = "conductor-contract/0.4 (stage 4: evidence-based release, interference classification)";

    DiscoveryResult discover();

    /** Sample {@code kind} now. Rejected without sampling if {@code policy} is null. */
    <T> ObservationResult<T> observe(ObservationKind<T> kind, FreshnessPolicy policy);

    /**
     * Re-check an observation taken earlier: it must belong to the current context and satisfy {@code policy} now.
     * Observations from an old context are rejected, however recent.
     */
    <T> ObservationResult<T> revalidate(Observation<T> observation, FreshnessPolicy policy);

    /**
     * Delegate one travel request to the engine's existing navigation boundary.
     * <p>
     * Granted only while the movement resource is {@link ResourceState#AVAILABLE}: release evidence has held for the
     * configured {@link ReleasePolicy#confirmStopWindowTicks()}. Without that setting nothing is ever granted. A
     * context change never grants anything.
     */
    OperationResult requestTravel(NavigationGoal goal);

    /**
     * Delegate one combat request to the engine's existing combat processes (Stage 5). Same rules as
     * {@link #requestTravel}: combat steers the movement resource, so it needs it AVAILABLE, is exclusive with travel,
     * and is stopped with {@link #cancel} and confirmed only on release evidence.
     */
    OperationResult requestCombat(CombatGoal goal);

    // ---- world queries (read-only) ----

    /** The block at {@code at}. Empty block when its chunk is not loaded. */
    BlockReading blockAt(BlockPoint at);

    /**
     * Blocks with one of {@code blockIds} within {@code radius} blocks (1-16, a cube around the player), nearest
     * first, at most {@code limit} (1-256). Loaded chunks only.
     */
    BlockSearch findBlocks(java.util.Set<String> blockIds, int radius, int limit);

    // ---- one-shot actions ----
    // A module must hold the action's lease (ActionKind.resource()). Actions the engine would overwrite (all but chat)
    // are refused with RESOURCE_NOT_RELEASED until the bot is free. Accepted means the game was told; check the effect
    // in the next observations.

    /** Select hotbar slot 0-8. Needs HOTBAR. */
    ActionResult selectHotbar(int slot);

    /** Turn to yaw/pitch in degrees (pitch -90..90). Needs LOOK. */
    ActionResult look(double yaw, double pitch);

    /** Attack an entity by id (from {@link ObservationKinds#NEARBY_ENTITIES}) if it is in reach. Needs INTERACT. */
    ActionResult attack(int entityId);

    /** Use the item in the main or off hand (eat, block, throw, ...). Needs INTERACT. */
    ActionResult useItem(boolean offhand);

    /** Send a chat message, or a command if it starts with {@code /}. One per tick; at most 256 characters. Needs CHAT. */
    ActionResult sendChat(String message);

    /**
     * Invoke the engine's cancel for an operation this consumer holds. The result is at most
     * {@link OperationState#CANCELLING}: cancel requested, not confirmed. The engine's cancel may stop more than this
     * operation; see {@link NavigationPort#cancel()}.
     */
    OperationResult cancel(OperationId operation);

    OperationResult operationStatus(OperationId operation);

    ResourceStatus resourceStatus();

    /**
     * Does {@code content} exist in the running game or engine? Use this instead of checking version numbers.
     * ABSENT means "looked, not there"; anything uncertain is UNKNOWN.
     */
    ContentResult content(ContentId content);
}
