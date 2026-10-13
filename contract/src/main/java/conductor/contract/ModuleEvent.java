package conductor.contract;

/** Published on {@link Topics#MODULES} whenever a module changes state. */
public record ModuleEvent(ModuleId module, ModuleState from, ModuleState to, String detail) {
}
