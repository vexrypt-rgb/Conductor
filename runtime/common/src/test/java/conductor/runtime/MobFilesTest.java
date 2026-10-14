package conductor.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom mob files: the JSON reader, Molang, Bedrock geometry in Java model space, and animation playback. */
class MobFilesTest {

    private static final float E = 1e-4f;

    @Test
    void jsonReadsWhatBlockbenchWrites() {
        Object v = MiniJson.parse("﻿{\"a\": [1, -2.5e1, \"x\\n\\u0041\"], \"b\": {\"c\": true, \"d\": null}}");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(Jdk8.listOf(1.0, -25.0, "x\nA"), m.get("a"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) m.get("b")).get("c"));
        assertTrue(((Map<?, ?>) m.get("b")).containsKey("d"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\n \"a\": [1,}"));
        assertTrue(e.getMessage().startsWith("JSON line 2"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1] x"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse(Jdk8.repeat("[", 100) + Jdk8.repeat("]", 100)));
    }

    private static Molang.Context at(double animTime) {
        return new Molang.Context() {
            public double animTime() { return animTime; }
            public double lifeTime() { return 10; }
            public double distanceMoved() { return 3; }
            public double moveSpeed() { return 0.5; }
        };
    }

    @Test
    void molangCoversWhatAnimationsUse() {
        assertEquals(7, Molang.parse("1 + 2 * 3").eval(at(0)), E);
        assertEquals(9, Molang.parse("(1 + 2) * 3;").eval(at(0)), E);
        assertEquals(-1, Molang.parse("-math.cos(q.anim_time * 180)").eval(at(0)), E);
        assertEquals(1, Molang.parse("math.sin(Query.Anim_Time * 90)").eval(at(1)), E, "degrees, case-insensitive");
        assertEquals(30, Molang.parse("math.clamp(query.life_time * 5, 0, 30)").eval(at(0)), E);
        assertEquals(2, Molang.parse("q.anim_time > 0.5 && !(1 == 2) ? 2 : 3").eval(at(1)), E);
        assertEquals(1.5, Molang.parse("q.modified_distance_moved * q.modified_move_speed").eval(at(0)), E);
        assertEquals(0, Molang.parse("1 / 0").eval(at(0)), E, "no infinity in a pose");
        assertEquals(2.5, Molang.parse("math.lerp(0, 10, 0.25)").eval(at(0)), E);
        assertThrows(IllegalArgumentException.class, () -> Molang.parse("variable.attack_time"));
        assertThrows(IllegalArgumentException.class, () -> Molang.parse("math.sin(1, 2)"));
        assertThrows(IllegalArgumentException.class, () -> Molang.parse("math.nope(1)"));
        assertThrows(IllegalArgumentException.class, () -> Molang.parse("1 +"));
        assertThrows(IllegalArgumentException.class, () -> Molang.parse(Jdk8.repeat("(", 200) + "1" + Jdk8.repeat(")", 200)));
    }

