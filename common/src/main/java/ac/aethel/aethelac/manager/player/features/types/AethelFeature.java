package ac.aethel.aethelac.manager.player.features.types;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;
import ac.aethel.aethelac.player.AethelPlayer;

public interface AethelFeature {
    String getName();

    void setState(AethelPlayer player, ConfigManager config, FeatureState state);

    boolean isEnabled(AethelPlayer player);

    boolean isEnabledInConfig(AethelPlayer player, ConfigManager config);
}
