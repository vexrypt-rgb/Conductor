package conductor.host.mc10.server;

import conductor.runtime.ServerHooks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server modules tick at the end of each server tick and stop when the server shuts down. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void conductor$tick(CallbackInfo ci) {
        ServerHooks.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void conductor$shutdown(CallbackInfo ci) {
        ServerHooks.stop();
    }
}
