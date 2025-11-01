package jinzo.worldy.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "worldy")
public class WorldyConfig implements ConfigData {

    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    public boolean displayLogoutMessages = true;

    @ConfigEntry.Category("waypoints")
    @ConfigEntry.Gui.CollapsibleObject
    public WaypointSettings waypoint = new WaypointSettings();

    public static class WaypointSettings {
        @ConfigEntry.Gui.Tooltip(count = 2)
        public boolean enabled = true;

        @ConfigEntry.Gui.Tooltip(count = 3)
        @ConfigEntry.BoundedDiscrete(min = 1, max = 40)
        public int pathLength = 10;
    }

    @Override
    public void validatePostLoad() {
        if (waypoint.pathLength < 1) waypoint.pathLength = 1;
        if (waypoint.pathLength > 40) waypoint.pathLength = 40;
    }
}
