package ac.aethel.aethelac.checks.impl.badpackets;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;

@CheckData(name = "BadPacketsA", stableKey = "aethel.badpackets.duplicate_slot", description = "Sent duplicate slot id")
public class BadPacketsA extends Check implements PreViaPacketReceiveListener {
    private static final Verbose V = Verbose.of("slot={sint}");

    private int lastSlot = -1;

    public BadPacketsA(final AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            final int slot = new WrapperPlayClientHeldItemChange(event).getSlot();

            if (slot == lastSlot && flag(V.write(verbose()).sint(slot)) && shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }

            lastSlot = slot;
        }
    }
}
