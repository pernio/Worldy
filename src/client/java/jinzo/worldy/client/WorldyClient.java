package jinzo.worldy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jinzo.worldy.client.commands.*;
import jinzo.worldy.client.utils.RuleHelper;
import jinzo.worldy.client.utils.StafflistHelper;
import jinzo.worldy.client.utils.WaypointManager;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class WorldyClient implements ClientModInitializer {

    private static final Set<String> previousPlayers = new HashSet<>();
    private static volatile boolean isTargetServer = false;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(WorldyConfig.class, JanksonConfigSerializer::new);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommand(dispatcher,
                    StafflistCommand.register(),
                    SpawnTownCommand.register(),
                    SpawnTownCommand.registerAlias(),
                    SpawnNationCommand.register(),
                    IcemapCommand.register(),
                    VpCommand.register(),
                    VoteCommand.register(),
                    RuleCommand.register()
            );
            WaypointCommand.register();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                ServerInfo server = client.getCurrentServerEntry();
                if (server != null) {
                    System.out.println("Joined server: " + server.address);
                    isTargetServer = server.address != null && server.address.endsWith("worldmc.org");
                } else {
                    System.out.println("Joined server: (server entry was null)");
                    isTargetServer = false;
                }

                if (isTargetServer && getConfig().fetch.fetchUrlsOnLogin) {
                    StafflistHelper.loadStaffListOnJoin(client);
                    RuleHelper.loadRulesAsync();
                }
            } catch (Exception e) {
                System.err.println("Error while handling JOIN event: " + e.getMessage());
                isTargetServer = false;
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            previousPlayers.clear();
            isTargetServer = false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isTargetServer) return;

            if (client.player == null || client.getNetworkHandler() == null) return;

            Set<String> currentPlayers = new HashSet<>();
            client.getNetworkHandler().getPlayerList().forEach(entry -> currentPlayers.add(entry.getProfile().name()));

            for (String playerName : previousPlayers) {
                if (!currentPlayers.contains(playerName) && getConfig().general.displayLogoutMessages) {
                    client.player.sendMessage(Text.literal("§7[§c-§7] " + playerName), false);
                }
            }

            previousPlayers.clear();
            previousPlayers.addAll(currentPlayers);

            if (WaypointManager.active() && getConfig().waypoint.enabled) WaypointManager.spawnPathParticles(getConfig().waypoint.pathLength);
        });

        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo currentServer = mc.getCurrentServerEntry();
        if (currentServer != null) {
            System.out.println("WorldyClient initialized. Current server: " + currentServer.address);
        } else {
            System.out.println("WorldyClient initialized. No server connected (main menu).");
        }
    }

    public static WorldyConfig getConfig() {
        return AutoConfig.getConfigHolder(WorldyConfig.class).getConfig();
    }

    @SafeVarargs
    private static void registerCommand(@NotNull CommandDispatcher<FabricClientCommandSource> dispatcher, @NotNull LiteralArgumentBuilder<FabricClientCommandSource>... commands) {
        for (LiteralArgumentBuilder<FabricClientCommandSource> command : commands) {
            dispatcher.register(command);
        }
    }
}
