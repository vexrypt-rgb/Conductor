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
public final class ContentPlan {
    private final List<Entry> entries;
    private final List<String> problems;

    public ContentPlan(List<Entry> entries, List<String> problems) {
        this.entries = entries;
        this.problems = problems;
    }

    public List<Entry> entries() { return entries; }

    public List<String> problems() { return problems; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentPlan)) return false;
        ContentPlan other = (ContentPlan) o;
        return java.util.Arrays.deepEquals(new Object[] {this.entries, this.problems}, new Object[] {other.entries, other.problems});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.entries, this.problems}); }

    @Override public String toString() { return "ContentPlan[" + "entries=" + entries + ", " + "problems=" + problems + "]"; }


    /** One accepted content module. */
    public static final class Entry {
        private final String moduleId;
        private final String namespace;
        private final List<ItemDeclaration> items;
        private final List<BlockDeclaration> blocks;
        private final List<MobDeclaration> mobs;

        public Entry(String moduleId, String namespace, List<ItemDeclaration> items, List<BlockDeclaration> blocks, List<MobDeclaration> mobs) {
            this.moduleId = moduleId;
            this.namespace = namespace;
            this.items = items;
            this.blocks = blocks;
            this.mobs = mobs;
        }

        public String moduleId() { return moduleId; }

        public String namespace() { return namespace; }

        public List<ItemDeclaration> items() { return items; }

        public List<BlockDeclaration> blocks() { return blocks; }

        public List<MobDeclaration> mobs() { return mobs; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry other = (Entry) o;
            return java.util.Arrays.deepEquals(new Object[] {this.moduleId, this.namespace, this.items, this.blocks, this.mobs}, new Object[] {other.moduleId, other.namespace, other.items, other.blocks, other.mobs});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.moduleId, this.namespace, this.items, this.blocks, this.mobs}); }

        @Override public String toString() { return "Entry[" + "moduleId=" + moduleId + ", " + "namespace=" + namespace + ", " + "items=" + items + ", " + "blocks=" + blocks + ", " + "mobs=" + mobs + "]"; }


        public Entry(String moduleId, String namespace, List<ItemDeclaration> items, List<BlockDeclaration> blocks) {
            this(moduleId, namespace, items, blocks, Jdk8.listOf());
        }
    }

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
            entries.add(new Entry(moduleId, checked.namespace(), checked.items(), checked.blocks(), checked.mobs()));
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
        return new ContentPlan(Jdk8.copyList(kept), Jdk8.copyList(problems));
    }

    public int itemCount() {
        return entries.stream().mapToInt(e -> e.items().size() + (int) e.blocks().stream().filter(BlockDeclaration::withItem).count()).sum();
    }

    public int mobCount() {
        return entries.stream().mapToInt(e -> e.mobs().size()).sum();
    }

    public int blockCount() {
        return entries.stream().mapToInt(e -> e.blocks().size()).sum();
    }
}
