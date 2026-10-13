package conductor.host.mixin;

import com.mojang.brigadier.CommandDispatcher;
import conductor.runtime.ServerHooks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server modules' {@code /} commands, added each time vanilla builds its command tree (server start, {@code /reload}).
 * The constructor's arguments differ between versions (1.19 added one); the handler takes none of them.
 */
@Mixin(Commands.class)
abstract class CommandsMixin {

    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void conductor$commands(CallbackInfo ci) {
        ServerHooks.commands(dispatcher);
    }
}
