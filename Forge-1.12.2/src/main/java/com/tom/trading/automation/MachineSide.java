package com.tom.trading.automation;

import net.minecraft.util.EnumFacing;

/** Ordinals are the persisted/UI bit order; the model front is opposite FACING. */
public enum MachineSide {
    BOTTOM, TOP, FRONT, LEFT, BACK, RIGHT;

    private static final MachineSide[] HORIZONTAL = {FRONT, RIGHT, BACK, LEFT};

    public int mask() { return 1 << ordinal(); }

    public static MachineSide fromWorld(EnumFacing facing, EnumFacing side) {
        if (side == EnumFacing.DOWN) return BOTTOM;
        if (side == EnumFacing.UP) return TOP;
        return HORIZONTAL[Math.floorMod(facing.getOpposite().getHorizontalIndex()
                - side.getHorizontalIndex(), 4)];
    }
}
