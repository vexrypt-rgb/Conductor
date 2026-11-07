package conductor.host.mc10.server;

import conductor.runtime.ServerHooks;
import net.minecraft.command.handler.CommandHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PendingCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A console command (and an operator's, which 1.0 queues here too): module commands first. */
@Mixin(CommandHandler.class)
abstract class CommandHandlerMixin {

    @Shadow
    private MinecraftServer server;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void conductor$command(PendingCommand command, CallbackInfo ci) {
        if (ServerHooks.command(server, command.command, null, command.source)) {
            ci.cancel();
        }
    }
}
