package jinzo.worldy.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "worldy")
public class WorldyConfig implements ConfigData {
    public boolean displayLogoutMessages = true;
}
