package conductor.host.mc10.client;

import conductor.runtime.ConductorLog;
import conductor.runtime.Hooks;
import conductor.runtime.LegacyClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.0.0: Conductor's heartbeat (the end of every client tick), and the game itself, which 1.0 keeps in a private field.
 * {@code -Dconductor.connect=host:port} joins that server at start, as newer versions' {@code --quickPlayMultiplayer}.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    @Shadow
    public abstract void setStartupServer(String address, int port);

    @Inject(method = "init", at = @At("HEAD"))
    private void conductor$init(CallbackInfo ci) {
        LegacyClient.started((Minecraft) (Object) this);
        String connect = System.getProperty("conductor.connect");
        if (connect != null && !connect.isBlank()) {
            try {
                int colon = connect.lastIndexOf(':');
                setStartupServer(colon < 0 ? connect : connect.substring(0, colon),
                        colon < 0 ? 25565 : Integer.parseInt(connect.substring(colon + 1)));
            } catch (RuntimeException e) {
                ConductorLog.warn("Conductor: cannot join {} at start: {}", connect, e.toString());
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void conductor$tick(CallbackInfo ci) {
        Hooks.clientTick();
    }
}
