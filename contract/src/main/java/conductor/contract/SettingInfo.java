package conductor.contract;

/** A setting as a settings UI sees it: name, description, current and default value as text. */
public record SettingInfo(String name, String description, String value, String defaultValue) {
}
