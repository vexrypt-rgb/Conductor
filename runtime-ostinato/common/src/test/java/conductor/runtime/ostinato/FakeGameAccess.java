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
