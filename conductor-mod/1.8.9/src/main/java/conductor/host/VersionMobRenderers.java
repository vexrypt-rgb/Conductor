package conductor.host;

import java.util.Map;

/** Minecraft 1.8.9 has no content mobs (see ContentInstaller), so there are no renderers to add. */
public final class VersionMobRenderers {

    private VersionMobRenderers() {}

    /** Adds a renderer for each mob; returns how many. None here. */
    public static int apply(Map<?, ?> mobs) {
        return 0;
    }
}
