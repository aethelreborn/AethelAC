package ac.aethel.aethelac.checks.impl.scaffolding;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockPlaceCheck;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

@CheckData(name = "InvalidPlaceFace", stableKey = "aethel.scaffolding.invalid_place_b", description = "Sent impossible block face id")
public class InvalidPlaceFace extends BlockPlaceCheck implements BlockPlaceListener {
    private static final Verbose V = Verbose.of("direction={sint}");

    public InvalidPlaceFace(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (place.getFaceId() == 255 && PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_8)) {
            return;
        }

        if (place.getFaceId() < 0 || place.getFaceId() > 5) {
            // ban
            int direction = place.getFaceId();
            if (flag(V.write(verbose()).sint(direction)) && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
        }
    }
}
