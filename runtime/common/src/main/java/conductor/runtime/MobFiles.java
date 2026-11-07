package conductor.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a custom mob's model and animation files straight from its module's jar. A file under
 * {@code mc_<version>/} wins over the plain one, as for the jar's other assets.
 */
public final class MobFiles {

    private static final long MAX_SIZE = 8L << 20;

    private MobFiles() {}

    /** The module jar of the content module with this namespace, or null. */
    public static Path jar(String namespace) {
        for (conductor.contract.ContentPlan.Entry e : ContentHooks.plan().entries()) {
            if (e.namespace().equals(namespace)) {
                return ContentHooks.jars().get(e.moduleId());
            }
        }
        return null;
    }

    /** The text of {@code path} in {@code jar}, preferring {@code mc_<minecraft>/path}; throws if neither exists. */
    public static String read(Path jar, String path, String minecraft) throws IOException {
        if (jar == null) {
            throw new IOException("no module jar");
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("mc_" + minecraft + "/" + path);
            if (entry == null) {
                entry = zip.getEntry(path);
            }
            if (entry == null) {
                throw new IOException(path + " is not in " + jar.getFileName());
            }
            if (entry.getSize() > MAX_SIZE) {
                throw new IOException(path + " is larger than 8 MiB");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while (out.size() <= MAX_SIZE && (n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                byte[] bytes = out.toByteArray();
                if (bytes.length > MAX_SIZE) {
                    throw new IOException(path + " is larger than 8 MiB");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }
}
