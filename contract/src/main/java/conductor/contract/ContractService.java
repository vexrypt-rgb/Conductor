package conductor.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Enforces the contract rules over a {@link RuntimeIntegration}. Each call checks, in order:
 * <ol>
 *   <li>Game thread. If not, reject before reading anything, and record the violation in the sink, which is safe
 *       from any thread.</li>
 *   <li>Arguments and freshness policy. A missing policy is rejected without sampling.</li>
 *   <li>Current context. Observations from any other context are rejected.</li>
 *   <li>Freshness, evaluated separately from availability.</li>
 * </ol>
 */
public final class ContractService implements Conductor {

    private final RuntimeIntegration runtime;
    private final ContextTracker contexts;
    private final DiagnosticSink sink;
    private final NavigationOperations navigation;

    /** Without a release policy: the resource is never released, so navigation is never granted. */
    public ContractService(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink) {
        this(runtime, contexts, sink, ReleasePolicy.none());
    }

    public ContractService(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink, ReleasePolicy policy) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.navigation = new NavigationOperations(runtime, contexts, sink, Objects.requireNonNull(policy, "policy"));
        contexts.addInvalidationListener(navigation::onContextInvalidated);
    }

    /**
     * Called by the runtime integration once per in-world game tick, on the game thread, after the context has
     * been updated. Not part of the consumer binding.
     */
    public void tick() {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("tick", "ignored off the game thread"));
            return;
        }
        navigation.tick();
    }

    @Override
    public ContentResult content(ContentId content) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("content", "rejected before reading the game"));
            return new ContentResult(content, ContentResult.Presence.UNKNOWN, "call was not on the game thread");
        }
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }
        try {
            ContentResult r = runtime.content(content);
            return r != null ? r : new ContentResult(content, ContentResult.Presence.UNKNOWN, "runtime returned nothing");
        } catch (RuntimeException | LinkageError e) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, runtime.currentTick(),
                    contexts.current().orElse(null), "content " + content, String.valueOf(e)));
            return new ContentResult(content, ContentResult.Presence.UNKNOWN, "lookup failed: " + e);
        }
    }

    @Override
    public ResourceStatus resourceStatus() {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("resourceStatus", "rejected before reading state"));
            return new ResourceStatus(RejectionReason.OFF_THREAD, null, -1, "call was not on the game thread");
        }
        return navigation.resourceStatus();
    }

    // ---- host API (module platform): same rules, attributed to a module ----

    OperationResult requestTravel(ModuleId module, NavigationGoal goal) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("requestTravel " + module, "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.requestTravel(module, goal);
    }

    OperationResult requestCombat(ModuleId module, CombatGoal goal) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("requestCombat " + module, "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.requestCombat(module, goal);
    }

    OperationResult cancel(ModuleId module, OperationId operation) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("cancel " + module, "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.cancel(module, operation);
    }

    OperationResult operationStatus(ModuleId module, OperationId operation) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("operationStatus " + module, "rejected before reading state"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.status(module, operation);
    }

    boolean onGameThread() {
        return runtime.isGameThread();
    }

    long currentTick() {
        return runtime.currentTick();
    }

    DiagnosticSink sink() {
        return sink;
    }

    Optional<ContextId> currentContext() {
        return contexts.current();
    }

    NavigationOperations navigation() {
        return navigation;
    }

    @Override
    public OperationResult requestTravel(NavigationGoal goal) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("requestTravel", "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.requestTravel(goal);
    }

    @Override
    public OperationResult requestCombat(CombatGoal goal) {
        return requestCombat(ModuleId.CORE, goal);
    }

    @Override
    public OperationResult cancel(OperationId operation) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("cancel", "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.cancel(operation);
    }

    @Override
    public OperationResult operationStatus(OperationId operation) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("operationStatus", "rejected before reading state"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.status(operation);
    }

    @Override
    public DiscoveryResult discover() {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("discover", "rejected before reading runtime state"));
            return new DiscoveryResult(RejectionReason.OFF_THREAD, CONTRACT_VERSION, Optional.empty(), -1, List.of());
        }
        return new DiscoveryResult(null, CONTRACT_VERSION, contexts.current(), runtime.currentTick(), runtime.capabilities());
    }

    @Override
    public <T> ObservationResult<T> observe(ObservationKind<T> kind, FreshnessPolicy policy) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("observe " + kind, "rejected before sampling"));
            return ObservationResult.rejected(RejectionReason.OFF_THREAD, null, null, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        if (kind == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "observe", null, null, tick, "kind is null");
        }
        if (policy == null) {
            return reject(RejectionReason.NO_FRESHNESS_POLICY, "observe " + kind, null, null, tick, "no freshness policy; not sampled");
        }
        Optional<ContextId> current = contexts.current();
        if (current.isEmpty()) {
            return reject(RejectionReason.NO_CURRENT_CONTEXT, "observe " + kind, null, null, tick, "no current context; not sampled");
        }
        if (!supports(kind)) {
            return reject(RejectionReason.UNSUPPORTED_KIND, "observe " + kind, null, null, tick, "runtime does not provide " + kind);
        }
        long nanos = runtime.nanoTime();
        Observation<T> observation = new Observation<>(kind, sampleSafely(kind, current.get(), tick), current.get(), tick, nanos);
        return evaluate(observation, policy, current.get(), tick, nanos, "observe " + kind);
    }

    @Override
    public <T> ObservationResult<T> revalidate(Observation<T> observation, FreshnessPolicy policy) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("revalidate", "rejected before reading context"));
            return ObservationResult.rejected(RejectionReason.OFF_THREAD, observation, null, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        if (observation == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "revalidate", null, null, tick, "observation is null");
        }
        String subject = "revalidate " + observation.kind();
        if (policy == null) {
            return reject(RejectionReason.NO_FRESHNESS_POLICY, subject, observation, null, tick, "no freshness policy");
        }
        Optional<ContextId> current = contexts.current();
        if (current.isEmpty()) {
            return reject(RejectionReason.CONTEXT_INVALIDATED, subject, observation, null, tick,
                    "no current context; observation was from " + observation.context());
        }
        return evaluate(observation, policy, current.get(), tick, runtime.nanoTime(), subject);
    }

    private <T> ObservationResult<T> evaluate(Observation<T> observation, FreshnessPolicy policy, ContextId current,
                                              long tick, long nanos, String subject) {
        if (!observation.context().equals(current)) {
            return reject(RejectionReason.CONTEXT_INVALIDATED, subject, observation, null, tick,
                    "observation from " + observation.context() + ", current is " + current);
        }
        Freshness freshness = policy.evaluate(observation.sampledTick(), observation.sampledNanos(), tick, nanos);
        if (freshness == Freshness.STALE) {
            return reject(RejectionReason.STALE, subject, observation, freshness, tick,
                    "sampled at tick " + observation.sampledTick() + ", now " + tick + ", policy " + policy);
        }
        return ObservationResult.accepted(observation);
    }

    private boolean supports(ObservationKind<?> kind) {
        for (CapabilityDescriptor capability : runtime.capabilities()) {
            if (capability.observationKinds().contains(kind)) {
                return true;
            }
        }
        return false;
    }

    private <T> Sample<T> sampleSafely(ObservationKind<T> kind, ContextId context, long tick) {
        try {
            Sample<T> sample = runtime.sample(kind);
            if (sample != null) {
                return sample;
            }
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, context, kind.name(), "runtime returned no sample"));
            return Sample.unknown("runtime returned no sample");
        } catch (RuntimeException | LinkageError e) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, context, kind.name(), String.valueOf(e)));
            return Sample.unknown("sampling failed: " + e);
        }
    }

    private <T> ObservationResult<T> reject(RejectionReason reason, String subject, Observation<T> observation,
                                            Freshness freshness, long tick, String detail) {
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.OBSERVATION_REJECTED, tick,
                contexts.current().orElse(null), subject, reason + ": " + detail));
        return ObservationResult.rejected(reason, observation, freshness, detail);
    }

    // ---- world queries ----

    @Override
    public BlockReading blockAt(BlockPoint at) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("blockAt", "rejected before reading the world"));
            return new BlockReading(RejectionReason.OFF_THREAD, at, Optional.empty(), -1, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        if (at == null) {
            return new BlockReading(RejectionReason.INVALID_ARGUMENT, null, Optional.empty(), tick, "position is null");
        }
        if (contexts.current().isEmpty()) {
            return new BlockReading(RejectionReason.NO_CURRENT_CONTEXT, at, Optional.empty(), tick, "not in a world");
        }
        Optional<WorldPort> world = runtime.world();
        if (world.isEmpty()) {
            return new BlockReading(RejectionReason.NO_ACTIONS, at, Optional.empty(), tick, "runtime offers no block access");
        }
        try {
            Optional<String> block = world.get().blockAt(at);
            return new BlockReading(null, at, block, tick, block.isPresent() ? "" : "chunk not loaded");
        } catch (RuntimeException | LinkageError e) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, contexts.current().orElse(null), "blockAt", e.toString()));
            return new BlockReading(null, at, Optional.empty(), tick, "read failed: " + e);
        }
    }

    static final int MAX_SEARCH_RADIUS = 16;
    static final int MAX_SEARCH_RESULTS = 256;

    @Override
    public BlockSearch findBlocks(java.util.Set<String> blockIds, int radius, int limit) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("findBlocks", "rejected before reading the world"));
            return new BlockSearch(RejectionReason.OFF_THREAD, java.util.List.of(), -1, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        if (blockIds == null || blockIds.isEmpty() || radius < 1 || radius > MAX_SEARCH_RADIUS || limit < 1 || limit > MAX_SEARCH_RESULTS) {
            return new BlockSearch(RejectionReason.INVALID_ARGUMENT, java.util.List.of(), tick,
                    "need block ids, radius 1-" + MAX_SEARCH_RADIUS + ", limit 1-" + MAX_SEARCH_RESULTS);
        }
        if (contexts.current().isEmpty()) {
            return new BlockSearch(RejectionReason.NO_CURRENT_CONTEXT, java.util.List.of(), tick, "not in a world");
        }
        Optional<WorldPort> world = runtime.world();
        if (world.isEmpty()) {
            return new BlockSearch(RejectionReason.NO_ACTIONS, java.util.List.of(), tick, "runtime offers no block access");
        }
        Sample<BlockPoint> here;
        try {
            here = runtime.sample(ObservationKinds.PLAYER_POSITION);
        } catch (RuntimeException | LinkageError e) {
            here = Sample.unknown(e.toString());
        }
        if (here == null || here.availability() != Availability.OBSERVED) {
            return new BlockSearch(RejectionReason.TARGET_UNAVAILABLE, java.util.List.of(), tick, "player position not observed");
        }
        try {
            return new BlockSearch(null, world.get().findBlocks(java.util.Set.copyOf(blockIds), here.value(), radius, limit), tick, "");
        } catch (RuntimeException | LinkageError e) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, contexts.current().orElse(null), "findBlocks", e.toString()));
            return new BlockSearch(null, java.util.List.of(), tick, "search failed: " + e);
        }
    }

    // ---- one-shot actions ----

    private long lastChatTick = Long.MIN_VALUE;

    @Override
    public ActionResult selectHotbar(int slot) {
        return selectHotbar(ModuleId.CORE, slot);
    }

    ActionResult selectHotbar(ModuleId module, int slot) {
        return act(module, ActionKind.SELECT_HOTBAR, slot < 0 || slot > 8 ? "slot must be 0-8" : null,
                p -> p.selectHotbar(slot), "slot " + slot);
    }

    @Override
    public ActionResult look(double yaw, double pitch) {
        return look(ModuleId.CORE, yaw, pitch);
    }

    ActionResult look(ModuleId module, double yaw, double pitch) {
        String bad = !Double.isFinite(yaw) || !Double.isFinite(pitch) || pitch < -90 || pitch > 90
                ? "yaw must be finite and pitch -90..90" : null;
        return act(module, ActionKind.LOOK, bad, p -> p.look(yaw, pitch), String.format("yaw %.1f pitch %.1f", yaw, pitch));
    }

    @Override
    public ActionResult attack(int entityId) {
        return attack(ModuleId.CORE, entityId);
    }

    ActionResult attack(ModuleId module, int entityId) {
        return act(module, ActionKind.ATTACK, null, p -> p.attack(entityId), "entity " + entityId);
    }

    @Override
    public ActionResult useItem(boolean offhand) {
        return useItem(ModuleId.CORE, offhand);
    }

    ActionResult useItem(ModuleId module, boolean offhand) {
        return act(module, ActionKind.USE_ITEM, null, p -> p.useItem(offhand), offhand ? "offhand" : "main hand");
    }

    @Override
    public ActionResult sendChat(String message) {
        return sendChat(ModuleId.CORE, message);
    }

    ActionResult sendChat(ModuleId module, String message) {
        return act(module, ActionKind.SEND_CHAT, chatProblem(message), p -> p.sendChat(message),
                message == null ? "" : message.length() + " characters");
    }

    static String chatProblem(String message) {
        if (message == null || message.isBlank()) {
            return "message is empty";
        }
        if (message.length() > 256) {
            return "message is longer than 256 characters";
        }
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c < 32 || c == 127 || c == '\u00a7') {
                return "message contains a character servers reject (control code or section sign)";
            }
        }
        return null;
    }

    /** Host API: the same action, attributed to {@code module} (its lease was checked by the host). */
    ActionResult act(ModuleId module, ActionKind kind, String invalid,
                     java.util.function.Function<ActionPort, Optional<String>> call, String what) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread(kind + " " + module, "rejected before any game call"));
            return ActionResult.rejected(RejectionReason.OFF_THREAD, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        ActionResult result = checkAndRun(kind, invalid, call, tick);
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.ACTION, tick, contexts.current().orElse(null),
                kind + " " + module, what + ": " + (result.isAccepted() ? "done" : result.rejection() + " " + result.detail())));
        return result;
    }

    private ActionResult checkAndRun(ActionKind kind, String invalid,
                                     java.util.function.Function<ActionPort, Optional<String>> call, long tick) {
        if (invalid != null) {
            return ActionResult.rejected(RejectionReason.INVALID_ARGUMENT, invalid);
        }
        if (contexts.current().isEmpty()) {
            return ActionResult.rejected(RejectionReason.NO_CURRENT_CONTEXT, "not in a world");
        }
        Optional<ActionPort> port = runtime.actions();
        if (port.isEmpty()) {
            return ActionResult.rejected(RejectionReason.NO_ACTIONS, "runtime offers no actions");
        }
        if (kind.engineConflict() && navigation.resourceState() != ResourceState.AVAILABLE) {
            return ActionResult.rejected(RejectionReason.RESOURCE_NOT_RELEASED,
                    "the engine may be steering and would overwrite this; wait until resourceStatus() is AVAILABLE");
        }
        if (kind == ActionKind.SEND_CHAT) {
            if (lastChatTick == tick) {
                return ActionResult.rejected(RejectionReason.RATE_LIMITED, "one chat message per tick");
            }
        }
        Optional<String> problem;
        try {
            problem = call.apply(port.get());
        } catch (RuntimeException | LinkageError e) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, contexts.current().orElse(null), kind.name(), e.toString()));
            return ActionResult.rejected(RejectionReason.ENGINE_DECLINED, "game call threw: " + e);
        }
        if (problem.isPresent()) {
            return ActionResult.rejected(kind == ActionKind.ATTACK ? RejectionReason.TARGET_UNAVAILABLE : RejectionReason.ENGINE_DECLINED, problem.get());
        }
        if (kind == ActionKind.SEND_CHAT) {
            lastChatTick = tick;
        }
        return ActionResult.done(kind.name());
    }
}
