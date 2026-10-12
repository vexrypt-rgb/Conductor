package conductor.example;

import conductor.contract.ReleasePolicy;
import conductor.runtime.ostinato.ConductorBootstrap;
import net.fabricmc.api.ClientModInitializer;

import java.util.OptionalLong;

/** Fabric entrypoint: the only place that knows the module runs on Ostinato. */
public final class WalkAndStopMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // How long the engine must look quiet before Conductor calls the movement resource free again.
        // There is no default on purpose; 20 ticks (1 s) covered every stop seen in testing.
        ReleasePolicy policy = new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
        ConductorBootstrap.start(new WalkAndStop(), policy);
    }
}
