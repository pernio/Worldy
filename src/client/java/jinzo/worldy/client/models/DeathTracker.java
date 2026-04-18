package jinzo.worldy.client.models;

import jinzo.worldy.client.utils.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.Vec3d;

public final class DeathTracker {
    private static volatile boolean initialized = false;
    private static boolean wasAlive = false;
    private static Vec3d lastKnownPos = null;

    private DeathTracker() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (client == null) return;
                if (client.player == null) {
                    wasAlive = false;
                    lastKnownPos = null;
                    return;
                }

                Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
                lastKnownPos = pos;

                boolean aliveNow = !client.player.isDead() && client.player.getHealth() > 0.0F;

                if (wasAlive && !aliveNow) {
                    Vec3d deathPos = (lastKnownPos != null) ? lastKnownPos : pos;
                    WaypointManager.setLastDeath(deathPos);
                }

                wasAlive = aliveNow;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }
}
