package conductor.contract;

import java.util.List;

/** A module as the management service sees it. */
public record ModuleInfo(ModuleId id, ModuleState state, Priority priority, List<Lease> leases,
                         List<String> provides, List<String> requires, String detail) {

    public ModuleInfo {
        leases = List.copyOf(leases);
        provides = List.copyOf(provides);
        requires = List.copyOf(requires);
    }
}
