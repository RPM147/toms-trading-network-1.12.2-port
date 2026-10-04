package com.tom.trading.trade;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class TradeItemHashTest {
    @Test public void repeatedHashLookupDoesNotRevisitImmutableNbt() {
        CountingTag tag = new CountingTag();
        tag.setIntArray("payload", new int[8192]);
        TradeItem item = new TradeItem("test:heavy", 0, tag, tag, Collections.emptySet(), 1024);
        int expected = item.hashCode();
        CountingTag.hashCalls = 0;
        for (int i = 0; i < 4096; i++) assertEquals(expected, item.hashCode());
        assertEquals("NBT must be hashed once at construction, not on every map access", 0, CountingTag.hashCalls);
    }

    @Test public void copiesAndCallerMutationsCannotChangeIdentityOrCachedHash() {
        NBTTagCompound tag = new NBTTagCompound(), caps = new NBTTagCompound();
        tag.setIntArray("payload", new int[] {1, 2, 3});
        caps.setInteger("energy", 99);
        TradeItem first = new TradeItem("test:item", 0, tag, caps, Collections.singleton("oreTest"), 64);
        TradeItem equal = new TradeItem("test:item", 0, tag, caps, Collections.singleton("oreTest"), 64);
        int hash = first.hashCode();
        tag.getIntArray("payload")[0] = 999;
        caps.setInteger("energy", 0);
        first.copyTag().getIntArray("payload")[1] = 999;
        assertEquals(equal, first);
        assertEquals(equal.hashCode(), first.hashCode());
        assertEquals(hash, first.hashCode());
        assertNotEquals(first, new TradeItem("test:item", 0, tag, caps, Collections.singleton("oreTest"), 64));
    }

    private static final class CountingTag extends NBTTagCompound {
        static int hashCalls;
        @Override public int hashCode() { hashCalls++; return super.hashCode(); }
        @Override public NBTTagCompound copy() {
            CountingTag copy = new CountingTag();
            for (String key : getKeySet()) copy.setTag(key, getTag(key).copy());
            return copy;
        }
    }
}
