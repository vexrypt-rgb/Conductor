package conductor.engine.ostinato;

final class FakeSecondaryBackend implements SecondaryBackendAccess {

    boolean bound;
    boolean pathing;
    boolean stopping;

    @Override
    public boolean isBound() {
        return bound;
    }

    @Override
    public boolean reportsPathing() {
        return pathing;
    }

    @Override
    public boolean reportsStopping() {
        return stopping;
    }
}
