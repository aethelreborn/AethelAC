package ac.aethel.aethelac.platform.fabric.mixins;

import ac.aethel.aethelac.platform.api.world.PlatformChunk;
import ac.aethel.aethelac.platform.api.world.PlatformWorld;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.utils.world.FabricIntermediaryLevelChunkUtil;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.UUID;

@Mixin(Level.class)
@Implements(@Interface(iface = PlatformWorld.class, prefix = "aethelac$"))
abstract class FabricIntermediaryLevelMixin implements LevelAccessor {

    @Shadow
    public abstract ResourceKey<Level> dimension();

    public boolean aethelac$isChunkLoaded(int chunkX, int chunkZ) {
        return FabricIntermediaryLevelChunkUtil.hasChunkAt((Level) (Object) this, chunkX, chunkZ);
    }

    public WrappedBlockState aethelac$getBlockAt(int x, int y, int z) {
        return WrappedBlockState.getByGlobalId(
                Block.getId(getBlockState(new BlockPos(x, y, z)))
        );
    }

    public String aethelac$getName() {
        return this.dimension().location().toString();
    }

    public @Nullable UUID aethelac$getUID() {
        return null;
    }

    public PlatformChunk aethelac$getChunkAt(int currChunkX, int currChunkZ) {
        return (PlatformChunk) getChunk(currChunkX, currChunkZ);
    }

    public boolean aethelac$isLoaded() {
        return AethelACFabricIntermediaryLoaderPlugin.FABRIC_SERVER.getLevel(this.dimension()) != null;
    }
}
