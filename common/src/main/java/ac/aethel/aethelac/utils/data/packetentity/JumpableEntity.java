package ac.aethel.aethelac.utils.data.packetentity;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.data.VectorData;

import java.util.Set;

public interface JumpableEntity {

    boolean isJumping();

    void setJumping(boolean jumping);

    float getJumpPower();

    void setJumpPower(float jumpPower);

    boolean canPlayerJump(AethelPlayer player);

    boolean hasSaddle();

    void executeJump(AethelPlayer player, Set<VectorData> possibleVectors);

}
