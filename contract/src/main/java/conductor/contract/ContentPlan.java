package conductor.contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the installed content modules declare, checked and in a fixed order: by namespace, then in declaration
 * order. The order matters: items and blocks get their network ids in registration order, so a client and server
 * with the same modules must register them identically, whatever order the modules were found in.
 */
public record ContentPlan(List<Entry> entries, List<String> problems) {

    /** One accepted content module. */
    public record Entry(String moduleId, String namespace, List<ItemDeclaration> items, List<BlockDeclaration> blocks) {}

    public static ContentPlan of(Map<String, ContentModule> modules) {
        List<String> problems = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ContentModule> m : modules.entrySet()) {
            String moduleId = m.getKey();
            ContentDeclarations.Checked checked;
            try {
                String ns = m.getValue().namespace();
                if (ns == null) {
                    problems.add(moduleId + ": skipped: namespace is null");
                    continue;
                }
                ContentDeclarations declarations = new ContentDeclarations(ns);
                m.getValue().declare(declarations);
                checked = declarations.check();
            } catch (RuntimeException | LinkageError e) {
                problems.add(moduleId + ": skipped: declaring content threw " + e);
                continue;
            }
            if (!checked.ok()) {
                checked.problems().forEach(p -> problems.add(moduleId + ": skipped: " + p));
                continue;
            }
            entries.add(new Entry(moduleId, checked.namespace(), checked.items(), checked.blocks()));
        }
        // One module per namespace. Ties are broken by module id, so the winner doesn't depend on discovery order.
        entries.sort(Comparator.comparing(Entry::namespace).thenComparing(Entry::moduleId));
        Map<String, String> owner = new HashMap<>();
        List<Entry> kept = new ArrayList<>();
        for (Entry e : entries) {
            String first = owner.putIfAbsent(e.namespace(), e.moduleId());
            if (first != null) {
                problems.add(e.moduleId() + ": skipped: namespace '" + e.namespace() + "' already used by " + first);
            } else {
                kept.add(e);
            }
        }
        return new ContentPlan(List.copyOf(kept), List.copyOf(problems));
    }

    public int itemCount() {
        return entries.stream().mapToInt(e -> e.items().size() + (int) e.blocks().stream().filter(BlockDeclaration::withItem).count()).sum();
    }

    public int blockCount() {
        return entries.stream().mapToInt(e -> e.blocks().size()).sum();
    }
}
