package conductor.contract;

/** A setting as a settings UI sees it: name, description, current and default value as text. */
public final class SettingInfo {
    private final String name;
    private final String description;
    private final String value;
    private final String defaultValue;

    public SettingInfo(String name, String description, String value, String defaultValue) {
        this.name = name;
        this.description = description;
        this.value = value;
        this.defaultValue = defaultValue;
    }

    public String name() { return name; }

    public String description() { return description; }

    public String value() { return value; }

    public String defaultValue() { return defaultValue; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingInfo)) return false;
        SettingInfo other = (SettingInfo) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.description, this.value, this.defaultValue}, new Object[] {other.name, other.description, other.value, other.defaultValue});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.description, this.value, this.defaultValue}); }

    @Override public String toString() { return "SettingInfo[" + "name=" + name + ", " + "description=" + description + ", " + "value=" + value + ", " + "defaultValue=" + defaultValue + "]"; }
}
