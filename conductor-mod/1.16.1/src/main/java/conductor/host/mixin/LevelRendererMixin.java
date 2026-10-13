package conductor.host.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import conductor.host.VersionLineDrawer;
import conductor.runtime.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16.1: after the world is drawn, Conductor draws module overlays. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void conductor$render(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                  Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                  CallbackInfo ci) {
        Hooks.renderWorld(new VersionLineDrawer.Frame(poseStack.last().pose().copy(), camera));
    }
}
