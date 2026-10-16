package ac.aethel.aethelac.checks.type;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;

public interface PrePredictionPacketReceiveListener {
    void onPrePredictionPacketReceive(PacketReceiveEvent event);
}
