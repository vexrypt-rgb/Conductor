package conductor.runtime;

import conductor.contract.ConductorModule;
import conductor.contract.ContentModule;
import conductor.contract.ServerModule;

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
 * {@code META-INF/services/conductor.contract.ConductorModule} (or {@code ContentModule}, {@code ServerModule}): the
 * JDK's standard service mechanism. No {@code fabric.mod.json}, {@code mods.toml} or loader API is involved. Each
 * kind is looked for separately, when it is needed, with its own class loader per jar.
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

    /**
     * Module id to module, content module id to content module, server module id to server module, the jar each
     * module id came from (modules found on the game's classpath have none), plus problems that kept a jar or class
     * from loading.
     */
    public record Found(Map<String, ConductorModule> modules, Map<String, ContentModule> content,
                        Map<String, ServerModule> server, Map<String, Path> jars, List<String> problems) {

        public Found(Map<String, ConductorModule> modules, List<String> problems) {
            this(modules, Map.of(), Map.of(), Map.of(), problems);
        }
    }

    public static final String CONTENT_SERVICE_FILE = "META-INF/services/" + ContentModule.class.getName();

    public static final String SERVER_SERVICE_FILE = "META-INF/services/" + ServerModule.class.getName();

    private enum Kind { BEHAVIOUR, CONTENT, SERVER }

    private ModuleDiscovery() {}

    /** Behaviour modules ({@link ConductorModule}); call once the game exists. */
    public static Found discover(Path modulesDir, ClassLoader game) {
        return discover(modulesDir, game, Kind.BEHAVIOUR);
    }

    /** Content modules ({@link ContentModule}) only; safe before the game starts (creates no behaviour module). */
    public static Found discoverContent(Path modulesDir, ClassLoader game) {
        return discover(modulesDir, game, Kind.CONTENT);
    }

    /** Server modules ({@link ServerModule}) only; call when a server first needs them. */
    public static Found discoverServer(Path modulesDir, ClassLoader game) {
        return discover(modulesDir, game, Kind.SERVER);
    }

    private static Found discover(Path modulesDir, ClassLoader game, Kind kind) {
        Map<String, ConductorModule> modules = new LinkedHashMap<>();
        Map<String, ContentModule> content = new LinkedHashMap<>();
        Map<String, ServerModule> server = new LinkedHashMap<>();
        Map<String, Path> jarOf = new LinkedHashMap<>();
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
                loadJar(jar, game, kind, modules, content, server, jarOf, problems);
            }
        }
        if (game != null) {
            switch (kind) {
                case BEHAVIOUR -> load(ServiceLoader.load(ConductorModule.class, game), null, "classpath", modules, problems);
                case CONTENT -> load(ServiceLoader.load(ContentModule.class, game), null, "classpath", content, problems);
                case SERVER -> load(ServiceLoader.load(ServerModule.class, game), null, "classpath", server, problems);
            }
        }
        return new Found(modules, content, server, jarOf, problems);
    }

    private static void loadJar(Path jar, ClassLoader parent, Kind kind, Map<String, ConductorModule> modules,
                                Map<String, ContentModule> content, Map<String, ServerModule> server,
                                Map<String, Path> jarOf, List<String> problems) {
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent);
        } catch (MalformedURLException e) {
            problems.add(jar + ": " + e);
            return;
        }
        String name = jar.getFileName().toString();
        String source = name.substring(0, name.length() - 4);
        List<String> ids = switch (kind) {
            case BEHAVIOUR -> load(ServiceLoader.load(ConductorModule.class, loader), loader, source, modules, problems);
            case CONTENT -> load(ServiceLoader.load(ContentModule.class, loader), loader, source, content, problems);
            case SERVER -> load(ServiceLoader.load(ServerModule.class, loader), loader, source, server, problems);
        };
        ids.forEach(id -> jarOf.put(id, jar));
        if (ids.isEmpty()) {
            // nothing of this kind came from the jar: do not keep it open (an open jar cannot be replaced on Windows)
            try {
                loader.close();
            } catch (IOException e) {
                problems.add(jar + ": " + e);
            }
        }
    }

    /** @param only when not null, keep only modules defined by this loader (not ones its parent also sees) */
    /** @return the ids of the modules added */
    private static <M> List<String> load(ServiceLoader<M> services, ClassLoader only, String source,
                                         Map<String, M> modules, List<String> problems) {
        List<String> added = new ArrayList<>();
        java.util.Iterator<ServiceLoader.Provider<M>> it = services.stream().iterator();
        while (true) {
            ServiceLoader.Provider<M> provider;
            try {
                if (!it.hasNext()) {
                    return added;
                }
                provider = it.next();
            } catch (java.util.ServiceConfigurationError | LinkageError e) {
                // a broken entry; the service loader carries on with the next one
                problems.add(source + ": " + e);
                continue;
            }
            try {
                Class<? extends M> type = provider.type();
                if (only != null && type.getClassLoader() != only) {
                    continue;
                }
                if (modules.values().stream().anyMatch(m -> m.getClass().getName().equals(type.getName()))) {
                    continue;
                }
                String id = only != null ? source : type.getName();
                String key = modules.containsKey(id) ? id + "/" + type.getSimpleName() : id;
                modules.put(key, provider.get());
                added.add(key);
            } catch (java.util.ServiceConfigurationError | RuntimeException | LinkageError e) {
                problems.add(source + ": module could not be created: " + e);
            }
        }
    }
}
