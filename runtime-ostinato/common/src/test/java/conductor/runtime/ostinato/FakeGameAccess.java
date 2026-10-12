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
}
