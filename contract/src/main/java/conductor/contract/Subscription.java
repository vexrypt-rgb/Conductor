package conductor.contract;

/** A topic subscription; cancel it to stop receiving messages. Cancelled automatically when the module stops. */
public interface Subscription {
    void cancel();
}
