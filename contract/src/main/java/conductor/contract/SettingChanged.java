package conductor.contract;

/** A module's setting changed to {@code value} (as text). */
public final class SettingChanged {
    private final ModuleId module;
    private final String name;
    private final String value;

    public SettingChanged(ModuleId module, String name, String value) {
        this.module = module;
        this.name = name;
        this.value = value;
    }

    public ModuleId module() { return module; }

    public String name() { return name; }

    public String value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingChanged)) return false;
        SettingChanged other = (SettingChanged) o;
        return java.util.Arrays.deepEquals(new Object[] {this.module, this.name, this.value}, new Object[] {other.module, other.name, other.value});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.module, this.name, this.value}); }

    @Override public String toString() { return "SettingChanged[" + "module=" + module + ", " + "name=" + name + ", " + "value=" + value + "]"; }
}
