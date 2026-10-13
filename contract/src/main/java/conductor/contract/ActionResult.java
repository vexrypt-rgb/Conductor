package conductor.contract;

/**
 * Outcome of a one-shot action. Accepted means the game was told to do it this tick; the effect shows in the next
 * observations (for example {@link ObservationKinds#SELECTED_SLOT}), and the server may still refuse it.
 */
public record ActionResult(RejectionReason rejection, String detail) {

    public static ActionResult done(String detail) {
        return new ActionResult(null, detail);
    }

    public static ActionResult rejected(RejectionReason reason, String detail) {
        return new ActionResult(reason, detail);
    }

    public boolean isAccepted() {
        return rejection == null;
    }
}
