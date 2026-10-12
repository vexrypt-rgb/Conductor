package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import conductor.contract.ObservationKind;
import conductor.contract.Sample;

import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;

import static conductor.contract.ObservationKinds.CONTROLLING_PROCESS;
import static conductor.contract.ObservationKinds.FORCED_INPUTS;
import static conductor.contract.ObservationKinds.NEXT_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.PATH_CALCULATION_PENDING;
import static conductor.contract.ObservationKinds.PATH_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_BOUND;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_PATHING;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_STOPPING;
import static conductor.contract.ObservationKinds.TRAVEL_GOAL_ACTIVE;

/**
 * Version adapter for Ostinato {@code main}: maps each contract kind to one read-only accessor. Nothing here calls
 * a method that starts, stops or changes movement. The accessors read unsynchronised engine state, so they are
 * called only on the game thread (stage1-audit T2).
 */
final class OstinatoSignals {

    private final IBaritone baritone;
    private final SecondaryBackendAccess secondary;

    OstinatoSignals(IBaritone baritone, SecondaryBackendAccess secondary) {
        this.baritone = baritone;
        this.secondary = secondary;
    }

    @SuppressWarnings("unchecked")
    <T> Sample<T> read(ObservationKind<T> kind) {
        return (Sample<T>) read0(kind);
    }

    private Sample<?> read0(ObservationKind<?> kind) {
        if (kind == PATH_SEGMENT_PRESENT) {
            return Sample.observed(baritone.getPathingBehavior().getCurrent() != null);
        }
        if (kind == NEXT_SEGMENT_PRESENT) {
            return Sample.observed(baritone.getPathingBehavior().getNext() != null);
        }
        if (kind == PATH_CALCULATION_PENDING) {
            return Sample.observed(baritone.getPathingBehavior().getInProgress().isPresent());
        }
        if (kind == CONTROLLING_PROCESS) {
            // Class name rather than displayName(): displayName() runs process code.
            Optional<String> name = baritone.getPathingControlManager().mostRecentInControl().map(p -> p.getClass().getName());
            return Sample.observed(name);
        }
        if (kind == TRAVEL_GOAL_ACTIVE) {
            return Sample.observed(baritone.getCustomGoalProcess().isActive());
        }
        if (kind == FORCED_INPUTS) {
            IInputOverrideHandler handler = baritone.getInputOverrideHandler();
            TreeSet<String> forced = new TreeSet<>();
            for (Input input : Input.values()) {
                if (handler.isInputForcedDown(input)) {
                    forced.add(input.name());
                }
            }
            return Sample.observed(Collections.unmodifiableSortedSet(forced));
        }
        if (kind == SECONDARY_BACKEND_BOUND) {
            return Sample.observed(secondary.isBound());
        }
        if (kind == SECONDARY_BACKEND_PATHING) {
            if (!secondary.isBound()) {
                return Sample.notApplicable("secondary backend not bound");
            }
            return secondary.reportsPathing() ? Sample.observed(true)
                    : Sample.unknown("TungstenMovementBackend.isPathing() returns false both when idle and when its reflection fails");
        }
        if (kind == SECONDARY_BACKEND_STOPPING) {
            if (!secondary.isBound()) {
                return Sample.notApplicable("secondary backend not bound");
            }
            return secondary.reportsStopping() ? Sample.observed(true)
                    : Sample.unknown("TungstenMovementBackend.isStopping() returns false both when not stopping and when its reflection fails");
        }
        throw new IllegalArgumentException("no Ostinato mapping for " + kind);
    }
}
