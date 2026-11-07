package conductor.host.mixin;

import conductor.runtime.ServerHooks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server modules' {@code /} commands, added when the server's command registry is built. */
@Mixin(CommandManager.class)
abstract class CommandManagerMixin {

    @Inject(method = "<init>()V", at = @At("TAIL"))
    private void conductor$commands(CallbackInfo ci) {
        ServerHooks.commands((CommandRegistry) (Object) this);
    }
}
