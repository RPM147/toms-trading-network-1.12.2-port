package com.tom.trading.tile;

import com.tom.trading.BuildInfo;
import com.tom.trading.automation.AutomationTransfer;
import com.tom.trading.automation.MachineSide;
import com.tom.trading.automation.SidedMachineHandler;
import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.compat.StackLimits;
import com.tom.trading.container.MachineView;
import com.tom.trading.directory.MachineDirectory;
import com.tom.trading.item.OreFilters;
import com.tom.trading.trade.ForgeTradeItemFactory;
import com.tom.trading.trade.ItemMatcher;
import com.tom.trading.trade.TradeDefinition;
import com.tom.trading.trade.TradeItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class TileVendingMachine extends TileEntity implements IWorldNameable, ITickable {
    public static final int DATA_VERSION = 3;
    public static final int DEFINITION_SLOTS = 4;
    public static final int REAL_INVENTORY_SLOTS = 8;
    public static final int MAX_QUANTITY = 1024;
    public static final int MAX_NAME_LENGTH = 64;

    private static final Logger LOGGER = LogManager.getLogger(BuildInfo.MOD_ID);
    private static final int ALL_SIDE_BITS = 0x3F;
    private static final int ALL_DEFINITION_BITS = 0xFF;
    private static final String DEFAULT_NAME_KEY = "tile." + BuildInfo.MOD_ID + ".vending_machine.name";

    private final NonNullList<ItemStack> paymentTemplates =
            NonNullList.withSize(DEFINITION_SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> saleTemplates =
            NonNullList.withSize(DEFINITION_SLOTS, ItemStack.EMPTY);
    private final int[] paymentQuantities = new int[DEFINITION_SLOTS];
    private final int[] saleQuantities = new int[DEFINITION_SLOTS];
    private final FixedItemStackHandler saleStock = new FixedItemStackHandler();
    private final FixedItemStackHandler earnings = new FixedItemStackHandler();
    private final AutomationTransfer automation =
            new AutomationTransfer(saleStock, earnings, this::acceptsAutomationItem, this::markDirty);
    private final IItemHandler[] sidedHandlers = new IItemHandler[6];
    private final int[] pullCursors = new int[6], pushCursors = new int[6], earningsCursors = new int[6];
    private boolean automationRunning, chunkUnloaded;
    private static final int NEIGHBOR_PROBES_PER_FACE = 32;

    private UUID machineUuid;
    private boolean directoryPending = true;
    private UUID ownerUuid;
    private String ownerNameCache;
    private String customName;
    private boolean creativeMode;
    private int inputSides;
    private int outputSides;
    private int autoSides;
    private int matchNbt = ALL_DEFINITION_BITS;
    private int loadedDataVersion = DATA_VERSION;
    private NBTTagCompound unsupportedFutureData;
    // Runtime-only: a container session cannot survive replacing/unloading its tile.
    private long offerRevision = 1;
    private long displayRevision = 1;
    private NBTTagCompound cachedDisplayState;
    private boolean capturingDisplayState;

    public long getOfferRevision() { return offerRevision; }

    public boolean matchesOfferRevision(long expected) { return expected > 0 && expected == offerRevision; }

    private void offerChanged() { offerRevision++; directoryPending = true; displayChanged(); }

    public long getDisplayRevision() { return displayRevision; }

    /** Shared display data only. Neither callers nor packet encoders receive the cached mutable NBT. */
    @Nullable
    public NBTTagCompound copyDisplayState() {
        if (cachedDisplayState == null) {
            if (capturingDisplayState) return null;
            long revision = displayRevision;
            capturingDisplayState = true;
            try {
                NBTTagCompound state = MachineView.capture(this);
                // Item/capability copy callbacks must not publish a mixed or already obsolete snapshot.
                if (displayRevision != revision) return null;
                cachedDisplayState = state;
            } finally {
                capturingDisplayState = false;
            }
        }
        return cachedDisplayState.copy();
    }

    private void invalidateDisplayState() {
        displayRevision++;
        cachedDisplayState = null;
    }

    private void displayChanged() { invalidateDisplayState(); markDirty(); }

    public ItemStackHandler getSaleStock() {
        return saleStock;
    }

    public ItemStackHandler getEarnings() {
        return earnings;
    }

    public boolean isAutomationQuarantined() { return automation.isQuarantined(); }

    public boolean isAutomationRunning() { return automationRunning; }

    public ItemStack takeAutomationRemainderForDrop() { return automation.takeForDrop(); }

    public boolean acceptsAutomationItem(ItemStack stack) {
        if (stack.isEmpty() || OreFilters.isFilter(stack) || !isDataVersionSupported()) return false;
        TradeItem candidate = ForgeTradeItemFactory.fromStack(stack);
        for (int slot = 0; slot < DEFINITION_SLOTS; slot++) {
            ItemStack template = saleTemplates.get(slot);
            if (!template.isEmpty() && !OreFilters.isFilter(template) && saleQuantities[slot] > 0
                    && ItemMatcher.matches(candidate, TradeDefinition.direct(
                    ForgeTradeItemFactory.fromStack(template), saleQuantities[slot], isMatchingNbt(slot + 4)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return side != null && canAccessAutomation() && sideEnabled(side, inputSides | outputSides);
        }
        return super.hasCapability(capability, side);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability != CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return super.getCapability(capability, side);
        if (!hasCapability(capability, side)) return null;
        int index = side.getIndex();
        if (sidedHandlers[index] == null) {
            sidedHandlers[index] = new SidedMachineHandler(saleStock, earnings,
                    () -> sideEnabled(side, inputSides), () -> sideEnabled(side, outputSides),
                    this::canAccessAutomation, this::acceptsAutomationItem);
        }
        return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(sidedHandlers[index]);
    }

    private boolean canAccessAutomation() {
        return !automationRunning && automationWorldReady();
    }

    private boolean automationWorldReady() {
        return world instanceof WorldServer && ((WorldServer) world).isCallingFromMinecraftThread()
                && !isInvalid() && !chunkUnloaded && isDataVersionSupported() && !automation.isQuarantined()
                && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this
                && world.getBlockState(pos).getBlock() instanceof BlockVendingMachine;
    }

    private boolean sideEnabled(EnumFacing side, int mask) {
        if (side == null || world == null || !world.isBlockLoaded(pos)) return false;
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockVendingMachine)) return false;
        MachineSide local = MachineSide.fromWorld(state.getValue(BlockVendingMachine.FACING), side);
        return local != MachineSide.FRONT && (mask & local.mask()) != 0;
    }

    @Override public void onLoad() {
        super.onLoad(); chunkUnloaded = false; invalidateDisplayState();
        directoryPending = !MachineDirectory.observe(this);
    }

    @Override public void onChunkUnload() {
        chunkUnloaded = true; invalidateDisplayState(); super.onChunkUnload();
    }

    @Override public void invalidate() {
        MachineDirectory.changed(this);
        invalidateDisplayState(); super.invalidate();
    }

    @Override
    public void update() {
        if (directoryPending && world instanceof WorldServer && !chunkUnloaded)
            directoryPending = !MachineDirectory.observe(this);
        if (automationRunning || (autoSides == 0 && !automation.hasPending()) || !automationWorldReady()
                || Math.floorMod(world.getTotalWorldTime(), 20) != Math.floorMod(pos.hashCode(), 20)) return;
        automationRunning = true;
        try {
            automation.drainPending();
            for (EnumFacing side : EnumFacing.values()) {
                if (automation.hasPending() || !automationWorldReady()) break;
                if (!sideEnabled(side, autoSides & (inputSides | outputSides))) continue;
                BlockPos neighborPos = pos.offset(side);
                if (!world.isBlockLoaded(neighborPos)) continue;
                TileEntity neighbor = world.getTileEntity(neighborPos);
                if (neighbor == null || neighbor.isInvalid()) continue;
                EnumFacing neighborFace = side.getOpposite();
                if (!neighbor.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, neighborFace)) continue;
                IItemHandler handler = neighbor.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, neighborFace);
                if (handler == null || handler.getSlots() <= 0) continue;
                BooleanSupplier neighborValid = () -> automationWorldReady() && world.isBlockLoaded(neighborPos)
                        && world.getTileEntity(neighborPos) == neighbor && !neighbor.isInvalid()
                        && neighbor.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, neighborFace)
                        && neighbor.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, neighborFace) == handler;
                if (sideEnabled(side, autoSides & inputSides)) {
                    transferFace(handler, side, true, () -> neighborValid.getAsBoolean()
                            && sideEnabled(side, autoSides & inputSides));
                }
                if (!automation.hasPending() && sideEnabled(side, autoSides & outputSides)) {
                    transferFace(handler, side, false, () -> neighborValid.getAsBoolean()
                            && sideEnabled(side, autoSides & outputSides));
                }
            }
        } catch (RuntimeException error) {
            if (autoSides != 0) {
                autoSides = 0;
                invalidateDisplayState();
            }
            markDirty();
            LOGGER.error("Automatic transfer stopped at {}. Quarantined={}. Inspect the neighbor and saved "
                    + "AutomationTransfer data before recovery; do not blindly retry an uncertain transfer.",
                    pos, automation.isQuarantined(), error);
        } finally {
            automationRunning = false;
        }
    }

    private void transferFace(IItemHandler neighbor, EnumFacing side, boolean pull, BooleanSupplier valid) {
        int face = side.getIndex();
        int count = neighbor.getSlots();
        if (count <= 0) return;
        int[] cursors = pull ? pullCursors : pushCursors;
        int start = Math.floorMod(cursors[face], count);
        int sourceSlot = -1;
        if (!pull) {
            for (int i = 0; i < REAL_INVENTORY_SLOTS; i++) {
                int slot = (earningsCursors[face] + i) % REAL_INVENTORY_SLOTS;
                if (!earnings.getStackInSlot(slot).isEmpty()) { sourceSlot = slot; break; }
            }
            if (sourceSlot < 0) return;
            earningsCursors[face] = sourceSlot;
        }
        for (int i = 0; i < Math.min(count, NEIGHBOR_PROBES_PER_FACE); i++) {
            int slot = (int) (((long) start + i) % count);
            cursors[face] = slot == count - 1 ? 0 : slot + 1;
            if (!valid.getAsBoolean() || slot >= neighbor.getSlots()) return;
            boolean moved = pull ? automation.pull(neighbor, slot, valid)
                    : automation.push(neighbor, slot, sourceSlot, valid);
            if (!pull && (moved || slot == count - 1)) {
                // Finish each destination sweep before changing source slots. Advancing both
                // cursors every cycle can permanently miss pairs in large filtered inventories.
                earningsCursors[face] = (sourceSlot + 1) % REAL_INVENTORY_SLOTS;
                return;
            }
            if (moved || automation.hasPending()) return;
        }
    }

    public ItemStack getPaymentTemplate(int slot) {
        checkDefinitionSlot(slot);
        return copyOrEmpty(paymentTemplates.get(slot));
    }

    public ItemStack getSaleTemplate(int slot) {
        checkDefinitionSlot(slot);
        return copyOrEmpty(saleTemplates.get(slot));
    }

    public int getPaymentQuantity(int slot) {
        checkDefinitionSlot(slot);
        return paymentQuantities[slot];
    }

    public int getSaleQuantity(int slot) {
        checkDefinitionSlot(slot);
        return saleQuantities[slot];
    }

    public void setPaymentDefinition(int slot, @Nullable ItemStack template, int quantity) {
        if (OreFilters.isFilter(template) && !OreFilters.isValid(template)) return;
        setDefinition(paymentTemplates, paymentQuantities, slot, template, quantity);
    }

    public void setSaleDefinition(int slot, @Nullable ItemStack template, int quantity) {
        if (OreFilters.isFilter(template)) return;
        setDefinition(saleTemplates, saleQuantities, slot, template, quantity);
    }

    public int getInputSides() {
        return inputSides;
    }

    public int getOutputSides() {
        return outputSides;
    }

    public int getAutoSides() {
        return autoSides;
    }

    public void setSideMasks(int inputSides, int outputSides, int autoSides) {
        if (!isDataVersionSupported()) {
            return;
        }

        int inputs = inputSides & ALL_SIDE_BITS;
        int outputs = outputSides & ALL_SIDE_BITS;
        int automatic = autoSides & ALL_SIDE_BITS;
        if (this.inputSides != inputs || this.outputSides != outputs || this.autoSides != automatic) {
            this.inputSides = inputs;
            this.outputSides = outputs;
            this.autoSides = automatic;
            displayChanged();
        }
    }

    public int getMatchNbtMask() {
        return matchNbt;
    }

    public boolean isMatchingNbt(int definitionSlot) {
        if (definitionSlot < 0 || definitionSlot >= DEFINITION_SLOTS * 2) {
            throw new IndexOutOfBoundsException("Definition slot " + definitionSlot + " is outside 0..7");
        }
        return (matchNbt & (1 << definitionSlot)) != 0;
    }

    public void setMatchingNbt(int definitionSlot, boolean enabled) {
        if (!isDataVersionSupported()) {
            return;
        }
        if (definitionSlot < 0 || definitionSlot >= DEFINITION_SLOTS * 2) {
            throw new IndexOutOfBoundsException("Definition slot " + definitionSlot + " is outside 0..7");
        }

        setMatchNbtMask(enabled ? matchNbt | (1 << definitionSlot) : matchNbt & ~(1 << definitionSlot));
    }

    public void setMatchNbtMask(int mask) {
        if (!isDataVersionSupported()) {
            return;
        }
        int updated = mask & ALL_DEFINITION_BITS;
        if (matchNbt != updated) {
            matchNbt = updated;
            offerChanged();
        }
    }

    public boolean isCreativeMode() {
        return creativeMode;
    }

    public void setCreativeMode(boolean creativeMode) {
        if (!isDataVersionSupported()) {
            return;
        }
        if (this.creativeMode != creativeMode) {
            this.creativeMode = creativeMode;
            offerChanged();
        }
    }

    public void setOwner(EntityPlayer player) {
        if (!isDataVersionSupported() || player == null) {
            return;
        }
        UUID updatedUuid = player.getUniqueID();
        String updatedName = normalizeName(player.getName());
        if (!Objects.equals(ownerUuid, updatedUuid) || !Objects.equals(ownerNameCache, updatedName)) {
            ownerUuid = updatedUuid;
            ownerNameCache = updatedName;
            displayChanged();
            directoryPending = true;
        }
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Nullable
    public String getOwnerNameCache() {
        return ownerNameCache;
    }

    public boolean isOwner(@Nullable EntityPlayer player) {
        return player != null && ownerUuid != null && ownerUuid.equals(player.getUniqueID());
    }

    public boolean canConfigure(@Nullable EntityPlayer player) {
        // Command privileges recover only genuinely ownerless machines, never an offline owner's property.
        return isOwner(player) || (player != null && ownerUuid == null && player.canUseCommand(2, BuildInfo.MOD_ID));
    }

    public boolean canEnableCreativeMode(@Nullable EntityPlayer player) {
        return canConfigure(player) && player.capabilities.isCreativeMode;
    }

    public int getLoadedDataVersion() {
        return loadedDataVersion;
    }

    public boolean isDataVersionSupported() {
        return unsupportedFutureData == null;
    }

    @Nullable public UUID getMachineUuid() { return machineUuid; }

    /** Legacy migration is server-only. Reading NBT on the client must never mint an identity. */
    public void ensureMachineIdentity() {
        if (machineUuid == null) renewMachineIdentity();
    }

    /** New physical placement, or an explicit loaded-target admin repair, never an automatic collision merge. */
    public void renewMachineIdentity() {
        if (world instanceof WorldServer && ((WorldServer) world).isCallingFromMinecraftThread()
                && isDataVersionSupported()) {
            machineUuid = UUID.randomUUID(); directoryPending = true; markDirty();
        }
    }

    public void setCustomName(@Nullable String customName) {
        if (!isDataVersionSupported()) {
            return;
        }
        String updated = normalizeName(customName);
        if (!Objects.equals(this.customName, updated)) {
            this.customName = updated;
            displayChanged();
            directoryPending = true;
        }
    }

    @Override
    public String getName() {
        return hasCustomName() ? customName : DEFAULT_NAME_KEY;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null;
    }

    @Override
    public ITextComponent getDisplayName() {
        return hasCustomName()
                ? new TextComponentString(customName)
                : new TextComponentTranslation(DEFAULT_NAME_KEY);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        readPortData(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        if (unsupportedFutureData != null) {
            copyAllTags(unsupportedFutureData, compound);
            return super.writeToNBT(compound);
        }

        ensureMachineIdentity();
        super.writeToNBT(compound);
        return writeCurrentData(compound);
    }

    void readPortData(NBTTagCompound compound) {
        offerRevision++; // Even an in-place reload must invalidate a previously displayed quote.
        invalidateDisplayState();
        try {
            loadPortData(compound);
        } finally {
            // Also discard snapshots requested reentrantly while capability NBT was being restored.
            invalidateDisplayState();
        }
    }

    private void loadPortData(NBTTagCompound compound) {
        resetPersistentState();

        int version = compound.hasKey("DataVersion", Constants.NBT.TAG_ANY_NUMERIC)
                ? compound.getInteger("DataVersion")
                : 1;
        loadedDataVersion = version;
        if (version < 1 || version > DATA_VERSION) {
            unsupportedFutureData = compound.copy();
            LOGGER.error(
                    "Refusing to interpret vending machine data version {} at {}; supported version is {}. "
                            + "The original payload will be preserved unchanged while the tile remains saved.",
                    version,
                    pos,
                    DATA_VERSION
            );
            return;
        }

        unsupportedFutureData = null;
        if (version >= 3 && compound.hasUniqueId("MachineUUID")) {
            machineUuid = compound.getUniqueId("MachineUUID");
        }
        if (compound.hasUniqueId("OwnerUUID")) {
            ownerUuid = compound.getUniqueId("OwnerUUID");
        }
        ownerNameCache = readBoundedString(compound, "OwnerNameCache");
        customName = readBoundedString(compound, "CustomName");
        creativeMode = compound.getBoolean("CreativeMode");
        inputSides = compound.getInteger("InputSides") & ALL_SIDE_BITS;
        outputSides = compound.getInteger("OutputSides") & ALL_SIDE_BITS;
        autoSides = compound.getInteger("AutoSides") & ALL_SIDE_BITS;
        matchNbt = compound.hasKey("MatchNBT", Constants.NBT.TAG_ANY_NUMERIC)
                ? compound.getInteger("MatchNBT") & ALL_DEFINITION_BITS
                : ALL_DEFINITION_BITS;

        readTemplates(compound, "PaymentTemplates", paymentTemplates);
        readTemplates(compound, "SaleTemplates", saleTemplates);
        readQuantities(compound, "PaymentQuantities", paymentTemplates, paymentQuantities);
        readQuantities(compound, "SaleQuantities", saleTemplates, saleQuantities);

        if (compound.hasKey("SaleStock", Constants.NBT.TAG_COMPOUND)) {
            saleStock.deserializeNBT(compound.getCompoundTag("SaleStock"));
        }
        if (compound.hasKey("Earnings", Constants.NBT.TAG_COMPOUND)) {
            earnings.deserializeNBT(compound.getCompoundTag("Earnings"));
        }
        if (version >= 2) automation.read(compound.getCompoundTag("AutomationTransfer"));
    }

    NBTTagCompound writePortData(NBTTagCompound compound) {
        if (unsupportedFutureData != null) {
            copyAllTags(unsupportedFutureData, compound);
            return compound;
        }
        return writeCurrentData(compound);
    }

    private NBTTagCompound writeCurrentData(NBTTagCompound compound) {
        compound.setInteger("DataVersion", DATA_VERSION);
        if (machineUuid != null) {
            compound.setUniqueId("MachineUUID", machineUuid);
        } else {
            compound.removeTag("MachineUUIDMost");
            compound.removeTag("MachineUUIDLeast");
        }
        if (ownerUuid != null) {
            compound.setUniqueId("OwnerUUID", ownerUuid);
        } else {
            compound.removeTag("OwnerUUIDMost");
            compound.removeTag("OwnerUUIDLeast");
        }
        writeOptionalString(compound, "OwnerNameCache", ownerNameCache);
        writeOptionalString(compound, "CustomName", customName);
        compound.setBoolean("CreativeMode", creativeMode);
        compound.setInteger("InputSides", inputSides & ALL_SIDE_BITS);
        compound.setInteger("OutputSides", outputSides & ALL_SIDE_BITS);
        compound.setInteger("AutoSides", autoSides & ALL_SIDE_BITS);
        compound.setInteger("MatchNBT", matchNbt & ALL_DEFINITION_BITS);
        compound.setTag("PaymentTemplates", writeTemplates(paymentTemplates));
        compound.setTag("SaleTemplates", writeTemplates(saleTemplates));
        compound.setIntArray("PaymentQuantities", paymentQuantities);
        compound.setIntArray("SaleQuantities", saleQuantities);
        compound.setTag("SaleStock", saleStock.serializeNBT());
        compound.setTag("Earnings", earnings.serializeNBT());
        compound.setTag("AutomationTransfer", automation.write());
        return compound;
    }

    private void setDefinition(
            NonNullList<ItemStack> templates,
            int[] quantities,
            int slot,
            @Nullable ItemStack template,
            int quantity
    ) {
        checkDefinitionSlot(slot);
        if (!isDataVersionSupported()) {
            return;
        }

        ItemStack normalized = ItemStack.EMPTY;
        int normalizedQuantity = 0;
        if (template != null && !template.isEmpty() && quantity > 0) {
            normalized = template.copy();
            normalized.setCount(1);
            normalizedQuantity = clampQuantity(quantity);
        }
        if (quantities[slot] != normalizedQuantity || !ItemStack.areItemStacksEqual(templates.get(slot), normalized)) {
            templates.set(slot, normalized);
            quantities[slot] = normalizedQuantity;
            offerChanged();
        }
    }

    private void resetPersistentState() {
        machineUuid = null;
        directoryPending = true;
        ownerUuid = null;
        ownerNameCache = null;
        customName = null;
        creativeMode = false;
        inputSides = 0;
        outputSides = 0;
        autoSides = 0;
        matchNbt = ALL_DEFINITION_BITS;
        loadedDataVersion = DATA_VERSION;
        unsupportedFutureData = null;
        Arrays.fill(paymentQuantities, 0);
        Arrays.fill(saleQuantities, 0);
        for (int slot = 0; slot < DEFINITION_SLOTS; slot++) {
            paymentTemplates.set(slot, ItemStack.EMPTY);
            saleTemplates.set(slot, ItemStack.EMPTY);
        }
        saleStock.clearForLoad();
        earnings.clearForLoad();
        automation.read(new NBTTagCompound());
        Arrays.fill(pullCursors, 0);
        Arrays.fill(pushCursors, 0);
        Arrays.fill(earningsCursors, 0);
    }

    private static void readTemplates(
            NBTTagCompound compound,
            String key,
            NonNullList<ItemStack> destination
    ) {
        NBTTagList list = compound.getTagList(key, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(index);
            int slot = itemTag.getInteger("Slot");
            if (slot < 0 || slot >= DEFINITION_SLOTS) {
                continue;
            }

            ItemStack template = new ItemStack(itemTag);
            if (!template.isEmpty()) {
                template.setCount(1);
                destination.set(slot, template);
            }
        }
    }

    private static NBTTagList writeTemplates(NonNullList<ItemStack> templates) {
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < templates.size(); slot++) {
            ItemStack template = templates.get(slot);
            if (template.isEmpty()) {
                continue;
            }

            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setInteger("Slot", slot);
            ItemStack normalized = template.copy();
            normalized.setCount(1);
            normalized.writeToNBT(itemTag);
            list.appendTag(itemTag);
        }
        return list;
    }

    private static void readQuantities(
            NBTTagCompound compound,
            String key,
            NonNullList<ItemStack> templates,
            int[] destination
    ) {
        int[] stored = compound.hasKey(key, Constants.NBT.TAG_INT_ARRAY)
                ? compound.getIntArray(key)
                : new int[0];
        for (int slot = 0; slot < DEFINITION_SLOTS; slot++) {
            if (templates.get(slot).isEmpty()) {
                destination[slot] = 0;
            } else {
                int quantity = slot < stored.length ? stored[slot] : 1;
                destination[slot] = clampQuantity(quantity);
            }
        }
    }

    private static int clampQuantity(int quantity) {
        return Math.max(1, Math.min(MAX_QUANTITY, quantity));
    }

    @Nullable
    private static String readBoundedString(NBTTagCompound compound, String key) {
        return compound.hasKey(key, Constants.NBT.TAG_STRING)
                ? normalizeName(compound.getString(key))
                : null;
    }

    @Nullable
    private static String normalizeName(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        int codePoints = value.codePointCount(0, value.length());
        return codePoints <= MAX_NAME_LENGTH
                ? value
                : value.substring(0, value.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }

    private static void writeOptionalString(NBTTagCompound compound, String key, @Nullable String value) {
        if (value == null) {
            compound.removeTag(key);
        } else {
            compound.setString(key, value);
        }
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static void checkDefinitionSlot(int slot) {
        if (slot < 0 || slot >= DEFINITION_SLOTS) {
            throw new IndexOutOfBoundsException("Definition slot " + slot + " is outside 0..3");
        }
    }

    private static void copyAllTags(NBTTagCompound source, NBTTagCompound destination) {
        for (String key : source.getKeySet()) {
            NBTBase tag = source.getTag(key);
            if (tag != null) {
                destination.setTag(key, tag.copy());
            }
        }
    }

    private final class FixedItemStackHandler extends ItemStackHandler {
        private FixedItemStackHandler() {
            super(REAL_INVENTORY_SLOTS);
        }

        @Override
        public void setSize(int ignoredSize) {
            super.setSize(REAL_INVENTORY_SLOTS);
        }

        @Override
        public int getSlotLimit(int slot) {
            return StackLimits.slotMaximum();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isDataVersionSupported();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isDataVersionSupported() ? super.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return isDataVersionSupported() && amount > 0
                    ? super.extractItem(slot, Math.min(amount, StackLimits.slotMaximum()), simulate).copy() : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (isDataVersionSupported()) {
                super.setStackInSlot(slot, stack);
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public NBTTagCompound serializeNBT() {
            NBTTagList items = new NBTTagList();
            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setInteger("Slot", slot);
                stack.writeToNBT(itemTag);
                itemTag.setInteger("CountInt", Math.min(MAX_QUANTITY, stack.getCount()));
                items.appendTag(itemTag);
            }

            NBTTagCompound result = new NBTTagCompound();
            result.setInteger("Size", REAL_INVENTORY_SLOTS);
            result.setTag("Items", items);
            return result;
        }

        @Override
        public void deserializeNBT(NBTTagCompound compound) {
            clearForLoad();
            NBTTagList items = compound.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < items.tagCount(); index++) {
                NBTTagCompound itemTag = items.getCompoundTagAt(index);
                int slot = itemTag.getInteger("Slot");
                if (slot < 0 || slot >= REAL_INVENTORY_SLOTS) {
                    continue;
                }

                int count = itemTag.hasKey("CountInt", Constants.NBT.TAG_ANY_NUMERIC)
                        ? itemTag.getInteger("CountInt")
                        : itemTag.getByte("Count");
                if (count <= 0) {
                    continue;
                }

                ItemStack stack = new ItemStack(itemTag);
                stack.setCount(Math.min(MAX_QUANTITY, count));
                if (!stack.isEmpty()) {
                    stacks.set(slot, stack);
                }
            }
            onLoad();
        }

        private void clearForLoad() {
            stacks = NonNullList.withSize(REAL_INVENTORY_SLOTS, ItemStack.EMPTY);
        }
    }
}
