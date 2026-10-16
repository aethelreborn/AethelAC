package ac.aethel.aethelac.checks.impl.badpackets;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "BadPacketsC", stableKey = "aethel.badpackets.wake_not_sleeping", description = "Tried to wake up while not sleeping", experimental = true)
public class BadPacketsC extends Check implements PreViaPacketReceiveListener {
    public BadPacketsC(@NotNull AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION
                && new WrapperPlayClientEntityAction(event).getAction() == WrapperPlayClientEntityAction.Action.LEAVE_BED
                && !player.isInBed) {
            flag();
        }
    }
}
