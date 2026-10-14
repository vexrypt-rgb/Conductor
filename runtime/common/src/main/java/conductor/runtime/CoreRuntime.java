package conductor.runtime;

import conductor.contract.ActionPort;
import conductor.contract.BlockPoint;
import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.CombatPort;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import conductor.contract.EventPort;
import conductor.contract.NavigationPort;
import conductor.contract.ObservationKind;
import conductor.contract.OverlayPort;
import conductor.contract.RuntimeIntegration;
import conductor.contract.Sample;
import conductor.contract.WorldPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static conductor.contract.ObservationKinds.ACTIVE_EFFECTS;
import static conductor.contract.ObservationKinds.INVENTORY_ITEMS;
import static conductor.contract.ObservationKinds.INVENTORY_SLOTS;
import static conductor.contract.ObservationKinds.NEARBY_ENTITIES;
import static conductor.contract.ObservationKinds.PLAYER_MOTION;
import static conductor.contract.ObservationKinds.PLAYER_POSITION;
import static conductor.contract.ObservationKinds.PLAYER_STATUS;
import static conductor.contract.ObservationKinds.SELECTED_SLOT;
import static conductor.contract.ObservationKinds.USE_KEY_HELD;

/**
 * Conductor's runtime. It needs only Minecraft: player, world and inventory reads, one-shot actions, content
 * detection, game events and overlays all come from plain game reads and Conductor's own hooks. An optional
 * {@link EngineLayer} (Ostinato, say) adds navigation, combat and the engine's signals; without one there is no
 * movement engine, so nothing has to be waited for before the movement resource is free.
 */
final class CoreRuntime implements RuntimeIntegration {

    static final String PLAYER = "player";
    static final String CONTENT = "content";
    static final String WORLD = "world";
    static final String ACTIONS = "actions";
    static final String EVENTS = "events";
    static final String OVERLAYS = "overlays";
    static final List<String> ACTION_OPERATIONS = Jdk8.listOf("selectHotbar", "look", "attack", "useItem", "sendChat", "showMessage");
    static final double ENTITY_RADIUS = 32;
    static final int MAX_ENTITIES = 64;

    private final GameAccess game;
    private final Lifecycle lifecycle;
    private final EngineFeatures engineFeatures;
    private PlayerAccess player;
    private EventPort events;
    private OverlayPort overlays;
    private EngineLayer engine;

    CoreRuntime(GameAccess game, Lifecycle lifecycle, EngineFeatures engineFeatures) {
        this.game = game;
        this.lifecycle = lifecycle;
        this.engineFeatures = engineFeatures;
    }

    void enablePlayer(PlayerAccess player) {
        this.player = player;
    }

    void enableEvents(EventPort events) {
        this.events = events;
    }

    void enableOverlays(OverlayPort overlays) {
        this.overlays = overlays;
    }

    /** Attach an optional movement engine (navigation, combat, its signals). */
    void attachEngine(EngineLayer engine) {
        this.engine = engine;
    }

    Optional<EngineLayer> engine() {
        return Optional.ofNullable(engine);
    }

    @Override
    public boolean isGameThread() {
        return game.isGameThread();
    }

