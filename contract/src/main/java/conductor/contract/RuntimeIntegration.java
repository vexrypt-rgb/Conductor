package conductor.contract;

import java.util.List;
import java.util.Optional;

/**
 * What the contract needs from the runtime integration boundary. Implemented once per engine and Minecraft version.
 * All Minecraft-specific and privileged access stays behind this interface.
 * <p>
 * Except for {@link #isGameThread()}, every method is called only after the contract has confirmed it is on the game
 * thread. Sampling and describing must be read-only: they must not start, stop, or alter any engine operation,
 * process, or input. Only {@link NavigationPort} may act on the engine.
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

    /**
     * Whether {@code content} exists in this game/engine. Return ABSENT only after actually looking; when unsure,
     * UNKNOWN. Read-only.
     */
    default ContentResult content(ContentId content) {
        return new ContentResult(content, ContentResult.Presence.UNKNOWN, "runtime cannot check content");
    }

    /** Read-only block access, if this runtime offers it. */
    default Optional<WorldPort> world() {
        return Optional.empty();
    }

    /** One-shot player actions, if this runtime offers them. */
    default Optional<ActionPort> actions() {
        return Optional.empty();
    }

    /** Combat delegation, if this runtime offers it (Stage 5). */
    default Optional<CombatPort> combat() {
        return Optional.empty();
    }

    /** Navigation delegation, if this runtime offers it (Stage 3). */
    default Optional<NavigationPort> navigation() {
        return Optional.empty();
    }
}
