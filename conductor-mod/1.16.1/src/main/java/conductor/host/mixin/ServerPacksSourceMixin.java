package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Content modules' data packs, added where vanilla adds its built-in data pack (Minecraft 1.16.1). */
@Mixin(ServerPacksSource.class)
abstract class ServerPacksSourceMixin {

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private <T extends Pack> void conductor$contentPacks(Consumer<T> add, Pack.PackConstructor<T> constructor, CallbackInfo ci) {
        ContentInstaller.loadPacks(PackType.SERVER_DATA, add, constructor);
    }
}
