package conductor.testjoin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.fabricmc.api.ClientModInitializer;

/**
 * Test-harness helper for Minecraft 1.16.x only (scripts/prod-run.sh); not part of Conductor.
 * Vanilla 1.16.1 started with --server connects while the first resource reload is still running, so chunks can be
 * drawn before block models are baked and the game crashes ("Tesselating block in world") even with no mods.
 * This joins the given server only once loading is over: the loading overlay has come and gone, and the title
 * screen is showing.
 * It reaches the game through reflection on intermediary names, so it needs no build against Minecraft.
 */
public final class LegacyJoin implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String target = System.getProperty("conductor.testJoin");
        if (target == null) return;
        Thread t = new Thread(() -> join(target), "conductor-test-join");
        t.setDaemon(true);
        t.start();
    }

    private static void join(String target) {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.class_310");
            Class<?> screenClass = Class.forName("net.minecraft.class_437");
            Class<?> titleClass = Class.forName("net.minecraft.class_442");
            Constructor<?> connect = Class.forName("net.minecraft.class_412")
                    .getConstructor(screenClass, mcClass, String.class, int.class);
            Method getInstance = mcClass.getMethod("method_1551");
            Method getOverlay = mcClass.getMethod("method_18506");
            Method setScreen = mcClass.getMethod("method_1507", screenClass);
            java.lang.reflect.Field screenField = mcClass.getField("field_1755");
            String host = target.substring(0, target.lastIndexOf(':'));
            int port = Integer.parseInt(target.substring(target.lastIndexOf(':') + 1));
            Object mc;
            while ((mc = getInstance.invoke(null)) == null) Thread.sleep(500);
            // the constructor shows the title screen just before it puts up the loading overlay, so wait for the
            // overlay to appear, then for it to go away
            while (getOverlay.invoke(mc) == null) Thread.sleep(100);
            while (getOverlay.invoke(mc) != null || !titleClass.isInstance(screenField.get(mc))) Thread.sleep(500);
            Thread.sleep(2000);
            Object game = mc;
            System.out.println("[conductor-test-join] loading finished; joining " + target);
            ((java.util.concurrent.Executor) mc).execute(() -> {
                try {
                    setScreen.invoke(game, connect.newInstance(titleClass.getConstructor().newInstance(), game, host, port));
                } catch (ReflectiveOperationException e) {
                    e.printStackTrace();
                }
            });
        } catch (ReflectiveOperationException | InterruptedException | RuntimeException e) {
            System.out.println("[conductor-test-join] could not join: " + e);
        }
    }
}
