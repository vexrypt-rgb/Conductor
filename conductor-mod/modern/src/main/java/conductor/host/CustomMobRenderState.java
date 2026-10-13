package conductor.host;

import conductor.runtime.MobAnimations;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A custom mob's frame: vanilla's living-entity state plus the animation pose worked out for it. Client only. */
public class CustomMobRenderState extends LivingEntityRenderState {
    public final MobAnimations.Pose pose = new MobAnimations.Pose();
}
