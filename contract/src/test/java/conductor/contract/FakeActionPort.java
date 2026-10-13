package conductor.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Test action port. Logs every call; {@code refuse} makes the next call report a problem. */
final class FakeActionPort implements ActionPort {

    final List<String> calls = new ArrayList<>();
    String refuse;

    private Optional<String> log(String call) {
        calls.add(call);
        String r = refuse;
        refuse = null;
        return Optional.ofNullable(r);
    }

    public Optional<String> selectHotbar(int slot) { return log("hotbar " + slot); }
    public Optional<String> look(double yaw, double pitch) { return log("look " + yaw + " " + pitch); }
    public Optional<String> attack(int entityId) { return log("attack " + entityId); }
    public Optional<String> useItem(boolean offhand) { return log("use " + (offhand ? "off" : "main")); }
    public Optional<String> sendChat(String message) { return log("chat " + message); }
}
