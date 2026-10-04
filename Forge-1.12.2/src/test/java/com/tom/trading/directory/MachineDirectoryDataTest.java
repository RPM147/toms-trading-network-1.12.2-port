package com.tom.trading.directory;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MachineDirectoryDataTest {
    private final MachineAddress a = new MachineAddress(0, 1, 64, 1);
    private final MachineAddress b = new MachineAddress(7, 17, 80, -20);
    private final UUID machine = UUID.randomUUID(), owner = UUID.randomUUID();
    private MachineDirectoryEntry entry(MachineAddress address, UUID id, MachineDirectoryEntry.Evidence evidence) {
        return new MachineDirectoryEntry(address, id, owner, "Owner", "Shop", evidence);
    }
    @Test public void roundTripKeepsOnlyMetadataAndHasNoStaticCrossWorldState() {
        MachineDirectoryData data = MachineDirectoryData.create();
        data.observe(entry(a, machine, MachineDirectoryEntry.Evidence.OBSERVED));
        NBTTagCompound tag = data.writeToNBT(new NBTTagCompound());
        MachineDirectoryData restored = new MachineDirectoryData(MachineDirectoryData.DATA_NAME);
        restored.readFromNBT(tag);
        assertTrue(restored.isSupported());
        assertEquals(data.getWorldId(), restored.getWorldId());
        assertEquals(data.get(a), restored.get(a));
        assertTrue(restored.isUniqueIdentity(a, machine));
        assertEquals(a, restored.pollPending()); // Restart rechecks even chunks loaded before registration.
        assertFalse(restored.isDirty());
        assertEquals(0, MachineDirectoryData.create().size());
        assertNotEquals(data.getWorldId(), MachineDirectoryData.create().getWorldId());
        assertFalse(tag.toString().contains("SaleStock"));
    }
    @Test public void legacyHintUpgradesAndRepeatedImportsNeverOverwriteObservedMetadata() {
        MachineDirectoryData data = MachineDirectoryData.create();
        MachineDirectoryEntry hint = entry(a, null, MachineDirectoryEntry.Evidence.HINT);
        data.importHints(Collections.singletonList(hint), false);
        assertFalse(data.isUniqueIdentity(a, machine));
        data.observe(entry(a, machine, MachineDirectoryEntry.Evidence.OBSERVED));
        data.importHints(Collections.singletonList(hint), false);
        assertEquals(1, data.size());
        assertEquals(machine, data.get(a).machineId);
        assertTrue(data.isUniqueIdentity(a, machine));
    }
    @Test public void copiedUuidBlocksBothLocationsUntilExplicitRepair() {
        MachineDirectoryData data = MachineDirectoryData.create();
        data.observe(entry(a, machine, MachineDirectoryEntry.Evidence.OBSERVED));
        data.observe(entry(b, machine, MachineDirectoryEntry.Evidence.OBSERVED));
        assertEquals(1, data.conflictCount());
        assertFalse(data.isUniqueIdentity(a, machine));
        assertFalse(data.isUniqueIdentity(b, machine));
        MachineDirectoryData restored = new MachineDirectoryData(MachineDirectoryData.DATA_NAME);
        restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        assertEquals(1, restored.conflictCount());
        UUID repaired = UUID.randomUUID();
        restored.observe(entry(b, repaired, MachineDirectoryEntry.Evidence.OBSERVED));
        assertTrue(restored.isUniqueIdentity(a, machine));
        assertTrue(restored.isUniqueIdentity(b, repaired));
        assertEquals(0, restored.conflictCount());
    }
    @Test public void invalidationQueueDoesNotDeleteAndReplacementRemovesOldIdentity() {
        MachineDirectoryData data = MachineDirectoryData.create();
        data.observe(entry(a, machine, MachineDirectoryEntry.Evidence.OBSERVED));
        data.enqueue(a); data.enqueue(a); data.enqueueChunk(0, 0, 0);
        assertEquals(a, data.pollPending()); assertNull(data.pollPending());
        assertEquals(1, data.size());
        UUID next = UUID.randomUUID();
        long before = data.getRevision();
        data.observe(entry(a, next, MachineDirectoryEntry.Evidence.OBSERVED));
        assertTrue(data.getRevision() > before);
        assertFalse(data.isUniqueIdentity(a, machine));
        assertTrue(data.isUniqueIdentity(a, next));
        data.removeVerified(a);
        assertEquals(0, data.size());
        assertFalse(data.isUniqueIdentity(a, next));
    }
    @Test public void futureAndMalformedDataRemainUnavailableAndPreserved() {
        for (int mode = 0; mode < 3; mode++) {
            NBTTagCompound tag = MachineDirectoryData.create().writeToNBT(new NBTTagCompound());
            if (mode == 0) tag.setInteger("Format", 99);
            if (mode == 1) {
                NBTTagList bad = new NBTTagList(); bad.appendTag(new NBTTagString("not an entry")); tag.setTag("Entries", bad);
            }
            if (mode == 2) tag.removeTag("WorldUUIDMost");
            MachineDirectoryData data = new MachineDirectoryData(MachineDirectoryData.DATA_NAME);
            assertFalse(data.isSupported());
            data.readFromNBT(tag);
            assertFalse(data.isSupported()); assertFalse(data.isDirty());
            assertEquals(tag, data.writeToNBT(new NBTTagCompound()));
        }
    }
    @Test public void duplicateAddressCorruptionIsNotSilentlyMerged() {
        MachineDirectoryData data = MachineDirectoryData.create();
        data.observe(entry(a, machine, MachineDirectoryEntry.Evidence.OBSERVED));
        NBTTagCompound tag = data.writeToNBT(new NBTTagCompound());
        NBTTagList list = tag.getTagList("Entries", 10); list.appendTag(list.getCompoundTagAt(0).copy());
        data.readFromNBT(tag);
        assertFalse(data.isSupported()); assertEquals(0, data.size());
    }
    @Test public void labelsAreBoundedAndCannotSupplyFormattingOrAuthority() {
        MachineDirectoryEntry entry = new MachineDirectoryEntry(a, machine, owner, "A\nB\u00a7c", "", MachineDirectoryEntry.Evidence.HINT);
        assertEquals("ABc", entry.ownerName);
        assertEquals(owner, entry.ownerId);
    }
}
