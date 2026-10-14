package conductor.runtime;

import conductor.contract.ContentId;
import conductor.contract.ContentResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Engine features a module can ask about with {@link ContentId#engine(String)}. Each one is answered by whether the
 * running Ostinato build contains the code for it.
 * <p>
 * PRESENT means the build has the feature's code; it says nothing about whether the feature can run right now (that
 * needs its own capability and observations). Ids not in this table are UNKNOWN, never ABSENT.
 */
final class EngineFeatures {

    /** Feature id to the Ostinato class that implements it. */
    static final Map<String, String> TABLE;

    static {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("ostinato:pvp", "baritone.process.PvpProcess");
        t.put("ostinato:pve", "baritone.process.PveProcess");
        t.put("ostinato:combat/spear", "baritone.process.CombatSpear");
        t.put("ostinato:combat/mace", "baritone.process.CombatMace");
        t.put("ostinato:combat/shield", "baritone.process.CombatShield");
        t.put("ostinato:combat/pearl", "baritone.process.CombatPearl");
        t.put("ostinato:combat/wind", "baritone.process.CombatWind");
        t.put("ostinato:combat/elytra", "baritone.process.CombatElytra");
        t.put("ostinato:combat/potions", "baritone.process.CombatPotions");
        t.put("ostinato:combat/buckets", "baritone.process.CombatBuckets");
        t.put("ostinato:combat/mending", "baritone.process.CombatMending");
        t.put("ostinato:pvp/autotune", "baritone.process.CombatTuner");
        TABLE = Jdk8.copyMap(t);
        IDS = Jdk8.copyList(t.keySet());
    }

    static final List<String> IDS;

    private final Predicate<String> classPresent;

    EngineFeatures(Predicate<String> classPresent) {
        this.classPresent = classPresent;
    }

    /** Looks classes up in the loader that loaded Ostinato's API, without initialising them. */
    static EngineFeatures ofLoader(ClassLoader loader) {
        return new EngineFeatures(name -> {
            try {
                Class.forName(name, false, loader);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }

    ContentResult check(ContentId content) {
        String cls = TABLE.get(content.id());
        if (cls == null) {
            return new ContentResult(content, ContentResult.Presence.UNKNOWN, "not an engine feature this runtime knows");
        }
        try {
            return classPresent.test(cls)
                    ? new ContentResult(content, ContentResult.Presence.PRESENT, cls + " present")
                    : new ContentResult(content, ContentResult.Presence.ABSENT, cls + " not in this Ostinato build");
        } catch (RuntimeException | LinkageError e) {
            return new ContentResult(content, ContentResult.Presence.UNKNOWN, "probe failed: " + e);
        }
    }

    List<String> present() {
        return IDS.stream().filter(id -> check(ContentId.engine(id)).isPresent()).collect(java.util.stream.Collectors.toList());
    }
}
