package conductor.host;

import java.util.Map;

/** 1.0.0 has no content blocks (see ContentInstaller), so no render layers to set. */
public final class VersionRenderLayers {

    private VersionRenderLayers() {}

    public static int apply(Map<String, Object> layers) {
        return 0;
    }
}
