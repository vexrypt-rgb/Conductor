package conductor.host;

import conductor.contract.MobDeclaration;
import net.minecraft.world.entity.EntityType;

import java.util.Map;

/** Minecraft 1.16.1: no declared mobs yet (see ContentInstaller), so no renderers to set up. Client only. */
final class VersionMobRenderers {

    private VersionMobRenderers() {}

    static int apply(Map<EntityType<?>, MobDeclaration> mobs) {
        return 0;
    }
}
