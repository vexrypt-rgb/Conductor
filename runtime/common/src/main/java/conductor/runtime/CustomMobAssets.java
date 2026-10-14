package conductor.runtime;

import conductor.contract.MobDeclaration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom mob's model and animations, read from its module's jar once and kept. A file that is missing or wrong is
 * reported once in the log, with the reason; the mob is then drawn with no model (nothing) and plays no animations,
 * and the game keeps running.
 */
public final class CustomMobAssets {

    /** What a custom mob is drawn with. */
    public static final class Loaded {
        private final MobGeometry geometry;
        private final MobAnimations animations;

        public Loaded(MobGeometry geometry, MobAnimations animations) {
            this.geometry = geometry;
            this.animations = animations;
        }

        public MobGeometry geometry() {
            return geometry;
        }

        public MobAnimations animations() {
            return animations;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Loaded)) return false;
            Loaded other = (Loaded) o;
            return java.util.Objects.equals(this.geometry, other.geometry) && java.util.Objects.equals(this.animations, other.animations);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(geometry, animations);
        }

        @Override public String toString() {
            return "Loaded[" + "geometry=" + geometry + ", animations=" + animations + "]";
        }
    }

    private static final Map<String, Optional<Loaded>> CACHE = new ConcurrentHashMap<>();

    private CustomMobAssets() {}

    /** The custom mob's files for this Minecraft version, or null if it has none or they could not be read. */
    public static Loaded get(MobDeclaration d, String minecraft) {
        if (!d.custom().isPresent()) {
            return null;
        }
        return CACHE.computeIfAbsent(d.id(), id -> load(d, minecraft)).orElse(null);
    }

    private static Optional<Loaded> load(MobDeclaration d, String minecraft) {
        MobDeclaration.Custom c = d.custom().get();
        String namespace = d.id().substring(0, d.id().indexOf(':'));
        java.nio.file.Path jar = MobFiles.jar(namespace);
        String file = c.model();
        try {
            MobGeometry geometry = MobGeometry.parse(MobFiles.read(jar, file, minecraft));
            MobAnimations animations = MobAnimations.NONE;
            if (c.animations().isPresent()) {
                file = c.animations().get();
                animations = MobAnimations.parse(MobFiles.read(jar, file, minecraft));
                java.util.List<String> unknown = animations.unknownBones(geometry.boneNames());
                if (!unknown.isEmpty()) {
                    log().warn("Conductor mob {}: animations move bones {} that the model does not have; those parts do nothing",
                            d.id(), unknown);
                }
            }
            return Optional.of(new Loaded(geometry, animations));
        } catch (java.io.IOException | RuntimeException e) {
            log().error("Conductor mob {}: cannot use {}: {}", d.id(), file, e.getMessage());
            return Optional.empty();
        }
    }

    private static org.apache.logging.log4j.Logger log() {
        return org.apache.logging.log4j.LogManager.getLogger("conductor");
    }
}
