package jinzo.worldy.client.models;

import org.jetbrains.annotations.NotNull;

public class WaypointEntry {
    double x;
    double y;
    double z;
    String world;

    public WaypointEntry(double x, double y, double z, @NotNull String world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public @NotNull String world() { return world; }
}
