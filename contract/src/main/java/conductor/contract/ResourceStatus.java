package conductor.contract;

/**
 * Snapshot of the movement resource.
 *
 * @param quietTicks consecutive ticks the release evidence has held
 */
public record ResourceStatus(RejectionReason rejection, ResourceState state, long quietTicks, String detail) {

    public boolean isAccepted() {
        return rejection == null;
    }
}
