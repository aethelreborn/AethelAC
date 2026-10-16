package ac.aethel.aethelac.platform.bukkit.utils.placeholder;

import ac.aethel.aethelac.AethelAPI;
import ac.grim.grimac.api.GrimUser;
import ac.aethel.aethelac.player.AethelPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "aethel";
    }

    public @NotNull String getAuthor() {
        return String.join(", ", AethelAPI.INSTANCE.getGrimPlugin().getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return AethelAPI.INSTANCE.getExternalAPI().getGrimVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        Set<String> staticReplacements = AethelAPI.INSTANCE.getExternalAPI().getStaticReplacements().keySet();
        Set<String> variableReplacements = AethelAPI.INSTANCE.getExternalAPI().getVariableReplacements().keySet();
        ArrayList<String> placeholders = new ArrayList<>(staticReplacements.size() + variableReplacements.size());
        for (String s : staticReplacements) {
            placeholders.add(s.equals("%aethel_version%") ? s : "%grim_" + s.replace("%", "") + "%");
        }
        for (String s : variableReplacements) {
            placeholders.add(s.equals("%player%") ? "%grim_player%" : "%grim_player_" + s.replace("%", "") + "%");
        }
        return placeholders;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        for (Map.Entry<String, String> entry : AethelAPI.INSTANCE.getExternalAPI().getStaticReplacements().entrySet()) {
            String key = entry.getKey().equals("%aethel_version%")
                    ? "version"
                    : entry.getKey().replace("%", "");
            if (params.equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }

        if (offlinePlayer instanceof Player player) {
            AethelPlayer aethelPlayer = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(player.getUniqueId());
            if (aethelPlayer == null) return null;

            for (Map.Entry<String, Function<GrimUser, String>> entry : AethelAPI.INSTANCE.getExternalAPI().getVariableReplacements().entrySet()) {
                String key = entry.getKey().equals("%player%")
                        ? "player"
                        : "player_" + entry.getKey().replace("%", "");
                if (params.equalsIgnoreCase(key)) {
                    return entry.getValue().apply(aethelPlayer);
                }
            }
        }

        return null;
    }
}
