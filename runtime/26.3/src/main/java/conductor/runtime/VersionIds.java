package conductor.runtime;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;

/** Minecraft 26.3's id class (Identifier), behind two methods so the shared content code needs neither name. */
public final class VersionIds {

    /** The Minecraft version this build is for; content packs use the overlay folder {@code mc_<version>}. */
    public static final String MINECRAFT = "26.3";

    private VersionIds() {}

    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String id) {
        return ResourceKey.create(registry, Identifier.parse(id));
    }

    public static String id(ResourceKey<?> key) {
        return key.identifier().toString();
    }

    /** A sound event by id that need not be registered (a module's own sound from its {@code sounds.json}). */
    public static SoundEvent soundEvent(String id) {
        return SoundEvent.createVariableRangeEvent(Identifier.parse(id));
    }
}
