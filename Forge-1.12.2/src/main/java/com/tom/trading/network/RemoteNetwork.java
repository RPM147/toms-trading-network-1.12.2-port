package com.tom.trading.network;

import com.tom.trading.BuildInfo;
import com.tom.trading.TradingNetworkMod;
import com.tom.trading.container.ContainerRemoteTrading;
import com.tom.trading.directory.MachineAddress;
import com.tom.trading.remote.*;
import com.tom.trading.trade.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import java.io.IOException;
import java.util.*;

/** Separate trade-only messages on the shared channel/budget. No owner/configuration operation exists here. */
public final class RemoteNetwork {
    private static final Set<EntityPlayerMP> QUEUED = Collections.newSetFromMap(new WeakHashMap<>());
    private RemoteNetwork() {}
    static int register(SimpleNetworkWrapper channel, int id) {
        channel.registerMessage(OpenHandler.class, Open.class, id++, Side.SERVER);
        channel.registerMessage(OpenedHandler.class, Opened.class, id++, Side.CLIENT);
        channel.registerMessage(BuyHandler.class, Buy.class, id++, Side.SERVER);
        channel.registerMessage(StateHandler.class, State.class, id++, Side.CLIENT);
        channel.registerMessage(FeedbackHandler.class, Feedback.class, id++, Side.CLIENT);
        channel.registerMessage(CancelHandler.class, Cancel.class, id++, Side.SERVER);
        return id;
    }
    public static void request(RemoteOpenRequest request) { MachineNetwork.CHANNEL.sendToServer(new Open(request)); }
    public static void cancel(RemoteOpenRequest request) { MachineNetwork.CHANNEL.sendToServer(new Cancel(request)); }
    public static void buy(ContainerRemoteTrading container, int count, long requestId) {
        MachineNetwork.CHANNEL.sendToServer(new Buy(container.context, count, container.view.offerRevision, requestId));
    }
    public static void opened(EntityPlayerMP player, RemoteOpenRequest request, RemoteContext context) {
        send(player, new Opened(request, RemoteOpenResult.OPENED, context));
    }
    public static void openResult(EntityPlayerMP player, RemoteOpenRequest request, RemoteOpenResult result) {
        send(player, new Opened(request, result, null));
    }
    public static void sendState(EntityPlayerMP player, RemoteContext context, NBTTagCompound data) {
        send(player, new State(context, data));
    }
    private static void send(EntityPlayerMP player, IMessage message) {
        if (!player.hasDisconnected()) MachineNetwork.CHANNEL.sendTo(message, player);
    }
    private static void uuid(PacketBuffer b, UUID id) { b.writeLong(id.getMostSignificantBits()); b.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(PacketBuffer b) { return new UUID(b.readLong(), b.readLong()); }
    private static void address(PacketBuffer b, MachineAddress a) { b.writeInt(a.dimension); b.writeInt(a.x); b.writeInt(a.y); b.writeInt(a.z); }
    private static MachineAddress address(PacketBuffer b) { return new MachineAddress(b.readInt(), b.readInt(), b.readInt(), b.readInt()); }
    private static boolean bool(PacketBuffer b) {
        int value = b.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid boolean");
        return value == 1;
    }
    private static void opening(PacketBuffer b, RemoteOpenRequest r) {
        uuid(b, r.screenId); b.writeLong(r.requestId); uuid(b, r.worldId); b.writeLong(r.revision);
        b.writeInt(r.playerDimension); address(b, r.address);
        b.writeBoolean(r.machineId != null); if (r.machineId != null) uuid(b, r.machineId);
    }
    private static RemoteOpenRequest opening(PacketBuffer b) {
        UUID screen = uuid(b); long id = b.readLong(); UUID world = uuid(b); long revision = b.readLong();
        int dimension = b.readInt(); MachineAddress address = address(b); UUID machine = bool(b) ? uuid(b) : null;
        return new RemoteOpenRequest(screen, id, world, revision, dimension, address, machine);
    }
    private static void context(PacketBuffer b, RemoteContext c) {
        b.writeVarInt(c.window); b.writeInt(c.playerDimension); uuid(b, c.sessionId); uuid(b, c.worldId); uuid(b, c.machineId); address(b, c.address);
    }
    private static RemoteContext context(PacketBuffer b) {
        return new RemoteContext(b.readVarInt(), b.readInt(), uuid(b), uuid(b), uuid(b), address(b));
    }
    public abstract static class Packet implements IMessage {
        public boolean valid;
        protected int limit() { return 256; }
        protected abstract void write(PacketBuffer b);
        protected abstract void read(PacketBuffer b) throws IOException;
        @Override public final void toBytes(ByteBuf bytes) { write(new PacketBuffer(bytes)); }
        @Override public final void fromBytes(ByteBuf bytes) {
            valid = false;
            if (bytes.readableBytes() > limit()) return;
            try { PacketBuffer b = new PacketBuffer(bytes); read(b); valid = !b.isReadable(); }
            catch (IOException | RuntimeException ex) { valid = false; }
        }
    }
    public static class Open extends Packet {
        public RemoteOpenRequest request;
        public Open() {}
        public Open(RemoteOpenRequest request) { this.request = request; }
        protected void write(PacketBuffer b) { opening(b, request); }
        protected void read(PacketBuffer b) { request = opening(b); }
    }
    public static final class Cancel extends Open {
        public Cancel() {}
        public Cancel(RemoteOpenRequest request) { super(request); }
    }
    public static final class Opened extends Packet {
        public RemoteOpenRequest request;
        public RemoteOpenResult result;
        public RemoteContext context;
        public Opened() {}
        public Opened(RemoteOpenRequest request, RemoteOpenResult result, RemoteContext context) {
            this.request = request; this.result = result; this.context = context;
        }
        protected void write(PacketBuffer b) {
            opening(b, request); b.writeVarInt(result.ordinal()); if (result == RemoteOpenResult.OPENED) context(b, context);
        }
        protected void read(PacketBuffer b) {
            request = opening(b); int code = b.readVarInt();
            if (code < 0 || code >= RemoteOpenResult.values().length) throw new IllegalArgumentException("Invalid open status");
            result = RemoteOpenResult.values()[code]; context = null;
            if (result == RemoteOpenResult.OPENED) {
                context = context(b);
                if (!context.worldId.equals(request.worldId) || !context.machineId.equals(request.machineId)
                        || !context.address.equals(request.address) || context.playerDimension != request.playerDimension)
                    throw new IllegalArgumentException("Open target mismatch");
            }
        }
    }
    public static final class Buy extends Packet {
        public RemoteContext context;
        public int count;
        public long offerRevision, requestId;
        public Buy() {}
        public Buy(RemoteContext context, int count, long revision, long requestId) {
            this.context = context; this.count = count; this.offerRevision = revision; this.requestId = requestId;
        }
        protected void write(PacketBuffer b) { context(b, context); b.writeVarInt(count); b.writeLong(offerRevision); b.writeLong(requestId); }
        protected void read(PacketBuffer b) {
            context = context(b); count = b.readVarInt(); offerRevision = b.readLong(); requestId = b.readLong();
            if (count < 1 || count > TradeLimits.MAX_BATCH_SIZE || offerRevision <= 0 || requestId <= 0)
                throw new IllegalArgumentException("Invalid purchase");
        }
    }
    public static final class State extends Packet {
        public RemoteContext context;
        public NBTTagCompound data;
        public State() {}
        public State(RemoteContext context, NBTTagCompound data) { this.context = context; this.data = data; }
        protected int limit() { return 524288; }
        protected void write(PacketBuffer b) {
            context(b, context);
            NBTTagCompound display = data.copy();
            int[] quantities = display.getIntArray("Quantities"); display.removeTag("Quantities");
            b.writeCompoundTag(display);
            for (int slot = 0; slot < 8; slot++) b.writeVarInt(slot < quantities.length ? quantities[slot] : 0);
        }
        protected void read(PacketBuffer b) throws IOException {
            context = context(b); data = b.readCompoundTag();
            if (data == null || data.getLong("OfferRevision") <= 0) throw new IllegalArgumentException("Missing offer");
            int[] quantities = new int[8];
            for (int slot = 0; slot < 8; slot++) {
                quantities[slot] = b.readVarInt();
                if (quantities[slot] < 0 || quantities[slot] > TradeLimits.MAX_QUANTITY) throw new IllegalArgumentException("Invalid quantity");
            }
            data.setIntArray("Quantities", quantities);
        }
    }
    public static final class Feedback extends Packet {
        public RemoteContext context;
        public long requestId;
        public int completed, retryAfterMillis;
        public TradeResultCode code;
        public Feedback() {}
        public Feedback(RemoteContext context, long requestId, TradeExecutor.Outcome result, int delay) {
            this.context = context; this.requestId = requestId; completed = result.completed; code = result.code; retryAfterMillis = delay;
        }
        protected void write(PacketBuffer b) {
            context(b, context); b.writeLong(requestId); b.writeVarInt(completed); b.writeVarInt(code.ordinal()); b.writeVarInt(retryAfterMillis);
        }
        protected void read(PacketBuffer b) {
            context = context(b); requestId = b.readLong(); completed = b.readVarInt(); int id = b.readVarInt(); retryAfterMillis = b.readVarInt();
            if (requestId <= 0 || completed < 0 || completed > TradeLimits.MAX_BATCH_SIZE || id < 0
                    || id >= TradeResultCode.values().length || retryAfterMillis < 0 || retryAfterMillis > 1000)
                throw new IllegalArgumentException("Invalid result");
            code = TradeResultCode.values()[id];
        }
    }
    private static void queue(Open message, MessageContext ctx, boolean cancel) {
        if (!message.valid) return;
        EntityPlayerMP player = ctx.getServerHandler().player;
        RequestBudget.Admission admitted = cancel ? MachineNetwork.accept(player, 0) : MachineNetwork.acceptRemoteOpen(player);
        if (!admitted.accepted) return;
        synchronized (QUEUED) { if (!QUEUED.add(player)) return; }
        player.getServerWorld().addScheduledTask(() -> {
            try {
                if (player.hasDisconnected()) return;
                RemoteTrading service = RemoteTrading.get(player.getServer());
                if (service != null) {
                    if (cancel) service.cancel(player, message.request); else service.request(player, message.request);
                } else if (!cancel) openResult(player, message.request, RemoteOpenResult.UNAVAILABLE);
            } finally { synchronized (QUEUED) { QUEUED.remove(player); } }
        });
    }
    public static final class OpenHandler implements IMessageHandler<Open, IMessage> {
        public IMessage onMessage(Open message, MessageContext ctx) { queue(message, ctx, false); return null; }
    }
    public static final class CancelHandler implements IMessageHandler<Cancel, IMessage> {
        public IMessage onMessage(Cancel message, MessageContext ctx) { queue(message, ctx, true); return null; }
    }
    public static final class BuyHandler implements IMessageHandler<Buy, IMessage> {
        public IMessage onMessage(Buy message, MessageContext ctx) {
            if (!message.valid) return null;
            EntityPlayerMP player = ctx.getServerHandler().player;
            RequestBudget.Admission admission = MachineNetwork.accept(player, message.count);
            if (!admission.accepted && !admission.notifyRejection) return null;
            player.getServerWorld().addScheduledTask(() -> apply(player, message, admission));
            return null;
        }
    }
    private static void apply(EntityPlayerMP player, Buy message, RequestBudget.Admission admission) {
        if (player.hasDisconnected() || !(player.openContainer instanceof ContainerRemoteTrading)) return;
        ContainerRemoteTrading container = (ContainerRemoteTrading) player.openContainer;
        if (!message.context.matches(container.context) || !container.canInteractWith(player)) return;
        try {
            TradeExecutor.Outcome result = container.tradeLedger.replay(message.requestId, message.count, message.offerRevision);
            if (result == null) {
                if (!admission.accepted) result = new TradeExecutor.Outcome(0, TradeResultCode.RATE_LIMITED);
                else result = TradeCompletion.execute(player, container.tradeLedger,
                        message.requestId, message.count, message.offerRevision, () -> {
                    RemoteSession session = container.session();
                    TradeResultCode denied = session.access();
                    if (denied != null) return new TradeExecutor.Outcome(0, denied);
                    session.activity();
                    return TradeExecutor.execute(player, session.tile, message.count, message.offerRevision, session::access);
                });
            }
            // Ledger is already final here. None of these notifications can repeat the purchase.
            container.detectAndSendChanges();
            container.sendState(player);
            send(player, new Feedback(container.context, message.requestId, result, admission.retryAfterMillis));
        } catch (RuntimeException | LinkageError ex) {
            LogManager.getLogger(BuildInfo.MOD_ID).error("Remote purchase interrupted; request will not be retried", ex);
            player.closeScreen();
        }
    }
    public static final class OpenedHandler implements IMessageHandler<Opened, IMessage> {
        public IMessage onMessage(Opened message, MessageContext ctx) {
            if (message.valid) TradingNetworkMod.proxy.receiveRemoteOpen(message, ctx.netHandler); return null;
        }
    }
    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        public IMessage onMessage(State message, MessageContext ctx) {
            if (message.valid) TradingNetworkMod.proxy.receiveRemoteState(message, ctx.netHandler); return null;
        }
    }
    public static final class FeedbackHandler implements IMessageHandler<Feedback, IMessage> {
        public IMessage onMessage(Feedback message, MessageContext ctx) {
            if (message.valid) TradingNetworkMod.proxy.receiveRemoteFeedback(message, ctx.netHandler); return null;
        }
    }
}
