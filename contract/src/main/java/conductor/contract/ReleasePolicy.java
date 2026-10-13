package conductor.contract;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Consumer-supplied evidence windows. There are deliberately no defaults. Without {@code confirmStopWindowTicks}
 * the resource is never released (fail closed), and without {@code uncertainAfterTicks} a cancel waits for evidence
 * indefinitely (the resource stays quarantined meanwhile).
 *
 * @param confirmStopWindowTicks consecutive ticks the release evidence must hold before the resource is AVAILABLE and
 *                               a cancelling operation is CANCELLED. Provisional; see docs/live-run-2026-10-12.md for
 *                               observed settle times.
 * @param uncertainAfterTicks    ticks after cancel() with no release evidence before the operation becomes UNCERTAIN.
 *                               A timeout never releases the resource.
 */
public final class ReleasePolicy {
    private final OptionalLong confirmStopWindowTicks;
    private final OptionalLong uncertainAfterTicks;

    public ReleasePolicy(OptionalLong confirmStopWindowTicks, OptionalLong uncertainAfterTicks) {
        Objects.requireNonNull(confirmStopWindowTicks, "confirmStopWindowTicks");
        Objects.requireNonNull(uncertainAfterTicks, "uncertainAfterTicks");
        if (confirmStopWindowTicks.isPresent() && confirmStopWindowTicks.getAsLong() < 1) {
        throw new IllegalArgumentException("confirmStopWindowTicks must be at least 1");
        }
        if (uncertainAfterTicks.isPresent() && uncertainAfterTicks.getAsLong() < 1) {
        throw new IllegalArgumentException("uncertainAfterTicks must be at least 1");
        }

        this.confirmStopWindowTicks = confirmStopWindowTicks;
        this.uncertainAfterTicks = uncertainAfterTicks;
    }

    public OptionalLong confirmStopWindowTicks() { return confirmStopWindowTicks; }

    public OptionalLong uncertainAfterTicks() { return uncertainAfterTicks; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReleasePolicy)) return false;
        ReleasePolicy other = (ReleasePolicy) o;
        return java.util.Arrays.deepEquals(new Object[] {this.confirmStopWindowTicks, this.uncertainAfterTicks}, new Object[] {other.confirmStopWindowTicks, other.uncertainAfterTicks});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.confirmStopWindowTicks, this.uncertainAfterTicks}); }

    @Override public String toString() { return "ReleasePolicy[" + "confirmStopWindowTicks=" + confirmStopWindowTicks + ", " + "uncertainAfterTicks=" + uncertainAfterTicks + "]"; }


    /** No release ever: the Stage 3 behaviour. */
    public static ReleasePolicy none() {
        return new ReleasePolicy(OptionalLong.empty(), OptionalLong.empty());
    }
}
