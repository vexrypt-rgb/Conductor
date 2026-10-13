package conductor.host;

import java.util.Map;

/** 1.0.0 has no content mobs (see ContentInstaller), so no renderers to add. */
public final class VersionMobRenderers {

    private VersionMobRenderers() {}

    public static int apply(Map<String, Object> mobs) {
        return 0;
    }
}
