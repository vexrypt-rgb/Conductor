package conductor.host.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import conductor.host.VersionLineDrawer;
import conductor.runtime.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.4: after the world is drawn, Conductor draws module overlays. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void conductor$render(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean renderBlockOutline,
                                  Camera camera, GameRenderer gameRenderer, Matrix4f modelView, Matrix4f projection,
                                  CallbackInfo ci) {
        Hooks.renderWorld(new VersionLineDrawer.Frame(modelView, camera));
    }
}
