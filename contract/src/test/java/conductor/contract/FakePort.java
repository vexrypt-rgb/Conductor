package conductor.contract;

import java.util.ArrayList;
import java.util.List;

/** Test engine port. Logs every call so tests can tell an engine call from a rejection. */
final class FakePort implements NavigationPort {

    final List<String> calls = new ArrayList<>();
    boolean observableRoute = true;
    Delegation next = new Delegation(true, "BARITONE", true, "");
    RuntimeException cancelFailure;

    @Override
    public boolean routesToObservableBackend() {
        calls.add("route");
        return observableRoute;
    }

    @Override
    public boolean isOwnController(String controller) {
        return OWN.equals(controller);
    }

    static final String OWN = "engine.TravelProcess";

    @Override
    public Delegation travel(NavigationGoal goal) {
        calls.add("travel " + goal);
        return next;
    }

    @Override
    public void cancel() {
        calls.add("cancel");
        if (cancelFailure != null) {
            throw cancelFailure;
        }
    }

    long engineCalls() {
        return calls.stream().filter(c -> !c.equals("route")).count();
    }
}
