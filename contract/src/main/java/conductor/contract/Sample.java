package conductor.contract;

import java.util.Objects;

/**
 * What the runtime integration read for one kind, before the contract stamps it with context and time.
 *
 * @param value non-null exactly when {@code availability} is {@link Availability#OBSERVED}
 */
public final class Sample<T> {
    private final Availability availability;
    private final T value;
    private final String detail;

    public Sample(Availability availability, T value, String detail) {
        Objects.requireNonNull(availability, "availability");
        detail = detail == null ? "" : detail;
        if ((availability == Availability.OBSERVED) != (value != null)) {
        throw new IllegalArgumentException("value must be present exactly when OBSERVED: " + availability);
        }

        this.availability = availability;
        this.value = value;
        this.detail = detail;
    }

    public Availability availability() { return availability; }

    public T value() { return value; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sample)) return false;
        Sample<?> other = (Sample<?>) o;
        return java.util.Arrays.deepEquals(new Object[] {this.availability, this.value, this.detail}, new Object[] {other.availability, other.value, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.availability, this.value, this.detail}); }

    @Override public String toString() { return "Sample[" + "availability=" + availability + ", " + "value=" + value + ", " + "detail=" + detail + "]"; }


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
