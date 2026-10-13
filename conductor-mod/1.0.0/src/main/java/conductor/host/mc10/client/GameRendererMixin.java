package conductor.host.mc10.client;

import conductor.host.VersionLineDrawer;
import conductor.runtime.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.0.0: when the world is drawn and the held item not yet, Conductor draws module overlays. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Shadow
    private Minecraft minecraft;

    @Inject(method = "renderWorld", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=hand"))
    private void conductor$render(float tickDelta, long finishNanoTime, CallbackInfo ci) {
        if (minecraft.camera != null) {
            Hooks.renderWorld(new VersionLineDrawer.Frame(minecraft.camera, tickDelta));
        }
    }
}
