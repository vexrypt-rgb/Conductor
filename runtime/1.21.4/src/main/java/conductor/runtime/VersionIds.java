package conductor.runtime;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

/** Minecraft 1.21.4's id class (ResourceLocation), behind two methods so the shared content code needs neither name. */
public final class VersionIds {

    /** The Minecraft version this build is for; content packs use the overlay folder {@code mc_<version>}. */
    public static final String MINECRAFT = "1.21.4";

    private VersionIds() {}

    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String id) {
        return ResourceKey.create(registry, ResourceLocation.parse(id));
    }

    public static String id(ResourceKey<?> key) {
        return key.location().toString();
    }
}
