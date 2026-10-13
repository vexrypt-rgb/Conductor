package conductor.contract;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of {@link Conductor#observe} or {@link Conductor#revalidate}.
 * <p>
 * An accepted result is fresh and belongs to the current context. Its observation may still be
 * {@code NOT_OBSERVED}, {@code NOT_APPLICABLE} or {@code UNKNOWN}: acceptance is about validity, not availability.
 * A {@link RejectionReason#STALE} rejection still carries the observation, so availability and freshness can be
 * read separately.
 */
public final class ObservationResult<T> {

    private final RejectionReason rejection;
    private final Observation<T> observation;
    private final Freshness freshness;
    private final String detail;

    private ObservationResult(RejectionReason rejection, Observation<T> observation, Freshness freshness, String detail) {
        this.rejection = rejection;
        this.observation = observation;
        this.freshness = freshness;
        this.detail = detail == null ? "" : detail;
    }

    static <T> ObservationResult<T> accepted(Observation<T> observation) {
        return new ObservationResult<>(null, Objects.requireNonNull(observation), Freshness.FRESH, "");
    }

    static <T> ObservationResult<T> rejected(RejectionReason reason, Observation<T> observation, Freshness freshness, String detail) {
        return new ObservationResult<>(Objects.requireNonNull(reason), observation, freshness, detail);
    }

    public boolean isAccepted() {
        return rejection == null;
    }

    public Optional<RejectionReason> rejection() {
        return Optional.ofNullable(rejection);
    }

    /** Present when a sample was taken or supplied: always when accepted, and for some rejections. */
    public Optional<Observation<T>> observation() {
        return Optional.ofNullable(observation);
    }

    /** Present when freshness was evaluated. */
    public Optional<Freshness> freshness() {
        return Optional.ofNullable(freshness);
    }

    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return "ObservationResult{" + (isAccepted() ? "ACCEPTED" : "REJECTED " + rejection)
                + ", freshness=" + freshness + ", " + observation
                + (detail.isEmpty() ? "" : ", detail='" + detail + "'") + "}";
    }
}
