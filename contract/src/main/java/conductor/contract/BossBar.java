package conductor.contract;

/**
 * The boss bar a custom mob shows at the top of the screen of players near it, filled by its health, like the
 * wither's. Change its colour later with {@link MobControl#bossBar}, e.g. for a second phase.
 */
public final class BossBar {
    private final Color color;
    private final Style style;

    public BossBar(Color color, Style style) {
        this.color = color;
        this.style = style;
    }

    public Color color() { return color; }

    public Style style() { return style; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BossBar)) return false;
        BossBar other = (BossBar) o;
        return java.util.Arrays.deepEquals(new Object[] {this.color, this.style}, new Object[] {other.color, other.style});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.color, this.style}); }

    @Override public String toString() { return "BossBar[" + "color=" + color + ", " + "style=" + style + "]"; }


    /** Vanilla's boss bar colours. */
    public enum Color { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }

    /** A plain bar, or one split into 6, 10, 12 or 20 notches. */
    public enum Style { PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 }
}
