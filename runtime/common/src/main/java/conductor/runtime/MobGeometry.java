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
public record MobGeometry(int textureWidth, int textureHeight, List<Bone> roots) {

    private static final int MAX_BONES = 512;
    private static final int MAX_CUBES = 8192;

    /**
     * A model part.
     *
     * @param offset   pivot, relative to the parent's pivot (Java units)
     * @param rotation radians, about x, y and z
     */
    public record Bone(String name, float[] offset, float[] rotation, List<Cube> cubes, List<Bone> children) {
    }

    /** A box: its corner relative to its part's pivot, its size, texture offset, inflation and mirroring. */
    public record Cube(float[] min, float[] size, int u, int v, float inflate, boolean mirror) {
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
        if (!(root instanceof Map<?, ?> top)) {
            throw new IllegalArgumentException("a geometry file is a JSON object");
        }
        Map<?, ?> geo;
        Map<?, ?> desc;
        if (top.get("minecraft:geometry") instanceof List<?> list) {
            // format 1.12.0 and later
            if (list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) {
                throw new IllegalArgumentException("minecraft:geometry is empty");
            }
            geo = first;
            desc = geo.get("description") instanceof Map<?, ?> d ? d : Map.of();
        } else {
            // format 1.8.0: "geometry.<name>": { "texturewidth": ..., "bones": [...] }
            geo = null;
            for (Map.Entry<?, ?> e : top.entrySet()) {
                if (String.valueOf(e.getKey()).startsWith("geometry.") && e.getValue() instanceof Map<?, ?> m) {
                    geo = m;
                    break;
                }
            }
            if (geo == null) {
                throw new IllegalArgumentException("no minecraft:geometry (Bedrock geometry 1.12.0+) or geometry.<name> (1.8.0)");
            }
            desc = Map.of("texture_width", number(geo.get("texturewidth"), 64), "texture_height", number(geo.get("textureheight"), 64));
        }
        int tw = (int) number(desc.get("texture_width"), 64);
        int th = (int) number(desc.get("texture_height"), 64);
        if (tw < 1 || th < 1 || tw > 8192 || th > 8192) {
            throw new IllegalArgumentException("texture size " + tw + "x" + th + " must be 1..8192");
        }
        if (!(geo.get("bones") instanceof List<?> bones) || bones.isEmpty()) {
            throw new IllegalArgumentException("the model has no bones");
        }
        if (bones.size() > MAX_BONES) {
            throw new IllegalArgumentException("more than " + MAX_BONES + " bones");
        }

        Map<String, Map<?, ?>> byName = new LinkedHashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        List<String> rootNames = new ArrayList<>();
        for (Object o : bones) {
            if (!(o instanceof Map<?, ?> b) || !(b.get("name") instanceof String name) || name.isEmpty()) {
                throw new IllegalArgumentException("a bone without a name");
            }
            if (byName.put(name, b) != null) {
                throw new IllegalArgumentException("bone '" + name + "' twice");
            }
        }
        for (Map.Entry<String, Map<?, ?>> e : byName.entrySet()) {
            Object parent = e.getValue().get("parent");
            if (parent == null) {
                rootNames.add(e.getKey());
            } else if (!(parent instanceof String p) || !byName.containsKey(p)) {
                throw new IllegalArgumentException("bone '" + e.getKey() + "' has parent '" + parent + "', which is not in the model");
            } else {
                children.computeIfAbsent(p, k -> new ArrayList<>()).add(e.getKey());
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
        return new MobGeometry(tw, th, List.copyOf(roots));
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
        if (b.get("cubes") instanceof List<?> list) {
            int n = 0;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> c)) {
                    throw new IllegalArgumentException("bone '" + name + "': a cube that is not an object");
                }
                if (++cubeCount[0] > MAX_CUBES) {
                    throw new IllegalArgumentException("more than " + MAX_CUBES + " cubes");
                }
                String where = "bone '" + name + "' cube " + n;
                float[] origin = vec(c.get("origin"), 0, where + " origin");
                float[] size = vec(c.get("size"), 0, where + " size");
                if (!(c.get("uv") instanceof List<?> uv) || uv.size() != 2) {
                    throw new IllegalArgumentException(where + ": uv must be [u, v] (box UV); per-face UV is not supported");
                }
                int u = (int) number(uv.get(0), 0);
                int v = (int) number(uv.get(1), 0);
                float inflate = (float) number(c.get("inflate"), boneInflate);
                boolean mirror = c.get("mirror") instanceof Boolean m ? m : boneMirror;
                // Bedrock origin is the cube's lowest corner, y up; in Java the top (lowest y) corner
                float[] javaMin = {origin[0], 24 - origin[1] - size[1], origin[2]};
                float[] cubeRotation = vec(c.get("rotation"), 0, where + " rotation");
                if (cubeRotation[0] == 0 && cubeRotation[1] == 0 && cubeRotation[2] == 0) {
                    cubes.add(new Cube(sub(javaMin, pivot), size, u, v, inflate, mirror));
                } else {
                    float[] cubePivot = c.get("pivot") == null ? pivot : java(vec(c.get("pivot"), 0, where + " pivot"));
                    kids.add(new Bone(name + "$cube" + n, sub(cubePivot, pivot), radians(cubeRotation),
                            List.of(new Cube(sub(javaMin, cubePivot), size, u, v, inflate, mirror)), List.of()));
                }
                n++;
            }
        }
        for (String child : children.getOrDefault(name, List.of())) {
            kids.add(bone(child, pivot, byName, children, reached, cubeCount, depth + 1));
        }
        return new Bone(name, offset, rotation, List.copyOf(cubes), List.copyOf(kids));
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
        if (!(o instanceof List<?> l) || l.size() != 3) {
            throw new IllegalArgumentException(what + " must be [x, y, z]");
        }
        float[] v = new float[3];
        for (int k = 0; k < 3; k++) {
            if (!(l.get(k) instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                throw new IllegalArgumentException(what + " must be three numbers");
            }
            v[k] = n.floatValue();
        }
        return v;
    }

    static double number(Object o, double fallback) {
        return o instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : fallback;
    }
}
