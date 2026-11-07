package conductor.host.mixin;

import conductor.runtime.ServerHooks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server modules' heartbeat: the end of every server tick, and the server stopping. Dedicated and single-player. */
@Mixin(MinecraftServer.class)
abstract class ServerMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void conductor$tick(CallbackInfo ci) {
        ServerHooks.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void conductor$stop(CallbackInfo ci) {
        ServerHooks.stop((MinecraftServer) (Object) this);
    }
}
