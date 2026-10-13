package conductor.contract;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Caller-supplied freshness requirement. There is deliberately no default: a call without a policy is rejected
 * ({@link RejectionReason#NO_FRESHNESS_POLICY}).
 *
 * @param requiredFreshness which rule applies
 * @param maxAgeTicks       for {@link Requirement#BOUNDED_AGE}: the maximum age in game ticks
 * @param maxAgeDuration    for {@link Requirement#BOUNDED_AGE}: the maximum age in monotonic time
 */
public final class FreshnessPolicy {
    private final Requirement requiredFreshness;
    private final OptionalLong maxAgeTicks;
    private final Optional<Duration> maxAgeDuration;

    public FreshnessPolicy(Requirement requiredFreshness, OptionalLong maxAgeTicks, Optional<Duration> maxAgeDuration) {
        Objects.requireNonNull(requiredFreshness, "requiredFreshness");
        Objects.requireNonNull(maxAgeTicks, "maxAgeTicks");
        Objects.requireNonNull(maxAgeDuration, "maxAgeDuration");
        boolean anyBound = maxAgeTicks.isPresent() || maxAgeDuration.isPresent();
        if (requiredFreshness == Requirement.SAME_TICK && anyBound) {
        throw new IllegalArgumentException("SAME_TICK takes no age bounds");
        }
        if (requiredFreshness == Requirement.BOUNDED_AGE && !anyBound) {
        throw new IllegalArgumentException("BOUNDED_AGE needs maxAgeTicks and/or maxAgeDuration");
        }
        if (maxAgeTicks.isPresent() && maxAgeTicks.getAsLong() < 0) {
        throw new IllegalArgumentException("maxAgeTicks must not be negative");
        }
        if (maxAgeDuration.isPresent() && maxAgeDuration.get().isNegative()) {
        throw new IllegalArgumentException("maxAgeDuration must not be negative");
        }

        this.requiredFreshness = requiredFreshness;
        this.maxAgeTicks = maxAgeTicks;
        this.maxAgeDuration = maxAgeDuration;
    }

    public Requirement requiredFreshness() { return requiredFreshness; }

    public OptionalLong maxAgeTicks() { return maxAgeTicks; }

    public Optional<Duration> maxAgeDuration() { return maxAgeDuration; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FreshnessPolicy)) return false;
        FreshnessPolicy other = (FreshnessPolicy) o;
        return java.util.Arrays.deepEquals(new Object[] {this.requiredFreshness, this.maxAgeTicks, this.maxAgeDuration}, new Object[] {other.requiredFreshness, other.maxAgeTicks, other.maxAgeDuration});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.requiredFreshness, this.maxAgeTicks, this.maxAgeDuration}); }

    @Override public String toString() { return "FreshnessPolicy[" + "requiredFreshness=" + requiredFreshness + ", " + "maxAgeTicks=" + maxAgeTicks + ", " + "maxAgeDuration=" + maxAgeDuration + "]"; }


    public enum Requirement {
        /** Only an observation sampled in the current game tick is fresh. Takes no bounds. */
        SAME_TICK,
        /** Fresh while every configured bound holds. At least one bound is required. */
        BOUNDED_AGE
    }

    public static FreshnessPolicy sameTick() {
        return new FreshnessPolicy(Requirement.SAME_TICK, OptionalLong.empty(), Optional.empty());
    }

    public static FreshnessPolicy maxAge(OptionalLong maxAgeTicks, Optional<Duration> maxAgeDuration) {
        return new FreshnessPolicy(Requirement.BOUNDED_AGE, maxAgeTicks, maxAgeDuration);
    }

    /** A negative age (the clock appears to have gone backwards) is treated as stale. */
    public Freshness evaluate(long sampledTick, long sampledNanos, long nowTick, long nowNanos) {
        long ageTicks = nowTick - sampledTick;
        long ageNanos = nowNanos - sampledNanos;
        if (ageTicks < 0 || ageNanos < 0) {
            return Freshness.STALE;
        }
        if (requiredFreshness == Requirement.SAME_TICK) {
            return ageTicks == 0 ? Freshness.FRESH : Freshness.STALE;
        }
        boolean ticksOk = !maxAgeTicks.isPresent() || ageTicks <= maxAgeTicks.getAsLong();
        boolean timeOk = !maxAgeDuration.isPresent() || ageNanos <= maxAgeDuration.get().toNanos();
        return ticksOk && timeOk ? Freshness.FRESH : Freshness.STALE;
    }
}
