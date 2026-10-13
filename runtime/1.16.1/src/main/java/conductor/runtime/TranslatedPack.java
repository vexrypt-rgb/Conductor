package conductor.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.ResourcePackFileNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A content module's files as Minecraft 1.16.1 reads them: translated once by {@link LegacyPackTranslator} and served
 * from memory. Listing follows vanilla's zip packs ({@code FilePackResources}) so the game sees the same results.
 */
final class TranslatedPack extends AbstractPackResources {

    private final Map<String, byte[]> files;

    TranslatedPack(File jar, Map<String, byte[]> files) {
        super(jar);
        this.files = files;
    }

    @Override
    protected InputStream getResource(String path) throws IOException {
        byte[] bytes = files.get(path);
        if (bytes == null) {
            throw new ResourcePackFileNotFoundException(file, path);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    protected boolean hasResource(String path) {
        return files.containsKey(path);
    }

    @Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String path, int maxDepth, Predicate<String> filter) {
        String root = type.getDirectory() + "/" + namespace + "/";
        String under = root + path + "/";
        Collection<ResourceLocation> found = new ArrayList<>();
        for (String name : files.keySet()) {
            if (name.endsWith(".mcmeta") || !name.startsWith(under)) {
                continue;
            }
            String relative = name.substring(root.length());
            String[] parts = relative.split("/");
            // as vanilla's zip pack does (maxDepth is Integer.MAX_VALUE in practice, so this overflows to "any depth")
            if (parts.length >= maxDepth + 1 && filter.test(parts[parts.length - 1])) {
                found.add(new ResourceLocation(namespace, relative));
            }
        }
        return found;
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new HashSet<>();
        String prefix = type.getDirectory() + "/";
        for (String name : files.keySet()) {
            if (name.startsWith(prefix)) {
                String rest = name.substring(prefix.length());
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    String ns = rest.substring(0, slash);
                    if (ns.equals(ns.toLowerCase(java.util.Locale.ROOT))) {
                        namespaces.add(ns);
                    }
                }
            }
        }
        return namespaces;
    }

    @Override
    public void close() {
        // nothing open: the files are in memory
    }
}
