package conductor.runtime;

import java.util.Map;

/**
 * Content modules on Minecraft 1.8.9 are not installed. Data packs (recipes, loot, tags, dimensions) arrived in 1.13,
 * and 1.8.9 has no way to add blocks, items or mobs from a data file, so this version offers no content at all. The
 * shared code asks for these tables; they are empty here.
 */
public final class ContentInstaller {

    /** The Minecraft version, for the modules' {@code minecraftVersion()}. */
    public static final String MINECRAFT = "1.8.9";

    private ContentInstaller() {}

    /** No content blocks, so no see-through render layers to set. */
    public static Map<Object, Object> renderLayers() {
        return Jdk8.mapOf();
    }

    /** No content mobs, so no renderers to add. */
    public static Map<Object, Object> mobs() {
        return Jdk8.mapOf();
    }
}
