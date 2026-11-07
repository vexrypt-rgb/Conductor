package conductor.host.mixin;

import conductor.host.VersionLineDrawer;
import conductor.runtime.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.8.9: after the world is drawn, Conductor draws module overlays from the camera's pose. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void conductor$render(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        if (camera == null) {
            return;
        }
        Vec3d p = camera.getCameraPosVec(partialTicks);
        Hooks.renderWorld(new VersionLineDrawer.Frame(camera.pitch, camera.yaw, p.x, p.y, p.z));
    }
}
