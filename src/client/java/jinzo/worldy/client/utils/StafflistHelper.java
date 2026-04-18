package jinzo.worldy.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jinzo.worldy.client.models.Staff;
import jinzo.worldy.client.WorldyClient;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class StafflistHelper {
    private static final Map<String, List<Staff>> cachedStaffData =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "worldy-staffloader");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean loading = false;
    private static volatile Instant lastFetched = Instant.EPOCH;
    private static volatile FetchState fetchState = FetchState.NOT_LOADED;

    private StafflistHelper() {}

    public enum FetchState {
        NOT_LOADED,
        LOADING,
        READY,
        EMPTY,
        UNAVAILABLE
    }

    public static @NotNull Map<String, List<Staff>> cachedStaffData() {
        Map<String, List<Staff>> snapshot = new LinkedHashMap<>();
        synchronized (cachedStaffData) {
            for (var e : cachedStaffData.entrySet()) {
                snapshot.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public static @NotNull FetchState getFetchState() {
        return fetchState;
    }

    public static void loadStaffListOnJoin(@NotNull MinecraftClient client) {
        if (loading) return;
        if (lastFetched.plusSeconds(60 * 5).isAfter(Instant.now()) && !cachedStaffData.isEmpty()) return;

        loading = true;
        fetchState = FetchState.LOADING;
        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(WorldyClient.getConfig().fetch.apiUrl + "player/staff/list");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    clearCachedStaffData();
                    fetchState = FetchState.UNAVAILABLE;
                    return;
                }

                try (InputStream inputStream = conn.getInputStream();
                     Scanner scanner = new Scanner(inputStream).useDelimiter("\\A")) {
                    String jsonContent = scanner.hasNext() ? scanner.next() : "";
                    if (jsonContent.isBlank()) {
                        clearCachedStaffData();
                        fetchState = FetchState.EMPTY;
                        return;
                    }

                    Map<String, List<Staff>> temp = parseStaffJson(jsonContent);

                    synchronized (cachedStaffData) {
                        cachedStaffData.clear();
                        cachedStaffData.putAll(temp);
                    }

                    fetchState = temp.isEmpty() ? FetchState.EMPTY : FetchState.READY;
                    lastFetched = Instant.now();
                }
            } catch (Exception ignored) {
                clearCachedStaffData();
                fetchState = FetchState.UNAVAILABLE;
            } finally {
                if (conn != null) conn.disconnect();
                loading = false;
            }
        });
    }

    private static @NotNull Map<String, List<Staff>> parseStaffJson(@NotNull String json) {
        Map<String, List<Staff>> staffData = new LinkedHashMap<>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;

                JsonObject entry = element.getAsJsonObject();

                String rank = entry.get("rank").getAsString().trim();
                if (rank.isEmpty()) continue;

                if (!entry.has("uuid")) continue;
                String uuid = entry.get("uuid").getAsString();

                if (!entry.has("name") || entry.get("name").isJsonNull()) continue;
                String name = entry.get("name").getAsString().trim();
                if (name.isEmpty()) continue;

                staffData.computeIfAbsent(rank, ignored -> new ArrayList<>()).add(new Staff(name, uuid));
            }
        } catch (IllegalStateException e) {
            throw new RuntimeException("Failed to parse staff response", e);
        }

        Map<String, List<Staff>> immutableData = new LinkedHashMap<>();
        for (Map.Entry<String, List<Staff>> entry : staffData.entrySet()) {
            immutableData.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return immutableData;
    }

    private static void clearCachedStaffData() {
        synchronized (cachedStaffData) {
            cachedStaffData.clear();
        }
    }

}
