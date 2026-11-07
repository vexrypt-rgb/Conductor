package conductor.contract;

import java.util.List;

/**
 * A command the player typed in chat, for example {@code +kit check now}: name {@code kit}, args
 * {@code [check, now]}. The line was not sent to the server.
 */
public final class CommandCall {
    private final String name;
    private final List<String> args;
    private final String line;

    public CommandCall(String name, List<String> args, String line) {
        args = Jdk8.copyList(args);

        this.name = name;
        this.args = args;
        this.line = line;
    }

    public String name() { return name; }

    public List<String> args() { return args; }

    public String line() { return line; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandCall)) return false;
        CommandCall other = (CommandCall) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.args, this.line}, new Object[] {other.name, other.args, other.line});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.args, this.line}); }

    @Override public String toString() { return "CommandCall[" + "name=" + name + ", " + "args=" + args + ", " + "line=" + line + "]"; }
}
