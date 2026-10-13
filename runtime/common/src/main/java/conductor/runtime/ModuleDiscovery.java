package conductor.runtime;

import conductor.contract.ConductorModule;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Finds Conductor modules without any mod loader. A module is a plain jar that lists its module class in
 * {@code META-INF/services/conductor.contract.ConductorModule}: the JDK's standard service mechanism. No
 * {@code fabric.mod.json}, {@code mods.toml} or loader API is involved.
 * <p>
 * Two places are searched:
 * <ul>
 *   <li>a modules folder (by default {@code <game dir>/conductor/modules}): each jar gets its own class loader, whose
 *       parent is the game's, so contract classes come from the Conductor that is running;</li>
 *   <li>the game's own class loader: for native modules shipped inside another mod's jar (a TenorClef bridge, say).</li>
 * </ul>
 * A module class found twice is started once.
 */
public final class ModuleDiscovery {

    public static final String SERVICE_FILE = "META-INF/services/" + ConductorModule.class.getName();

    /** Requested module id to module, plus problems that kept a jar or class from loading. */
    public record Found(Map<String, ConductorModule> modules, List<String> problems) {}

    private ModuleDiscovery() {}

    public static Found discover(Path modulesDir, ClassLoader game) {
        Map<String, ConductorModule> modules = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        if (modulesDir != null && Files.isDirectory(modulesDir)) {
            List<Path> jars;
            try (Stream<Path> listing = Files.list(modulesDir)) {
                jars = listing.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
            } catch (IOException e) {
                jars = List.of();
                problems.add("cannot list " + modulesDir + ": " + e);
            }
            for (Path jar : jars) {
                loadJar(jar, game, modules, problems);
            }
        }
        if (game != null) {
            load(ServiceLoader.load(ConductorModule.class, game), null, "classpath", modules, problems);
        }
        return new Found(modules, problems);
    }

    private static void loadJar(Path jar, ClassLoader parent, Map<String, ConductorModule> modules, List<String> problems) {
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent);
        } catch (MalformedURLException e) {
            problems.add(jar + ": " + e);
            return;
        }
        String name = jar.getFileName().toString();
        load(ServiceLoader.load(ConductorModule.class, loader), loader, name.substring(0, name.length() - 4), modules, problems);
    }

    /** @param only when not null, keep only modules defined by this loader (not ones its parent also sees) */
    private static void load(ServiceLoader<ConductorModule> services, ClassLoader only, String source,
                             Map<String, ConductorModule> modules, List<String> problems) {
        java.util.Iterator<ServiceLoader.Provider<ConductorModule>> it = services.stream().iterator();
        while (true) {
            ServiceLoader.Provider<ConductorModule> provider;
            try {
                if (!it.hasNext()) {
                    return;
                }
                provider = it.next();
            } catch (java.util.ServiceConfigurationError | LinkageError e) {
                // a broken entry; the service loader carries on with the next one
                problems.add(source + ": " + e);
                continue;
            }
            try {
                Class<? extends ConductorModule> type = provider.type();
                if (only != null && type.getClassLoader() != only) {
                    continue;
                }
                if (modules.values().stream().anyMatch(m -> m.getClass().getName().equals(type.getName()))) {
                    continue;
                }
                String id = only != null ? source : type.getName();
                modules.put(modules.containsKey(id) ? id + "/" + type.getSimpleName() : id, provider.get());
            } catch (java.util.ServiceConfigurationError | RuntimeException | LinkageError e) {
                problems.add(source + ": module could not be created: " + e);
            }
        }
    }
}
