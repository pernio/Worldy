package jinzo.worldy.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

public class WorldyClient implements ClientModInitializer {

    private static final Set<String> previousPlayers = new HashSet<>();

    @Override
    public void onInitializeClient() {
        AutoConfig.register(WorldyConfig.class, JanksonConfigSerializer::new);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            previousPlayers.clear();
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().getPlayerList().forEach(entry -> {
                    previousPlayers.add(entry.getProfile().getName());
                });
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            previousPlayers.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) return;

            Set<String> currentPlayers = new HashSet<>();

            client.getNetworkHandler().getPlayerList().forEach(entry -> {
                currentPlayers.add(entry.getProfile().getName());
            });

            for (String playerName : previousPlayers) {
                if (!currentPlayers.contains(playerName) && getConfig().displayLogoutMessages) {
                    client.player.sendMessage(Text.literal("§7[§c-§7] " + playerName), false);
                }
            }

            previousPlayers.clear();
            previousPlayers.addAll(currentPlayers);
        });
    }

    public static WorldyConfig getConfig() {
        return AutoConfig.getConfigHolder(WorldyConfig.class).getConfig();
    }
}