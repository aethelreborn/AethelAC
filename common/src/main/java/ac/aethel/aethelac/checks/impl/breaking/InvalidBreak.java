package ac.aethel.aethelac.checks.impl.breaking;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.impl.verbose.VerboseCodecs;
import ac.aethel.aethelac.checks.type.BlockBreakListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;

@CheckData(name = "InvalidBreak", stableKey = "aethel.breaking.invalid_break", description = "Sent impossible block face id")
public class InvalidBreak extends Check implements BlockBreakListener {
    private static final Verbose V = Verbose.of("face={sint}, action={digging}");

    public InvalidBreak(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.faceId == 255 && blockBreak.action == DiggingAction.CANCELLED_DIGGING && player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_7_10)) {
            return;
        }

        if (blockBreak.faceId < 0 || blockBreak.faceId > 5) {
            // ban
            if (flag(V.write(verbose())
                    .sint(blockBreak.faceId)
                    .uint(VerboseCodecs.enumId(blockBreak.action))) && shouldModifyPackets()) {
                blockBreak.cancel();
            }
        }
    }
}
