package conductor.contract;

/**
 * The boss bar a custom mob shows at the top of the screen of players near it, filled by its health, like the
 * wither's. Change its colour later with {@link MobControl#bossBar}, e.g. for a second phase.
 */
public record BossBar(Color color, Style style) {

    /** Vanilla's boss bar colours. */
    public enum Color { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }

    /** A plain bar, or one split into 6, 10, 12 or 20 notches. */
    public enum Style { PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 }
}
