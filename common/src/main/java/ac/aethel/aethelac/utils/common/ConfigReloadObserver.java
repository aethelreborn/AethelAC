package ac.aethel.aethelac.utils.common;


import ac.grim.grimac.api.config.ConfigManager;

public interface ConfigReloadObserver {

    void onReload(ConfigManager config);

}
