package ac.aethel.aethelac.checks.type;

import com.github.retrooper.packetevents.event.PacketSendEvent;

public interface PreViaPacketSendListener {
    void onPreViaPacketSend(PacketSendEvent event);
}
