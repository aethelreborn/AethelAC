package ac.aethel.aethelac.manager.player.features;

import ac.aethel.aethelac.manager.player.features.types.AethelFeature;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import com.google.common.collect.ImmutableMap;

import java.util.regex.Pattern;

public class FeatureBuilder {

    private static final Pattern VALID = Pattern.compile("[a-zA-Z0-9_]{1,64}");
    private final ImmutableMap.Builder<String, AethelFeature> mapBuilder = ImmutableMap.builder();

    public <T extends AethelFeature> void register(T feature) {
        if (!VALID.matcher(feature.getName()).matches()) {
            LogUtil.error("Invalid feature name: " + feature.getName());
            return;
        }
        mapBuilder.put(feature.getName(), feature);
    }

    public ImmutableMap<String, AethelFeature> buildMap() {
        return mapBuilder.build();
    }

}
