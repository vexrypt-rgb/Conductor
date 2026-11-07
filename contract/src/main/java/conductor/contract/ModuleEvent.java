package conductor.contract;

/** Published on {@link Topics#MODULES} whenever a module changes state. */
public final class ModuleEvent {
    private final ModuleId module;
    private final ModuleState from;
    private final ModuleState to;
    private final String detail;

    public ModuleEvent(ModuleId module, ModuleState from, ModuleState to, String detail) {
        this.module = module;
        this.from = from;
        this.to = to;
        this.detail = detail;
    }

    public ModuleId module() { return module; }

    public ModuleState from() { return from; }

    public ModuleState to() { return to; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleEvent)) return false;
        ModuleEvent other = (ModuleEvent) o;
        return java.util.Arrays.deepEquals(new Object[] {this.module, this.from, this.to, this.detail}, new Object[] {other.module, other.from, other.to, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.module, this.from, this.to, this.detail}); }

    @Override public String toString() { return "ModuleEvent[" + "module=" + module + ", " + "from=" + from + ", " + "to=" + to + ", " + "detail=" + detail + "]"; }
}
