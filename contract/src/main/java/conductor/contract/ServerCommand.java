package conductor.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A {@code /} command added by a {@link ServerModule}. {@code /name} runs it with no arguments; anything after the
 * name is split on spaces into {@link Call#args()}. The handler returns the reply for whoever typed it (null for none).
 * To answer with an error, throw {@link #fail(String)}; any other exception counts as the module failing.
 *
 * @param name     1–32 of {@code a-z 0-9 _ -}
 * @param help     one line, shown in the server log when the command is added
 * @param operator only operators (permission level 2, the level of {@code /gamemode}) and the console may use it
 */
public final class ServerCommand {
    private final String name;
    private final String help;
    private final boolean operator;
    private final Handler handler;

    public ServerCommand(String name, String help, boolean operator, Handler handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (!NAME.matcher(name).matches()) {
        throw new IllegalArgumentException("command name must be 1-32 of a-z 0-9 _ -: " + name);
        }
        help = help == null ? "" : help;

        this.name = name;
        this.help = help;
        this.operator = operator;
        this.handler = handler;
    }

    public String name() { return name; }

    public String help() { return help; }

    public boolean operator() { return operator; }

    public Handler handler() { return handler; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerCommand)) return false;
        ServerCommand other = (ServerCommand) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.help, this.operator, this.handler}, new Object[] {other.name, other.help, other.operator, other.handler});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.help, this.operator, this.handler}); }

    @Override public String toString() { return "ServerCommand[" + "name=" + name + ", " + "help=" + help + ", " + "operator=" + operator + ", " + "handler=" + handler + "]"; }


    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    /** A command anyone may use. */
    public static ServerCommand of(String name, String help, Handler handler) {
        return new ServerCommand(name, help, false, handler);
    }

    /** A command only operators and the console may use. */
    public static ServerCommand operator(String name, String help, Handler handler) {
        return new ServerCommand(name, help, true, handler);
    }

    /** Throw this from a handler to answer with an error message. */
    public static Failure fail(String message) {
        return new Failure(message);
    }

    @FunctionalInterface
    public interface Handler {
        /** @return the reply to whoever typed the command, or null for none */
        String run(ServerControl server, Call call);
    }

    /**
     * One use of the command.
     *
     * @param sender who typed it: a player's name, or {@code Server} for the console
     * @param player the player who typed it; empty for the console or a command block
     */
    public static final class Call {
        private final String name;
        private final List<String> args;
        private final String sender;
        private final Optional<OnlinePlayer> player;

        public Call(String name, List<String> args, String sender, Optional<OnlinePlayer> player) {
            args = Jdk8.copyList(args);

            this.name = name;
            this.args = args;
            this.sender = sender;
            this.player = player;
        }

        public String name() { return name; }

        public List<String> args() { return args; }

        public String sender() { return sender; }

        public Optional<OnlinePlayer> player() { return player; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Call)) return false;
            Call other = (Call) o;
            return java.util.Arrays.deepEquals(new Object[] {this.name, this.args, this.sender, this.player}, new Object[] {other.name, other.args, other.sender, other.player});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.args, this.sender, this.player}); }

        @Override public String toString() { return "Call[" + "name=" + name + ", " + "args=" + args + ", " + "sender=" + sender + ", " + "player=" + player + "]"; }
    }

    /** An error answer to the sender; not a module failure. */
    public static final class Failure extends RuntimeException {

        public Failure(String message) {
            super(message, null, false, false);
        }
    }
}
