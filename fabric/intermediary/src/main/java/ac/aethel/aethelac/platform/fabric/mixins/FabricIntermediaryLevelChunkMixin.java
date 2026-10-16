package ac.aethel.aethelac.platform.fabric.mixins;

import ac.aethel.aethelac.platform.api.world.PlatformChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
@Implements(@Interface(iface = PlatformChunk.class, prefix = "aethelac$"))
abstract class FabricIntermediaryLevelChunkMixin {
    @Unique
    private static final BlockPos.MutableBlockPos aethelac$sharedPos = new BlockPos.MutableBlockPos();

    public int aethelac$getBlockID(int x, int y, int z) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        aethelac$sharedPos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
        return Block.getId(chunk.getBlockState(aethelac$sharedPos));
    }
}
