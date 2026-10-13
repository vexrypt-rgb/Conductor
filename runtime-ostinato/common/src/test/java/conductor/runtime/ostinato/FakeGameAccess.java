package conductor.runtime.ostinato;

import net.minecraft.client.multiplayer.ClientLevel;

final class FakeGameAccess implements GameAccess {

    volatile Thread gameThread = Thread.currentThread();
    String dimension = "minecraft:overworld";
    String dimensionOfNewWorld = "minecraft:overworld";
    Object connection = new Object();

    @Override
    public boolean isGameThread() {
        return Thread.currentThread() == gameThread;
    }

    @Override
    public String dimensionId() {
        return dimension;
    }

    @Override
    public String dimensionOf(ClientLevel level) {
        return dimensionOfNewWorld;
    }

    @Override
    public Object connectionIdentity() {
        return connection;
    }

    conductor.contract.BlockPoint player = new conductor.contract.BlockPoint(1, 64, -2);

    @Override
    public conductor.contract.BlockPoint playerBlock() {
        return player;
    }

    Boolean useKey = false;
    int selected;

    final java.util.Map<java.util.UUID, String> names = new java.util.HashMap<>();

    @Override
    public String playerName(java.util.UUID id) {
        return names.get(id);
    }

    @Override
    public boolean canReach(net.minecraft.world.entity.Entity target) {
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
    java.util.Map<String, Integer> inventory = new java.util.HashMap<>(java.util.Map.of("minecraft:iron_sword", 1));

    @Override
    public java.util.Map<String, Integer> inventoryItems() {
        return inventory;
    }

    @Override
    public Boolean useKeyDown() {
        return useKey;
    }

    /** "kind:id" entries that exist; kinds in {@code unsupported} have no registry. */
    final java.util.Set<String> registry = new java.util.HashSet<>();
    final java.util.Set<conductor.contract.ContentId.Kind> unsupported = java.util.EnumSet.of(conductor.contract.ContentId.Kind.ENGINE);

    @Override
    public conductor.contract.ContentResult.Presence registryPresence(conductor.contract.ContentId.Kind kind, String id) {
        if (unsupported.contains(kind) || !id.contains(":")) {
            return null;
        }
        return registry.contains(kind + ":" + id) ? conductor.contract.ContentResult.Presence.PRESENT
                : conductor.contract.ContentResult.Presence.ABSENT;
    }
}
