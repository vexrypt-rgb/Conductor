package conductor.runtime.ostinato;

import baritone.movement.TungstenMovementBackend;

/**
 * Delegates to Ostinato's {@link TungstenMovementBackend} singleton, which is internal and not part of the Ostinato
 * API. Only its read methods are called. The first {@code isAvailable()} call binds its reflection handles, and on
 * success Ostinato posts a chat line. That is the same probe {@code HybridMovementEngine.goTo} runs, and it does
 * not affect movement.
 */
final class TungstenBackendAccess implements SecondaryBackendAccess {

    @Override
    public boolean isBound() {
        return TungstenMovementBackend.INSTANCE.isAvailable();
    }

    @Override
    public boolean reportsPathing() {
        return TungstenMovementBackend.INSTANCE.isPathing();
    }

    @Override
    public boolean reportsStopping() {
        return TungstenMovementBackend.INSTANCE.isStopping();
    }
}