    @Override
    public long currentTick() {
        return lifecycle.lastTick();
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public boolean hasMovementEngine() {
        return engine != null;
    }

    @Override
    public List<CapabilityDescriptor> capabilities() {
        boolean p = player != null;
        List<ObservationKind<?>> playerKinds = p
                ? Jdk8.listOf(PLAYER_POSITION, INVENTORY_ITEMS, PLAYER_STATUS, PLAYER_MOTION, NEARBY_ENTITIES, INVENTORY_SLOTS,
                        SELECTED_SLOT, ACTIVE_EFFECTS, USE_KEY_HELD)
                : Jdk8.listOf(PLAYER_POSITION, INVENTORY_ITEMS, USE_KEY_HELD);
        List<CapabilityDescriptor> caps = new ArrayList<>();
        caps.add(new CapabilityDescriptor(PLAYER, CapabilityAvailability.AVAILABLE, "read-only player state", playerKinds, Jdk8.listOf()));
        caps.add(new CapabilityDescriptor(WORLD, availability(p), "read-only blocks from loaded chunks", Jdk8.listOf(),
                p ? Jdk8.listOf("blockAt", "findBlocks") : Jdk8.listOf()));
        caps.add(new CapabilityDescriptor(ACTIONS, availability(p), "one-shot player actions; each needs its resource lease",
                Jdk8.listOf(), p ? ACTION_OPERATIONS : Jdk8.listOf()));
        caps.add(new CapabilityDescriptor(EVENTS, availability(events != null),
                "block changes, chunks, deaths, received chat, chat commands (Conductor's own hooks)", Jdk8.listOf(), Jdk8.listOf()));
        caps.add(new CapabilityDescriptor(OVERLAYS, availability(overlays != null), "boxes, lines and tracers in the world",
                Jdk8.listOf(), overlays != null ? Jdk8.listOf("showOverlay") : Jdk8.listOf()));
        caps.add(contentDescriptor());
        if (engine != null) {
            caps.addAll(engine.capabilities());
        }
        return Jdk8.copyList(caps);
    }

    private static CapabilityAvailability availability(boolean on) {
        return on ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE;
    }

    private CapabilityDescriptor contentDescriptor() {
        String detail;
        try {
            detail = "item/block/entity/effect ids via built-in registries; engine "
                    + (engine == null ? "none" : engine.name()) + ", engine features present: " + engineFeatures.present();
        } catch (RuntimeException | LinkageError e) {
            detail = "item/block/entity/effect ids via built-in registries; engine feature probe failed: " + e;
        }
        return new CapabilityDescriptor(CONTENT, CapabilityAvailability.AVAILABLE, detail, Jdk8.listOf(), Jdk8.listOf("content"));
    }

    @Override
    public ContentResult content(ContentId content) {
        if (content.kind() == ContentId.Kind.ENGINE) {
            return engineFeatures.check(content);
        }
        ContentResult.Presence presence = game.registryPresence(content.kind(), content.id());
        if (presence == null) {
            return new ContentResult(content, ContentResult.Presence.UNKNOWN, "no built-in registry answer for this id");
        }
        return new ContentResult(content, presence, "built-in registry");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Sample<T> sample(ObservationKind<T> kind) {
        if (kind == PLAYER_POSITION) {
            BlockPoint at = game.playerBlock();
            return (Sample<T>) (at == null ? Sample.notObserved("no player") : Sample.observed(at));
        }
        if (kind == INVENTORY_ITEMS) {
            Map<String, Integer> items = game.inventoryItems();
            return (Sample<T>) (items == null ? Sample.notObserved("no player") : Sample.observed(Jdk8.copyMap(items)));
        }
        if (kind == USE_KEY_HELD) {
            Boolean down = game.useKeyDown();
            return (Sample<T>) (down == null ? Sample.notObserved("no game options yet") : Sample.observed(down));
        }
        if (player != null) {
            Object value;
            if (kind == PLAYER_STATUS) {
                value = player.status();
            } else if (kind == PLAYER_MOTION) {
                value = player.motion();
            } else if (kind == NEARBY_ENTITIES) {
                value = player.entities(ENTITY_RADIUS, MAX_ENTITIES);
            } else if (kind == INVENTORY_SLOTS) {
                value = player.slots();
            } else if (kind == SELECTED_SLOT) {
                value = player.selectedSlot();
            } else if (kind == ACTIVE_EFFECTS) {
                value = player.effects();
            } else {
                value = NOT_MINE;
            }
            if (value != NOT_MINE) {
                return (Sample<T>) (value == null ? Sample.notObserved("no player or world") : Sample.observed(value));
            }
        }
        if (engine != null) {
            Sample<T> s = engine.sample(kind);
            if (s != null) {
                return s;
            }
        }
        return Sample.notApplicable(kind + " is not provided by this runtime");
    }

    private static final Object NOT_MINE = new Object();

    @Override
    public Optional<WorldPort> world() {
        return Optional.ofNullable(player);
    }

    @Override
    public Optional<ActionPort> actions() {
        return Optional.ofNullable(player);
    }

    @Override
    public Optional<EventPort> events() {
        return Optional.ofNullable(events);
    }

    @Override
    public Optional<OverlayPort> overlays() {
        return Optional.ofNullable(overlays);
    }

    @Override
    public Optional<NavigationPort> navigation() {
        return engine == null ? Optional.empty() : engine.navigation();
    }

    @Override
    public Optional<CombatPort> combat() {
        return engine == null ? Optional.empty() : engine.combat();
    }
}
