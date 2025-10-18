package jinzo.worldy.client.utils;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import jinzo.worldy.client.WorldyConfig;
import me.shedaniel.autoconfig.AutoConfig;

public class ModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(WorldyConfig.class, parent).get();
    }
}
