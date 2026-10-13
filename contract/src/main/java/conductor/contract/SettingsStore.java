package conductor.contract;

import java.util.Map;

/** Where module settings are kept between games. Values are stored as text. */
public interface SettingsStore {

    /** Saved values for {@code module}; empty when there are none. */
    Map<String, String> load(ModuleId module);

    void save(ModuleId module, Map<String, String> values);

    /** Keeps nothing between games. */
    SettingsStore IN_MEMORY = new SettingsStore() {
        private final Map<ModuleId, Map<String, String>> saved = new java.util.HashMap<>();

        @Override
        public Map<String, String> load(ModuleId module) {
            return saved.getOrDefault(module, Map.of());
        }

        @Override
        public void save(ModuleId module, Map<String, String> values) {
            saved.put(module, Map.copyOf(values));
        }
    };
}
