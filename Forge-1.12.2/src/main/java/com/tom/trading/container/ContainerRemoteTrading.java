package com.tom.trading.container;

import com.tom.trading.network.RemoteNetwork;
import com.tom.trading.remote.RemoteContext;
import com.tom.trading.remote.RemoteSession;
import com.tom.trading.trade.TradeRequestLedger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Exactly 36 player slots. No target TileEntity is needed or constructed on the client. */
public final class ContainerRemoteTrading extends Container {
    public final RemoteContext context;
    private final EntityPlayer viewer;
    private final RemoteSession serverSession;
    public final TradeRequestTracker tradeRequests = new TradeRequestTracker();
    public final TradeRequestLedger tradeLedger = new TradeRequestLedger();
    public MachineView view = new MachineView();
    public String feedbackKey = "";
    public int completedTrades;
    private long lastDisplayRevision = -1;
    private int syncTicks;

    public ContainerRemoteTrading(EntityPlayer player, RemoteContext context, RemoteSession serverSession) {
        this.viewer = player; this.context = context; this.serverSession = serverSession;
        windowId = context.window;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 142));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player == viewer && player.isEntityAlive() && !player.isSpectator() && player.dimension == context.playerDimension
                && (player.world.isRemote || (serverSession != null && serverSession.current()));
    }
    public RemoteSession session() { return serverSession; }
    @Override public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (!canInteractWith(player) || slot < -999 || slot >= inventorySlots.size()) return ItemStack.EMPTY;
        return super.slotClick(slot, button, type, player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!canInteractWith(player) || index < 0 || index >= 36) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = stack.copy();
        if (!mergeItemStack(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        slot.onTake(player, stack);
        return original;
    }
    @Override public void addListener(IContainerListener listener) {
        if (!viewer.world.isRemote && (!canInteractWith(viewer) || listener != viewer)) return;
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP) sendState((EntityPlayerMP) listener);
    }
    public void sendState(EntityPlayerMP player) {
        if (!canInteractWith(player)) return;
        long revision = serverSession.tile.getDisplayRevision();
        NBTTagCompound data = serverSession.tile.copyDisplayState();
        if (data == null || revision != serverSession.tile.getDisplayRevision() || !canInteractWith(player)) return;
        RemoteNetwork.sendState(player, context, data);
        lastDisplayRevision = revision;
    }
    @Override public void detectAndSendChanges() {
        try {
            if (!canInteractWith(viewer)) return;
            super.detectAndSendChanges();
            if (viewer.world.isRemote || ++syncTicks % 5 != 0) return;
            if (lastDisplayRevision != serverSession.tile.getDisplayRevision()) sendState((EntityPlayerMP) viewer);
        } catch (RuntimeException | LinkageError ex) {
            if (serverSession == null) throw ex;
            org.apache.logging.log4j.LogManager.getLogger(com.tom.trading.BuildInfo.MOD_ID)
                    .warn("Remote display synchronization failed; closing session", ex);
            serverSession.end();
        }
    }
    @Override public void onContainerClosed(EntityPlayer player) {
        // Release even when a cursor item callback fails during vanilla cleanup.
        try { super.onContainerClosed(player); }
        finally { if (player == viewer && serverSession != null) serverSession.close(); }
    }
}
