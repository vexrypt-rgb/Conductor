package conductor.host.mixin;

import conductor.runtime.ServerHooks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Server modules' heartbeat: the end of every server tick, and the server stopping. Dedicated and single-player. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void conductor$tick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ServerHooks.tick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void conductor$stop(CallbackInfo ci) {
        ServerHooks.stop((MinecraftServer) (Object) this);
    }
}
