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

    private static final java.util.logging.Logger FAULT_LOG = java.util.logging.Logger.getLogger("conductor");


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

    /** Default chat-command prefix: {@code +kit} runs the {@code kit} command. */
    public static final String DEFAULT_COMMAND_PREFIX = "+";

    private static final class Command {
        private final Entry owner;
        private final String help;
        private final Consumer<CommandCall> handler;

        public Command(Entry owner, String help, Consumer<CommandCall> handler) {
            this.owner = owner;
            this.help = help;
            this.handler = handler;
        }

        public Entry owner() { return owner; }

        public String help() { return help; }

        public Consumer<CommandCall> handler() { return handler; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Command)) return false;
            Command other = (Command) o;
            return java.util.Arrays.deepEquals(new Object[] {this.owner, this.help, this.handler}, new Object[] {other.owner, other.help, other.handler});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.owner, this.help, this.handler}); }

        @Override public String toString() { return "Command[" + "owner=" + owner + ", " + "help=" + help + ", " + "handler=" + handler + "]"; }
    }

    /** Command name (lower case) to command; owner null for Conductor's own. */
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final List<String> typedCommands = new ArrayList<>();
    private final String commandPrefix;

    public ModuleHost(ContractService core) {
        this(core, DEFAULT_COMMAND_PREFIX);
    }

    /** @param commandPrefix chat prefix for commands; null for no chat commands */
    public ModuleHost(ContractService core, String commandPrefix) {
        this.core = Objects.requireNonNull(core, "core");
        this.commandPrefix = commandPrefix;
        this.nav = core.navigation();
        this.sink = core.sink();
        for (ResourceKey r : Resources.ALL) {
            slots.put(r, new Slot());
        }
        nav.setControllerAttribution(this::attributedToMovementHolder);
        services.put(ModuleManagement.KEY, new Provided(ModuleId.CORE, new Management()));
        builtInCommands();
        if (commandPrefix != null) {
            core.interceptCommands(commandPrefix);
        }
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
        if (!a.isPresent()) {
            return b;
        }
        return !b.isPresent() ? a : OptionalLong.of(Math.max(a.getAsLong(), b.getAsLong()));
    }

    /** Register a module under {@code requestedId} (made unique if taken). Started on a later tick. */
    public ModuleId register(String requestedId, ConductorModule module) {
        Objects.requireNonNull(module, "module");
        String base = requestedId == null || requestedId.trim().isEmpty() ? module.getClass().getName() : requestedId;
        ModuleId id = new ModuleId(base);
        for (int n = 2; modules.containsKey(id) || id.equals(ModuleId.CORE); n++) {
            id = new ModuleId(base + "#" + n);
        }
        Entry e = new Entry(id, module);
        modules.put(id, e);
        loadSettings(e);
        moduleEvent(e, null, ModuleState.DISCOVERED, "registered");
        return id;
    }

    private SettingsStore store = SettingsStore.IN_MEMORY;

    /** Where module settings are kept; set before registering modules. */
    public void useSettingsStore(SettingsStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    private void loadSettings(Entry e) {
        List<Setting<?>> declared;
        try {
            declared = e.module.settings();
        } catch (RuntimeException | LinkageError ex) {
            declared = Jdk8.listOf();
            settingEvent(e, "settings() threw " + ex);
        }
        Map<String, String> saved;
        try {
            saved = store.load(e.id);
        } catch (RuntimeException ex) {
            saved = Jdk8.mapOf();
            settingEvent(e, "could not load saved settings: " + ex);
        }
        for (Setting<?> setting : declared == null ? Jdk8.<Setting<?>>listOf() : declared) {
            if (setting == null || e.settings.containsKey(setting.name())) {
                settingEvent(e, "setting " + setting + " is null or declared twice; ignored");
                continue;
            }
            e.settings.put(setting.name(), setting);
            String text = saved.get(setting.name());
            Optional<?> value = text == null ? Optional.empty() : setting.parse(text);
            if (text != null && !value.isPresent()) {
                settingEvent(e, setting.name() + ": saved value '" + text + "' is not valid; using the default");
            }
            e.values.put(setting.name(), value.isPresent() ? value.get() : setting.defaultValue());
        }
    }

    private void settingEvent(Entry e, String why) {
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.SETTING, core.currentTick(),
                core.currentContext().orElse(null), e.id.value(), why));
    }

    @SuppressWarnings("unchecked")
    private <T> String formatted(Setting<T> setting, Object value) {
        return setting.format((T) value);
    }

    /** Change a setting; returns false for an unknown setting. Saves and announces the change. */
    private boolean changeSetting(Entry e, String name, Object value) {
        Setting<?> setting = e.settings.get(name);
        if (setting == null) {
            return false;
        }
        e.values.put(name, value);
        Map<String, String> all = new LinkedHashMap<>();
        e.settings.forEach((n, st) -> all.put(n, formatted(st, e.values.get(n))));
        try {
            store.save(e.id, all);
        } catch (RuntimeException ex) {
            settingEvent(e, "could not save: " + ex);
        }
        String text = formatted(setting, value);
        settingEvent(e, name + " = " + text);
        publish(Topics.SETTINGS, new SettingChanged(e.id, name, text));
        return true;
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
        if (!core.currentContext().isPresent()) {
            return;
        }
        progressLeases();
        handleFaults();
        events();
        lifecycle();
        handleFaults();
        runTypedCommands();
        for (Entry e : Jdk8.copyList(modules.values())) {
            if (e.state == ModuleState.RUNNING) {
                call(e, () -> e.module.onTick(e.context));
                handleFaults();
            }
        }
        flush();
        handleFaults();
    }

    // ---- game events and commands ----

    private void events() {
        for (GameEvent event : core.drainEvents()) {
            if (event instanceof GameEvent.BlockChange) {
                publish(Topics.BLOCK_CHANGES, (GameEvent.BlockChange) event);
            } else if (event instanceof GameEvent.ChunkChange) {
                publish(Topics.CHUNKS, (GameEvent.ChunkChange) event);
            } else if (event instanceof GameEvent.PlayerDeath) {
                publish(Topics.DEATHS, (GameEvent.PlayerDeath) event);
            } else if (event instanceof GameEvent.ChatMessage) {
                publish(Topics.CHAT, (GameEvent.ChatMessage) event);
            } else if (event instanceof GameEvent.CommandLine) {
                typedCommands.add(((GameEvent.CommandLine) event).line());
            }
        }
    }

    private void runTypedCommands() {
        for (String line : Jdk8.copyList(typedCommands)) {
            typedCommands.remove(0);
            String body = commandPrefix != null && line.startsWith(commandPrefix) ? line.substring(commandPrefix.length()) : line;
            List<String> words = Jdk8.listOf(body.trim().split("\\s+"));
            String name = words.get(0).toLowerCase(java.util.Locale.ROOT);
            CommandCall call = new CommandCall(name, words.subList(1, words.size()), line);
            Command command = commands.get(name);
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.ACTION, core.currentTick(),
                    core.currentContext().orElse(null), "command", line + (command == null ? " (unknown)" : "")));
            if (command == null) {
                say("unknown command " + name + "; " + commandPrefix + "help lists them");
            } else if (command.owner() == null) {
                command.handler().accept(call);
            } else if (command.owner().state != ModuleState.RUNNING) {
                say(name + " belongs to " + command.owner().id.value() + ", which is " + command.owner().state);
            } else {
                call(command.owner(), () -> command.handler().accept(call));
                handleFaults();
            }
        }
    }

    private void say(String text) {
        core.showMessage(ModuleId.CORE, text);
    }

    private Subscription addCommand(Entry owner, String name, String help, Consumer<CommandCall> handler) {
        String key = Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
        if (!key.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("command names are 1-32 of a-z 0-9 _ -: " + name);
        }
        Command existing = commands.get(key);
        if (existing != null) {
            throw new IllegalArgumentException("command " + key + " is taken by "
                    + (existing.owner() == null ? "Conductor" : existing.owner().id.value()));
        }
        Command command = new Command(owner, help == null ? "" : help, Objects.requireNonNull(handler, "handler"));
        commands.put(key, command);
        return () -> commands.remove(key, command);
    }

    private void builtInCommands() {
        addCommand(null, "help", "list commands", call -> commands.forEach((name, c) ->
                say(commandPrefix + name + " - " + c.help() + (c.owner() == null ? "" : " (" + c.owner().id.value() + ")"))));
        addCommand(null, "modules", "list modules and their state", call ->
                modules.values().forEach(e -> say(e.id.value() + ": " + e.state)));
        addCommand(null, "disable", "disable <module>: stop a module", call -> toggle(call, false));
        addCommand(null, "enable", "enable <module>: start a disabled module", call -> toggle(call, true));
        addCommand(null, "settings", "settings <module>: show a module's settings", call -> {
            if (call.args().size() != 1 || !modules.containsKey(new ModuleId(call.args().get(0)))) {
                say("usage: " + commandPrefix + "settings <module>  (" + commandPrefix + "modules lists them)");
                return;
            }
            List<SettingInfo> list = management().settings(new ModuleId(call.args().get(0)));
            if (list.isEmpty()) {
                say(call.args().get(0) + " has no settings");
            }
            list.forEach(i -> say(i.name() + " = " + i.value() + " (default " + i.defaultValue() + ") " + i.description()));
        });
        addCommand(null, "set", "set <module> <setting> <value>: change a setting", call -> {
            if (call.args().size() < 3) {
                say("usage: " + commandPrefix + "set <module> <setting> <value>");
                return;
            }
            String value = String.join(" ", call.args().subList(2, call.args().size()));
            boolean ok = management().setSetting(new ModuleId(call.args().get(0)), call.args().get(1), value);
            say(ok ? call.args().get(0) + " " + call.args().get(1) + " = " + value : "not changed: unknown module or setting, or value out of range");
        });
    }

    private void toggle(CommandCall call, boolean enable) {
        if (call.args().size() != 1) {
            say("usage: " + commandPrefix + call.name() + " <module>");
            return;
        }
        ModuleId id = new ModuleId(call.args().get(0));
        boolean done = enable ? new Management().enable(id) : new Management().disable(id, "chat command");
        say(id.value() + (done ? (enable ? " enabled" : " disabled") : ": no change (unknown module, or already in that state)"));
    }

    private void lifecycle() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Entry e : Jdk8.copyList(modules.values())) {
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
            return r == null ? Jdk8.setOf() : r;
        } catch (RuntimeException ex) {
            faults.add(new Fault(e, ex));
            return Jdk8.setOf();
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

    private static final class Sub<T> {
        private final Entry owner;
        private final Consumer<T> handler;
        private final boolean[] active;

        public Sub(Entry owner, Consumer<T> handler, boolean[] active) {
            this.owner = owner;
            this.handler = handler;
            this.active = active;
        }

        public Entry owner() { return owner; }

        public Consumer<T> handler() { return handler; }

        public boolean[] active() { return active; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Sub)) return false;
            Sub<?> other = (Sub<?>) o;
            return java.util.Arrays.deepEquals(new Object[] {this.owner, this.handler, this.active}, new Object[] {other.owner, other.handler, other.active});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.owner, this.handler, this.active}); }

        @Override public String toString() { return "Sub[" + "owner=" + owner + ", " + "handler=" + handler + ", " + "active=" + active + "]"; }
    }

    private static final class Delivery<T> {
        private final Topic<T> topic;
        private final T message;

        public Delivery(Topic<T> topic, T message) {
            this.topic = topic;
            this.message = message;
        }

        public Topic<T> topic() { return topic; }

        public T message() { return message; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Delivery)) return false;
            Delivery<?> other = (Delivery<?>) o;
            return java.util.Arrays.deepEquals(new Object[] {this.topic, this.message}, new Object[] {other.topic, other.message});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.topic, this.message}); }

        @Override public String toString() { return "Delivery[" + "topic=" + topic + ", " + "message=" + message + "]"; }
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
            for (Sub<?> raw : Jdk8.copyList(subscribers.getOrDefault(d.topic(), Jdk8.listOf()))) {
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

    private static final class Provided {
        private final ModuleId provider;
        private final Object service;

        public Provided(ModuleId provider, Object service) {
            this.provider = provider;
            this.service = service;
        }

        public ModuleId provider() { return provider; }

        public Object service() { return service; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Provided)) return false;
            Provided other = (Provided) o;
            return java.util.Arrays.deepEquals(new Object[] {this.provider, this.service}, new Object[] {other.provider, other.service});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.provider, this.service}); }

        @Override public String toString() { return "Provided[" + "provider=" + provider + ", " + "service=" + service + "]"; }
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

    private static final class Fault {
        private final Entry entry;
        private final Throwable error;

        public Fault(Entry entry, Throwable error) {
            this.entry = entry;
            this.error = error;
        }

        public Entry entry() { return entry; }

        public Throwable error() { return error; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Fault)) return false;
            Fault other = (Fault) o;
            return java.util.Arrays.deepEquals(new Object[] {this.entry, this.error}, new Object[] {other.entry, other.error});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.entry, this.error}); }

        @Override public String toString() { return "Fault[" + "entry=" + entry + ", " + "error=" + error + "]"; }
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
                // also to the log: a stopped module must be visible without reading the sink
                FAULT_LOG.log(java.util.logging.Level.WARNING, "module " + f.entry.id.value() + " stopped (FAILED)", f.error);
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
        commands.values().removeIf(c -> c.owner() == e);
        core.clearOverlay(e.id);
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
        final Map<String, Setting<?>> settings = new LinkedHashMap<>();
        final Map<String, Object> values = new LinkedHashMap<>();

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
            return s != null ? s : new FeatureStatus(name, false, Jdk8.listOf(), "not declared by this module");
        }

        @Override
        public Map<String, FeatureStatus> features() {
            return Jdk8.copyMap(e.features);
        }

        @Override
        public OperationResult requestTravel(NavigationGoal goal) {
            return withMovement("travel", () -> core.requestTravel(e.id, goal));
        }

        @Override
        public OperationResult requestCombat(CombatGoal goal) {
            return withMovement("combat", () -> core.requestCombat(e.id, goal));
        }

        /** Runs a movement operation under this module's MOVEMENT lease, taking a scoped one if it holds none. */
        private OperationResult withMovement(String what, java.util.function.Supplier<OperationResult> operation) {
            if (!core.onGameThread()) {
                return operation.get(); // records and rejects OFF_THREAD
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
                scoped = r.lease().orElseThrow(java.util.NoSuchElementException::new);
            }
            OperationResult result = operation.get();
            if (!result.isAccepted() && scoped != null && movement.holder != null && movement.holder.id() == scoped.id()) {
                movement.holder = null;
                leaseEvent(LeaseEvent.Kind.RELEASED, scoped, e.id, what + " not accepted: " + result.rejection().orElse(null));
            }
            return result;
        }

        @Override
        public OperationResult cancel(OperationId operation) {
            return core.cancel(e.id, operation);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Setting<T> setting) {
            Object value = e.values.get(Objects.requireNonNull(setting, "setting").name());
            if (value == null || !e.settings.containsKey(setting.name())) {
                throw new IllegalArgumentException(setting.name() + " is not one of this module's settings()");
            }
            return (T) value;
        }

        @Override
        public <T> void set(Setting<T> setting, T value) {
            Objects.requireNonNull(value, "value");
            if (!e.settings.containsKey(Objects.requireNonNull(setting, "setting").name())) {
                throw new IllegalArgumentException(setting.name() + " is not one of this module's settings()");
            }
            if (!setting.parse(setting.format(value)).isPresent()) {
                throw new IllegalArgumentException(value + " is not valid for " + setting.name());
            }
            if (core.onGameThread()) {
                changeSetting(e, setting.name(), value);
            } else {
                sink.record(DiagnosticEvent.offThread("set " + setting.name() + " " + e.id, "setting not changed"));
            }
        }

        @Override
        public Subscription command(String name, String help, Consumer<CommandCall> handler) {
            if (!usable("command")) {
                return () -> { };
            }
            return addCommand(e, name, help, handler);
        }

        @Override
        public ActionResult showOverlay(List<OverlayShape> shapes) {
            if (!core.onGameThread()) {
                return core.showOverlay(e.id, shapes);
            }
            if (e.state != ModuleState.RUNNING) {
                return ActionResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, "module is " + e.state);
            }
            return core.showOverlay(e.id, shapes);
        }

        @Override
        public ActionResult showMessage(String text) {
            if (!core.onGameThread()) {
                return core.showMessage(e.id, text);
            }
            if (e.state != ModuleState.RUNNING) {
                return ActionResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, "module is " + e.state);
            }
            return core.showMessage(e.id, text);
        }

        @Override
        public BlockReading blockAt(BlockPoint at) {
            return core.blockAt(at);
        }

        @Override
        public BlockSearch findBlocks(Set<String> blockIds, int radius, int limit) {
            return core.findBlocks(blockIds, radius, limit);
        }

        /** The module must be running and hold the action's lease; then the core's rules apply. */
        private ActionResult leased(ActionKind kind, java.util.function.Supplier<ActionResult> action) {
            if (!core.onGameThread()) {
                return action.get(); // the core records and rejects OFF_THREAD
            }
            if (e.state != ModuleState.RUNNING) {
                return ActionResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, "module is " + e.state);
            }
            Lease held = slots.get(kind.resource()).holder;
            if (held == null || !held.holder().equals(e.id)) {
                return ActionResult.rejected(RejectionReason.LEASE_REQUIRED, kind + " needs the " + kind.resource().name()
                        + " lease" + (held == null ? "" : "; held by " + held.holder()));
            }
            return action.get();
        }

        @Override
        public ActionResult selectHotbar(int slot) {
            return leased(ActionKind.SELECT_HOTBAR, () -> core.selectHotbar(e.id, slot));
        }

        @Override
        public ActionResult look(double yaw, double pitch) {
            return leased(ActionKind.LOOK, () -> core.look(e.id, yaw, pitch));
        }

        @Override
        public ActionResult attack(int entityId) {
            return leased(ActionKind.ATTACK, () -> core.attack(e.id, entityId));
        }

        @Override
        public ActionResult useItem(boolean offhand) {
            return leased(ActionKind.USE_ITEM, () -> core.useItem(e.id, offhand));
        }

        @Override
        public ActionResult sendChat(String message) {
            return leased(ActionKind.SEND_CHAT, () -> core.sendChat(e.id, message));
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
                return Jdk8.listOf();
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

        @Override
        public List<SettingInfo> settings(ModuleId module) {
            Entry e = modules.get(module);
            if (!core.onGameThread() || e == null) {
                return Jdk8.listOf();
            }
            List<SettingInfo> out = new ArrayList<>();
            e.settings.forEach((n, st) -> out.add(new SettingInfo(n, st.description(), formatted(st, e.values.get(n)),
                    formatted(st, st.defaultValue()))));
            return out;
        }

        @Override
        public boolean setSetting(ModuleId module, String name, String value) {
            Entry e = modules.get(module);
            if (!core.onGameThread() || e == null || name == null) {
                return false;
            }
            Setting<?> setting = e.settings.get(name);
            if (setting == null) {
                return false;
            }
            Optional<?> parsed = setting.parse(value);
            return parsed.isPresent() && changeSetting(e, name, parsed.get());
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
