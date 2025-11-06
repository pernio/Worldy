package jinzo.worldy.client.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import jinzo.worldy.client.utils.WaypointManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class WaypointCommand {

    public static void register() {
        // Ensure death tracker is running so /waypoint death has data
        DeathTracker.init();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("waypoint")
                            .then(literal("clear").executes(WaypointCommand::clearWaypoint))
                            .then(literal("here").executes(WaypointCommand::setWaypointHere))
                            .then(literal("death").executes(WaypointCommand::setWaypointToDeath))
                            .then(literal("set")
                                    .then(argument("x", DoubleArgumentType.doubleArg())
                                            .then(argument("y", DoubleArgumentType.doubleArg())
                                                    .then(argument("z", DoubleArgumentType.doubleArg())
                                                            .executes(WaypointCommand::setWaypointFromArgs)
                                                    )
                                            )
                                    )
                            )
            );
        });
    }

    private static int clearWaypoint(CommandContext<?> ctx) {
        WaypointManager.clearWaypoint();
        MinecraftClient.getInstance().player.sendMessage(Text.literal("§aWaypoint cleared."), false);
        return 1;
    }

    private static int setWaypointHere(CommandContext<?> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return 0;
        }

        Vec3d pos = mc.player.getPos();
        double x = centerOfBlock(pos.x);
        double y = centerOfBlock(pos.y);
        double z = centerOfBlock(pos.z);

        Vec3d target = new Vec3d(x, y, z);
        WaypointManager.setWaypoint(target);

        mc.player.sendMessage(Text.literal(String.format("§aWaypoint set to your position (%.2f, %.2f, %.2f).", x, y, z)), false);
        return 1;
    }

    private static int setWaypointToDeath(CommandContext<?> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return 0;
        }

        Vec3d lastDeath = WaypointManager.getLastDeath();

        double x = centerOfBlock(lastDeath.x);
        double y = centerOfBlock(lastDeath.y);
        double z = centerOfBlock(lastDeath.z);

        Vec3d target = new Vec3d(x, y, z);
        WaypointManager.setWaypoint(target);

        mc.player.sendMessage(Text.literal(String.format("§aWaypoint set to last death (%.2f, %.2f, %.2f).", x, y, z)), false);
        return 1;
    }

    private static int setWaypointFromArgs(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return 0;
        }

        String input = ctx.getInput();
        String after = input.trim().substring("waypoint set".length()).trim();
        String[] parts = after.split("\\s+");
        if (parts.length < 3) {
            mc.player.sendMessage(Text.literal("§cUsage: /waypoint set <x|~> <y|~> <z|~>"), false);
            return 0;
        }

        Vec3d base = mc.player.getPos();
        Vec3d eye = mc.player.getCameraPosVec(1.0F);

        double x = parseCoordinate(parts[0], base.x, eye.x);
        double y = parseCoordinate(parts[1], base.y, eye.y);
        double z = parseCoordinate(parts[2], base.z, eye.z);

        x = centerOfBlock(x);
        y = centerOfBlock(y);
        z = centerOfBlock(z);

        Vec3d target = new Vec3d(x, y, z);
        WaypointManager.setWaypoint(target);

        mc.player.sendMessage(Text.literal(String.format("§aWaypoint set to block center (%.2f, %.2f, %.2f). Showing next 10 blocks.", x, y, z)), false);
        return 1;
    }

    private static double centerOfBlock(double coord) {
        return Math.floor(coord) + 0.5;
    }

    private static double parseCoordinate(String token, double relativeFeet, double relativeEye) {
        token = token.trim();
        if (token.startsWith("~")) {
            if (token.length() == 1) {
                return relativeFeet;
            } else {
                String off = token.substring(1);
                try {
                    double val = Double.parseDouble(off);
                    return relativeFeet + val;
                } catch (NumberFormatException e) {
                    return relativeFeet;
                }
            }
        } else {
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                return relativeFeet;
            }
        }
    }

    private static final class DeathTracker {
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

                    lastKnownPos = client.player.getPos();

                    boolean isAliveNow = !client.player.isDead() && client.player.getHealth() > 0.0F;

                    if (wasAlive && !isAliveNow) {
                        Vec3d deathPos = (lastKnownPos != null) ? lastKnownPos : client.player.getPos();
                        WaypointManager.setLastDeath(deathPos);
                    }

                    wasAlive = isAliveNow;
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    }
}
