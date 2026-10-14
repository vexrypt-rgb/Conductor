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
    public static final class Found {
        private final Map<String, ConductorModule> modules;
        private final Map<String, ContentModule> content;
        private final Map<String, ServerModule> server;
        private final Map<String, Path> jars;
        private final List<String> problems;

        public Found(Map<String, ConductorModule> modules, Map<String, ContentModule> content, Map<String, ServerModule> server, Map<String, Path> jars, List<String> problems) {
            this.modules = modules;
            this.content = content;
            this.server = server;
            this.jars = jars;
            this.problems = problems;
        }

        public Map<String, ConductorModule> modules() {
            return modules;
        }

        public Map<String, ContentModule> content() {
            return content;
        }

        public Map<String, ServerModule> server() {
            return server;
        }

        public Map<String, Path> jars() {
            return jars;
        }

        public List<String> problems() {
            return problems;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Found)) return false;
            Found other = (Found) o;
            return java.util.Objects.equals(this.modules, other.modules) && java.util.Objects.equals(this.content, other.content) && java.util.Objects.equals(this.server, other.server) && java.util.Objects.equals(this.jars, other.jars) && java.util.Objects.equals(this.problems, other.problems);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(modules, content, server, jars, problems);
        }

        @Override public String toString() {
            return "Found[" + "modules=" + modules + ", content=" + content + ", server=" + server + ", jars=" + jars + ", problems=" + problems + "]";
        }

        public Found(Map<String, ConductorModule> modules, List<String> problems) {
            this(modules, Jdk8.mapOf(), Jdk8.mapOf(), Jdk8.mapOf(), problems);
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
                jars = listing.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().collect(java.util.stream.Collectors.toList());
            } catch (IOException e) {
                jars = Jdk8.listOf();
                problems.add("cannot list " + modulesDir + ": " + e);
            }
            for (Path jar : jars) {
                loadJar(jar, game, kind, modules, content, server, jarOf, problems);
            }
        }
        if (game != null) {
            switch (kind) {
                case BEHAVIOUR:
                    load(ServiceLoader.load(ConductorModule.class, game), null, "classpath", modules, problems);
                    break;
                case CONTENT:
                    load(ServiceLoader.load(ContentModule.class, game), null, "classpath", content, problems);
                    break;
                case SERVER:
                    load(ServiceLoader.load(ServerModule.class, game), null, "classpath", server, problems);
                    break;
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
        List<String> ids = kind == Kind.BEHAVIOUR
                ? load(ServiceLoader.load(ConductorModule.class, loader), loader, source, modules, problems)
                : kind == Kind.CONTENT
                ? load(ServiceLoader.load(ContentModule.class, loader), loader, source, content, problems)
                : load(ServiceLoader.load(ServerModule.class, loader), loader, source, server, problems);
        ids.forEach(id -> jarOf.put(id, jar));
    }

    /** @param only when not null, keep only modules defined by this loader (not ones its parent also sees) */
    /** @return the ids of the modules added */
    private static <M> List<String> load(ServiceLoader<M> services, ClassLoader only, String source,
                                         Map<String, M> modules, List<String> problems) {
        List<String> added = new ArrayList<>();
        java.util.Iterator<M> it = services.iterator();
        while (true) {
            M module;
            try {
                if (!it.hasNext()) {
                    return added;
                }
                module = it.next();
            } catch (java.util.ServiceConfigurationError | LinkageError e) {
                // a broken entry; the service loader carries on with the next one
                problems.add(source + ": " + e);
                continue;
            }
            try {
                Class<?> type = module.getClass();
                if (only != null && type.getClassLoader() != only) {
                    continue;
                }
                if (modules.values().stream().anyMatch(m -> m.getClass().getName().equals(type.getName()))) {
                    continue;
                }
                String id = only != null ? source : type.getName();
                String key = modules.containsKey(id) ? id + "/" + type.getSimpleName() : id;
                modules.put(key, module);
                added.add(key);
            } catch (java.util.ServiceConfigurationError | RuntimeException | LinkageError e) {
                problems.add(source + ": module could not be created: " + e);
            }
        }
    }
}
