package com.tom.trading.directory;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class SharedEconomyTabTest {
    private MachineDirectoryEntry entry(int x, UUID id, String name) {
        return new MachineDirectoryEntry(new MachineAddress(0, x, 64, 0), id, UUID.randomUUID(), "Owner", name,
                MachineDirectoryEntry.Evidence.OBSERVED);
    }
    private MachineDirectoryData restore(NBTTagCompound tag) {
        MachineDirectoryData data = new MachineDirectoryData(MachineDirectoryData.DATA_NAME); data.readFromNBT(tag); return data;
    }
    private DirectoryQuery query(MachineDirectoryData data, int page, String search, DirectoryTab tab) {
        return new DirectoryQuery(UUID.randomUUID(), 1, 0, page, data.getWorldId(), data.getRevision(), search, tab);
    }
    private DirectoryPage page(MachineDirectoryData data, DirectoryQuery query) {
        List<DirectoryPage> answers = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query, 0, () -> true, answers::add);
        for (int i = 0; i < 100 && answers.isEmpty(); i++) assertTrue(data.pager().tick(1, d -> true) <= DirectoryPager.RECORDS_PER_TICK);
        assertEquals(1, answers.size()); return answers.get(0);
    }
    @Test public void membershipSurvivesRestartAndRenameButNotReplacementOrOtherSave() {
        MachineDirectoryData data = MachineDirectoryData.create();
        UUID id = UUID.randomUUID(); MachineDirectoryEntry e = entry(0, id, "Exchange"); data.observe(e);
        assertTrue(data.addSpecial(e.address, id));
        long revision = data.getRevision(); assertFalse(data.addSpecial(e.address, id)); assertEquals(revision, data.getRevision());
        data.observe(entry(0, id, "Renamed")); assertTrue(data.isSpecial(id));
        NBTTagCompound tag = data.writeToNBT(new NBTTagCompound()); assertEquals(2, tag.getInteger("Format"));
        data = restore(tag); assertTrue(data.isSupported()); assertTrue(data.isSpecial(id));
        assertFalse(MachineDirectoryData.create().isSpecial(id));
        UUID replacement = UUID.randomUUID(); data.observe(entry(0, replacement, "Exchange"));
        assertFalse(data.isSpecial(replacement)); assertEquals(0, page(data, query(data, 0, "", DirectoryTab.ECONOMY)).total);
        assertTrue(data.removeSpecial(id)); assertFalse(data.removeSpecial(id));
        assertFalse(restore(data.writeToNBT(new NBTTagCompound())).isSpecial(id));
    }
    @Test public void legacyCatalogueMigratesWithoutInventingMembershipOrChangingWorldIdentity() {
        MachineDirectoryData original = MachineDirectoryData.create(); original.observe(entry(0, UUID.randomUUID(), "Currency & Tax"));
        NBTTagCompound old = original.writeToNBT(new NBTTagCompound()); old.setInteger("Format", 1); old.removeTag("SpecialMachines");
        MachineDirectoryData data = restore(old);
        assertTrue(data.isSupported()); assertEquals(original.getWorldId(), data.getWorldId()); assertEquals(1, data.size());
        assertTrue(data.specialMachines().isEmpty()); assertEquals(2, data.writeToNBT(new NBTTagCompound()).getInteger("Format"));
    }
    @Test public void changingOnlyTabLabelDoesNotChangeMembershipOrSavedSchema() {
        MachineDirectoryData data = MachineDirectoryData.create();
        MachineDirectoryEntry e = entry(0, UUID.randomUUID(), "Shop"); data.observe(e); data.addSpecial(e.address, e.machineId);
        NBTTagCompound before = data.writeToNBT(new NBTTagCompound());
        DirectoryPage original = page(data, query(data, 0, "", DirectoryTab.ECONOMY));
        DirectoryPage renamed = original.withSpecialTabName("Community Shops");
        assertEquals(original.rows, renamed.rows); assertEquals(original.revision, renamed.revision);
        assertEquals(original.worldId, renamed.worldId); assertTrue(data.isSpecial(e.machineId));
        assertEquals(before, data.writeToNBT(new NBTTagCompound()));
    }
    @Test public void malformedMembershipIsPreservedAndFailsClosed() {
        for (int mode = 0; mode < 6; mode++) {
            NBTTagCompound tag = MachineDirectoryData.create().writeToNBT(new NBTTagCompound());
            NBTTagList ids = new NBTTagList(); NBTTagCompound row = new NBTTagCompound(); row.setUniqueId("MachineUUID", UUID.randomUUID());
            if (mode == 0) tag.removeTag("SpecialMachines");
            if (mode == 1) { ids.appendTag(new NBTTagString("wrong type")); tag.setTag("SpecialMachines", ids); }
            if (mode == 2) { ids.appendTag(new NBTTagCompound()); tag.setTag("SpecialMachines", ids); }
            if (mode == 3) { ids.appendTag(row); ids.appendTag(row.copy()); tag.setTag("SpecialMachines", ids); }
            if (mode == 4) {
                for (int i = 0; i <= MachineDirectoryData.MAX_SPECIAL; i++) {
                    NBTTagCompound next = new NBTTagCompound(); next.setUniqueId("MachineUUID", UUID.randomUUID()); ids.appendTag(next);
                }
                tag.setTag("SpecialMachines", ids);
            }
            if (mode == 5) tag.setInteger("Format", 3);
            MachineDirectoryData data = restore(tag);
            assertFalse(data.isSupported()); assertFalse(data.isDirty()); assertTrue(data.specialMachines().isEmpty());
            assertEquals(tag, data.writeToNBT(new NBTTagCompound()));
            try { data.removeSpecial(UUID.randomUUID()); fail(); } catch (IllegalStateException expected) { }
        }
    }
    @Test public void onlyObservedUniqueIdentitiesCanBeAddedAndConflictsStillDisableRows() {
        MachineDirectoryData data = MachineDirectoryData.create(); UUID id = UUID.randomUUID();
        MachineDirectoryEntry e = entry(0, id, "Tax");
        data.importHints(Collections.singletonList(new MachineDirectoryEntry(e.address, id, e.ownerId, "Owner", "Tax", MachineDirectoryEntry.Evidence.HINT)), false);
        try { data.addSpecial(e.address, id); fail(); } catch (IllegalArgumentException expected) { }
        data.observe(e); assertTrue(data.addSpecial(e.address, id)); data.observe(entry(1, id, "Copy"));
        try { data.addSpecial(e.address, id); fail(); } catch (IllegalArgumentException expected) { }
        DirectoryPage result = page(data, query(data, 0, "", DirectoryTab.ECONOMY));
        assertEquals(2, result.total);
        for (DirectoryPage.Row row : result.rows) assertEquals(DirectoryPage.State.IDENTITY_CONFLICT, row.state);
        assertFalse(data.isUniqueIdentity(e.address, id));
    }
    @Test public void capacityIsBoundedAndRemovingAbsentTargetsFreesMembershipWithoutDeletingMachines() {
        MachineDirectoryData data = MachineDirectoryData.create(); UUID first = null;
        for (int i = 0; i <= MachineDirectoryData.MAX_SPECIAL; i++) {
            MachineDirectoryEntry e = entry(i, UUID.randomUUID(), "Tax"); data.observe(e);
            if (i == 0) first = e.machineId;
            if (i == MachineDirectoryData.MAX_SPECIAL) {
                try { data.addSpecial(e.address, e.machineId); fail(); } catch (IllegalStateException expected) { }
                data.removeVerified(new MachineAddress(0, 0, 64, 0));
                assertTrue(data.isSpecial(first)); assertTrue(data.removeSpecial(first)); assertTrue(data.addSpecial(e.address, e.machineId));
            } else data.addSpecial(e.address, e.machineId);
        }
        assertEquals(256, data.specialMachines().size()); assertEquals(256, data.size());
        try { data.specialMachines().clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void categoryPagingSearchAndAllFastPathKeepCorrectTotals() {
        MachineDirectoryData data = MachineDirectoryData.create();
        for (int i = 0; i < 124; i++) {
            MachineDirectoryEntry e = entry(i, UUID.randomUUID(), i % 2 == 0 ? "Exchange" : "Ordinary"); data.observe(e);
            if (i % 2 == 0) data.addSpecial(e.address, e.machineId);
        }
        DirectoryPage first = page(data, query(data, 0, "", DirectoryTab.ECONOMY));
        DirectoryPage last = page(data, query(data, 1, "", DirectoryTab.ECONOMY));
        assertEquals(62, first.total); assertEquals(50, first.rows.size()); assertEquals(12, last.rows.size());
        assertEquals(100, last.rows.get(0).entry.address.x);
        assertEquals(0, page(data, query(data, 0, "Ordinary", DirectoryTab.ECONOMY)).total);
        assertEquals(62, page(data, query(data, 0, "Exchange", DirectoryTab.ECONOMY)).total);
        List<DirectoryPage> answers = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query(data, 0, "", DirectoryTab.ALL), 0, () -> true, answers::add);
        assertEquals(50, data.pager().tick(1, d -> true)); assertEquals(124, answers.get(0).total);
    }
    @Test public void changedMembershipInvalidatesPendingSnapshotsAndWorkRemainsBounded() {
        MachineDirectoryData data = MachineDirectoryData.create(); MachineDirectoryEntry member = entry(0, UUID.randomUUID(), "Tax"); data.observe(member);
        for (int i = 1; i < 5000; i++) data.observe(entry(i, UUID.randomUUID(), "Shop"));
        List<DirectoryPage> answers = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query(data, 0, "", DirectoryTab.ECONOMY), 0, () -> true, answers::add);
        assertEquals(4096, data.pager().tick(1, d -> true)); assertTrue(answers.isEmpty());
        data.addSpecial(member.address, member.machineId);
        assertEquals(0, data.pager().tick(2, d -> true)); assertEquals(DirectoryPage.Result.STALE, answers.get(0).result);
        assertTrue(data.isDirty());
    }
    @Test public void nonAdminCommandExecutionIsDeniedBeforeAnyWorldAccess() throws Exception {
        DirectoryCommand command = new DirectoryCommand(); assertEquals(2, command.getRequiredPermissionLevel());
        ICommandSender denied = (ICommandSender) Proxy.newProxyInstance(ICommandSender.class.getClassLoader(), new Class<?>[]{ICommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("canUseCommand")) { assertEquals(2, args[0]); assertEquals("ttndirectory", args[1]); return false; }
                    throw new AssertionError("Unauthorized command accessed " + method.getName());
                });
        for (String[] args : Arrays.asList(new String[]{"special", "add", "0", "0", "64", "0"},
                new String[]{"special", "remove", UUID.randomUUID().toString()}, new String[]{"special", "list"})) {
            try { command.execute(null, denied, args); fail(); }
            catch (CommandException expected) { assertEquals("commands.generic.permission", expected.getMessage()); }
        }
    }
}
