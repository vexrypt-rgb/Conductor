package conductor.contract;

/** A module's setting changed to {@code value} (as text). */
public record SettingChanged(ModuleId module, String name, String value) {
}
