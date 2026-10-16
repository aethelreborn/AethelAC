package ac.aethel.aethelac.manager.player.features.types;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;
import ac.aethel.aethelac.player.AethelPlayer;

public class ExemptElytraFeature implements AethelFeature {

    @Override
    public String getName() {
        return "ExemptElytra";
    }

    @Override
    public void setState(AethelPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setExemptElytra(true);
            case DISABLED -> player.setExemptElytra(false);
            default -> player.setExemptElytra(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(AethelPlayer player) {
        return player.isExemptElytra();
    }

    @Override
    public boolean isEnabledInConfig(AethelPlayer player, ConfigManager config) {
        return config.getBooleanElse("exempt-elytra", false);
    }

}
