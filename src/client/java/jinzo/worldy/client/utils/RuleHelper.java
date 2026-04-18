package jinzo.worldy.client.utils;

import com.google.gson.Gson;
import jinzo.worldy.client.models.Rule;
import jinzo.worldy.client.models.RuleData;
import jinzo.worldy.client.WorldyClient;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class RuleHelper {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "worldy-rulesloader");
        t.setDaemon(true);
        return t;
    });

    private static volatile RuleData cachedRules;
    private static volatile Instant lastFetched = Instant.EPOCH;
    private static volatile boolean loading = false;

    private RuleHelper() {}

    public static void loadRulesAsync() {
        if (loading) return;
        if (lastFetched.plusSeconds(60 * 5).isAfter(Instant.now()) && cachedRules != null) return;

        loading = true;
        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(WorldyClient.getConfig().fetch.rulesDataUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (InputStream in = conn.getInputStream();
                     Scanner scanner = new Scanner(in).useDelimiter("\\A")) {

                    String json = scanner.hasNext() ? scanner.next() : "";
                    RuleData data = new Gson().fromJson(json, RuleData.class);

                    if (data != null && data.rules != null) {
                        cachedRules = data;
                        lastFetched = Instant.now();
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch rules: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
                loading = false;
            }
        });
    }

    public static Optional<Rule> ruleById(@NotNull String id) {
        if (cachedRules == null) return Optional.empty();
        return cachedRules.rules.stream()
                .filter(r -> r.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public static List<Rule> allRules() {
        if (cachedRules == null) return List.of();
        return Collections.unmodifiableList(cachedRules.rules);
    }
}
