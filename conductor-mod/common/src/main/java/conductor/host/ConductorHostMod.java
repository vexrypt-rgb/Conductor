package conductor.host;

import conductor.contract.ConductorModule;
import conductor.runtime.ostinato.ConductorBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The Conductor mod for one Minecraft version. Finds every module declared under the {@code "conductor"}
 * entrypoint in any mod's {@code fabric.mod.json} and starts it.
 * <p>
 * Conductor currently serves one consumer, so only the first module found is started; the others are named in the
 * log as not started.
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
        EntrypointContainer<ConductorModule> first = found.get(0);
        LOG.info("Conductor starting module {} from mod {}", first.getEntrypoint().getClass().getName(),
                first.getProvider().getMetadata().getId());
        for (EntrypointContainer<ConductorModule> other : found.subList(1, found.size())) {
            LOG.warn("Conductor serves one module at a time; not starting {} from mod {}",
                    other.getEntrypoint().getClass().getName(), other.getProvider().getMetadata().getId());
        }
        ConductorBootstrap.start(first.getEntrypoint());
    }
}
