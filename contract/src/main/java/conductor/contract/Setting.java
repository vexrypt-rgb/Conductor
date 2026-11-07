package conductor.contract;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A typed module setting: declared in {@link ConductorModule#settings()}, read with {@link ModuleContext#get},
 * saved by Conductor between games, and changeable by the player ({@code +set <module> <name> <value>}) or by a
 * settings UI module through {@link ModuleManagement}.
 */
public final class Setting<T> {

    private final String name;
    private final String description;
    private final T defaultValue;
    private final Function<String, Optional<T>> parser;

    private Setting(String name, String description, T defaultValue, Function<String, Optional<T>> parser) {
        if (!Objects.requireNonNull(name, "name").matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("setting names are 1-32 of a-z 0-9 _ -: " + name);
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.parser = parser;
        if (!parse(format(defaultValue)).isPresent()) {
            throw new IllegalArgumentException("default value is not valid for " + name);
        }
    }

    public static Setting<Boolean> bool(String name, String description, boolean defaultValue) {
        return new Setting<>(name, description, defaultValue, s -> {
            switch (s.toLowerCase(Locale.ROOT)) {
                case "true": case "on": case "yes": return Optional.of(true);
                case "false": case "off": case "no": return Optional.of(false);
                default: return Optional.empty();
            }
        });
    }

    public static Setting<Integer> integer(String name, String description, int defaultValue, int min, int max) {
        return new Setting<>(name, description, defaultValue, s -> {
            try {
                int v = Integer.parseInt(s.trim());
                return v >= min && v <= max ? Optional.of(v) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    public static Setting<Double> decimal(String name, String description, double defaultValue, double min, double max) {
        return new Setting<>(name, description, defaultValue, s -> {
            try {
                double v = Double.parseDouble(s.trim());
                return Double.isFinite(v) && v >= min && v <= max ? Optional.of(v) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    public static Setting<String> text(String name, String description, String defaultValue) {
        return new Setting<>(name, description, defaultValue, s -> s.length() <= 256 ? Optional.of(s) : Optional.empty());
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** The value {@code text} stands for, if it is valid for this setting. */
    public Optional<T> parse(String text) {
        return text == null ? Optional.empty() : parser.apply(text);
    }

    public String format(T value) {
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return name;
    }
}
