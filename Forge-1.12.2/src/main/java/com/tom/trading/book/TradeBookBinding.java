package com.tom.trading.book;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import java.util.Objects;
import java.util.UUID;

/** A server-issued bearer key: possession permits reading this machine's ledger, never management. */
public final class TradeBookBinding {
    public static final String TAG = "TTNTradeBook";
    public final UUID world, machine, key;
    public TradeBookBinding(UUID world, UUID machine, UUID key) {
        this.world = Objects.requireNonNull(world); this.machine = Objects.requireNonNull(machine); this.key = Objects.requireNonNull(key);
    }
    public static TradeBookBinding read(NBTTagCompound item) {
        if (item == null || !item.hasKey(TAG, Constants.NBT.TAG_COMPOUND)) return null;
        NBTTagCompound tag = item.getCompoundTag(TAG);
        if (!tag.hasKey("Format", Constants.NBT.TAG_INT) || tag.getInteger("Format") != 1
                || !tag.hasUniqueId("World") || !tag.hasUniqueId("Machine") || !tag.hasUniqueId("Key")) return null;
        return new TradeBookBinding(tag.getUniqueId("World"), tag.getUniqueId("Machine"), tag.getUniqueId("Key"));
    }
    public void write(NBTTagCompound item) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Format", 1);
        tag.setUniqueId("World", world); tag.setUniqueId("Machine", machine); tag.setUniqueId("Key", key);
        item.setTag(TAG, tag);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof TradeBookBinding)) return false;
        TradeBookBinding binding = (TradeBookBinding) other;
        return world.equals(binding.world) && machine.equals(binding.machine) && key.equals(binding.key);
    }
    @Override public int hashCode() { return Objects.hash(world, machine, key); }
}
