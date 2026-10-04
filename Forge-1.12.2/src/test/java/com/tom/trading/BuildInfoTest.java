package com.tom.trading;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuildInfoTest {
    @Test
    public void identityIsStable() {
        assertEquals("toms_trading_network", BuildInfo.MOD_ID);
        assertEquals("Tom's Trading Network", BuildInfo.NAME);
    }

    @Test
    public void targetVersionsArePinned() {
        assertEquals("1.12.2", BuildInfo.MINECRAFT_VERSION);
        assertEquals("14.23.5.2859", BuildInfo.FORGE_VERSION);
        assertEquals(System.getProperty("tomsTradingNetworkVersion"), BuildInfo.VERSION);
        assertTrue(BuildInfo.VERSION.matches("0[.]3[.]4-port[.][1-9][0-9]*(-dev)?"));
    }

    @Test public void protocolChangeRequiresTheExactSameModVersionOnBothPeers() {
        TradingNetworkMod mod = new TradingNetworkMod();
        for (net.minecraftforge.fml.relauncher.Side side : net.minecraftforge.fml.relauncher.Side.values()) {
            assertTrue(mod.acceptsPeer(java.util.Collections.singletonMap(BuildInfo.MOD_ID, BuildInfo.VERSION), side));
            org.junit.Assert.assertFalse(mod.acceptsPeer(java.util.Collections.singletonMap(BuildInfo.MOD_ID, "0.3.4-port.4"), side));
            org.junit.Assert.assertFalse(mod.acceptsPeer(java.util.Collections.singletonMap(BuildInfo.MOD_ID, "0.3.4-port.6"), side));
            org.junit.Assert.assertFalse(mod.acceptsPeer(java.util.Collections.emptyMap(), side));
        }
    }
}
