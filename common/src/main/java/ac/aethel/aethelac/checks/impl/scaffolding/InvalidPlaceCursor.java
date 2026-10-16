package ac.aethel.aethelac.checks.impl.scaffolding;

import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockPlaceCheck;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.util.Vector3f;

@CheckData(name = "InvalidPlaceCursor", stableKey = "aethel.scaffolding.invalid_place_a", description = "Sent invalid cursor position")
public class InvalidPlaceCursor extends BlockPlaceCheck implements BlockPlaceListener {
    public InvalidPlaceCursor(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        Vector3f cursor = place.cursor;
        if (cursor == null) return;
        if (!Float.isFinite(cursor.x) || !Float.isFinite(cursor.y) || !Float.isFinite(cursor.z)) {
            if (flag() && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
        }
    }
}
