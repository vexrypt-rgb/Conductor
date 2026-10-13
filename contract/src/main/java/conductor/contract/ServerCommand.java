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
public record ServerCommand(String name, String help, boolean operator, Handler handler) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public ServerCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("command name must be 1-32 of a-z 0-9 _ -: " + name);
        }
        help = help == null ? "" : help;
    }

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
    public record Call(String name, List<String> args, String sender, Optional<OnlinePlayer> player) {

        public Call {
            args = List.copyOf(args);
        }
    }

    /** An error answer to the sender; not a module failure. */
    public static final class Failure extends RuntimeException {

        public Failure(String message) {
            super(message, null, false, false);
        }
    }
}
