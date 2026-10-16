package ac.aethel.aethelac.utils.data.interpolation;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.data.packetentity.PacketEntity;
import ac.aethel.aethelac.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.protocol.vector.positionpath.PositionPath;
import org.jetbrains.annotations.Nullable;

public interface EntityInterpolation {

    void begin(@Nullable PositionPath path, boolean relative, boolean hasPos, double packetX, double packetY, double packetZ, @Nullable Float yaw, @Nullable Float pitch, boolean positionSync, int transaction);

    void confirm(int transaction);

    void tick(boolean tickingReliably);

    void reset(SimpleCollisionBox position);

    SimpleCollisionBox position();

    default SimpleCollisionBox hitbox(AethelPlayer player, PacketEntity entity) {
        SimpleCollisionBox hitbox = position();
        GetBoundingBox.expandBoundingBoxByEntityDimensions(hitbox, player, entity);
        return hitbox;
    }

    default void initializeRotation(float yaw, float pitch) {
    }

}
