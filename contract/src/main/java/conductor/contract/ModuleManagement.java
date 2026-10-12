package conductor.contract;

import java.util.List;

/**
 * Management of all modules, provided by Conductor as the service {@link #KEY}. A UI, chat-command or automation
 * module can use it. Game thread only, like everything else.
 */
public interface ModuleManagement {

    ServiceKey<ModuleManagement> KEY = new ServiceKey<>("conductor:management", ModuleManagement.class);

    List<ModuleInfo> modules();

    /** Stop a module: its leases are revoked (and its operations cancelled), its services and subscriptions removed. */
    boolean disable(ModuleId module, String reason);

    /** Start a DISABLED or FAILED module again (it gets onReady once more). */
    boolean enable(ModuleId module);
}
