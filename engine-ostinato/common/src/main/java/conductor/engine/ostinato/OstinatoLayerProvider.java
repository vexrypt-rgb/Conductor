package conductor.engine.ostinato;

import conductor.runtime.EngineLayer;
import conductor.runtime.EngineLayerProvider;

/**
 * Attaches Ostinato when it is installed. Its own signatures mention no Ostinato class, so Conductor can ask
 * {@link #available} without loading Ostinato.
 */
public final class OstinatoLayerProvider implements EngineLayerProvider {

    /** Ostinato classes this layer links against; the obfuscated Ostinato builds rename them. */
    static final String[] NEEDED = {"baritone.api.BaritoneAPI", "baritone.Baritone", "baritone.movement.MovementBackends"};

    @Override
    public boolean available(ClassLoader loader) {
        for (String name : NEEDED) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public EngineLayer create() {
        return OstinatoLayer.forPrimary();
    }
}
