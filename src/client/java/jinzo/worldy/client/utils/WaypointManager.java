package jinzo.worldy.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

public class WaypointManager {
    private static Vec3d target = null;
    private static boolean active = false;

    private static int tickCounter = 0;

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

            world.addParticle(ParticleTypes.CRIT, px, py, pz, 0.0, 0.01, 0.0);
        }

        if (distance <= steps * spacing + 0.5) {
            world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    target.x, target.y + 0.1, target.z, 0.0, 0.02, 0.0);
        }
    }
}
