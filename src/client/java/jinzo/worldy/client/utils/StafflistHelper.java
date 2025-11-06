package jinzo.worldy.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import jinzo.worldy.client.Models.Staff;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class StafflistHelper {

    private static final String STAFF_JSON_URL = "https://raw.githubusercontent.com/pernio/Worldy/refs/heads/main/data/staff.json";

    private static final Map<UUID, String> uuidToNameCache = new ConcurrentHashMap<>();
    private static final Map<String, UUID> playerUuidMap = new ConcurrentHashMap<>();
    // Preserve insertion order and provide basic thread-safety for single operations + synchronized iteration
    private static final Map<String, List<Staff>> cachedStaffData =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "worldy-staffloader");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean isLoading = false;
    private static volatile Instant lastFetched = Instant.EPOCH;

    private StafflistHelper() {}

    public static Map<String, List<Staff>> getCachedStaffData() {
        Map<String, List<Staff>> snapshot = new LinkedHashMap<>();
        synchronized (cachedStaffData) {
            for (var e : cachedStaffData.entrySet()) {
                snapshot.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public static void loadStaffListOnJoin(MinecraftClient client) {
        if (isLoading) return;
        if (lastFetched.plusSeconds(60 * 5).isAfter(Instant.now()) && !cachedStaffData.isEmpty()) return;

        isLoading = true;
        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(STAFF_JSON_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                try (InputStream inputStream = conn.getInputStream();
                     Scanner scanner = new Scanner(inputStream).useDelimiter("\\A")) {
                    String jsonContent = scanner.hasNext() ? scanner.next() : "";
                    Map<String, List<UUID>> staffData = parseStaffJson(jsonContent);

                    Map<String, List<Staff>> temp = new LinkedHashMap<>();
                    for (Map.Entry<String, List<UUID>> entry : staffData.entrySet()) {
                        String role = entry.getKey();
                        List<UUID> uuids = entry.getValue();
                        List<Staff> members = new ArrayList<>();
                        for (UUID id : uuids) {
                            String maybeName = uuidToNameCache.get(id);
                            Staff s = (maybeName != null)
                                    ? new Staff(maybeName, id, false)
                                    : new Staff("Unknown (" + id.toString().substring(0, 8) + "...)",
                                    id, true);
                            members.add(s);
                            if (!s.isUnknown()) playerUuidMap.put(s.getDisplayName(), id);
                        }
                        temp.put(role, Collections.unmodifiableList(members));
                    }

                    synchronized (cachedStaffData) {
                        cachedStaffData.clear();
                        cachedStaffData.putAll(temp);
                    }

                    resolveUnknownNamesAsync(client, staffData);

                    lastFetched = Instant.now();
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
                isLoading = false;
            }
        });
    }

    private static Map<String, List<UUID>> parseStaffJson(String json) {
        Map<String, List<UUID>> staffData = new LinkedHashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(true);
            reader.beginObject();
            while (reader.hasNext()) {
                String role = reader.nextName();
                List<UUID> uuids = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    String uuidStr = reader.nextString().trim();
                    try {
                        String formattedUuid = uuidStr;
                        if (formattedUuid.length() == 32) {
                            formattedUuid = formattedUuid.substring(0, 8) + "-" +
                                    formattedUuid.substring(8, 12) + "-" +
                                    formattedUuid.substring(12, 16) + "-" +
                                    formattedUuid.substring(16, 20) + "-" +
                                    formattedUuid.substring(20, 32);
                        }
                        uuids.add(UUID.fromString(formattedUuid));
                    } catch (Exception ex) {
                        System.err.println("Invalid UUID in staff.json: " + uuidStr);
                    }
                }
                reader.endArray();
                staffData.put(role, uuids);
            }
            reader.endObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage(), e);
        }
        return staffData;
    }

    private static void resolveUnknownNamesAsync(MinecraftClient client, Map<String, List<UUID>> staffData) {
        executor.submit(() -> {
            for (Map.Entry<String, List<UUID>> entry : staffData.entrySet()) {
                for (UUID uuid : entry.getValue()) {
                    if (uuidToNameCache.containsKey(uuid)) continue;

                    String maybe = null;
                    if (client.getNetworkHandler() != null) {
                        var found = client.getNetworkHandler().getPlayerList().stream()
                                .filter(pl -> pl.getProfile().getId().equals(uuid))
                                .findFirst();
                        if (found.isPresent()) {
                            maybe = found.get().getProfile().getName();
                        }
                    }

                    if (maybe == null) {
                        maybe = fetchUsernameFromMojang(uuid);
                    }

                    if (maybe != null) {
                        uuidToNameCache.put(uuid, maybe);
                        playerUuidMap.put(maybe, uuid);
                    }
                }
            }

            Map<String, List<Staff>> resolved = new LinkedHashMap<>();
            for (var entry : staffData.entrySet()) {
                List<Staff> list = new ArrayList<>();
                for (UUID id : entry.getValue()) {
                    String name = uuidToNameCache.get(id);
                    if (name != null) {
                        list.add(new Staff(name, id, false));
                    } else {
                        list.add(new Staff("Unknown (" + id.toString().substring(0, 8) + "...)",
                                id, true));
                    }
                }
                resolved.put(entry.getKey(), Collections.unmodifiableList(list));
            }
            synchronized (cachedStaffData) {
                cachedStaffData.clear();
                cachedStaffData.putAll(resolved);
            }
        });
    }

    private static String fetchUsernameFromMojang(UUID uuid) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (InputStream inputStream = connection.getInputStream();
                     Scanner scanner = new Scanner(inputStream).useDelimiter("\\A")) {
                    String jsonResponse = scanner.hasNext() ? scanner.next() : "";
                    JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                    if (jsonObject.has("name")) {
                        return jsonObject.get("name").getAsString();
                    }
                }
            } else {
                System.out.println("Mojang API returned: " + responseCode + " for UUID: " + uuid);
            }
        } catch (Exception e) {
            System.err.println("Error fetching from Mojang API for UUID " + uuid + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

}
