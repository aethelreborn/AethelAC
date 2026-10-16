package ac.aethel.aethelac.utils.data.packetentity;

import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import java.util.UUID;

public class PacketEntityUnHittable extends PacketEntity {

    public PacketEntityUnHittable(AethelPlayer player, UUID uuid, EntityType type, double x, double y, double z) {
        super(player, uuid, type, x, y, z);
    }

    @Override
    public boolean canHit() {
        return false;
    }
}
