package conductor.contract;

import java.util.Objects;

/**
 * What the runtime integration read for one kind, before the contract stamps it with context and time.
 *
 * @param value non-null exactly when {@code availability} is {@link Availability#OBSERVED}
 */
public record Sample<T>(Availability availability, T value, String detail) {

    public Sample {
        Objects.requireNonNull(availability, "availability");
        detail = detail == null ? "" : detail;
        if ((availability == Availability.OBSERVED) != (value != null)) {
            throw new IllegalArgumentException("value must be present exactly when OBSERVED: " + availability);
        }
    }

    public static <T> Sample<T> observed(T value) {
        return new Sample<>(Availability.OBSERVED, Objects.requireNonNull(value, "value"), "");
    }

    public static <T> Sample<T> notObserved(String detail) {
        return new Sample<>(Availability.NOT_OBSERVED, null, detail);
    }

    public static <T> Sample<T> notApplicable(String detail) {
        return new Sample<>(Availability.NOT_APPLICABLE, null, detail);
    }

    public static <T> Sample<T> unknown(String detail) {
        return new Sample<>(Availability.UNKNOWN, null, detail);
    }
}
