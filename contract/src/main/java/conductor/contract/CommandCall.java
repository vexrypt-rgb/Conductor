package conductor.contract;

import java.util.List;

/**
 * A command the player typed in chat, for example {@code +kit check now}: name {@code kit}, args
 * {@code [check, now]}. The line was not sent to the server.
 */
public record CommandCall(String name, List<String> args, String line) {

    public CommandCall {
        args = List.copyOf(args);
    }
}
