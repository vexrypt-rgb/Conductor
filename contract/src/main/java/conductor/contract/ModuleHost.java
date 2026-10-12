package conductor.contract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Runs many modules side by side on one engine core: leases with priority preemption, topics, services, and module
 * lifecycle with failure isolation. See docs/MODULE-PLATFORM.md.
 * <p>
 * Game thread only. The runtime calls {@link #tick()} once per in-world tick, after the core's own tick.
 */
public final class ModuleHost {

    /** Upper bound on topic deliveries per tick, so a publish loop cannot stall the game. */
    static final int MAX_DELIVERIES_PER_TICK = 10_000;

    private final ContractService core;
    private final NavigationOperations nav;
    private final DiagnosticSink sink;

    private final Map<ModuleId, Entry> modules = new LinkedHashMap<>();
    private final Map<ResourceKey, Slot> slots = new LinkedHashMap<>();
    private final Map<Topic<?>, List<Sub<?>>> subscribers = new HashMap<>();
    private final ArrayDeque<Delivery<?>> outbox = new ArrayDeque<>();
    private final Map<ServiceKey<?>, Provided> services = new HashMap<>();
    private final List<Fault> faults = new ArrayList<>();
    private long nextLeaseId = 1;

    public ModuleHost(ContractService core) {
        this.core = Objects.requireNonNull(core, "core");
        this.nav = core.navigation();
        this.sink = core.sink();
        for (ResourceKey r : Resources.ALL) {
            slots.put(r, new Slot());
        }
        nav.setControllerAttribution(this::attributedToMovementHolder);
        services.put(ModuleManagement.KEY, new Provided(ModuleId.CORE, new Management()));
    }

    /** The release policy the core should use for these modules: the longest windows any module asks for. */
    public static ReleasePolicy combinedPolicy(Collection<ConductorModule> modules) {
        OptionalLong window = OptionalLong.empty();
        OptionalLong uncertain = OptionalLong.empty();
        for (ConductorModule m : modules) {
            ReleasePolicy p = m.releasePolicy();
            if (p == null) {
                continue;
            }
            window = max(window, p.confirmStopWindowTicks());
            uncertain = max(uncertain, p.uncertainAfterTicks());
        }
        return new ReleasePolicy(window, uncertain);
    }

    private static OptionalLong max(OptionalLong a, OptionalLong b) {
        if (a.isEmpty()) {
            return b;
        }
        return b.isEmpty() ? a : OptionalLong.of(Math.max(a.getAsLong(), b.getAsLong()));
    }

    /** Register a module under {@code requestedId} (made unique if taken). Started on a later tick. */
    public ModuleId register(String requestedId, ConductorModule module) {
        Objects.requireNonNull(module, "module");
        String base = requestedId == null || requestedId.isBlank() ? module.getClass().getName() : requestedId;
        ModuleId id = new ModuleId(base);
        for (int n = 2; modules.containsKey(id) || id.equals(ModuleId.CORE); n++) {
            id = new ModuleId(base + "#" + n);
        }
        Entry e = new Entry(id, module);
        modules.put(id, e);
        moduleEvent(e, null, ModuleState.DISCOVERED, "registered");
        return id;
    }

    public ModuleManagement management() {
        return (ModuleManagement) services.get(ModuleManagement.KEY).service;
    }

    // ---- tick ----

    public void tick() {
        if (!core.onGameThread()) {
            sink.record(DiagnosticEvent.offThread("ModuleHost.tick", "ignored off the game thread"));
            return;
        }
        if (core.currentContext().isEmpty()) {
            return;
        }
        progressLeases();
        handleFaults();
        lifecycle();
        handleFaults();
        for (Entry e : List.copyOf(modules.values())) {
            if (e.state == ModuleState.RUNNING) {
                call(e, () -> e.module.onTick(e.context));
                handleFaults();
            }
        }
        flush();
        handleFaults();
    }

    private void lifecycle() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Entry e : List.copyOf(modules.values())) {
                Set<ServiceKey<?>> missing = missing(e);
                if (e.state == ModuleState.RUNNING && !missing.isEmpty()) {
                    stop(e, ModuleState.BLOCKED, "required service went away: " + names(missing));
                    changed = true;
                } else if ((e.state == ModuleState.DISCOVERED || e.state == ModuleState.BLOCKED)) {
                    if (missing.isEmpty()) {
                        evaluateFeatures(e);
                        setState(e, ModuleState.RUNNING, "started");
                        call(e, () -> e.module.onReady(e.context));
                        handleFaults();
                        changed = true;
                    } else if (e.state == ModuleState.DISCOVERED) {
                        setState(e, ModuleState.BLOCKED, "waiting for " + names(missing));
                    }
                }
            }
        }
    }

    /** Check each optional feature's content once per start; the module reads the result via its context. */
    private void evaluateFeatures(Entry e) {
        e.features.clear();
        List<ModuleFeature> declared;
        try {
            declared = e.module.features();
        } catch (RuntimeException ex) {
            faults.add(new Fault(e, ex));
            return;
        }
        if (declared == null) {
            return;
        }
        for (ModuleFeature f : declared) {
            List<ContentResult> checks = new ArrayList<>();
            for (ContentId c : f.requires()) {
                checks.add(core.content(c));
            }
            FeatureStatus status = FeatureStatus.evaluate(f, checks);
            e.features.put(f.name(), status);
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.MODULE_FEATURE, core.currentTick(),
                    core.currentContext().orElse(null), e.id.value(), f.name() + " " + (status.enabled() ? "ON" : "OFF") + ": " + status.reason()));
        }
    }

    private Set<ServiceKey<?>> missing(Entry e) {
        Set<ServiceKey<?>> required = safeRequires(e);
        Set<ServiceKey<?>> missing = new HashSet<>();
        for (ServiceKey<?> key : required) {
            Provided p = services.get(key);
            if (p == null || !(p.provider.equals(ModuleId.CORE) || isRunning(p.provider))) {
                missing.add(key);
            }
        }
        return missing;
    }

    private Set<ServiceKey<?>> safeRequires(Entry e) {
        try {
            Set<ServiceKey<?>> r = e.module.requires();
            return r == null ? Set.of() : r;
        } catch (RuntimeException ex) {
            faults.add(new Fault(e, ex));
            return Set.of();
        }
    }

    private boolean isRunning(ModuleId id) {
        Entry e = modules.get(id);
        return e != null && e.state == ModuleState.RUNNING;
    }

    // ---- leases ----

    private final class Slot {
        Lease holder;
        Lease pending;
    }

    private LeaseResult acquire(Entry e, ResourceKey resource, Priority priority, boolean scoped) {
        Slot slot = slots.get(resource);
        if (slot == null) {
            return denied(null, "unknown resource " + resource);
        }
        Lease held = slot.holder;
        if (held != null && held.holder().equals(e.id)) {
            return new LeaseResult(LeaseResult.Status.GRANTED, Optional.of(held), Optional.empty(), "already held");
        }
        boolean evidenceFree = !resource.evidenceBacked() || nav.resourceState() == ResourceState.AVAILABLE;
        Lease request = new Lease(nextLeaseId++, resource, e.id, priority, scoped);

        if (held == null) {
            if (evidenceFree && (slot.pending == null || slot.pending.priority().compareTo(priority) < 0)) {
                if (slot.pending != null) {
                    dropPending(slot, e.id);
                }
                grant(slot, request, null);
                return new LeaseResult(LeaseResult.Status.GRANTED, Optional.of(request), Optional.empty(), "free");
            }
            if (scoped) {
                return denied(null, evidenceFree ? "a higher-priority request is waiting" : "resource not shown idle");
            }
            return pend(slot, request, null, evidenceFree ? "waiting behind " + slot.pending.holder() : "resource not shown idle yet");
        }
        if (scoped || priority.compareTo(held.priority()) <= 0) {
            return denied(held.holder(), "held by " + held.holder() + " at " + held.priority());
        }
        // preemption
        if (slot.pending != null && slot.pending.priority().compareTo(priority) >= 0) {
            return denied(slot.pending.holder(), "a request at " + slot.pending.priority() + " is already preempting");
        }
        revoke(slot, e.id, "preempted by " + e.id + " at " + priority);
        if (!resource.evidenceBacked()) {
            if (slot.pending != null) {
                dropPending(slot, e.id);
            }
            grant(slot, request, null);
            return new LeaseResult(LeaseResult.Status.GRANTED, Optional.of(request), Optional.of(held.holder()), "preempted " + held.holder());
        }
        return pend(slot, request, held.holder(), "preempting " + held.holder() + "; granted once the resource is shown idle");
    }

    private LeaseResult pend(Slot slot, Lease request, ModuleId holder, String why) {
        if (slot.pending != null) {
            dropPending(slot, request.holder());
        }
        slot.pending = request;
        leaseEvent(LeaseEvent.Kind.PENDING, request, request.holder(), why);
        return new LeaseResult(LeaseResult.Status.PENDING, Optional.of(request), Optional.ofNullable(holder), why);
    }

    private static LeaseResult denied(ModuleId holder, String why) {
        return new LeaseResult(LeaseResult.Status.DENIED, Optional.empty(), Optional.ofNullable(holder), why);
    }

    private void grant(Slot slot, Lease lease, Entry notify) {
        slot.holder = lease;
        if (slot.pending != null && slot.pending.id() == lease.id()) {
            slot.pending = null;
        }
        leaseEvent(LeaseEvent.Kind.GRANTED, lease, lease.holder(), "granted");
        if (notify != null) {
            call(notify, () -> notify.module.onLeaseGranted(notify.context, lease));
        }
    }

    private void revoke(Slot slot, ModuleId by, String why) {
        Lease lease = slot.holder;
        slot.holder = null;
        leaseEvent(LeaseEvent.Kind.REVOKED, lease, by, why);
        Entry holder = modules.get(lease.holder());
        if (holder != null && holder.state == ModuleState.RUNNING) {
            call(holder, () -> holder.module.onLeaseRevoked(holder.context, lease, by));
        }
        if (lease.resource().equals(Resources.MOVEMENT)) {
            nav.cancelOwnedBy(lease.holder(), "MOVEMENT lease revoked (" + why + ")");
        }
    }

    private void dropPending(Slot slot, ModuleId cause) {
        Lease dropped = slot.pending;
        slot.pending = null;
        leaseEvent(LeaseEvent.Kind.PENDING_DROPPED, dropped, cause, "superseded");
    }

    private void release(Entry e, Lease lease) {
        Slot slot = slots.get(lease.resource());
        if (slot == null) {
            return;
        }
        if (slot.holder != null && slot.holder.id() == lease.id() && slot.holder.holder().equals(e.id)) {
            slot.holder = null;
            if (lease.resource().equals(Resources.MOVEMENT)) {
                nav.cancelOwnedBy(e.id, "MOVEMENT lease released");
            }
            leaseEvent(LeaseEvent.Kind.RELEASED, lease, e.id, "released by holder");
        } else if (slot.pending != null && slot.pending.id() == lease.id() && slot.pending.holder().equals(e.id)) {
            slot.pending = null;
            leaseEvent(LeaseEvent.Kind.PENDING_DROPPED, lease, e.id, "withdrawn");
        }
    }

    private void progressLeases() {
        Slot movement = slots.get(Resources.MOVEMENT);
        // a scoped (per-operation) lease ends with its operation
        if (movement.holder != null && movement.holder.scoped()
                && !nav.activeOwner().map(o -> o.equals(movement.holder.holder())).orElse(false)) {
            Lease ended = movement.holder;
            movement.holder = null;
            leaseEvent(LeaseEvent.Kind.RELEASED, ended, ended.holder(), "operation ended");
        }
        if (movement.holder == null && movement.pending != null && nav.resourceState() == ResourceState.AVAILABLE) {
            Lease next = movement.pending;
            Entry e = modules.get(next.holder());
            if (e != null && e.state == ModuleState.RUNNING) {
                grant(movement, next, e);
            } else {
                movement.pending = null;
            }
        }
    }

    private boolean attributedToMovementHolder(String controller) {
        Lease holder = slots.get(Resources.MOVEMENT).holder;
        if (holder == null) {
            return false;
        }
        Entry e = modules.get(holder.holder());
        return e != null && e.declaredControllers.contains(controller);
    }

    private List<Lease> heldBy(ModuleId id) {
        List<Lease> out = new ArrayList<>();
        for (Slot s : slots.values()) {
            if (s.holder != null && s.holder.holder().equals(id)) {
                out.add(s.holder);
            }
        }
        return out;
    }

    // ---- topics ----

    private record Sub<T>(Entry owner, Consumer<T> handler, boolean[] active) {
    }

    private record Delivery<T>(Topic<T> topic, T message) {
    }

    private <T> void publish(Topic<T> topic, T message) {
        outbox.add(new Delivery<>(topic, message));
    }

    private <T> Subscription subscribe(Entry e, Topic<T> topic, Consumer<T> handler) {
        boolean[] active = {true};
        Sub<T> sub = new Sub<>(e, handler, active);
        subscribers.computeIfAbsent(topic, t -> new ArrayList<>()).add(sub);
        e.subscriptions.add(sub);
        return () -> active[0] = false;
    }

    @SuppressWarnings("unchecked")
    private void flush() {
        int delivered = 0;
        while (!outbox.isEmpty() && delivered < MAX_DELIVERIES_PER_TICK) {
            Delivery<Object> d = (Delivery<Object>) outbox.poll();
            for (Sub<?> raw : List.copyOf(subscribers.getOrDefault(d.topic(), List.of()))) {
                Sub<Object> sub = (Sub<Object>) raw;
                if (sub.active()[0] && sub.owner().state == ModuleState.RUNNING) {
                    call(sub.owner(), () -> sub.handler().accept(d.message()));
                    delivered++;
                }
            }
            handleFaults();
        }
        if (!outbox.isEmpty()) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.MODULE_FAULT, core.currentTick(),
                    core.currentContext().orElse(null), "topics", outbox.size() + " messages deferred to next tick (delivery cap)"));
        }
        subscribers.values().forEach(list -> list.removeIf(s -> !s.active()[0]));
    }

    // ---- services ----

    private record Provided(ModuleId provider, Object service) {
    }

    private <T> void provide(Entry e, ServiceKey<T> key, T service) {
        Provided existing = services.get(key);
        if (existing != null && !existing.provider.equals(e.id)
                && (existing.provider.equals(ModuleId.CORE) || isRunning(existing.provider))) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.MODULE_FAULT, core.currentTick(),
                    core.currentContext().orElse(null), e.id.value(), "service " + key.name() + " already provided by " + existing.provider));
            return;
        }
        services.put(key, new Provided(e.id, Objects.requireNonNull(service, "service")));
        e.provided.add(key);
    }

    private <T> Optional<T> service(ServiceKey<T> key) {
        Provided p = services.get(key);
        if (p == null || !(p.provider.equals(ModuleId.CORE) || isRunning(p.provider))) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(p.service));
    }

    // ---- lifecycle helpers ----

    private record Fault(Entry entry, Throwable error) {
    }

    private void call(Entry e, Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException | LinkageError ex) {
            faults.add(new Fault(e, ex));
        }
    }

    private void handleFaults() {
        while (!faults.isEmpty()) {
            Fault f = faults.remove(0);
            if (f.entry.state == ModuleState.RUNNING || f.entry.state == ModuleState.BLOCKED || f.entry.state == ModuleState.DISCOVERED) {
                sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.MODULE_FAULT, core.currentTick(),
                        core.currentContext().orElse(null), f.entry.id.value(), String.valueOf(f.error)));
                stop(f.entry, ModuleState.FAILED, "threw " + f.error);
            }
        }
    }

    private void stop(Entry e, ModuleState to, String why) {
        if (e.state == ModuleState.RUNNING) {
            ModuleState before = e.state;
            e.state = to; // no more calls into it except onDisable
            try {
                e.module.onDisable(e.context);
            } catch (RuntimeException | LinkageError ignored) {
                // already stopping
            }
            e.state = before;
        }
        for (Slot slot : slots.values()) {
            if (slot.holder != null && slot.holder.holder().equals(e.id)) {
                Lease lease = slot.holder;
                slot.holder = null;
                if (lease.resource().equals(Resources.MOVEMENT)) {
                    nav.cancelOwnedBy(e.id, "module stopped: " + why);
                }
                leaseEvent(LeaseEvent.Kind.REVOKED, lease, ModuleId.CORE, "module stopped: " + why);
            }
            if (slot.pending != null && slot.pending.holder().equals(e.id)) {
                dropPending(slot, ModuleId.CORE);
            }
        }
        for (Sub<?> s : e.subscriptions) {
            s.active()[0] = false;
        }
        e.subscriptions.clear();
        for (ServiceKey<?> key : e.provided) {
            Provided p = services.get(key);
            if (p != null && p.provider.equals(e.id)) {
                services.remove(key);
            }
        }
        e.provided.clear();
        e.declaredControllers.clear();
        setState(e, to, why);
    }

    private void setState(Entry e, ModuleState to, String why) {
        ModuleState from = e.state;
        e.state = to;
        e.detail = why;
        moduleEvent(e, from, to, why);
    }

    private void moduleEvent(Entry e, ModuleState from, ModuleState to, String why) {
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.MODULE_STATE_CHANGED, core.currentTick(),
                core.currentContext().orElse(null), e.id.value(), from + " -> " + to + ": " + why));
        publish(Topics.MODULES, new ModuleEvent(e.id, from, to, why));
    }

    private void leaseEvent(LeaseEvent.Kind kind, Lease lease, ModuleId cause, String why) {
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.LEASE_CHANGED, core.currentTick(),
                core.currentContext().orElse(null), lease.resource().name(),
                kind + " lease " + lease.id() + " of " + lease.holder() + " at " + lease.priority() + " (cause " + cause + "): " + why));
        publish(Topics.LEASES, new LeaseEvent(kind, lease, cause, why));
    }

    private static String names(Set<ServiceKey<?>> keys) {
        List<String> n = new ArrayList<>();
        keys.forEach(k -> n.add(k.name()));
        n.sort(null);
        return n.toString();
    }

    // ---- per-module state and context ----

    private final class Entry {
        final ModuleId id;
        final ConductorModule module;
        final ModuleContext context;
        ModuleState state = ModuleState.DISCOVERED;
        String detail = "";
        final List<Sub<?>> subscriptions = new ArrayList<>();
        final List<ServiceKey<?>> provided = new ArrayList<>();
        final Set<String> declaredControllers = new HashSet<>();
        final Map<String, FeatureStatus> features = new LinkedHashMap<>();

        Entry(ModuleId id, ConductorModule module) {
            this.id = id;
            this.module = module;
            this.context = new Context(this);
        }

        Priority priority() {
            try {
                Priority p = module.priority();
                return p == null ? Priority.NORMAL : p;
            } catch (RuntimeException ex) {
                return Priority.NORMAL;
            }
        }
    }

    /** One module's view. Every call checks the game thread and that the module is running. */
    private final class Context implements ModuleContext {

        private final Entry e;

        Context(Entry e) {
            this.e = e;
        }

        private boolean usable(String what) {
            if (!core.onGameThread()) {
                sink.record(DiagnosticEvent.offThread(what + " " + e.id, "rejected before reading state"));
                return false;
            }
            return e.state == ModuleState.RUNNING;
        }

        @Override
        public ModuleId self() {
            return e.id;
        }

        @Override
        public DiscoveryResult discover() {
            return core.discover();
        }

        @Override
        public <T> ObservationResult<T> observe(ObservationKind<T> kind, FreshnessPolicy policy) {
            return core.observe(kind, policy);
        }

        @Override
        public <T> ObservationResult<T> revalidate(Observation<T> observation, FreshnessPolicy policy) {
            return core.revalidate(observation, policy);
        }

        @Override
        public ResourceStatus resourceStatus() {
            return core.resourceStatus();
        }

        @Override
        public ContentResult content(ContentId content) {
            return core.content(content);
        }

        @Override
        public FeatureStatus feature(String name) {
            FeatureStatus s = e.features.get(name);
            return s != null ? s : new FeatureStatus(name, false, List.of(), "not declared by this module");
        }

        @Override
        public Map<String, FeatureStatus> features() {
            return Map.copyOf(e.features);
        }

        @Override
        public OperationResult requestTravel(NavigationGoal goal) {
            if (!core.onGameThread()) {
                return core.requestTravel(e.id, goal); // records and rejects OFF_THREAD
            }
            if (e.state != ModuleState.RUNNING) {
                return OperationResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, null, "module is " + e.state);
            }
            Slot movement = slots.get(Resources.MOVEMENT);
            boolean holds = movement.holder != null && movement.holder.holder().equals(e.id);
            Lease scoped = null;
            if (!holds) {
                LeaseResult r = ModuleHost.this.acquire(e, Resources.MOVEMENT, e.priority(), true);
                handleFaults();
                if (!r.isGranted()) {
                    RejectionReason reason = r.holder().isPresent() ? RejectionReason.LEASE_HELD : RejectionReason.RESOURCE_NOT_RELEASED;
                    return OperationResult.rejected(reason, null, "MOVEMENT: " + r.detail());
                }
                scoped = r.lease().orElseThrow();
            }
            OperationResult result = core.requestTravel(e.id, goal);
            if (!result.isAccepted() && scoped != null && movement.holder != null && movement.holder.id() == scoped.id()) {
                movement.holder = null;
                leaseEvent(LeaseEvent.Kind.RELEASED, scoped, e.id, "travel not accepted: " + result.rejection().orElse(null));
            }
            return result;
        }

        @Override
        public OperationResult cancel(OperationId operation) {
            return core.cancel(e.id, operation);
        }

        @Override
        public OperationResult operationStatus(OperationId operation) {
            return core.operationStatus(e.id, operation);
        }

        @Override
        public LeaseResult acquire(ResourceKey resource) {
            return acquire(resource, e.priority());
        }

        @Override
        public LeaseResult acquire(ResourceKey resource, Priority priority) {
            if (!usable("acquire")) {
                return denied(null, "module not running or off the game thread");
            }
            LeaseResult r = ModuleHost.this.acquire(e, Objects.requireNonNull(resource), Objects.requireNonNull(priority), false);
            handleFaults();
            return r;
        }

        @Override
        public void release(Lease lease) {
            if (usable("release") && lease != null) {
                ModuleHost.this.release(e, lease);
                handleFaults();
            }
        }

        @Override
        public List<Lease> leases() {
            if (!core.onGameThread()) {
                return List.of();
            }
            List<Lease> all = new ArrayList<>();
            slots.values().forEach(s -> {
                if (s.holder != null) {
                    all.add(s.holder);
                }
            });
            return all;
        }

        @Override
        public <T> void publish(Topic<T> topic, T message) {
            if (usable("publish")) {
                ModuleHost.this.publish(Objects.requireNonNull(topic), Objects.requireNonNull(message));
            }
        }

        @Override
        public <T> Subscription subscribe(Topic<T> topic, Consumer<T> handler) {
            if (!usable("subscribe")) {
                return () -> { };
            }
            return ModuleHost.this.subscribe(e, Objects.requireNonNull(topic), Objects.requireNonNull(handler));
        }

        @Override
        public <T> void provide(ServiceKey<T> key, T service) {
            if (usable("provide")) {
                ModuleHost.this.provide(e, Objects.requireNonNull(key), service);
            }
        }

        @Override
        public <T> Optional<T> service(ServiceKey<T> key) {
            if (!core.onGameThread()) {
                return Optional.empty();
            }
            return ModuleHost.this.service(Objects.requireNonNull(key));
        }

        @Override
        public void declareEngineProcesses(Set<String> processes) {
            if (usable("declareEngineProcesses")) {
                e.declaredControllers.clear();
                e.declaredControllers.addAll(processes);
            }
        }
    }

    // ---- management service ----

    private final class Management implements ModuleManagement {

        @Override
        public List<ModuleInfo> modules() {
            List<ModuleInfo> out = new ArrayList<>();
            for (Entry e : modules.values()) {
                List<String> provides = new ArrayList<>();
                e.provided.forEach(k -> provides.add(k.name()));
                List<String> requires = new ArrayList<>();
                safeRequires(e).forEach(k -> requires.add(k.name()));
                out.add(new ModuleInfo(e.id, e.state, e.priority(), heldBy(e.id), provides, requires, e.detail));
            }
            return out;
        }

        @Override
        public boolean disable(ModuleId module, String reason) {
            Entry e = modules.get(module);
            if (!core.onGameThread() || e == null || e.state == ModuleState.DISABLED) {
                return false;
            }
            stop(e, ModuleState.DISABLED, "disabled: " + reason);
            handleFaults();
            return true;
        }

        @Override
        public boolean enable(ModuleId module) {
            Entry e = modules.get(module);
            if (!core.onGameThread() || e == null || (e.state != ModuleState.DISABLED && e.state != ModuleState.FAILED)) {
                return false;
            }
            setState(e, ModuleState.DISCOVERED, "enabled");
            return true;
        }
    }

    /** For tests and the runtime: current state of a module. */
    public Optional<ModuleState> state(ModuleId id) {
        Entry e = modules.get(id);
        return e == null ? Optional.empty() : Optional.of(e.state);
    }

    /** For tests and the runtime: the context handed to a module. */
    Optional<ModuleContext> contextOf(ModuleId id) {
        Entry e = modules.get(id);
        return e == null ? Optional.empty() : Optional.of(e.context);
    }
}
