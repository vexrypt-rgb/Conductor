package conductor.host.mixin;

import conductor.host.ShaderMaterials;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional: only applies when Iris is installed ({@code @Pseudo}: no Iris, no target, nothing happens). When Iris
 * builds the block-to-material map from a shader pack, module blocks get the material of their vanilla lookalike
 * ({@link ShaderMaterials}). Iris is not a dependency; this names its class by string. The method has kept this name
 * and shape from Iris 1.8 (Minecraft 1.21.4) to 1.10 (1.21.11).
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping", remap = false)
abstract class IrisBlockIdsMixin {

    @Inject(method = "createBlockStateIdMap", at = @At("RETURN"), cancellable = true, require = 0)
    private static void conductor$contentBlocks(CallbackInfoReturnable<Object2IntMap<BlockState>> cir) {
        cir.setReturnValue(ShaderMaterials.addContentBlocks(cir.getReturnValue()));
    }
}
