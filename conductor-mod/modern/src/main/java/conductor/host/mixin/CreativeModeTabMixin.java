package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Set;

/** Lists module items in the creative tabs they asked for. */
@Mixin(CreativeModeTab.class)
abstract class CreativeModeTabMixin {

    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void conductor$addModuleItems(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        try {
            ContentInstaller.addTabItems((CreativeModeTab) (Object) this, displayItems, displayItemsSearchTab);
        } catch (RuntimeException | LinkageError e) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").warn("Conductor could not list module items in a creative tab: {}", e.toString());
        }
    }
}
