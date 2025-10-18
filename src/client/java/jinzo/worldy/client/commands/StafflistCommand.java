package jinzo.worldy.client.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class StafflistCommand {

    // Cache voor UUID -> naam mapping om API calls te verminderen
    private static final Map<UUID, String> uuidToNameCache = new ConcurrentHashMap<>();
    private static final Map<String, UUID> playerUuidMap = new HashMap<>();

    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return literal("stafflist")
                .executes(ctx -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null) return 0;

                    playerUuidMap.clear();

                    new Thread(() -> {
                        try {
                            URL url = new URL("https://raw.githubusercontent.com/pernio/Worldy/refs/heads/main/data/staff.json");
                            InputStream inputStream = url.openStream();
                            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                            String jsonContent = scanner.hasNext() ? scanner.next() : "";
                            scanner.close();

                            Map<String, List<UUID>> staffData = parseStaffJson(jsonContent);
                            Map<String, List<StaffMember>> staffWithNames = convertUuidsToNames(staffData);

                            client.execute(() -> {
                                sendStaffList(client, staffWithNames);
                            });

                        } catch (Exception e) {
                            client.execute(() -> {
                                client.player.sendMessage(Text.literal("§cError fetching staff list: " + e.getMessage()).formatted(Formatting.RED), false);
                            });
                        }
                    }).start();

                    client.player.sendMessage(Text.literal("§7Fetching staff list...").formatted(Formatting.GRAY), false);
                    return 1;
                });
    }

    private static Map<String, List<UUID>> parseStaffJson(String json) {
        Map<String, List<UUID>> staffData = new LinkedHashMap<>();

        try {
            json = json.trim().replaceAll("\\s+", "");
            json = json.substring(1, json.length() - 1);

            String[] roles = json.split("(?<=\\]),");

            for (String roleSection : roles) {
                String[] parts = roleSection.split(":", 2);
                if (parts.length == 2) {
                    String role = parts[0].replace("\"", "").trim();
                    String playersStr = parts[1].replace("[", "").replace("]", "").replace("\"", "");

                    List<UUID> players = new ArrayList<>();
                    if (!playersStr.isEmpty()) {
                        String[] uuidArray = playersStr.split(",");
                        for (String uuidStr : uuidArray) {
                            try {
                                String formattedUuid = uuidStr.trim();
                                if (formattedUuid.length() == 32) {
                                    formattedUuid = formattedUuid.substring(0, 8) + "-" +
                                            formattedUuid.substring(8, 12) + "-" +
                                            formattedUuid.substring(12, 16) + "-" +
                                            formattedUuid.substring(16, 20) + "-" +
                                            formattedUuid.substring(20, 32);
                                }
                                players.add(UUID.fromString(formattedUuid));
                            } catch (Exception e) {
                                System.err.println("Invalid UUID: " + uuidStr);
                            }
                        }
                    }
                    staffData.put(role, players);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage());
        }
        return staffData;
    }

    private static Map<String, List<StaffMember>> convertUuidsToNames(Map<String, List<UUID>> staffData) {
        Map<String, List<StaffMember>> staffWithNames = new LinkedHashMap<>();
        MinecraftClient client = MinecraftClient.getInstance();

        // Eerst online spelers checken
        for (Map.Entry<String, List<UUID>> entry : staffData.entrySet()) {
            String role = entry.getKey();
            List<UUID> uuids = entry.getValue();
            List<StaffMember> staffMembers = new ArrayList<>();

            for (UUID uuid : uuids) {
                StaffMember member = getPlayerNameFromUuid(uuid, client);
                staffMembers.add(member);

                if (!member.isUnknown) {
                    playerUuidMap.put(member.displayName, uuid);
                }
            }

            staffWithNames.put(role, staffMembers);
        }
        return staffWithNames;
    }

    private static StaffMember getPlayerNameFromUuid(UUID uuid, MinecraftClient client) {
        try {
            // Method 1: Check cache first
            if (uuidToNameCache.containsKey(uuid)) {
                String cachedName = uuidToNameCache.get(uuid);
                return new StaffMember(cachedName, uuid, false);
            }

            // Method 2: Check if player is currently online
            if (client.getNetworkHandler() != null) {
                var playerListEntry = client.getNetworkHandler().getPlayerList().stream()
                        .filter(entry -> entry.getProfile().getId().equals(uuid))
                        .findFirst();

                if (playerListEntry.isPresent()) {
                    String playerName = playerListEntry.get().getProfile().getName();
                    uuidToNameCache.put(uuid, playerName);
                    return new StaffMember(playerName, uuid, false);
                }
            }

            // Method 3: Use Mojang API to get username from UUID
            String playerName = fetchUsernameFromMojang(uuid);
            if (playerName != null) {
                uuidToNameCache.put(uuid, playerName);
                return new StaffMember(playerName, uuid, false);
            }

            // Method 4: Return unknown as last resort
            return new StaffMember("Unknown (" + uuid.toString().substring(0, 8) + "...)", uuid, true);

        } catch (Exception e) {
            return new StaffMember("Error (" + uuid.toString().substring(0, 8) + "...)", uuid, true);
        }
    }

    private static String fetchUsernameFromMojang(UUID uuid) {
        try {
            // Mojang API endpoint voor UUID -> username
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                InputStream inputStream = connection.getInputStream();
                Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                String jsonResponse = scanner.hasNext() ? scanner.next() : "";
                scanner.close();

                // Parse JSON response
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                if (jsonObject.has("name")) {
                    return jsonObject.get("name").getAsString();
                }
            } else if (responseCode == 204) {
                System.out.println("No content found for UUID: " + uuid);
            } else {
                System.out.println("Mojang API returned: " + responseCode + " for UUID: " + uuid);
            }

            connection.disconnect();
        } catch (Exception e) {
            System.err.println("Error fetching from Mojang API for UUID " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    private static void sendStaffList(MinecraftClient client, Map<String, List<StaffMember>> staffData) {
        if (staffData.isEmpty()) {
            client.player.sendMessage(Text.literal("§cNo staff data found").formatted(Formatting.RED), false);
            return;
        }

        client.player.sendMessage(Text.literal("§6=== Staff List ===").formatted(Formatting.GOLD), false);

        boolean hasUnknownPlayers = false;
        boolean hasOfflinePlayers = false;

        for (Map.Entry<String, List<StaffMember>> entry : staffData.entrySet()) {
            String role = entry.getKey();
            List<StaffMember> staffMembers = entry.getValue();

            if (staffMembers.isEmpty()) continue;

            String displayRole = role.substring(0, 1).toUpperCase() + role.substring(1);
            MutableText roleMessage = createRoleMessage(displayRole, staffMembers);
            client.player.sendMessage(roleMessage, false);

            if (staffMembers.stream().anyMatch(member -> member.isUnknown)) {
                hasUnknownPlayers = true;
            }
            if (staffMembers.stream().anyMatch(member -> !isPlayerOnline(member.displayName, client))) {
                hasOfflinePlayers = true;
            }
        }

        int totalStaff = staffData.values().stream().mapToInt(List::size).sum();
        client.player.sendMessage(Text.literal("§7Total staff members: §b" + totalStaff).formatted(Formatting.GRAY), false);

        if (hasUnknownPlayers) {
            client.player.sendMessage(Text.literal("§cNote: Could not resolve some usernames").formatted(Formatting.RED), false);
        } else if (hasOfflinePlayers) {
            client.player.sendMessage(Text.literal("§eNote: Some staff members are currently offline").formatted(Formatting.YELLOW), false);
        }
    }

    private static boolean isPlayerOnline(String playerName, MinecraftClient client) {
        if (client.getNetworkHandler() == null) return false;
        return client.getNetworkHandler().getPlayerList().stream()
                .anyMatch(entry -> entry.getProfile().getName().equals(playerName));
    }

    private static MutableText createRoleMessage(String displayRole, List<StaffMember> staffMembers) {
        MutableText baseMessage = Text.literal(displayRole + ": ").formatted(Formatting.YELLOW);
        MinecraftClient client = MinecraftClient.getInstance();

        for (int i = 0; i < staffMembers.size(); i++) {
            StaffMember member = staffMembers.get(i);
            MutableText playerText = createHoverablePlayerText(member, client);

            if (i > 0) {
                baseMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
            }
            baseMessage.append(playerText);
        }
        return baseMessage;
    }

    private static MutableText createHoverablePlayerText(StaffMember member, MinecraftClient client) {
        boolean isOnline = isPlayerOnline(member.displayName, client);
        Formatting color;

        if (member.isUnknown) {
            color = Formatting.RED;
        } else {
            color = isOnline ? Formatting.GREEN : Formatting.YELLOW;
        }

        MutableText hoverText = Text.literal("UUID: " + member.uuid.toString()).formatted(Formatting.GRAY);
        hoverText.append(Text.literal("\nStatus: " + (isOnline ? "Online" : "Offline")).formatted(isOnline ? Formatting.GREEN : Formatting.YELLOW));

        if (member.isUnknown) {
            hoverText.append(Text.literal("\nNote: Username could not be resolved").formatted(Formatting.RED));
        }

        return Text.literal(member.displayName)
                .styled(style -> style
                        .withColor(color)
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                hoverText
                        ))
                );
    }

    private static class StaffMember {
        public final String displayName;
        public final UUID uuid;
        public final boolean isUnknown;

        public StaffMember(String displayName, UUID uuid, boolean isUnknown) {
            this.displayName = displayName;
            this.uuid = uuid;
            this.isUnknown = isUnknown;
        }
    }
}