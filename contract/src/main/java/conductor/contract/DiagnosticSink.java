package conductor.contract;

import java.util.List;

/**
 * In-process diagnostic sink, readable by tests. No IPC.
 * <p>
 * Implementations must be safe to call from any thread. The contract relies on this to record off-thread
 * violations. {@link InMemoryDiagnosticSink} meets it through {@code java.util.concurrent}.
 */
public interface DiagnosticSink {

    void record(DiagnosticEvent event);

    List<DiagnosticEvent> snapshot();
}
