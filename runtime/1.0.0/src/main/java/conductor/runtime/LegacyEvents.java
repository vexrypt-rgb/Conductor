package conductor.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the Conductor mod's 1.0.0 hooks call, in 1.0's own terms (block numbers, chat lines as plain strings), turned
 * into {@link Hooks} events. 1.0 handles every packet on the game thread, in the client tick.
 */
public final class LegacyEvents {

    /** A player's chat line as a 1.0 server sends it: {@code <name> text}. Names are 1 to 16 letters, digits and _. */
    private static final Pattern PLAYER_LINE = Pattern.compile("<([A-Za-z0-9_]{1,16})> (.*)", Pattern.DOTALL);

    /** The longest chat line a 1.0 client reads; the game cuts longer ones off. */
    static final int MAX_LINE = 119;

    private LegacyEvents() {}

    public static void blockChanged(int x, int y, int z, int block, int metadata) {
        Hooks.blockChanged(x, y, z, LegacyIds.block(block, metadata));
    }

    /** A chat line arrived. A player's line goes on as its text and the sender's offline id; anything else as is. */
    public static void chat(String line) {
        if (line == null) {
            return;
        }
        String[] player = playerLine(line);
        if (player != null) {
            Hooks.chatReceived(player[1], LegacyIds.offlineId(player[0]));
        } else {
            Hooks.chatReceived(line, null);
        }
    }

    /** The name and text of a player's chat line, or null for any other line. */
    static String[] playerLine(String line) {
        Matcher m = PLAYER_LINE.matcher(line);
        return m.matches() ? new String[] {m.group(1), m.group(2)} : null;
    }

    /** Splits a message into lines a 1.0 client shows whole: at line breaks, then every {@link #MAX_LINE} characters. */
    public static List<String> chatLines(String message) {
        List<String> out = new ArrayList<>();
        for (String line : message.split("\n", -1)) {
            if (line.isEmpty()) {
                out.add(line);
            }
            for (int at = 0; at < line.length(); at += MAX_LINE) {
                out.add(line.substring(at, Math.min(line.length(), at + MAX_LINE)));
            }
        }
        return out;
    }
}
