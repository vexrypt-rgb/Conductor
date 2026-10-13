package conductor.contract;

import java.util.ArrayList;
import java.util.List;

/** Test combat port. Logs every call. */
final class FakeCombatPort implements CombatPort {

    static final String OWN = "engine.PvpProcess";

    final List<String> calls = new ArrayList<>();
    NavigationPort.Delegation next = new NavigationPort.Delegation(true, "engine-pvp", true, "");

    @Override
    public boolean isOwnController(String controller) {
        return OWN.equals(controller);
    }

    @Override
    public NavigationPort.Delegation engage(CombatGoal goal) {
        calls.add("engage " + goal);
        return next;
    }

    @Override
    public void cancel() {
        calls.add("cancel");
    }
}
