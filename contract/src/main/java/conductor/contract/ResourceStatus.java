package conductor.contract;

/**
 * Snapshot of the movement resource.
 *
 * @param quietTicks consecutive ticks the release evidence has held
 */
public final class ResourceStatus {
    private final RejectionReason rejection;
    private final ResourceState state;
    private final long quietTicks;
    private final String detail;

    public ResourceStatus(RejectionReason rejection, ResourceState state, long quietTicks, String detail) {
        this.rejection = rejection;
        this.state = state;
        this.quietTicks = quietTicks;
        this.detail = detail;
    }

    public RejectionReason rejection() { return rejection; }

    public ResourceState state() { return state; }

    public long quietTicks() { return quietTicks; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceStatus)) return false;
        ResourceStatus other = (ResourceStatus) o;
        return java.util.Arrays.deepEquals(new Object[] {this.rejection, this.state, this.quietTicks, this.detail}, new Object[] {other.rejection, other.state, other.quietTicks, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.rejection, this.state, this.quietTicks, this.detail}); }

    @Override public String toString() { return "ResourceStatus[" + "rejection=" + rejection + ", " + "state=" + state + ", " + "quietTicks=" + quietTicks + ", " + "detail=" + detail + "]"; }


    public boolean isAccepted() {
        return rejection == null;
    }
}
