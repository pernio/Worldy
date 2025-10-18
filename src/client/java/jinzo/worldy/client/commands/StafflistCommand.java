package jinzo.worldy.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class StafflistCommand {

    // Store UUID mapping for hover texts
    private static Map<String, UUID> playerUuidMap = new HashMap<>();

    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return literal("stafflist")
                .executes(ctx -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null) return 0;

                    // Clear previous UUID mapping
                    playerUuidMap.clear();

                    // Run in separate thread to avoid blocking the game
                    new Thread(() -> {
                        try {
                            URL url = new URL("https://raw.githubusercontent.com/WorldMinecraft/server-patch/main/data/staff.json");
                            InputStream inputStream = url.openStream();
                            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                            String jsonContent = scanner.hasNext() ? scanner.next() : "";
                            scanner.close();

                            // Parse JSON and get UUIDs
                            Map<String, List<UUID>> staffData = parseStaffJson(jsonContent);

                            // Convert UUIDs to player names and store mapping
                            Map<String, List<StaffMember>> staffWithNames = convertUuidsToNames(staffData);

                            // Send result back to main thread
                            client.execute(() -> {
                                sendStaffList(client, staffWithNames);
                            });

                        } catch (Exception e) {
                            client.execute(() -> {
                                client.player.sendMessage(Text.literal("§cError fetching staff list: " + e.getMessage()).formatted(Formatting.RED), false);
                            });
                        }
                    }).start();

                    // Send loading message
                    client.player.sendMessage(Text.literal("§7Fetching staff list...").formatted(Formatting.GRAY), false);

                    return 1;
                });
    }

    private static Map<String, List<UUID>> parseStaffJson(String json) {
        Map<String, List<UUID>> staffData = new LinkedHashMap<>();

        try {
            // Remove whitespace and brackets
            json = json.trim().replaceAll("\\s+", "");
            json = json.substring(1, json.length() - 1);

            // Split by role sections
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
                                // Ensure UUID has hyphens
                                String formattedUuid = uuidStr.trim();
                                if (formattedUuid.length() == 32) {
                                    // Add hyphens: 8-4-4-4-12
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

        for (Map.Entry<String, List<UUID>> entry : staffData.entrySet()) {
            String role = entry.getKey();
            List<UUID> uuids = entry.getValue();
            List<StaffMember> staffMembers = new ArrayList<>();

            for (UUID uuid : uuids) {
                StaffMember member = getPlayerNameFromUuid(uuid, client);
                staffMembers.add(member);

                // Store UUID mapping for hover text
                if (!member.displayName.startsWith("Unknown") && !member.displayName.startsWith("Error")) {
                    playerUuidMap.put(member.displayName, uuid);
                }
            }

            staffWithNames.put(role, staffMembers);
        }

        return staffWithNames;
    }

    private static StaffMember getPlayerNameFromUuid(UUID uuid, MinecraftClient client) {
        try {
            // Method 1: Check if player is currently online
            if (client.getNetworkHandler() != null) {
                var playerListEntry = client.getNetworkHandler().getPlayerList().stream()
                        .filter(entry -> entry.getProfile().getId().equals(uuid))
                        .findFirst();

                if (playerListEntry.isPresent()) {
                    String playerName = playerListEntry.get().getProfile().getName();
                    return new StaffMember(playerName, uuid, false);
                }
            }

            // Return unknown with UUID info
            return new StaffMember("Unknown (" + uuid.toString().substring(0, 8) + "...)", uuid, true);

        } catch (Exception e) {
            return new StaffMember("Error (" + uuid.toString().substring(0, 8) + "...)", uuid, true);
        }
    }

    private static void sendStaffList(MinecraftClient client, Map<String, List<StaffMember>> staffData) {
        if (staffData.isEmpty()) {
            client.player.sendMessage(Text.literal("§cNo staff data found").formatted(Formatting.RED), false);
            return;
        }

        client.player.sendMessage(Text.literal("§6=== Staff List ===").formatted(Formatting.GOLD), false);

        boolean hasUnknownPlayers = false;

        for (Map.Entry<String, List<StaffMember>> entry : staffData.entrySet()) {
            String role = entry.getKey();
            List<StaffMember> staffMembers = entry.getValue();

            if (staffMembers.isEmpty()) {
                continue;
            }

            // Capitalize role name for display
            String displayRole = role.substring(0, 1).toUpperCase() + role.substring(1);

            // Create message with hoverable player names
            MutableText roleMessage = createRoleMessage(displayRole, staffMembers);
            client.player.sendMessage(roleMessage, false);

            // Check for unknown players
            if (staffMembers.stream().anyMatch(member -> member.isUnknown)) {
                hasUnknownPlayers = true;
            }
        }

        // Show total count
        int totalStaff = staffData.values().stream().mapToInt(List::size).sum();
        client.player.sendMessage(Text.literal("§7Total staff members: §b" + totalStaff).formatted(Formatting.GRAY), false);

        if (hasUnknownPlayers) {
            client.player.sendMessage(Text.literal("§cNote: Some staff members are not currently online").formatted(Formatting.RED), false);
        }
    }

    private static MutableText createRoleMessage(String displayRole, List<StaffMember> staffMembers) {
        MutableText baseMessage = Text.literal(displayRole + ": ").formatted(Formatting.YELLOW);

        for (int i = 0; i < staffMembers.size(); i++) {
            StaffMember member = staffMembers.get(i);

            // Create hover text with UUID information
            MutableText playerText = createHoverablePlayerText(member);

            if (i > 0) {
                baseMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
            }

            baseMessage.append(playerText);
        }

        return baseMessage;
    }

    private static MutableText createHoverablePlayerText(StaffMember member) {
        Formatting color = member.isUnknown ? Formatting.RED : Formatting.GREEN;

        // Create hover text showing the UUID
        MutableText hoverText = Text.literal("UUID: " + member.uuid.toString()).formatted(Formatting.GRAY);

        if (member.isUnknown) {
            hoverText.append(Text.literal("\nStatus: Offline/Unknown").formatted(Formatting.RED));
        } else {
            hoverText.append(Text.literal("\nStatus: Online").formatted(Formatting.GREEN));
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

    // Helper class to store staff member information
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