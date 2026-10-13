package conductor.contract;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Settings as one {@code <module>.properties} file per module in a folder (by default
 * {@code <game dir>/conductor/config}). Plain text, so players can also edit them by hand while the game is closed.
 */
public final class FileSettingsStore implements SettingsStore {

    private final Path dir;

    public FileSettingsStore(Path dir) {
        this.dir = dir;
    }

    private Path file(ModuleId module) {
        return dir.resolve(module.value().replaceAll("[^A-Za-z0-9._-]", "_") + ".properties");
    }

    @Override
    public Map<String, String> load(ModuleId module) {
        Path f = file(module);
        if (!Files.isRegularFile(f)) {
            return Map.of();
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return Map.of();
        }
        Map<String, String> values = new TreeMap<>();
        p.forEach((k, v) -> values.put(String.valueOf(k), String.valueOf(v)));
        return values;
    }

    @Override
    public void save(ModuleId module, Map<String, String> values) {
        try {
            Files.createDirectories(dir);
            Properties p = new Properties();
            p.putAll(values);
            Path tmp = Files.createTempFile(dir, "settings", ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, "Conductor settings for " + module.value());
            }
            Files.move(tmp, file(module), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("cannot save settings for " + module.value() + ": " + e, e);
        }
    }
}
