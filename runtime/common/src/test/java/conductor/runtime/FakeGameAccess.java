package conductor.runtime;

/** A game with no Minecraft behind it, for runtime and engine-layer tests. Public so engine layers can use it. */
public final class FakeGameAccess implements GameAccess {

    public volatile Thread gameThread = Thread.currentThread();
    public String dimension = "minecraft:overworld";
    public Object level = new Object();
    public Object connection = new Object();

    @Override
    public boolean isGameThread() {
        return Thread.currentThread() == gameThread;
    }

    @Override
    public String dimensionId() {
        return dimension;
    }

    @Override
    public Object levelIdentity() {
        return level;
    }

    @Override
    public boolean hasPlayer() {
        return player != null;
    }

    @Override
    public Object connectionIdentity() {
        return connection;
    }

    public conductor.contract.BlockPoint player = new conductor.contract.BlockPoint(1, 64, -2);

    @Override
    public conductor.contract.BlockPoint playerBlock() {
        return player;
    }

    public Boolean useKey = false;
    public int selected;

    public final java.util.Map<java.util.UUID, String> names = new java.util.HashMap<>();

    @Override
    public String playerName(java.util.UUID id) {
        return names.get(id);
    }

    @Override
    public boolean canReach(Object target) {
        return true;
    }

    @Override
    public int selectedSlot() {
        return selected;
    }

    @Override
    public void setSelectedSlot(int slot) {
        selected = slot;
    }
    public java.util.Map<String, Integer> inventory = new java.util.HashMap<>(java.util.Map.of("minecraft:iron_sword", 1));

    @Override
    public java.util.Map<String, Integer> inventoryItems() {
        return inventory;
    }

    @Override
    public Boolean useKeyDown() {
        return useKey;
    }

    /** "kind:id" entries that exist; kinds in {@code unsupported} have no registry. */
    public final java.util.Set<String> registry = new java.util.HashSet<>();
    public final java.util.Set<conductor.contract.ContentId.Kind> unsupported = java.util.EnumSet.of(conductor.contract.ContentId.Kind.ENGINE);

    @Override
    public conductor.contract.ContentResult.Presence registryPresence(conductor.contract.ContentId.Kind kind, String id) {
        if (unsupported.contains(kind) || !id.contains(":")) {
            return null;
        }
        return registry.contains(kind + ":" + id) ? conductor.contract.ContentResult.Presence.PRESENT
                : conductor.contract.ContentResult.Presence.ABSENT;
    }
}
