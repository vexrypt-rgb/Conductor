package conductor.contract;

import java.util.Optional;
import java.util.Set;

import static conductor.contract.ObservationKinds.CONTROLLING_PROCESS;
import static conductor.contract.ObservationKinds.FORCED_INPUTS;
import static conductor.contract.ObservationKinds.NEXT_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.PATH_CALCULATION_PENDING;
import static conductor.contract.ObservationKinds.PATH_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_BOUND;
import static conductor.contract.ObservationKinds.TRAVEL_GOAL_ACTIVE;
import static conductor.contract.ObservationKinds.USE_KEY_HELD;

/**
 * One tick's release evidence, built from raw observations. Only {@code OBSERVED} values count: an unavailable or
 * unknown reading is never evidence of quiet.
 * <p>
 * The live runs (docs/live-run-2026-10-12.md) showed that only the path segment and forced inputs track real control
 * after a cancel; the other signals go quiet at once. All of them are still required here, as necessary
 * conditions.
 *
 * @param pathingStopped  no current or next segment and no pending calculation: the candidate for STOPPED_PATHING
 * @param quiet           pathing stopped, no forced inputs, no controlling process, no active travel goal, no
 *                        secondary backend that could be steering unobserved, and (where combat is offered or the
 *                        signal is reported) the use key released
 * @param controller      the process the engine reports in control, if one was observed
 * @param reason          why the evidence is not quiet; empty when it is
 */
final class ReleaseEvidence {
    private final boolean pathingStopped;
    private final boolean quiet;
    private final Optional<String> controller;
    private final String reason;

    ReleaseEvidence(boolean pathingStopped, boolean quiet, Optional<String> controller, String reason) {
        this.pathingStopped = pathingStopped;
        this.quiet = quiet;
        this.controller = controller;
        this.reason = reason;
    }

    public boolean pathingStopped() { return pathingStopped; }

    public boolean quiet() { return quiet; }

    public Optional<String> controller() { return controller; }

    public String reason() { return reason; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReleaseEvidence)) return false;
        ReleaseEvidence other = (ReleaseEvidence) o;
        return java.util.Arrays.deepEquals(new Object[] {this.pathingStopped, this.quiet, this.controller, this.reason}, new Object[] {other.pathingStopped, other.quiet, other.controller, other.reason});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.pathingStopped, this.quiet, this.controller, this.reason}); }

    @Override public String toString() { return "ReleaseEvidence[" + "pathingStopped=" + pathingStopped + ", " + "quiet=" + quiet + ", " + "controller=" + controller + ", " + "reason=" + reason + "]"; }


    static ReleaseEvidence sample(RuntimeIntegration runtime, Set<ObservationKind<?>> supported) {
        StringBuilder why = new StringBuilder();
        if (!hasEngine(runtime)) {
            // No movement engine: no path, process, forced keys or secondary backend can exist. The use key is still
            // held to account where it is reported (a person or a module may hold it).
            boolean useClear = !supported.contains(USE_KEY_HELD) || isFalse(runtime, supported, USE_KEY_HELD, why);
            return new ReleaseEvidence(true, useClear, Optional.empty(), why.toString());
        }
        boolean segment = isFalse(runtime, supported, PATH_SEGMENT_PRESENT, why);
        boolean next = isFalse(runtime, supported, NEXT_SEGMENT_PRESENT, why);
        boolean calc = isFalse(runtime, supported, PATH_CALCULATION_PENDING, why);
        boolean pathingStopped = segment && next && calc;

        boolean goal = isFalse(runtime, supported, TRAVEL_GOAL_ACTIVE, why);
        Optional<String> controller = Optional.empty();
        boolean noController = false;
        Optional<Optional<String>> c = observed(runtime, supported, CONTROLLING_PROCESS, why);
        if (c.isPresent()) {
            controller = c.get();
            noController = !controller.isPresent();
            if (!noController) {
                why.append("controller ").append(controller.get()).append("; ");
            }
        }
        boolean noInputs = false;
        Optional<Set<String>> inputs = observed(runtime, supported, FORCED_INPUTS, why);
        if (inputs.isPresent()) {
            noInputs = inputs.get().isEmpty();
            if (!noInputs) {
                why.append("forced inputs ").append(inputs.get()).append("; ");
            }
        }
        // A bound secondary backend reports "not pathing" ambiguously (stage1-audit O2), so it always blocks release.
        boolean secondaryClear = !supported.contains(SECONDARY_BACKEND_BOUND)
                || isFalse(runtime, supported, SECONDARY_BACKEND_BOUND, why);

        // Engine combat holds the use key directly (Stage 5 audit), outside FORCED_INPUTS. A runtime that offers combat
        // must show it released; any runtime that reports it is held to it.
        boolean useClear = !(supported.contains(USE_KEY_HELD) || offersCombat(runtime))
                || isFalse(runtime, supported, USE_KEY_HELD, why);

        boolean quiet = pathingStopped && goal && noController && noInputs && secondaryClear && useClear;
        return new ReleaseEvidence(pathingStopped, quiet, controller, why.toString());
    }

    private static boolean hasEngine(RuntimeIntegration runtime) {
        try {
            return runtime.hasMovementEngine();
        } catch (RuntimeException | LinkageError e) {
            return true; // cannot tell: the strict rules
        }
    }

    private static boolean offersCombat(RuntimeIntegration runtime) {
        try {
            return runtime.combat().isPresent();
        } catch (RuntimeException | LinkageError e) {
            return true; // cannot tell: hold release to the stricter rule
        }
    }

    private static boolean isFalse(RuntimeIntegration runtime, Set<ObservationKind<?>> supported,
                                   ObservationKind<Boolean> kind, StringBuilder why) {
        Optional<Boolean> v = observed(runtime, supported, kind, why);
        if (v.isPresent() && v.get()) {
            why.append(kind.name()).append("=true; ");
        }
        return v.isPresent() && !v.get();
    }

    private static <T> Optional<T> observed(RuntimeIntegration runtime, Set<ObservationKind<?>> supported,
                                            ObservationKind<T> kind, StringBuilder why) {
        if (!supported.contains(kind)) {
            why.append(kind.name()).append(" not provided; ");
            return Optional.empty();
        }
        try {
            Sample<T> sample = runtime.sample(kind);
            if (sample != null && sample.availability() == Availability.OBSERVED) {
                return Optional.of(sample.value());
            }
            why.append(kind.name()).append(' ').append(sample == null ? "no sample" : sample.availability()).append("; ");
        } catch (RuntimeException | LinkageError e) {
            why.append(kind.name()).append(" failed: ").append(e).append("; ");
        }
        return Optional.empty();
    }
}
