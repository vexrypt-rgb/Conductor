package conductor.contract;

import java.util.Objects;
import java.util.Optional;

/** Outcome of a navigation call. Present status means an operation exists; it does not mean movement succeeded. */
public final class OperationResult {

    private final RejectionReason rejection;
    private final OperationStatus status;
    private final String detail;

    private OperationResult(RejectionReason rejection, OperationStatus status, String detail) {
        this.rejection = rejection;
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }

    static OperationResult accepted(OperationStatus status) {
        return new OperationResult(null, Objects.requireNonNull(status), "");
    }

    static OperationResult rejected(RejectionReason reason, OperationStatus status, String detail) {
        return new OperationResult(Objects.requireNonNull(reason), status, detail);
    }

    public boolean isAccepted() {
        return rejection == null;
    }

    public Optional<RejectionReason> rejection() {
        return Optional.ofNullable(rejection);
    }

    public Optional<OperationStatus> status() {
        return Optional.ofNullable(status);
    }

    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return "OperationResult{" + (isAccepted() ? "ACCEPTED" : "REJECTED " + rejection) + ", " + status
                + (detail.isEmpty() ? "" : ", detail='" + detail + "'") + "}";
    }
}
