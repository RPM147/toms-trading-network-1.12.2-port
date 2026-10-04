package com.tom.trading.container;

import com.tom.trading.network.MachineNetwork;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ContainerMachine extends Container {
    private static final AtomicLong SESSIONS = new AtomicLong(new java.security.SecureRandom().nextLong());
    public final TileVendingMachine tile;
    public final BlockPos position;
    public final int dimension;
    public final boolean configuration;
    private final EntityPlayer viewer;
    public long session;
    public MachineView view = new MachineView();
    public int viewRevision;
    public String feedbackKey = "";
    public int completedTrades;
    public final TradeRequestTracker tradeRequests = new TradeRequestTracker();
    public final com.tom.trading.trade.TradeRequestLedger tradeLedger = new com.tom.trading.trade.TradeRequestLedger();
    private long lastDisplayRevision = -1;
    private int syncTicks;

    protected ContainerMachine(EntityPlayer viewer, TileVendingMachine tile, boolean configuration) {
        this.viewer = viewer;
        this.tile = tile;
        this.position = tile.getPos().toImmutable();
        this.dimension = viewer.dimension;
        this.configuration = configuration;
        session = viewer.world.isRemote ? 0 : SESSIONS.incrementAndGet();
    }

    protected void addPlayerSlots(int top, int hotbar) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(viewer.inventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(viewer.inventory, col, 8 + col * 18, hotbar));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player == viewer && !player.isSpectator() && player.isEntityAlive()
                && player.dimension == dimension && player.world == tile.getWorld()
                && player.world.isBlockLoaded(position) && !tile.isInvalid()
                && player.world.getTileEntity(position) == tile
                && player.getDistanceSq(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 64
                && tile.isDataVersionSupported()
                && !tile.isAutomationQuarantined() && !tile.isAutomationRunning()
                && (!configuration || player.world.isRemote || (tile.canConfigure(player)
                    && (!tile.isCreativeMode() || player.capabilities.isCreativeMode)));
    }

    @Override
    public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (!player.world.isRemote && !canInteractWith(player)) return ItemStack.EMPTY;
        if (slot < -999 || slot >= inventorySlots.size()) return ItemStack.EMPTY;
        return super.slotClick(slot, button, type, player);
    }

    @Override
    public void addListener(IContainerListener listener) {
        if (!viewer.world.isRemote && !canInteractWith(viewer)) return;
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP) sendState((EntityPlayerMP) listener);
    }

    public void sendState(EntityPlayerMP player) {
        if (player.openContainer != this || !canInteractWith(player)) return;
        long revision = tile.getDisplayRevision();
        NBTTagCompound state = tile.copyDisplayState();
        if (state == null || revision != tile.getDisplayRevision()
                || player.openContainer != this || !canInteractWith(player)) return;
        MachineNetwork.sendState(player, this, state);
    }

    @Override
    public void detectAndSendChanges() {
        // Vanilla sync includes the real inventory slots; check access before it sends anything.
        if (!viewer.world.isRemote && !canInteractWith(viewer)) return;
        super.detectAndSendChanges();
        if (viewer.world.isRemote || ++syncTicks % 5 != 0) return;
        if (!canInteractWith(viewer)) return;
        long revision = tile.getDisplayRevision();
        if (revision == lastDisplayRevision) return;
        NBTTagCompound state = tile.copyDisplayState();
        if (state == null || revision != tile.getDisplayRevision() || !canInteractWith(viewer)) return;
        lastDisplayRevision = revision;
        for (IContainerListener listener : listeners) {
            if (listener instanceof EntityPlayerMP) MachineNetwork.sendState((EntityPlayerMP) listener, this, state);
        }
    }

    protected boolean moveStack(ItemStack stack, int start, int end, boolean backwards) {
        // Forge's generic merge implementation does not consistently consult isItemValid
        // on occupied slots. Check it on both passes (earnings are extraction-only).
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int offset = 0; offset < end - start && !stack.isEmpty(); offset++) {
                int index = backwards ? end - offset - 1 : start + offset;
                Slot slot = inventorySlots.get(index);
                if (!slot.isItemValid(stack)) continue;
                ItemStack current = slot.getStack();
                if (pass == 0 && (current.isEmpty()
                        || !net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(stack, current))) continue;
                if (pass == 1 && !current.isEmpty()) continue;
                int limit = Math.min(stack.getMaxStackSize(), slot.getItemStackLimit(stack));
                int amount = Math.min(stack.getCount(), limit - (current.isEmpty() ? 0 : current.getCount()));
                if (amount <= 0) continue;
                if (current.isEmpty()) {
                    slot.putStack(stack.splitStack(amount));
                } else {
                    current.grow(amount);
                    stack.shrink(amount);
                    slot.onSlotChanged();
                }
            }
        }
        return stack.isEmpty();
    }
}
