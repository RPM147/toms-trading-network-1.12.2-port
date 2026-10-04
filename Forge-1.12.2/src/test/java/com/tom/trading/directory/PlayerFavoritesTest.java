package com.tom.trading.directory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PlayerFavoritesTest {
    private static final String KEY = "toms_trading_network_favorites";
    private MachineDirectoryEntry entry(int x, UUID id, MachineDirectoryEntry.Evidence evidence) {
        return new MachineDirectoryEntry(new MachineAddress(0, x, 64, 0), id, UUID.randomUUID(), "Owner", "Shop", evidence);
    }
    private DirectoryQuery change(MachineDirectoryData data, UUID id, boolean value) {
        return new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, data.getWorldId(), data.getRevision(), "",
                DirectoryTab.FAVORITES, id, value);
    }
    private NBTTagCompound saved(NBTTagCompound entity) {
        return entity.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(KEY);
    }

    @Test public void favoritesArePrivateWorldBoundPersistentAndIdempotent() {
        MachineDirectoryData data = MachineDirectoryData.create(); UUID id = UUID.randomUUID();
        data.observe(entry(1, id, MachineDirectoryEntry.Evidence.OBSERVED)); long revision = data.getRevision();
        NBTTagCompound player = new NBTTagCompound(), otherPlayer = new NBTTagCompound();
        NBTTagCompound persisted = new NBTTagCompound(); persisted.setString("OtherMod", "keep");
        player.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        DirectoryQuery add = change(data, id, true);
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, add));
        NBTTagCompound once = player.copy();
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, add)); assertEquals(once, player);
        assertEquals(Collections.singleton(id), PlayerFavorites.read(player.copy(), data.getWorldId()));
        assertTrue(PlayerFavorites.read(otherPlayer, data.getWorldId()).isEmpty()); assertTrue(otherPlayer.getKeySet().isEmpty());
        assertTrue(PlayerFavorites.read(player, UUID.randomUUID()).isEmpty());
        assertEquals("keep", player.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getString("OtherMod"));
        assertEquals(revision, data.getRevision()); // Private changes do not invalidate everyone else's cursor.
        NBTTagCompound clonedPlayer = new NBTTagCompound();
        clonedPlayer.setTag(EntityPlayer.PERSISTED_NBT_TAG, player.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).copy());
        assertEquals(Collections.singleton(id), PlayerFavorites.read(clonedPlayer, data.getWorldId()));
        try { PlayerFavorites.read(player, data.getWorldId()).clear(); fail("Mutable snapshot"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void maximumMembershipRejectsAdditionalAddsButAllowsIdempotenceAndStaleRemoval() {
        MachineDirectoryData data = MachineDirectoryData.create(); NBTTagCompound player = new NBTTagCompound();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i <= PlayerFavorites.MAX_FAVORITES; i++) {
            UUID id = UUID.randomUUID(); ids.add(id); data.observe(entry(i, id, MachineDirectoryEntry.Evidence.OBSERVED));
            assertEquals(i == PlayerFavorites.MAX_FAVORITES ? DirectoryPage.Result.FAVORITES_FULL : DirectoryPage.Result.OK,
                    PlayerFavorites.apply(player, data, change(data, id, true)));
        }
        assertEquals(PlayerFavorites.MAX_FAVORITES, PlayerFavorites.read(player, data.getWorldId()).size());
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, ids.get(0), true)));
        data.removeVerified(new MachineAddress(0, 0, 64, 0));
        assertEquals(DirectoryPage.Result.STALE, PlayerFavorites.apply(player, data, change(data, ids.get(0), true)));
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, ids.get(0), false)));
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, ids.get(256), true)));
        assertEquals(PlayerFavorites.MAX_FAVORITES, PlayerFavorites.read(player, data.getWorldId()).size());
    }

    @Test public void staleCursorUnknownHintAndConflictingIdentityNeverMutate() {
        MachineDirectoryData data = MachineDirectoryData.create(); NBTTagCompound player = new NBTTagCompound();
        UUID valid = UUID.randomUUID(), hint = UUID.randomUUID(), duplicate = UUID.randomUUID();
        data.observe(entry(1, valid, MachineDirectoryEntry.Evidence.OBSERVED));
        DirectoryQuery old = change(data, valid, true);
        data.importHints(Collections.singletonList(entry(2, hint, MachineDirectoryEntry.Evidence.HINT)), false);
        data.observe(entry(3, duplicate, MachineDirectoryEntry.Evidence.OBSERVED));
        data.observe(entry(4, duplicate, MachineDirectoryEntry.Evidence.OBSERVED));
        assertEquals(DirectoryPage.Result.STALE, PlayerFavorites.apply(player, data, old));
        DirectoryQuery wrongWorld = new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, UUID.randomUUID(), data.getRevision(), "",
                DirectoryTab.ALL, valid, true);
        assertEquals(DirectoryPage.Result.STALE, PlayerFavorites.apply(player, data, wrongWorld));
        for (UUID id : Arrays.asList(hint, duplicate, UUID.randomUUID()))
            assertEquals(DirectoryPage.Result.STALE, PlayerFavorites.apply(player, data, change(data, id, true)));
        assertTrue(player.getKeySet().isEmpty());
        assertEquals(DirectoryPage.Result.UNAVAILABLE, PlayerFavorites.apply(player,
                new MachineDirectoryData(MachineDirectoryData.DATA_NAME), old));
    }

    @Test public void replacementAtSameAddressDoesNotInheritFavorites() {
        MachineDirectoryData data = MachineDirectoryData.create(); NBTTagCompound player = new NBTTagCompound();
        UUID first = UUID.randomUUID(), replacement = UUID.randomUUID();
        data.observe(entry(1, first, MachineDirectoryEntry.Evidence.OBSERVED));
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, first, true)));
        data.observe(entry(1, replacement, MachineDirectoryEntry.Evidence.OBSERVED));
        assertFalse(PlayerFavorites.read(player, data.getWorldId()).contains(replacement));
    }

    @Test public void malformedAndFuturePayloadsArePreservedWithoutOverwritingOtherData() {
        MachineDirectoryData data = MachineDirectoryData.create(); UUID id = UUID.randomUUID();
        data.observe(entry(1, id, MachineDirectoryEntry.Evidence.OBSERVED));
        NBTTagCompound original = new NBTTagCompound();
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(original, data, change(data, id, true)));
        for (int mode = 0; mode < 5; mode++) {
            NBTTagCompound malformed = original.copy(), payload = saved(malformed);
            if (mode == 0) payload.setInteger("Format", 999);
            if (mode == 1) payload.removeTag("WorldUUIDMost");
            if (mode == 2) payload.setString("Machines", "invalid");
            if (mode == 3) {
                NBTTagList wrongList = new NBTTagList(); wrongList.appendTag(new NBTTagString("invalid")); payload.setTag("Machines", wrongList);
            }
            if (mode == 4) {
                NBTTagList duplicate = payload.getTagList("Machines", 10); duplicate.appendTag(duplicate.getCompoundTagAt(0).copy());
            }
            NBTTagCompound before = malformed.copy();
            assertEquals(DirectoryPage.Result.UNAVAILABLE, PlayerFavorites.apply(malformed, data, change(data, id, false)));
            assertEquals(before, malformed);
        }
    }

    @Test public void unsupportedPersistedContainerIsNeverReplaced() {
        MachineDirectoryData data = MachineDirectoryData.create(); UUID id = UUID.randomUUID();
        data.observe(entry(1, id, MachineDirectoryEntry.Evidence.OBSERVED));
        NBTTagCompound malformed = new NBTTagCompound(); malformed.setString(EntityPlayer.PERSISTED_NBT_TAG, "future");
        NBTTagCompound before = malformed.copy();
        assertEquals(DirectoryPage.Result.UNAVAILABLE, PlayerFavorites.apply(malformed, data, change(data, id, true)));
        assertEquals(before, malformed);
    }
    @Test public void newAddsReclaimDeletedMembershipButKeepConflictingIdentities() {
        MachineDirectoryData data = MachineDirectoryData.create(); NBTTagCompound player = new NBTTagCompound();
        UUID removed = UUID.randomUUID(), conflicted = UUID.randomUUID(), next = UUID.randomUUID();
        data.observe(entry(1, removed, MachineDirectoryEntry.Evidence.OBSERVED));
        data.observe(entry(2, conflicted, MachineDirectoryEntry.Evidence.OBSERVED));
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, removed, true)));
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, conflicted, true)));
        data.removeVerified(new MachineAddress(0, 1, 64, 0));
        data.observe(entry(3, conflicted, MachineDirectoryEntry.Evidence.OBSERVED));
        data.observe(entry(4, next, MachineDirectoryEntry.Evidence.OBSERVED));
        assertEquals(DirectoryPage.Result.OK, PlayerFavorites.apply(player, data, change(data, next, true)));
        assertEquals(new HashSet<>(Arrays.asList(conflicted, next)), PlayerFavorites.read(player, data.getWorldId()));
    }
}
