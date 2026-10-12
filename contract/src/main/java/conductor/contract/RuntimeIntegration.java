package conductor.contract;

import java.util.List;

/**
 * What the contract needs from the runtime integration boundary. Implemented once per engine and Minecraft version.
 * All Minecraft-specific and privileged access stays behind this interface.
 * <p>
 * Except for {@link #isGameThread()}, every method is called only after the contract has confirmed it is on the game
 * thread. Implementations must be read-only in Stage 2: sampling and describing must not start, stop, or alter any
 * engine operation, process, or input.
 */
public interface RuntimeIntegration {

    /** Must be safe from any thread and must not touch engine state. */
    boolean isGameThread();

    /** Monotonic game-tick counter. */
    long currentTick();

    /** Monotonic time source in nanoseconds. */
    long nanoTime();

    List<CapabilityDescriptor> capabilities();

    /**
     * Read one kind now. When a value cannot be read with certainty, return a non-{@code OBSERVED} sample; never
     * substitute a default. May throw; the contract records a fault and treats the result as {@code UNKNOWN}.
     */
    <T> Sample<T> sample(ObservationKind<T> kind);
}
