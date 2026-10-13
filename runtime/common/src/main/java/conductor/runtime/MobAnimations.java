package conductor.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A custom mob's animations, read from a Bedrock animation file (what Blockbench exports), and played onto a
 * {@link Pose} in Java model space. The same code runs on every Minecraft version.
 * <p>
 * Supported: {@code loop} (true, false, {@code "hold_on_last_frame"}), {@code animation_length}, per-bone
 * {@code rotation}, {@code position} and {@code scale} channels given as a constant, an array of numbers or Molang
 * expressions, or keyframes (with {@code pre}/{@code post}). Keyframes interpolate linearly; {@code catmullrom}
 * keyframes are read but also play linearly. Sound and particle keyframes are ignored: a mob's
 * {@link conductor.contract.MobBehaviour} plays those.
 * <p>
 * Rotation and scale mean the same in both editions (see {@link MobGeometry}); a position moves y the other way.
 */
public final class MobAnimations {

    private static final int MAX_ANIMATIONS = 256;
    private static final int MAX_KEYFRAMES = 4096;

    private final Map<String, Animation> byName;

    /** No animations: the model stands in its rest pose (the head still follows the look). */
    public static final MobAnimations NONE = new MobAnimations(Map.of());

    private MobAnimations(Map<String, Animation> byName) {
        this.byName = byName;
    }

    public enum Loop { ONCE, LOOP, HOLD }

    /**
     * One animation.
     *
     * @param name  as in the file, e.g. {@code animation.ruby_golem.slam}
     * @param role  the part after the last dot, lower case ({@code slam}): what {@code idle}, {@code walk},
     *              {@code attack} and {@code MobControl.animate} match
     */
    public record Animation(String name, String role, Loop loop, double length, Map<String, Channels> bones) {

        /** Whether a non-looping animation started {@code seconds} ago is over. */
        public boolean finished(double seconds) {
            return loop == Loop.ONCE && seconds > length;
        }
    }

    /** A bone's channels; any may be null. */
    public record Channels(Track rotation, Track position, Track scale) {
    }

    /** One channel: keyframe times (seconds) and values, each before ({@code pre}) and after ({@code post}). */
    public record Track(double[] times, Molang.Expr[][] pre, Molang.Expr[][] post) {

        void sample(double t, Molang.Context c, double[] out) {
            int n = times.length;
            if (n == 1 || t <= times[0]) {
                eval(n == 1 ? post[0] : pre[0], c, out);
                return;
            }
            if (t >= times[n - 1]) {
                eval(post[n - 1], c, out);
                return;
            }
            int k = 0;
            while (k + 1 < n && times[k + 1] <= t) {
                k++;
            }
            double span = times[k + 1] - times[k];
            double f = span <= 0 ? 1 : (t - times[k]) / span;
            double[] a = new double[3];
            double[] b = new double[3];
            eval(post[k], c, a);
            eval(pre[k + 1], c, b);
            for (int i = 0; i < 3; i++) {
                out[i] = a[i] + (b[i] - a[i]) * f;
            }
        }

        private static void eval(Molang.Expr[] v, Molang.Context c, double[] out) {
            for (int i = 0; i < 3; i++) {
                double x = v[i].eval(c);
                out[i] = Double.isFinite(x) ? x : 0;
            }
        }
    }

    /** Every animation, by full name. */
    public Map<String, Animation> all() {
        return byName;
    }

    /** The animation with this role (the name's last part) or full name, or null. */
    public Animation find(String nameOrRole) {
        if (nameOrRole == null) {
            return null;
        }
        Animation a = byName.get(nameOrRole);
        if (a != null) {
            return a;
        }
        String role = nameOrRole.toLowerCase(Locale.ROOT);
        for (Animation x : byName.values()) {
            if (x.role().equals(role)) {
                return x;
            }
        }
        return null;
    }

