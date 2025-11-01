package jinzo.worldy.client.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import jinzo.worldy.client.utils.WaypointManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class WaypointCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("waypoint")
                            .then(literal("clear").executes(WaypointCommand::clearWaypoint))
                            .then(argument("x", DoubleArgumentType.doubleArg())
                                    .then(argument("y", DoubleArgumentType.doubleArg())
                                            .then(argument("z", DoubleArgumentType.doubleArg())
                                                    .executes(WaypointCommand::setWaypointFromArgs)
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

    private static int setWaypointFromArgs(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return 0;
        }

        double rawX = DoubleArgumentType.getDouble(ctx, "x");
        double rawY = DoubleArgumentType.getDouble(ctx, "y");
        double rawZ = DoubleArgumentType.getDouble(ctx, "z");

        String input = ctx.getInput();
        String after = input.trim().substring("waypoint".length()).trim();
        String[] parts = after.split("\\s+");
        if (parts.length < 3) {
            mc.player.sendMessage(Text.literal("§cUsage: /waypoint <x|~> <y|~> <z|~>"), false);
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
}
