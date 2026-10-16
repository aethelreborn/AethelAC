package ac.aethel.aethelac.checks.impl.badpackets;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockBreakListener;
import ac.aethel.aethelac.checks.type.BlockPlaceCheck;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockBreak;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;

@CheckData(name = "BadPacketsH", stableKey = "aethel.badpackets.unexpected_sequence", description = "Sent unexpected sequence id", experimental = true)
public class BadPacketsH extends BlockPlaceCheck implements PacketReceiveListener, BlockPlaceListener, BlockBreakListener {
    private static final Verbose V = Verbose.of("expected={sint}, id={sint}");

    private int lastSequence;

    public BadPacketsH(final AethelPlayer player) {
        super(player);
    }

    @Override
    public boolean isApplicable() {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19)
                && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM
                && shouldCancel(new WrapperPlayClientUseItem(event).getSequence())) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (shouldCancel(place.sequence) && shouldCancel()) {
            place.resync();
        }
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        switch (blockBreak.action) {
            case START_DIGGING, FINISHED_DIGGING -> {
                if (shouldCancel(blockBreak.sequence)) {
                    blockBreak.cancel();
                }
            }
            case CANCELLED_DIGGING -> { // other actions will be checked by BadPacketsL
                if (blockBreak.sequence != 0 && flagSequence(0, blockBreak.sequence) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }
        }
    }

    public boolean shouldCancel(int sequence) {
        int expected = lastSequence + 1;
        lastSequence = sequence;
        return sequence != expected
                && flagSequence(expected, sequence)
                && shouldModifyPackets();
    }

    private boolean flagSequence(int expected, int sequence) {
        return flag(V.write(verbose()).sint(expected).sint(sequence));
    }

    public void onWorldChange() {
        lastSequence = 0;
    }
}
