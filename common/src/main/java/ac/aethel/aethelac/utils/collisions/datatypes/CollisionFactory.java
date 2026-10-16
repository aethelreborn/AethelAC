package ac.aethel.aethelac.utils.collisions.datatypes;

import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

public interface CollisionFactory {
    CollisionBox fetch(AethelPlayer player, ClientVersion version, WrappedBlockState block, int x, int y, int z);
}
