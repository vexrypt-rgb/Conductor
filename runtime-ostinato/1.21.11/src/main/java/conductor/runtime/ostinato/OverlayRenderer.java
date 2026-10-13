package conductor.runtime.ostinato;

import baritone.api.event.events.RenderEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import conductor.contract.OverlayPort;
import conductor.contract.OverlayShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws modules' overlay shapes in Ostinato's world render pass, with Ostinato's own line renderer (the one that
 * draws its paths). Per version because the renderer and camera APIs differ. Ostinato main (1.21.11): line width is per vertex.
 */
final class OverlayRenderer implements OverlayPort, AbstractGameEventListener {

    private static final float WIDTH = 2.0F;

    private volatile List<OverlayShape> shapes = List.of();

    @Override
    public void show(List<OverlayShape> shapes) {
        this.shapes = shapes;
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        List<OverlayShape> current = shapes;
        if (current.isEmpty()) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        var look = camera.forwardVector();
        Vec3 start = eye.add(look.x() * 0.6, look.y() * 0.6, look.z() * 0.6); // tracers start just ahead of the crosshair
        PoseStack stack = event.getModelViewStack();

        Map<Long, List<OverlayShape>> groups = new LinkedHashMap<>(); // one draw call per colour and depth mode
        for (OverlayShape s : current) {
            groups.computeIfAbsent(((long) s.argb() << 1) | (s.throughWalls() ? 1 : 0), k -> new ArrayList<>()).add(s);
        }
        for (List<OverlayShape> group : groups.values()) {
            OverlayShape first = group.get(0);
            Color color = new Color(first.argb() & 0xFFFFFF);
            float alpha = ((first.argb() >>> 24) & 0xFF) / 255F;
            boolean through = first.throughWalls();
            BufferBuilder buffer = IRenderer.startLines(color, alpha);
            for (OverlayShape s : group) {
                switch (s.kind()) {
                    case BOX -> IRenderer.emitAABB(buffer, stack, new AABB(s.x1(), s.y1(), s.z1(), s.x2(), s.y2(), s.z2()), WIDTH);
                    case LINE -> IRenderer.emitLine(buffer, stack, new Vec3(s.x1(), s.y1(), s.z1()), new Vec3(s.x2(), s.y2(), s.z2()), WIDTH);
                    case TRACER -> IRenderer.emitLine(buffer, stack, start, new Vec3(s.x1(), s.y1(), s.z1()), WIDTH);
                }
            }
            IRenderer.endLines(buffer, through);
        }
    }
}
