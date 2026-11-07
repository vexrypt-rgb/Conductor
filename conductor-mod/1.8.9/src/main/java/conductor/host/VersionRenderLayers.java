package conductor.host;

import java.util.Map;

/** Minecraft 1.8.9 has no content blocks (see ContentInstaller), so there are no render layers to set. */
public final class VersionRenderLayers {

    private VersionRenderLayers() {}

    /** Puts each block in the layer it asked for; returns how many. None here. */
    public static int apply(Map<?, ?> layers) {
        return 0;
    }
}
