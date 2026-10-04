package com.tom.trading.directory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/** Per-player save data, accessed only on the server thread. No shared membership or permissions. */
public final class PlayerFavorites {
    public static final int MAX_FAVORITES = 256;
    private static final int FORMAT = 1;
    private static final String KEY = "toms_trading_network_favorites";

    private PlayerFavorites() {}

    /** Forge copies PlayerPersisted across respawn; vanilla player saving persists entity ForgeData. */
    public static Set<UUID> read(NBTTagCompound entityData, UUID worldId) {
        Objects.requireNonNull(worldId, "worldId");
        if (entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)
                && !entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, Constants.NBT.TAG_COMPOUND))
            throw new IllegalStateException("Invalid persisted player data");
        NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(KEY)) return Collections.emptySet();
        if (!persisted.hasKey(KEY, Constants.NBT.TAG_COMPOUND)) throw new IllegalStateException("Invalid favorite data");
        NBTTagCompound saved = persisted.getCompoundTag(KEY);
        if (!saved.hasKey("Format", Constants.NBT.TAG_INT) || saved.getInteger("Format") != FORMAT
                || !saved.hasUniqueId("WorldUUID")) throw new IllegalStateException("Unsupported favorite format");
        // Copying player data into another save must not favor a replacement at the same coordinates.
        if (!worldId.equals(saved.getUniqueId("WorldUUID"))) return Collections.emptySet();
        NBTTagList machines = saved.getTagList("Machines", Constants.NBT.TAG_COMPOUND);
        if (!saved.hasKey("Machines", Constants.NBT.TAG_LIST)
                || ((NBTTagList) saved.getTag("Machines")).tagCount() != machines.tagCount()
                || machines.tagCount() > MAX_FAVORITES) throw new IllegalStateException("Invalid favorite list");
        Set<UUID> result = new LinkedHashSet<>();
        for (int i = 0; i < machines.tagCount(); i++) {
            NBTTagCompound machine = machines.getCompoundTagAt(i);
            if (!machine.hasUniqueId("MachineUUID") || !result.add(machine.getUniqueId("MachineUUID")))
                throw new IllegalStateException("Invalid or duplicate favorite identity");
        }
        return Collections.unmodifiableSet(result);
    }

    /** Validate the save/cursor and identity before touching this authenticated player's data. */
    public static DirectoryPage.Result apply(NBTTagCompound entityData, MachineDirectoryData data, DirectoryQuery query) {
        if (!data.isSupported()) return DirectoryPage.Result.UNAVAILABLE;
        if (query.favoriteMachine == null || query.page != 0 || !data.getWorldId().equals(query.expectedWorld)
                || query.expectedRevision != data.getRevision()) return DirectoryPage.Result.STALE;
        // Removing an obsolete UUID remains safe. Adding hints, unknown or conflicting identities does not.
        if (query.favoriteValue && !data.isUniqueIdentity(query.favoriteMachine)) return DirectoryPage.Result.STALE;
        try {
            Set<UUID> favorites = new LinkedHashSet<>(read(entityData, data.getWorldId()));
            boolean present = favorites.contains(query.favoriteMachine);
            if (present == query.favoriteValue) return DirectoryPage.Result.OK;
            if (query.favoriteValue) {
                // Removed machines are no longer listed. Reclaim their slots without loading any chunks.
                // Keep identities that still exist as hints or conflicts; they may be repaired later.
                favorites.removeIf(id -> !data.containsIdentity(id));
                if (favorites.size() >= MAX_FAVORITES) return DirectoryPage.Result.FAVORITES_FULL;
                favorites.add(query.favoriteMachine);
            } else favorites.remove(query.favoriteMachine);
            save(entityData, data.getWorldId(), favorites);
            return DirectoryPage.Result.OK;
        } catch (IllegalStateException unsupported) {
            // Preserve future or corrupt persisted data instead of silently overwriting it.
            return DirectoryPage.Result.UNAVAILABLE;
        }
    }

    private static void save(NBTTagCompound entityData, UUID worldId, Set<UUID> favorites) {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("Format", FORMAT); saved.setUniqueId("WorldUUID", worldId);
        NBTTagList machines = new NBTTagList();
        for (UUID id : favorites) {
            NBTTagCompound machine = new NBTTagCompound();
            machine.setUniqueId("MachineUUID", id); machines.appendTag(machine);
        }
        saved.setTag("Machines", machines);
        NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setTag(KEY, saved);
        entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }
}
