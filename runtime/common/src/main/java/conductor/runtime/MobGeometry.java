package conductor.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A custom mob's model, read from a Bedrock geometry file (what Blockbench exports as "Bedrock Entity") and turned
 * into Java Edition's model space, ready to build vanilla model parts from on any version.
 * <p>
 * Java's model space has y pointing down with the feet at y = 24 and 1/16 block units. Bedrock's has y up with the
 * feet at y = 0. So a Bedrock point (x, y, z) is (x, 24 - y, z) in Java. That is a reflection of the y axis.
 * Bedrock's stored rotations already account for it (Blockbench negates two axes when it writes them), so a
 * Bedrock rotation in degrees is the same rotation in Java in radians, in the same Z, Y, X order. Checked against
 * vanilla's player and pig, which both editions ship (see the tests).
 * <p>
 * A cube with its own rotation becomes a child part holding just that cube, since a Java part rotates all its cubes
 * together. Only box UV is read: per-face UV needs a different cube builder than vanilla's.
 *
 * @param roots bones without a parent, each with its children
 */
public final class MobGeometry {
    private final int textureWidth;
    private final int textureHeight;
    private final List<Bone> roots;

    public MobGeometry(int textureWidth, int textureHeight, List<Bone> roots) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.roots = roots;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public List<Bone> roots() {
        return roots;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobGeometry)) return false;
        MobGeometry other = (MobGeometry) o;
        return java.util.Objects.equals(this.textureWidth, other.textureWidth) && java.util.Objects.equals(this.textureHeight, other.textureHeight) && java.util.Objects.equals(this.roots, other.roots);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(textureWidth, textureHeight, roots);
    }

    @Override public String toString() {
        return "MobGeometry[" + "textureWidth=" + textureWidth + ", textureHeight=" + textureHeight + ", roots=" + roots + "]";
    }

    private static final int MAX_BONES = 512;
    private static final int MAX_CUBES = 8192;

    /**
     * A model part.
     *
     * @param offset   pivot, relative to the parent's pivot (Java units)
     * @param rotation radians, about x, y and z
     */
    public static final class Bone {
        private final String name;
        private final float[] offset;
        private final float[] rotation;
        private final List<Cube> cubes;
        private final List<Bone> children;

        public Bone(String name, float[] offset, float[] rotation, List<Cube> cubes, List<Bone> children) {
            this.name = name;
            this.offset = offset;
            this.rotation = rotation;
            this.cubes = cubes;
            this.children = children;
        }

        public String name() {
            return name;
        }

        public float[] offset() {
            return offset;
        }

        public float[] rotation() {
            return rotation;
        }

        public List<Cube> cubes() {
            return cubes;
        }

        public List<Bone> children() {
            return children;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Bone)) return false;
            Bone other = (Bone) o;
            return java.util.Objects.equals(this.name, other.name) && java.util.Objects.equals(this.offset, other.offset) && java.util.Objects.equals(this.rotation, other.rotation) && java.util.Objects.equals(this.cubes, other.cubes) && java.util.Objects.equals(this.children, other.children);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(name, offset, rotation, cubes, children);
        }

        @Override public String toString() {
            return "Bone[" + "name=" + name + ", offset=" + offset + ", rotation=" + rotation + ", cubes=" + cubes + ", children=" + children + "]";
        }
    }

    /** A box: its corner relative to its part's pivot, its size, texture offset, inflation and mirroring. */
    public static final class Cube {
        private final float[] min;
        private final float[] size;
        private final int u;
        private final int v;
        private final float inflate;
        private final boolean mirror;

        public Cube(float[] min, float[] size, int u, int v, float inflate, boolean mirror) {
            this.min = min;
            this.size = size;
            this.u = u;
            this.v = v;
            this.inflate = inflate;
            this.mirror = mirror;
        }

        public float[] min() {
            return min;
        }

        public float[] size() {
            return size;
        }

        public int u() {
            return u;
        }

        public int v() {
            return v;
        }

        public float inflate() {
            return inflate;
        }

        public boolean mirror() {
            return mirror;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cube)) return false;
            Cube other = (Cube) o;
            return java.util.Objects.equals(this.min, other.min) && java.util.Objects.equals(this.size, other.size) && java.util.Objects.equals(this.u, other.u) && java.util.Objects.equals(this.v, other.v) && java.util.Objects.equals(this.inflate, other.inflate) && java.util.Objects.equals(this.mirror, other.mirror);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(min, size, u, v, inflate, mirror);
        }

        @Override public String toString() {
            return "Cube[" + "min=" + min + ", size=" + size + ", u=" + u + ", v=" + v + ", inflate=" + inflate + ", mirror=" + mirror + "]";
        }
    }

    /** All bone names in the model, depth first (generated cube parts included). */
    public List<String> boneNames() {
        List<String> names = new ArrayList<>();
        for (Bone b : roots) {
            names(b, names);
        }
        return names;
    }

    private static void names(Bone b, List<String> out) {
        out.add(b.name());
        for (Bone c : b.children()) {
            names(c, out);
        }
    }

    /** Read a geometry file's text; throws {@link IllegalArgumentException} saying what is wrong. */
    public static MobGeometry parse(String json) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("a geometry file is a JSON object");
        }
        Map<?, ?> top = (Map<?, ?>) root;
        Map<?, ?> geo;
        Map<?, ?> desc;
        Object formatList = top.get("minecraft:geometry");
        if (formatList instanceof List) {
            // format 1.12.0 and later
            List<?> list = (List<?>) formatList;
            if (list.isEmpty() || !(list.get(0) instanceof Map)) {
                throw new IllegalArgumentException("minecraft:geometry is empty");
            }
            geo = (Map<?, ?>) list.get(0);
            Object descValue = geo.get("description");
            desc = descValue instanceof Map ? (Map<?, ?>) descValue : Jdk8.mapOf();
        } else {
            // format 1.8.0: "geometry.<name>": { "texturewidth": ..., "bones": [...] }
            geo = null;
            for (Map.Entry<?, ?> e : top.entrySet()) {
                if (String.valueOf(e.getKey()).startsWith("geometry.") && e.getValue() instanceof Map) {
                    geo = (Map<?, ?>) e.getValue();
                    break;
                }
            }
            if (geo == null) {
                throw new IllegalArgumentException("no minecraft:geometry (Bedrock geometry 1.12.0+) or geometry.<name> (1.8.0)");
            }
            desc = Jdk8.mapOf("texture_width", number(geo.get("texturewidth"), 64), "texture_height", number(geo.get("textureheight"), 64));
        }
        int tw = (int) number(desc.get("texture_width"), 64);
        int th = (int) number(desc.get("texture_height"), 64);
        if (tw < 1 || th < 1 || tw > 8192 || th > 8192) {
            throw new IllegalArgumentException("texture size " + tw + "x" + th + " must be 1..8192");
        }
        if (!(geo.get("bones") instanceof List) || ((List<?>) geo.get("bones")).isEmpty()) {
            throw new IllegalArgumentException("the model has no bones");
        }
        List<?> bones = (List<?>) geo.get("bones");
        if (bones.size() > MAX_BONES) {
            throw new IllegalArgumentException("more than " + MAX_BONES + " bones");
        }

        Map<String, Map<?, ?>> byName = new LinkedHashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        List<String> rootNames = new ArrayList<>();
        for (Object o : bones) {
            if (!(o instanceof Map)) {
                throw new IllegalArgumentException("a bone without a name");
            }
            Map<?, ?> b = (Map<?, ?>) o;
            if (!(b.get("name") instanceof String) || ((String) b.get("name")).isEmpty()) {
                throw new IllegalArgumentException("a bone without a name");
            }
            String name = (String) b.get("name");
            if (byName.put(name, b) != null) {
                throw new IllegalArgumentException("bone '" + name + "' twice");
            }
        }
        for (Map.Entry<String, Map<?, ?>> e : byName.entrySet()) {
            Object parent = e.getValue().get("parent");
            if (parent == null) {
                rootNames.add(e.getKey());
            } else if (!(parent instanceof String) || !byName.containsKey(parent)) {
                throw new IllegalArgumentException("bone '" + e.getKey() + "' has parent '" + parent + "', which is not in the model");
            } else {
                children.computeIfAbsent((String) parent, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        int[] cubes = {0};
        List<Bone> roots = new ArrayList<>();
        java.util.Set<String> reached = new java.util.HashSet<>();
        for (String r : rootNames) {
            roots.add(bone(r, null, byName, children, reached, cubes, 0));
        }
        if (reached.size() != byName.size()) {
            List<String> loose = new ArrayList<>(byName.keySet());
            loose.removeAll(reached);
            throw new IllegalArgumentException("bones " + loose + " are parented in a loop");
        }
        return new MobGeometry(tw, th, Jdk8.copyList(roots));
    }

    private static Bone bone(String name, float[] parentPivot, Map<String, Map<?, ?>> byName, Map<String, List<String>> children,
                             java.util.Set<String> reached, int[] cubeCount, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("bones nested more than 64 deep");
        }
        reached.add(name);
        Map<?, ?> b = byName.get(name);
        float[] pivot = java(vec(b.get("pivot"), 0, name + " pivot"));
        float[] offset = parentPivot == null ? pivot : sub(pivot, parentPivot);
        float[] rotation = radians(vec(b.get("rotation"), 0, name + " rotation"));
        boolean boneMirror = Boolean.TRUE.equals(b.get("mirror"));
        float boneInflate = (float) number(b.get("inflate"), 0);

        List<Cube> cubes = new ArrayList<>();
        List<Bone> kids = new ArrayList<>();
        Object cubesValue = b.get("cubes");
        if (cubesValue instanceof List) {
            List<?> list = (List<?>) cubesValue;
            int n = 0;
            for (Object o : list) {
                if (!(o instanceof Map)) {
                    throw new IllegalArgumentException("bone '" + name + "': a cube that is not an object");
                }
                Map<?, ?> c = (Map<?, ?>) o;
                if (++cubeCount[0] > MAX_CUBES) {
                    throw new IllegalArgumentException("more than " + MAX_CUBES + " cubes");
                }
                String where = "bone '" + name + "' cube " + n;
                float[] origin = vec(c.get("origin"), 0, where + " origin");
                float[] size = vec(c.get("size"), 0, where + " size");
                if (!(c.get("uv") instanceof List) || ((List<?>) c.get("uv")).size() != 2) {
                    throw new IllegalArgumentException(where + ": uv must be [u, v] (box UV); per-face UV is not supported");
                }
                List<?> uv = (List<?>) c.get("uv");
                int u = (int) number(uv.get(0), 0);
                int v = (int) number(uv.get(1), 0);
                float inflate = (float) number(c.get("inflate"), boneInflate);
                Object mirrorValue = c.get("mirror");
                boolean mirror = mirrorValue instanceof Boolean ? (Boolean) mirrorValue : boneMirror;
                // Bedrock origin is the cube's lowest corner, y up; in Java the top (lowest y) corner
                float[] javaMin = {origin[0], 24 - origin[1] - size[1], origin[2]};
                float[] cubeRotation = vec(c.get("rotation"), 0, where + " rotation");
                if (cubeRotation[0] == 0 && cubeRotation[1] == 0 && cubeRotation[2] == 0) {
                    cubes.add(new Cube(sub(javaMin, pivot), size, u, v, inflate, mirror));
                } else {
                    float[] cubePivot = c.get("pivot") == null ? pivot : java(vec(c.get("pivot"), 0, where + " pivot"));
                    kids.add(new Bone(name + "$cube" + n, sub(cubePivot, pivot), radians(cubeRotation),
                            Jdk8.listOf(new Cube(sub(javaMin, cubePivot), size, u, v, inflate, mirror)), Jdk8.listOf()));
                }
                n++;
            }
        }
        for (String child : children.getOrDefault(name, Jdk8.listOf())) {
            kids.add(bone(child, pivot, byName, children, reached, cubeCount, depth + 1));
        }
        return new Bone(name, offset, rotation, Jdk8.copyList(cubes), Jdk8.copyList(kids));
    }

    /** A Bedrock point in Java model space. */
    private static float[] java(float[] p) {
        return new float[]{p[0], 24 - p[1], p[2]};
    }

    private static float[] radians(float[] degrees) {
        return new float[]{(float) Math.toRadians(degrees[0]), (float) Math.toRadians(degrees[1]), (float) Math.toRadians(degrees[2])};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static float[] vec(Object o, float fallback, String what) {
        if (o == null) {
            return new float[]{fallback, fallback, fallback};
        }
        if (!(o instanceof List) || ((List<?>) o).size() != 3) {
            throw new IllegalArgumentException(what + " must be [x, y, z]");
        }
        List<?> l = (List<?>) o;
        float[] v = new float[3];
        for (int k = 0; k < 3; k++) {
            Object item = l.get(k);
            if (!(item instanceof Number) || !Double.isFinite(((Number) item).doubleValue())) {
                throw new IllegalArgumentException(what + " must be three numbers");
            }
            v[k] = ((Number) item).floatValue();
        }
        return v;
    }

    static double number(Object o, double fallback) {
        return o instanceof Number && Double.isFinite(((Number) o).doubleValue()) ? ((Number) o).doubleValue() : fallback;
    }
}
