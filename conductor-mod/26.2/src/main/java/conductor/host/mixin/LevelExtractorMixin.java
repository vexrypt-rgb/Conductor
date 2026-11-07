package conductor.host.mixin;

import conductor.host.VersionLineDrawer;
import conductor.runtime.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: module overlays are emitted as gizmos while the frame's level state is extracted, just before vanilla hands
 * this frame's gizmos to the level renderer (the same point its debug renderers use).
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {

    @Inject(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractGizmos()V"))
    private void conductor$render(DeltaTracker delta, Camera camera, float partialTick, CallbackInfo ci) {
        Hooks.renderWorld(new VersionLineDrawer.Frame(camera));
    }
}
