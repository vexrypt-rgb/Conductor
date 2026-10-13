package conductor.contract;

import java.util.Set;

/**
 * Run the engine's own commands from a module, provided as the service {@link #KEY} by the engine's Conductor
 * module (for Ostinato: {@code #mine diamond_ore}, {@code #follow player Alex}, {@code #explore}, ...). This makes
 * every engine feature usable by other modules before it has its own contract capability.
 * <p>
 * Rules (the same as everywhere else in Conductor):
 * <ul>
 *   <li>Commands that move the bot ({@link #taskCommands()}) need the caller to hold the MOVEMENT lease. While it
 *       does, the engine's processes count as the caller's own (not interference).</li>
 *   <li>{@link #stop} asks the engine to stop; the stop is proven only when {@link Conductor#resourceStatus()} says
 *       AVAILABLE again.</li>
 *   <li>The command language belongs to the engine; check {@link #engine()} if you depend on details.</li>
 * </ul>
 * Game thread only.
 */
public interface EngineCommands {

    ServiceKey<EngineCommands> KEY = new ServiceKey<>("conductor:engine-commands", EngineCommands.class);

    /** Engine name and version, e.g. {@code Ostinato 1.21.0}. */
    String engine();

    /** Command names that move the bot and therefore need the MOVEMENT lease. */
    Set<String> taskCommands();

    /** Run one command line (without the engine's chat prefix) for {@code caller}. */
    ActionResult run(ModuleContext caller, String commandLine);

    /** Ask the engine to stop everything it does for {@code caller}. Needs the caller to hold MOVEMENT. */
    ActionResult stop(ModuleContext caller);
}
