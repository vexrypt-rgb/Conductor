package conductor.harness;

import net.fabricmc.api.ClientModInitializer;

/** Starts the scenario thread only when {@code -Dconductor.harness.out=<file>} is set; otherwise does nothing. */
public final class HarnessClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        String out = System.getProperty("conductor.harness.out");
        if (out == null) {
            return;
        }
        Thread thread = new Thread(new Scenario(new JsonLog(out)), "conductor-harness");
        thread.setDaemon(true);
        thread.start();
    }
}
