package conductor.contract;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Observation kinds defined by the contract for Stage 2: raw, read-only signals about the movement resource.
 * <p>
 * Each kind is one raw signal. None of them, alone or combined, is defined here as evidence that pathing stopped,
 * that overrides were cleared, or that the resource is safe. Those evidence rules belong to Stage 4. See
 * {@code docs/stage1-audit.md} §3 for why each underlying signal is insufficient alone.
 */
public final class ObservationKinds {

    private ObservationKinds() {}

    public static final ObservationKind<Boolean> PATH_SEGMENT_PRESENT = new ObservationKind<>(
            "PATH_SEGMENT_PRESENT",
            "The primary pathing executor holds a current path segment.");

    public static final ObservationKind<Boolean> NEXT_SEGMENT_PRESENT = new ObservationKind<>(
            "NEXT_SEGMENT_PRESENT",
            "The primary pathing executor holds a planned next segment.");

    public static final ObservationKind<Boolean> PATH_CALCULATION_PENDING = new ObservationKind<>(
            "PATH_CALCULATION_PENDING",
            "The engine's in-progress calculation slot is occupied. Empty does not prove that no calculation thread is "
                    + "still running (stage1-audit R1).");

    public static final ObservationKind<Optional<String>> CONTROLLING_PROCESS = new ObservationKind<>(
            "CONTROLLING_PROCESS",
            "Class name of the process that issued a pathing command this tick, or empty when none did.");

    public static final ObservationKind<Boolean> TRAVEL_GOAL_ACTIVE = new ObservationKind<>(
            "TRAVEL_GOAL_ACTIVE",
            "The engine's custom-goal travel process reports itself active.");

    public static final ObservationKind<Set<String>> FORCED_INPUTS = new ObservationKind<>(
            "FORCED_INPUTS",
            "Names of inputs currently forced down through the engine's input override handler. Does not cover "
                    + "inputs written by other means (e.g. vanilla key state).");

    public static final ObservationKind<Boolean> SECONDARY_BACKEND_BOUND = new ObservationKind<>(
            "SECONDARY_BACKEND_BOUND",
            "The secondary travel backend was found and bound.");

    public static final ObservationKind<Boolean> SECONDARY_BACKEND_PATHING = new ObservationKind<>(
            "SECONDARY_BACKEND_PATHING",
            "The secondary travel backend reports following, searching or executing.");

    public static final ObservationKind<Boolean> SECONDARY_BACKEND_STOPPING = new ObservationKind<>(
            "SECONDARY_BACKEND_STOPPING",
            "The secondary travel backend reports a cancelled search that has not yet wound down.");

    public static final List<ObservationKind<?>> ALL = List.of(
            PATH_SEGMENT_PRESENT, NEXT_SEGMENT_PRESENT, PATH_CALCULATION_PENDING, CONTROLLING_PROCESS,
            TRAVEL_GOAL_ACTIVE, FORCED_INPUTS, SECONDARY_BACKEND_BOUND, SECONDARY_BACKEND_PATHING,
            SECONDARY_BACKEND_STOPPING);
}
