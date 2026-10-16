package ac.aethel.aethelac.checks.impl.breaking;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockBreakListener;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;

@CheckData(name = "NoSwingBreak", stableKey = "aethel.breaking.no_swing_break", description = "Did not swing while breaking block", experimental = true)
public class NoSwingBreak extends Check implements BlockBreakListener, PreViaPacketReceiveListener {
    private boolean sentAnimation;
    private boolean sentBreak;

    public NoSwingBreak(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action != DiggingAction.CANCELLED_DIGGING) {
            sentBreak = true;
        }
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (isAnimation(event.getPacketType())) {
            sentAnimation = true;
        }

        if (isTickPacket(event.getPacketType())) {
            if (sentBreak && !sentAnimation) {
                flag();
            }

            sentAnimation = sentBreak = false;
        }
    }
}
