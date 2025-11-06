package jinzo.worldy.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "worldy")
public class WorldyConfig implements ConfigData {

    @ConfigEntry.Gui.CollapsibleObject
    public GeneralSettings general = new GeneralSettings();
    @ConfigEntry.Gui.CollapsibleObject
    public WaypointSettings waypoint = new WaypointSettings();

    public static class GeneralSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean displayLogoutMessages = true;
    }

    public static class WaypointSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean enabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 40)
        public int pathLength = 10;

        public double lastDeathX = 0;
        public double lastDeathY = 0;
        public double lastDeathZ = 0;
    }

    @Override
    public void validatePostLoad() {
        if (waypoint.pathLength < 1) waypoint.pathLength = 1;
        if (waypoint.pathLength > 40) waypoint.pathLength = 40;
    }
}