    /** Vanilla's player arm and pig body, as Bedrock ships them, must come out as Java's own models build them. */
    @Test
    void bedrockGeometryLandsWhereJavasVanillaModelsAre() {
        MobGeometry g = MobGeometry.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{\n"
                    + "  \"description\": {\"identifier\": \"geometry.test\", \"texture_width\": 64, \"texture_height\": 32},\n"
                    + "  \"bones\": [\n"
                    + "    {\"name\": \"body\", \"pivot\": [0, 13, 2], \"rotation\": [90, 0, 0],\n"
                    + "     \"cubes\": [{\"origin\": [-5, 7, -5], \"size\": [10, 16, 8], \"uv\": [28, 8]}]},\n"
                    + "    {\"name\": \"leftArm\", \"pivot\": [5, 22, 0],\n"
                    + "     \"cubes\": [{\"origin\": [4, 12, -2], \"size\": [4, 12, 4], \"uv\": [32, 48], \"mirror\": true}]},\n"
                    + "    {\"name\": \"head\", \"parent\": \"body\", \"pivot\": [0, 24, 0],\n"
                    + "     \"cubes\": [{\"origin\": [-4, 24, -4], \"size\": [8, 8, 8], \"uv\": [0, 0], \"inflate\": 0.5},\n"
                    + "               {\"origin\": [-1, 30, -6], \"size\": [2, 2, 2], \"uv\": [0, 0], \"pivot\": [0, 31, -5], \"rotation\": [0, 0, 45]}]}\n"
                    + "  ]}]}\n");
        assertEquals(64, g.textureWidth());
        assertEquals(32, g.textureHeight());
        assertEquals(Jdk8.listOf("body", "head", "head$cube1", "leftArm"), g.boneNames());

        // Java pig: PartPose.offsetAndRotation(0, 11, 2, PI/2, 0, 0), addBox(-5, -10, -7, 10, 16, 8), texOffs(28, 8)
        MobGeometry.Bone body = g.roots().get(0);
        assertArrayEquals(new float[]{0, 11, 2}, body.offset(), E);
        assertArrayEquals(new float[]{(float) (Math.PI / 2), 0, 0}, body.rotation(), E);
        MobGeometry.Cube bodyCube = body.cubes().get(0);
        assertArrayEquals(new float[]{-5, -10, -7}, bodyCube.min(), E);
        assertArrayEquals(new float[]{10, 16, 8}, bodyCube.size(), E);
        assertEquals(28, bodyCube.u());
        assertEquals(8, bodyCube.v());

        // Java player: leftArm PartPose.offset(5, 2, 0), addBox(-1, -2, -2, 4, 12, 4), texOffs(32, 48)
        MobGeometry.Bone arm = g.roots().get(1);
        assertArrayEquals(new float[]{5, 2, 0}, arm.offset(), E);
        assertArrayEquals(new float[]{-1, -2, -2}, arm.cubes().get(0).min(), E);
        assertTrue(arm.cubes().get(0).mirror());

        // a child's offset is from its parent's pivot; Java head: addBox(-4, -8, -4, 8, 8, 8)
        MobGeometry.Bone head = body.children().get(0);
        assertArrayEquals(new float[]{0, 24 - 24 - 11, -2}, head.offset(), E);
        assertArrayEquals(new float[]{-4, -8, -4}, head.cubes().get(0).min(), E);
        assertEquals(0.5f, head.cubes().get(0).inflate(), E);

        // a rotated cube becomes a child part turning about the cube's pivot
        MobGeometry.Bone horn = head.children().get(0);
        assertEquals("head$cube1", horn.name());
        assertArrayEquals(new float[]{0, -7, -5}, horn.offset(), E);
        assertArrayEquals(new float[]{0, 0, (float) (Math.PI / 4)}, horn.rotation(), E);
        assertArrayEquals(new float[]{-1, -1, -1}, horn.cubes().get(0).min(), E);
    }

    @Test
    void badGeometryIsRefusedWithAReason() {
        String bones = "{\"minecraft:geometry\": [{\"bones\": %s}]}";
        assertThrows(IllegalArgumentException.class, () -> MobGeometry.parse(String.format(bones, "[]")));
        assertThrows(IllegalArgumentException.class, () -> MobGeometry.parse(String.format(bones,
                "[{\"name\": \"a\"}, {\"name\": \"a\"}]")));
        assertThrows(IllegalArgumentException.class, () -> MobGeometry.parse(String.format(bones,
                "[{\"name\": \"a\", \"parent\": \"ghost\"}]")));
        assertThrows(IllegalArgumentException.class, () -> MobGeometry.parse(String.format(bones,
                "[{\"name\": \"a\", \"parent\": \"b\"}, {\"name\": \"b\", \"parent\": \"a\"}]")));
        IllegalArgumentException perFace = assertThrows(IllegalArgumentException.class, () -> MobGeometry.parse(String.format(bones,
                "[{\"name\": \"a\", \"cubes\": [{\"origin\": [0,0,0], \"size\": [1,1,1], \"uv\": {\"north\": {\"uv\": [0,0]}}}]}]")));
        assertTrue(perFace.getMessage().contains("per-face UV"), perFace.getMessage());
        // the old 1.8.0 layout is still read
        MobGeometry old = MobGeometry.parse("{\"format_version\": \"1.8.0\", \"geometry.old\": {\"texturewidth\": 32, \"textureheight\": 16,"
                + " \"bones\": [{\"name\": \"root\", \"pivot\": [0, 0, 0]}]}}");
        assertEquals(32, old.textureWidth());
        assertArrayEquals(new float[]{0, 24, 0}, old.roots().get(0).offset(), E);
    }

