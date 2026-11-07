package conductor.contract;

/**
 * Outcome of a one-shot action. Accepted means the game was told to do it this tick; the effect shows in the next
 * observations (for example {@link ObservationKinds#SELECTED_SLOT}), and the server may still refuse it.
 */
public final class ActionResult {
    private final RejectionReason rejection;
    private final String detail;

    public ActionResult(RejectionReason rejection, String detail) {
        this.rejection = rejection;
        this.detail = detail;
    }

    public RejectionReason rejection() { return rejection; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionResult)) return false;
        ActionResult other = (ActionResult) o;
        return java.util.Arrays.deepEquals(new Object[] {this.rejection, this.detail}, new Object[] {other.rejection, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.rejection, this.detail}); }

    @Override public String toString() { return "ActionResult[" + "rejection=" + rejection + ", " + "detail=" + detail + "]"; }


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
