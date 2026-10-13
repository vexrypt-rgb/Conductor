package conductor.runtime;

import conductor.contract.OverlayShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns overlay shapes into camera-relative line segments, one batch per colour and depth mode. No game classes. */
final class OverlayGeometry {

    /** How far ahead of the camera a tracer starts, so it is not a single point under the crosshair. */
    static final double TRACER_START = 0.6;

    private OverlayGeometry() {}

    static List<LineDrawer.LineBatch> batches(List<OverlayShape> shapes, double[] camera) {
        double cx = camera[0], cy = camera[1], cz = camera[2];
        Map<Long, List<Float>> groups = new LinkedHashMap<>();
        Map<Long, OverlayShape> firsts = new LinkedHashMap<>();
        for (OverlayShape s : shapes) {
            long key = ((long) s.argb() << 1) | (s.throughWalls() ? 1 : 0);
            List<Float> out = groups.computeIfAbsent(key, k -> new ArrayList<>());
            firsts.putIfAbsent(key, s);
            switch (s.kind()) {
                case BOX -> box(out, s.x1() - cx, s.y1() - cy, s.z1() - cz, s.x2() - cx, s.y2() - cy, s.z2() - cz);
                case LINE -> segment(out, s.x1() - cx, s.y1() - cy, s.z1() - cz, s.x2() - cx, s.y2() - cy, s.z2() - cz);
                case TRACER -> segment(out, camera[3] * TRACER_START, camera[4] * TRACER_START, camera[5] * TRACER_START,
                        s.x1() - cx, s.y1() - cy, s.z1() - cz);
            }
        }
        List<LineDrawer.LineBatch> batches = new ArrayList<>();
        groups.forEach((key, floats) -> {
            float[] segs = new float[floats.size()];
            for (int i = 0; i < segs.length; i++) {
                segs[i] = floats.get(i);
            }
            OverlayShape first = firsts.get(key);
            batches.add(new LineDrawer.LineBatch(first.argb(), first.throughWalls(), segs));
        });
        return batches;
    }

    private static void box(List<Float> out, double x1, double y1, double z1, double x2, double y2, double z2) {
        double[][] c = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            segment(out, c[e[0]][0], c[e[0]][1], c[e[0]][2], c[e[1]][0], c[e[1]][1], c[e[1]][2]);
        }
    }

    private static void segment(List<Float> out, double x1, double y1, double z1, double x2, double y2, double z2) {
        out.add((float) x1);
        out.add((float) y1);
        out.add((float) z1);
        out.add((float) x2);
        out.add((float) y2);
        out.add((float) z2);
    }
}