    /** Read an animation file's text; throws {@link IllegalArgumentException} saying what is wrong. */
    public static MobAnimations parse(String json) {
        if (!(MiniJson.parse(json) instanceof Map<?, ?> top) || !(top.get("animations") instanceof Map<?, ?> anims)) {
            throw new IllegalArgumentException("an animation file is an object with \"animations\"");
        }
        if (anims.size() > MAX_ANIMATIONS) {
            throw new IllegalArgumentException("more than " + MAX_ANIMATIONS + " animations");
        }
        int[] keyframes = {0};
        Map<String, Animation> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : anims.entrySet()) {
            String name = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> a)) {
                throw new IllegalArgumentException("animation " + name + " is not an object");
            }
            try {
                out.put(name, animation(name, a, keyframes));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("animation " + name + ": " + ex.getMessage(), ex);
            }
        }
        return new MobAnimations(java.util.Collections.unmodifiableMap(out));
    }

    private static Animation animation(String name, Map<?, ?> a, int[] keyframes) {
        Object loopValue = a.get("loop");
        Loop loop = Boolean.TRUE.equals(loopValue) ? Loop.LOOP
                : "hold_on_last_frame".equals(loopValue) ? Loop.HOLD : Loop.ONCE;
        Map<String, Channels> bones = new LinkedHashMap<>();
        double last = 0;
        if (a.get("bones") instanceof Map<?, ?> bs) {
            for (Map.Entry<?, ?> be : bs.entrySet()) {
                String bone = String.valueOf(be.getKey());
                if (!(be.getValue() instanceof Map<?, ?> ch)) {
                    throw new IllegalArgumentException("bone " + bone + " is not an object");
                }
                Track r = track(ch.get("rotation"), 0, keyframes);
                Track p = track(ch.get("position"), 0, keyframes);
                Track s = track(ch.get("scale"), 1, keyframes);
                for (Track t : new Track[]{r, p, s}) {
                    if (t != null) {
                        last = Math.max(last, t.times()[t.times().length - 1]);
                    }
                }
                bones.put(bone, new Channels(r, p, s));
            }
        }
        double length = MobGeometry.number(a.get("animation_length"), last);
        if (!(length >= 0 && length <= 3600)) {
            throw new IllegalArgumentException("animation_length must be 0..3600 seconds");
        }
        int dot = name.lastIndexOf('.');
        String role = (dot >= 0 ? name.substring(dot + 1) : name).toLowerCase(Locale.ROOT);
        return new Animation(name, role, loop, length, java.util.Collections.unmodifiableMap(bones));
    }

    private static Track track(Object o, double fallback, int[] keyframes) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map<?, ?> frames && !frames.containsKey("pre") && !frames.containsKey("post") && !frames.isEmpty()
                && !frames.containsKey("vector")) {
            TreeMap<Double, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> f : frames.entrySet()) {
                double t;
                try {
                    t = Double.parseDouble(String.valueOf(f.getKey()));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("keyframe time '" + f.getKey() + "' is not a number");
                }
                if (!(t >= 0 && t <= 3600)) {
                    throw new IllegalArgumentException("keyframe time " + t + " must be 0..3600");
                }
                sorted.put(t, f.getValue());
            }
            if ((keyframes[0] += sorted.size()) > MAX_KEYFRAMES) {
                throw new IllegalArgumentException("more than " + MAX_KEYFRAMES + " keyframes");
            }
            int n = sorted.size();
            double[] times = new double[n];
            Molang.Expr[][] pre = new Molang.Expr[n][];
            Molang.Expr[][] post = new Molang.Expr[n][];
            int k = 0;
            for (Map.Entry<Double, Object> f : sorted.entrySet()) {
                times[k] = f.getKey();
                Object v = f.getValue();
                if (v instanceof Map<?, ?> m && (m.containsKey("pre") || m.containsKey("post") || m.containsKey("vector"))) {
                    Object single = m.containsKey("vector") ? m.get("vector") : null;
                    pre[k] = vector(m.containsKey("pre") ? m.get("pre") : single != null ? single : m.get("post"), fallback);
                    post[k] = vector(m.containsKey("post") ? m.get("post") : single != null ? single : m.get("pre"), fallback);
                } else {
                    pre[k] = vector(v, fallback);
                    post[k] = pre[k];
                }
                k++;
            }
            return new Track(times, pre, post);
        }
        Molang.Expr[] v = vector(o instanceof Map<?, ?> m ? (m.containsKey("post") ? m.get("post") : m.containsKey("vector") ? m.get("vector") : m.get("pre")) : o, fallback);
        return new Track(new double[]{0}, new Molang.Expr[][]{v}, new Molang.Expr[][]{v});
    }

    /** A value: [x, y, z] of numbers or Molang strings, or one number/string for all three. */
    private static Molang.Expr[] vector(Object o, double fallback) {
        if (o instanceof List<?> l) {
            if (l.size() != 3) {
                throw new IllegalArgumentException("a keyframe value must have three parts, not " + l.size());
            }
            return new Molang.Expr[]{scalar(l.get(0), fallback), scalar(l.get(1), fallback), scalar(l.get(2), fallback)};
        }
        Molang.Expr e = scalar(o, fallback);
        return new Molang.Expr[]{e, e, e};
    }

    private static Molang.Expr scalar(Object o, double fallback) {
        if (o instanceof Number n) {
            return Molang.constant(Double.isFinite(n.doubleValue()) ? n.doubleValue() : fallback);
        }
        if (o instanceof String s) {
            return Molang.parse(s);
        }
        if (o == null) {
            return Molang.constant(fallback);
        }
        throw new IllegalArgumentException("a keyframe value must be a number or a Molang string, not " + o);
    }

    /**
     * Per-bone changes to the model's rest pose, in Java model space: rotation added (radians), position added
     * (Java units, y down), scale multiplied. Several animations add up, each with a weight.
     */
    public static final class Pose {
        private final Map<String, float[]> bones = new HashMap<>();

        /** The bones the pose changes: values are {rx, ry, rz, x, y, z, sx, sy, sz}. */
        public Map<String, float[]> bones() {
            return bones;
        }

        public void clear() {
            bones.clear();
        }

        private float[] bone(String name) {
            return bones.computeIfAbsent(name, k -> new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1});
        }

        /** Add a rotation in radians, Java's sense (used for the head following the look). */
        public void rotate(String bone, float rx, float ry, float rz) {
            float[] b = bone(bone);
            b[0] += rx;
            b[1] += ry;
            b[2] += rz;
        }

        /**
         * Play {@code a} at {@code seconds} after it started, with {@code weight} (0 to 1). A looping animation wraps
         * around; one that does not stays on its last frame.
         */
        public void apply(Animation a, double seconds, float weight, Molang.Context context) {
            if (weight <= 0) {
                return;
            }
            double t = seconds;
            if (a.loop() == Loop.LOOP && a.length() > 0) {
                t = seconds % a.length();
            } else if (t > a.length()) {
                t = a.length();
            }
            double animTime = t;
            Molang.Context c = new Molang.Context() {
                public double animTime() { return animTime; }
                public double lifeTime() { return context.lifeTime(); }
                public double distanceMoved() { return context.distanceMoved(); }
                public double moveSpeed() { return context.moveSpeed(); }
            };
            double[] v = new double[3];
            for (Map.Entry<String, Channels> e : a.bones().entrySet()) {
                Channels ch = e.getValue();
                float[] b = null;
                if (ch.rotation() != null) {
                    ch.rotation().sample(t, c, v);
                    b = bone(e.getKey());
                    b[0] += (float) Math.toRadians(v[0]) * weight;
                    b[1] += (float) Math.toRadians(v[1]) * weight;
                    b[2] += (float) Math.toRadians(v[2]) * weight;
                }
                if (ch.position() != null) {
                    ch.position().sample(t, c, v);
                    b = b == null ? bone(e.getKey()) : b;
                    b[3] += (float) v[0] * weight;
                    b[4] -= (float) v[1] * weight;
                    b[5] += (float) v[2] * weight;
                }
                if (ch.scale() != null) {
                    ch.scale().sample(t, c, v);
                    b = b == null ? bone(e.getKey()) : b;
                    b[6] *= (float) (1 + (v[0] - 1) * weight);
                    b[7] *= (float) (1 + (v[1] - 1) * weight);
                    b[8] *= (float) (1 + (v[2] - 1) * weight);
                }
            }
        }
    }

    /**
     * One frame of a mob: its {@code idle} animation, faded into {@code walk} as it walks faster, then the animation
     * it was told to play ({@code triggered}, or null) on top until that ends.
     *
     * @param walkPosition vanilla's limb swing position (blocks walked)
     * @param walkSpeed    vanilla's limb swing amount, 0 standing to about 1 running
     */
    public void play(Pose pose, double lifeSeconds, float walkPosition, float walkSpeed, String triggered, double triggeredSeconds) {
        Molang.Context c = new Molang.Context() {
            public double animTime() { return lifeSeconds; }
            public double lifeTime() { return lifeSeconds; }
            public double distanceMoved() { return walkPosition; }
            public double moveSpeed() { return walkSpeed; }
        };
        Animation walk = find("walk");
        float walking = walk == null ? 0 : Math.max(0, Math.min(1, walkSpeed * 2));
        Animation idle = find("idle");
        if (idle != null) {
            pose.apply(idle, lifeSeconds, 1 - walking, c);
        }
        if (walk != null) {
            pose.apply(walk, lifeSeconds, walking, c);
        }
        Animation t = find(triggered);
        if (t != null && !t.finished(triggeredSeconds)) {
            pose.apply(t, triggeredSeconds, 1, c);
        }
    }

    /** Bone names the animations move that the model does not have (worth a warning; they do nothing). */
    public List<String> unknownBones(java.util.Collection<String> modelBones) {
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (Animation a : byName.values()) {
            for (String b : a.bones().keySet()) {
                if (!modelBones.contains(b)) {
                    missing.add(b);
                }
            }
        }
        return new ArrayList<>(missing);
    }
}
