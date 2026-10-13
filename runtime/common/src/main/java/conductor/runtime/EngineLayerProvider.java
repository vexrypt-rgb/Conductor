package conductor.runtime;

/**
 * Finds and attaches an optional engine. Listed in {@code META-INF/services/conductor.runtime.EngineLayerProvider}.
 * Implementations must not mention engine classes in their own signatures, so that checking {@link #available}
 * never loads the engine when it is not installed.
 */
public interface EngineLayerProvider {

    /** Whether the engine is installed and usable (its classes are present under their expected names). */
    boolean available(ClassLoader loader);

    /** Game thread, in a world. */
    EngineLayer create();
}
