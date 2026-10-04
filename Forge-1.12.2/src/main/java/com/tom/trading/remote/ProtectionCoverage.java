package com.tom.trading.remote;

import java.util.function.BiPredicate;

/** An unloaded search region cannot establish the absence of an entity-based protection. */
public final class ProtectionCoverage {
    private ProtectionCoverage() {}
    public static boolean loaded(double x, double z, double entityRadius, BiPredicate<Integer, Integer> loadedChunk) {
        Bounds bounds = bounds(x, z, entityRadius);
        if (bounds == null) return false;
        for (int cx = bounds.minX; cx < bounds.maxX; cx++) for (int cz = bounds.minZ; cz < bounds.maxZ; cz++)
            if (!loadedChunk.test(cx, cz)) return false;
        return true;
    }
    /** The typed World#getEntitiesWithinAABB query uses floor/ceil, with exclusive upper bounds. */
    public static Bounds bounds(double x, double z, double entityRadius) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(entityRadius) || entityRadius < 0
                || entityRadius > 64 || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) return null;
        double radius = 20 + entityRadius;
        int minX = (int) Math.floor((x - radius) / 16), maxX = (int) Math.ceil((x + radius) / 16);
        int minZ = (int) Math.floor((z - radius) / 16), maxZ = (int) Math.ceil((z + radius) / 16);
        return new Bounds(minX, maxX, minZ, maxZ);
    }
    public static final class Bounds {
        public final int minX, maxX, minZ, maxZ;
        private Bounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
        }
        public int size() { return (maxX - minX) * (maxZ - minZ); }
    }
}
