package conductor.contract;

import java.util.Objects;

/** Handle for one navigation operation. Bound to the context it was granted in. */
public record OperationId(long id, ContextId context) {

    public OperationId {
        Objects.requireNonNull(context, "context");
    }
}