    @Test
    void animationsInterpolateLoopAndAddUp() {
        MobAnimations anims = MobAnimations.parse(
                "{\"format_version\": \"1.8.0\", \"animations\": {\n"
                    + "  \"animation.golem.walk\": {\"loop\": true, \"animation_length\": 1.0, \"bones\": {\n"
                    + "    \"leg\": {\"rotation\": {\"0.0\": [0, 0, 0], \"0.5\": [40, 0, 0], \"1.0\": [0, 0, 0]}}}},\n"
                    + "  \"animation.golem.slam\": {\"animation_length\": 0.5, \"bones\": {\n"
                    + "    \"body\": {\"position\": [0, \"math.sin(q.anim_time * 360) * 4\", 0], \"scale\": 2},\n"
                    + "    \"arm\": {\"rotation\": {\"0.0\": {\"pre\": [0, 0, 0], \"post\": [-90, 0, 0]}, \"0.5\": [0, 0, 0]}}}},\n"
                    + "  \"animation.golem.idle\": {\"loop\": \"hold_on_last_frame\", \"bones\": {\"head\": {\"rotation\": [0, 10, 0]}}}\n"
                    + "}}\n");
        MobAnimations.Animation walk = anims.find("walk");
        assertNotNull(walk);
        assertEquals(MobAnimations.Loop.LOOP, walk.loop());
        assertEquals(walk, anims.find("animation.golem.walk"));
        assertNull(anims.find("fly"));
        assertEquals(MobAnimations.Loop.HOLD, anims.find("idle").loop());
        MobAnimations.Animation slam = anims.find("SLAM");
        assertTrue(slam.finished(0.6));
        assertTrue(!walk.finished(100));

        MobAnimations.Pose pose = new MobAnimations.Pose();
        pose.apply(walk, 0.25, 1, at(0));
        assertEquals(Math.toRadians(20), pose.bones().get("leg")[0], E, "halfway to the 0.5 s keyframe");
        pose.clear();
        pose.apply(walk, 1.25, 0.5f, at(0));
        assertEquals(Math.toRadians(10), pose.bones().get("leg")[0], E, "loops, and the weight scales it");

        pose.clear();
        pose.apply(slam, 0.25, 1, at(0));
        float[] body = pose.bones().get("body");
        assertEquals(-4, body[4], E, "Bedrock's up is Java's minus y; q.anim_time is the animation's own time");
        assertEquals(2, body[6], E);
        assertEquals(Math.toRadians(-45), pose.bones().get("arm")[0], E, "from the post value of the first keyframe");

        pose.clear();
        pose.apply(slam, 5, 1, at(0));
        assertEquals(0, pose.bones().get("arm")[0], E, "a finished animation rests on its last frame");
        assertEquals(Jdk8.listOf("arm", "body"), anims.unknownBones(Jdk8.listOf("leg", "head")));

        assertThrows(IllegalArgumentException.class, () -> MobAnimations.parse("{\"animations\": {\"a\": {\"bones\": {\"b\": {\"rotation\": \"q.nope\"}}}}}"));
        assertThrows(IllegalArgumentException.class, () -> MobAnimations.parse("{\"animations\": {\"a\": {\"bones\": {\"b\": {\"rotation\": {\"x\": [0, 0, 0]}}}}}}"));
        assertThrows(IllegalArgumentException.class, () -> MobAnimations.parse("{\"animations\": {\"a\": {\"bones\": {\"b\": {\"rotation\": [1, 2]}}}}}"));
    }
}
