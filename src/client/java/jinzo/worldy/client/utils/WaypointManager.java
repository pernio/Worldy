package jinzo.worldy.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import me.shedaniel.autoconfig.AutoConfig;
import jinzo.worldy.client.WorldyConfig;

public class WaypointManager {
    private static volatile Vec3d target = null;
    private static volatile boolean active = false;

    private static volatile int tickCounter = 0;

    private WaypointManager() {}

    public static void setWaypoint(Vec3d t) {
        target = t;
        active = t != null;
        tickCounter = 0;
    }

    public static void clearWaypoint() {
        target = null;
        active = false;
        tickCounter = 0;
    }

    public static Vec3d getWaypoint() {
        return target;
    }

    public static boolean isActive() {
        return active && target != null;
    }

    public static void spawnPathParticles(int maxBlocks) {
        if (!isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter % 4 != 0) return;

        Vec3d start = mc.player.getPos().add(0, 0.3, 0);
        Vec3d dir = target.subtract(start);
        double distance = dir.length();
        if (distance <= 0.001) return;

        Vec3d unit = dir.normalize();
        ClientWorld world = mc.world;

        int steps = Math.min(maxBlocks, (int) Math.ceil(distance));
        double spacing = 0.8;
        for (int i = 1; i <= steps; i++) {
            double px = start.x + unit.x * i * spacing;
            double py = start.y + unit.y * i * spacing;
            double pz = start.z + unit.z * i * spacing;

            world.addParticleClient(ParticleTypes.CRIT, px, py, pz, 0.0, 0.01, 0.0);
        }

        if (distance <= steps * spacing + 0.5) {
            world.addParticleClient(ParticleTypes.SOUL_FIRE_FLAME,
                    target.x, target.y + 0.1, target.z, 0.0, 0.02, 0.0);
        }
    }

    public static void setLastDeath(Vec3d deathPos) {
        if (deathPos == null) return;
        saveLastDeathToConfig(deathPos);
    }

    public static Vec3d getLastDeath() {
        WorldyConfig cfg = AutoConfig.getConfigHolder(WorldyConfig.class).getConfig();
        return new Vec3d(cfg.waypoint.lastDeathX, cfg.waypoint.lastDeathY, cfg.waypoint.lastDeathZ);
    }

    private static void saveLastDeathToConfig(Vec3d deathPos) {
        try {
            WorldyConfig cfg = AutoConfig.getConfigHolder(WorldyConfig.class).getConfig();
            cfg.waypoint.lastDeathX = (int)deathPos.x;
            cfg.waypoint.lastDeathY = (int)deathPos.y;
            cfg.waypoint.lastDeathZ = (int)deathPos.z;
            AutoConfig.getConfigHolder(WorldyConfig.class).save();
        } catch (Throwable t) {
            System.err.println("Failed to save last death to config: " + t.getMessage());
        }
    }
}
