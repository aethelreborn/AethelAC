package ac.aethel.aethelac.manager.player.features.types;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;
import ac.aethel.aethelac.player.AethelPlayer;

public class ForceSlowMovementFeature implements AethelFeature {

    @Override
    public String getName() {
        return "ForceSlowMovement";
    }

    @Override
    public void setState(AethelPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setForceSlowMovement(true);
            case DISABLED -> player.setForceSlowMovement(false);
            default -> player.setForceSlowMovement(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(AethelPlayer player) {
        return player.isForceSlowMovement();
    }

    @Override
    public boolean isEnabledInConfig(AethelPlayer player, ConfigManager config) {
        return config.getBooleanElse("force-slow-movement", true);
    }

}
