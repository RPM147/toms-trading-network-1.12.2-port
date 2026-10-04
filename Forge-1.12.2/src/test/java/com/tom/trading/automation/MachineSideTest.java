package com.tom.trading.automation;

import net.minecraft.util.EnumFacing;
import org.junit.Test;
import static org.junit.Assert.*;

public class MachineSideTest {
    @Test public void allRotationsKeepTheUpstreamModelAndSavedBitOrder() {
        EnumFacing[][] cases = {
                {EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.WEST},
                {EnumFacing.WEST, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH},
                {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST},
                {EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH}
        };
        for (EnumFacing[] row : cases) {
            assertEquals(MachineSide.FRONT, MachineSide.fromWorld(row[0], row[1]));
            assertEquals(MachineSide.BACK, MachineSide.fromWorld(row[0], row[0]));
            assertEquals(MachineSide.LEFT, MachineSide.fromWorld(row[0], row[2]));
            assertEquals(MachineSide.RIGHT, MachineSide.fromWorld(row[0], row[3]));
            assertEquals(MachineSide.TOP, MachineSide.fromWorld(row[0], EnumFacing.UP));
            assertEquals(MachineSide.BOTTOM, MachineSide.fromWorld(row[0], EnumFacing.DOWN));
        }
        assertEquals(4, MachineSide.FRONT.mask());
        assertEquals(32, MachineSide.RIGHT.mask());
    }
}
