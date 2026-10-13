package conductor.contract;

import java.util.Objects;
import java.util.Optional;

/**
 * A sample stamped with the context and time it was taken in.
 * <p>
 * Availability ({@link #availability()}) and freshness (evaluated against a {@link FreshnessPolicy}) are
 * independent: an observation can be {@code OBSERVED} yet stale, or {@code UNKNOWN} yet fresh.
 */
public final class Observation<T> {

    private final ObservationKind<T> kind;
    private final Sample<T> sample;
    private final ContextId context;
    private final long sampledTick;
    private final long sampledNanos;

    Observation(ObservationKind<T> kind, Sample<T> sample, ContextId context, long sampledTick, long sampledNanos) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sample = Objects.requireNonNull(sample, "sample");
        this.context = Objects.requireNonNull(context, "context");
        this.sampledTick = sampledTick;
        this.sampledNanos = sampledNanos;
    }

    public ObservationKind<T> kind() {
        return kind;
    }

    public Availability availability() {
        return sample.availability();
    }

    /** The value, present only when {@link Availability#OBSERVED}. Never a default for missing data. */
    public Optional<T> observedValue() {
        return Optional.ofNullable(sample.value());
    }

    public String detail() {
        return sample.detail();
    }

    public ContextId context() {
        return context;
    }

    public long sampledTick() {
        return sampledTick;
    }

    public long sampledNanos() {
        return sampledNanos;
    }

    @Override
    public String toString() {
        return "Observation{" + kind + "=" + availability()
                + (sample.value() != null ? "(" + sample.value() + ")" : "")
                + ", context=" + context + ", tick=" + sampledTick
                + (detail().isEmpty() ? "" : ", detail='" + detail() + "'") + "}";
    }
}
