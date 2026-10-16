package ac.aethel.aethelac.utils.worldborder;

import ac.aethel.aethelac.utils.math.AethelMath;

public record StaticBorderExtent(double size) implements BorderExtent {

    @Override
    public double getMinX(double centerX, double absoluteMaxSize) {
        return AethelMath.clamp(centerX - size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public double getMaxX(double centerX, double absoluteMaxSize) {
        return AethelMath.clamp(centerX + size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public double getMinZ(double centerZ, double absoluteMaxSize) {
        return AethelMath.clamp(centerZ - size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public double getMaxZ(double centerZ, double absoluteMaxSize) {
        return AethelMath.clamp(centerZ + size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public BorderExtent tick() {
        return this;
    }

    @Override
    public BorderExtent update() {
        return this;
    }

}
