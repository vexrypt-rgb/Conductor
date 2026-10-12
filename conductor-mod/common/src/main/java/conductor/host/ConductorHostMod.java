package conductor.host;

import conductor.contract.ConductorModule;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.runtime.ostinato.ConductorBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Conductor mod for one Minecraft version. Finds every module declared under the {@code "conductor"}
 * entrypoint in any mod's {@code fabric.mod.json} and starts them all, side by side.
 */
public final class ConductorHostMod implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");

    @Override
    public void onInitializeClient() {
        List<EntrypointContainer<ConductorModule>> found =
                FabricLoader.getInstance().getEntrypointContainers("conductor", ConductorModule.class);
        if (found.isEmpty()) {
            LOG.info("Conductor loaded; no modules declared a \"conductor\" entrypoint");
            return;
        }
        Map<String, ConductorModule> modules = new LinkedHashMap<>();
        for (EntrypointContainer<ConductorModule> c : found) {
            String id = c.getProvider().getMetadata().getId();
            // several entrypoints in one mod get distinct ids from the host (id, id#2, ...)
            modules.put(modules.containsKey(id) ? id + "/" + c.getEntrypoint().getClass().getSimpleName() : id, c.getEntrypoint());
        }
        LOG.info("Conductor starting {} module(s): {}", modules.size(), modules.keySet());
        ConductorBootstrap.start(modules, new InMemoryDiagnosticSink(50_000));
    }
}
