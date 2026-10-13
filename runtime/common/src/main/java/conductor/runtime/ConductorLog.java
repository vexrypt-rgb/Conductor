package conductor.runtime;

/**
 * Conductor's log: log4j's "conductor" logger when the game has log4j (every version from 1.7 on), plain stdout and
 * stderr otherwise (Minecraft 1.0, whose loader adds no logging library). Messages use log4j's {@code {}}
 * placeholders; a Throwable as the last argument is printed with its stack trace, as log4j does.
 */
public final class ConductorLog {

    private ConductorLog() {
    }

    private static final boolean LOG4J = present("org.apache.logging.log4j.LogManager");

    private static boolean present(String name) {
        try {
            Class.forName(name, false, ConductorLog.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void info(String message, Object... args) {
        if (LOG4J) {
            Log4j.LOG.info(message, args);
        } else {
            print(System.out, "INFO", message, args);
        }
    }

    public static void warn(String message, Object... args) {
        if (LOG4J) {
            Log4j.LOG.warn(message, args);
        } else {
            print(System.out, "WARN", message, args);
        }
    }

    public static void error(String message, Object... args) {
        if (LOG4J) {
            Log4j.LOG.error(message, args);
        } else {
            print(System.err, "ERROR", message, args);
        }
    }

    /** log4j's formatting: each {@code {}} takes the next argument; a leftover Throwable is printed after. */
    static String format(String message, Object[] args) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        int from = 0;
        for (int at = message.indexOf("{}"); at >= 0; at = message.indexOf("{}", from)) {
            out.append(message, from, at);
            if (next < args.length) {
                out.append(args[next++]);
            } else {
                out.append("{}");
            }
            from = at + 2;
        }
        return out.append(message.substring(from)).toString();
    }

    private static synchronized void print(java.io.PrintStream stream, String level, String message, Object[] args) {
        stream.println("[" + Thread.currentThread().getName() + "/" + level + "] (conductor) " + format(message, args));
        if (args.length > 0 && args[args.length - 1] instanceof Throwable t && placeholders(message) < args.length) {
            t.printStackTrace(stream);
        }
    }

    private static int placeholders(String message) {
        int n = 0;
        for (int at = message.indexOf("{}"); at >= 0; at = message.indexOf("{}", at + 2)) {
            n++;
        }
        return n;
    }

    /** Loaded only when log4j is there. */
    private static final class Log4j {
        static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("conductor");
    }
}
