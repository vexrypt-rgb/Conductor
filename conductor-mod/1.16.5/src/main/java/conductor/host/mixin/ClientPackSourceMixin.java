package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Content modules' resource packs, added where vanilla adds its own resources (Minecraft 1.16.5). Client only. */
@Mixin(ClientPackSource.class)
abstract class ClientPackSourceMixin {

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void conductor$contentPacks(Consumer<Pack> add, Pack.PackConstructor constructor, CallbackInfo ci) {
        ContentInstaller.loadPacks(PackType.CLIENT_RESOURCES, add, constructor);
    }
}
