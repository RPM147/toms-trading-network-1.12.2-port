package com.tom.trading.directory;

import net.minecraft.util.math.BlockPos;
import java.util.Objects;
import java.util.Comparator;

/** Save-local address, never a permission or a machine identity. */
public final class MachineAddress {
    public static final Comparator<MachineAddress> ORDER = Comparator.comparingInt((MachineAddress a) -> a.dimension)
            .thenComparingInt(a -> a.x).thenComparingInt(a -> a.z).thenComparingInt(a -> a.y);
    public final int dimension, x, y, z;

    public MachineAddress(int dimension, int x, int y, int z) {
        if (Math.abs((long) x) > 30000000L || Math.abs((long) z) > 30000000L || y < 0 || y > 255)
            throw new IllegalArgumentException("Invalid machine position");
        this.dimension = dimension; this.x = x; this.y = y; this.z = z;
    }

    public MachineAddress(int dimension, BlockPos pos) { this(dimension, pos.getX(), pos.getY(), pos.getZ()); }
    public BlockPos pos() { return new BlockPos(x, y, z); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof MachineAddress)) return false;
        MachineAddress a = (MachineAddress) other;
        return dimension == a.dimension && x == a.x && y == a.y && z == a.z;
    }
    @Override public int hashCode() { return Objects.hash(dimension, x, y, z); }
    @Override public String toString() { return dimension + ":" + x + "," + y + "," + z; }
}
