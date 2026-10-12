package conductor.runtime.ostinato;

/**
 * Read-only view of the secondary travel backend (Tungsten). Kept separate so tests do not load
 * {@code TungstenMovementBackend}, whose {@code Helper} superinterface initialises {@code Minecraft}.
 */
interface SecondaryBackendAccess {

    boolean isBound();

    /** {@code false} is ambiguous: idle, or the backend's own reflection failed (stage1-audit §3.3). */
    boolean reportsPathing();

    /** {@code false} is ambiguous in the same way as {@link #reportsPathing()}. */
    boolean reportsStopping();
}
