package com.tom.trading.network;

import com.tom.trading.TradingNetworkMod;
import com.tom.trading.container.ContainerMachine;
import com.tom.trading.container.MachineGuiHandler;
import com.tom.trading.item.OreFilters;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeExecutor;
import com.tom.trading.trade.TradeCompletion;
import com.tom.trading.trade.TradeLimits;
import com.tom.trading.trade.TradeResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

/** Each operation has its own discriminator, bounded primitive payload, and access policy. */
public final class MachineNetwork {
    public static final String PROTOCOL = "ttn_112_v1";
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(PROTOCOL);
    private static final Map<EntityPlayerMP, RequestBudget> BUDGETS = new WeakHashMap<>();
    private static int nextId;

    private MachineNetwork() {}

    public static void register() {
        register(RequestState.class);
        register(SetTemplate.class);
        register(SetQuantity.class);
        register(SetMatchNbt.class);
        register(SetSideMode.class);
        register(SetCustomName.class);
        register(SetCreativeMode.class);
        register(Trade.class);
        register(OpenTrading.class);
        CHANNEL.registerMessage(StateHandler.class, State.class, nextId++, Side.CLIENT);
        CHANNEL.registerMessage(FeedbackHandler.class, Feedback.class, nextId++, Side.CLIENT);
        // Append new IDs: existing request/response discriminators remain stable.
        register(SetOreFilter.class);
        register(SetGhostTemplate.class);
        nextId = DirectoryNetwork.register(CHANNEL, nextId);
        nextId = RemoteNetwork.register(CHANNEL, nextId);
    }

    private static <M extends Request> void register(Class<M> type) {
        CHANNEL.registerMessage(new ServerHandler<M>(), type, nextId++, Side.SERVER);
    }

    public static void send(Request message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendState(EntityPlayerMP player, ContainerMachine container, NBTTagCompound state) {
        CHANNEL.sendTo(new State(container, state), player);
    }

    private static void feedback(EntityPlayerMP player, ContainerMachine container, int count, TradeResultCode code) {
        feedback(player, container, count, code, 0, 0);
    }

    private static void feedback(EntityPlayerMP player, ContainerMachine container, int count, TradeResultCode code,
                                 long requestId, int retryAfterMillis) {
        CHANNEL.sendTo(new Feedback(container, count, code, requestId, retryAfterMillis), player);
    }

    static RequestBudget.Admission accept(EntityPlayerMP player, int tradeUnits) {
        synchronized (BUDGETS) {
            return BUDGETS.computeIfAbsent(player, p -> new RequestBudget()).admit(System.nanoTime(), tradeUnits);
        }
    }

    static boolean acceptDirectory(EntityPlayerMP player) {
        synchronized (BUDGETS) {
            return BUDGETS.computeIfAbsent(player, p -> new RequestBudget()).admitDirectory(System.nanoTime()).accepted;
        }
    }

    static RequestBudget.Admission acceptRemoteOpen(EntityPlayerMP player) {
        synchronized (BUDGETS) {
            return BUDGETS.computeIfAbsent(player, p -> new RequestBudget()).admitRemoteOpen(System.nanoTime());
        }
    }

    public static final class Context {
        public int window, dimension;
        public BlockPos position = BlockPos.ORIGIN;
        public long session;

        public Context() {}
        public Context(ContainerMachine container) {
            window = container.windowId;
            dimension = container.dimension;
            position = container.position;
            session = container.session;
        }
        void write(PacketBuffer buf) {
            buf.writeVarInt(window);
            buf.writeInt(dimension);
            buf.writeLong(position.toLong());
            buf.writeLong(session);
        }
        void read(PacketBuffer buf) {
            window = buf.readVarInt();
            dimension = buf.readInt();
            position = BlockPos.fromLong(buf.readLong());
            session = buf.readLong();
        }
        public boolean matches(ContainerMachine container, boolean requireSession) {
            return matches(new Context(container), requireSession);
        }
        public boolean matches(Context expected, boolean requireSession) {
            return window == expected.window && dimension == expected.dimension
                    && position.equals(expected.position) && (!requireSession || session == expected.session);
        }
    }

    public abstract static class Request implements IMessage {
        public Context context = new Context();
        public boolean valid;
        protected Request() {}
        protected Request(ContainerMachine container) { context = new Context(container); }
        protected abstract void writeBody(PacketBuffer buf);
        protected abstract void readBody(PacketBuffer buf);
        protected abstract boolean validBody();
        protected abstract void apply(EntityPlayerMP player, ContainerMachine container);
        protected boolean configurationOnly() { return true; }
        protected boolean requiresSession() { return true; }

        @Override public final void toBytes(ByteBuf bytes) {
            PacketBuffer buf = new PacketBuffer(bytes);
            context.write(buf);
            writeBody(buf);
        }
        @Override public final void fromBytes(ByteBuf bytes) {
            valid = false;
            if (bytes.readableBytes() > 300) { bytes.skipBytes(bytes.readableBytes()); return; }
            try {
                PacketBuffer buf = new PacketBuffer(bytes);
                context.read(buf);
                readBody(buf);
                valid = !buf.isReadable() && context.window >= 1 && context.window <= 100 && validBody();
            } catch (RuntimeException malformed) {
                valid = false;
            }
        }
    }

    public static final class ServerHandler<M extends Request> implements IMessageHandler<M, IMessage> {
        @Override public IMessage onMessage(M message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (!message.valid) return null;
            RequestBudget.Admission admission = accept(player, message instanceof Trade ? ((Trade) message).count : 0);
            // At most four rejected trade notices per player/window; never enqueue one task per spam packet.
            if (!admission.accepted && !admission.notifyRejection) return null;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.hasDisconnected() || !(player.openContainer instanceof ContainerMachine)) return;
                ContainerMachine container = (ContainerMachine) player.openContainer;
                if (!message.context.matches(container, message.requiresSession())
                        || !container.canInteractWith(player)
                        || (message.configurationOnly() && !container.configuration)) return;
                if (message instanceof Trade) {
                    if (container.configuration) return;
                    Trade trade = (Trade) message;
                    TradeExecutor.Outcome replay = container.tradeLedger.replay(trade.requestId, trade.count, trade.offerRevision);
                    if (replay != null) feedback(player, container, replay.completed, replay.code,
                            trade.requestId, admission.retryAfterMillis);
                    else if (!admission.accepted) feedback(player, container, 0, TradeResultCode.RATE_LIMITED,
                            trade.requestId, admission.retryAfterMillis);
                    else trade.applyWithDelay(player, container, admission.retryAfterMillis);
                } else message.apply(player, container);
            });
            return null;
        }
    }

    private static boolean bool(PacketBuffer buf) {
        int value = buf.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid boolean");
        return value == 1;
    }

    private static ItemStack template(ContainerMachine c, int slot) {
        return slot < 4 ? c.tile.getPaymentTemplate(slot) : c.tile.getSaleTemplate(slot - 4);
    }

    private static void definition(ContainerMachine c, int slot, ItemStack stack, int count) {
        if (slot < 4) c.tile.setPaymentDefinition(slot, stack, count);
        else c.tile.setSaleDefinition(slot - 4, stack, count);
    }

    private static void setAndSync(EntityPlayerMP player, ContainerMachine c, int slot, ItemStack sample, int count) {
        try {
            sample = sample.copy();
            if (!sample.isEmpty()) {
                if (sample.getItem().getRegistryName() == null || sample.getMetadata() < 0
                        || sample.getMetadata() > Short.MAX_VALUE) throw new IllegalArgumentException("Invalid sample");
                sample.setCount(1);
                if (OreFilters.isFilter(sample)) {
                    if (slot >= 4) throw new IllegalArgumentException("Payment-only filter");
                    sample = OreFilters.create(OreFilters.sampleOf(sample), OreFilters.oreName(sample));
                }
                ByteBuf bytes = Unpooled.buffer();
                try {
                    new PacketBuffer(bytes).writeCompoundTag(sample.serializeNBT());
                    if (bytes.readableBytes() > 32768) throw new IllegalArgumentException("Oversized sample");
                } finally { bytes.release(); }
            }
            definition(c, slot, sample, sample.isEmpty() ? 0 : count);
            c.sendState(player);
        } catch (IllegalArgumentException invalid) {
            feedback(player, c, 0, TradeResultCode.INVALID_OFFER);
        }
    }

    public static boolean validGhostIdentity(String id, int metadata) {
        return id != null && id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                && metadata >= 0 && metadata < 32767;
    }

    /** A ghost request carries no NBT, capabilities, count, numeric registry ID, or actual item. */
    public static ItemStack resolveGhost(String id, int metadata) {
        if (!validGhostIdentity(id, metadata)) return ItemStack.EMPTY;
        ResourceLocation name = new ResourceLocation(id);
        if (!Item.REGISTRY.containsKey(name)) return ItemStack.EMPTY;
        try {
            // A registered mod item may reject a subtype during capability initialization.
            // Client-supplied metadata must not propagate that failure onto the server task.
            ItemStack result = new ItemStack(Item.REGISTRY.getObject(name), 1, metadata);
            return OreFilters.isFilter(result) ? ItemStack.EMPTY : result;
        } catch (RuntimeException unsupportedSubtype) {
            return ItemStack.EMPTY;
        }
    }

    public static final class SetGhostTemplate extends Request {
        public int slot, metadata;
        public String itemId = "";
        public SetGhostTemplate() {}
        public SetGhostTemplate(ContainerMachine c, int slot, ItemStack sample) {
            super(c); this.slot = slot;
            itemId = sample.getItem().getRegistryName().toString(); metadata = sample.getMetadata();
        }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(slot); b.writeString(itemId); b.writeVarInt(metadata); }
        protected void readBody(PacketBuffer b) { slot = b.readVarInt(); itemId = b.readString(256); metadata = b.readVarInt(); }
        protected boolean validBody() { return slot >= 0 && slot < 8 && validGhostIdentity(itemId, metadata); }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            ItemStack sample = resolveGhost(itemId, metadata);
            if (!sample.isEmpty()) setAndSync(player, c, slot, sample, 1);
        }
    }

    public static final class SetOreFilter extends Request {
        public int slot;
        public String oreName = "";
        public SetOreFilter() {}
        public SetOreFilter(ContainerMachine c, int slot, String name) { super(c); this.slot = slot; oreName = name; }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(slot); b.writeString(oreName); }
        protected void readBody(PacketBuffer b) { slot = b.readVarInt(); oreName = b.readString(128); }
        protected boolean validBody() { return slot >= 0 && slot < 4 && (oreName.isEmpty() || OreFilters.validName(oreName)); }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            ItemStack sample = OreFilters.sampleOf(c.tile.getPaymentTemplate(slot));
            if (sample.isEmpty()) return;
            try {
                ItemStack definition = oreName.isEmpty() ? sample : OreFilters.create(sample, oreName);
                setAndSync(player, c, slot, definition, c.tile.getPaymentQuantity(slot));
            } catch (IllegalArgumentException invalid) {
                feedback(player, c, 0, TradeResultCode.INVALID_OFFER);
            }
        }
    }

    public static final class RequestState extends Request {
        public RequestState() {}
        public RequestState(ContainerMachine c) { super(c); }
        protected void writeBody(PacketBuffer b) {}
        protected void readBody(PacketBuffer b) {}
        protected boolean validBody() { return true; }
        protected boolean configurationOnly() { return false; }
        protected boolean requiresSession() { return false; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) { c.sendState(player); }
    }

    public static final class SetTemplate extends Request {
        public int slot;
        public boolean clear;
        public SetTemplate() {}
        public SetTemplate(ContainerMachine c, int slot, boolean clear) { super(c); this.slot = slot; this.clear = clear; }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(slot); b.writeBoolean(clear); }
        protected void readBody(PacketBuffer b) { slot = b.readVarInt(); clear = bool(b); }
        protected boolean validBody() { return slot >= 0 && slot < 8; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            ItemStack sample = clear ? ItemStack.EMPTY : player.inventory.getItemStack().copy();
            if (!clear && sample.isEmpty()) return;
            setAndSync(player, c, slot, sample, 1);
        }
    }

    public static final class SetQuantity extends Request {
        public int slot, count;
        public SetQuantity() {}
        public SetQuantity(ContainerMachine c, int slot, int count) { super(c); this.slot = slot; this.count = count; }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(slot); b.writeVarInt(count); }
        protected void readBody(PacketBuffer b) { slot = b.readVarInt(); count = b.readVarInt(); }
        protected boolean validBody() { return slot >= 0 && slot < 8 && count >= 1 && count <= 1024; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            ItemStack stack = template(c, slot);
            if (!stack.isEmpty()) definition(c, slot, stack, count);
            c.sendState(player);
        }
    }

    public static final class SetMatchNbt extends Request {
        public int slot;
        public boolean enabled;
        public SetMatchNbt() {}
        public SetMatchNbt(ContainerMachine c, int slot, boolean enabled) { super(c); this.slot = slot; this.enabled = enabled; }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(slot); b.writeBoolean(enabled); }
        protected void readBody(PacketBuffer b) { slot = b.readVarInt(); enabled = bool(b); }
        protected boolean validBody() { return slot >= 0 && slot < 8; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            c.tile.setMatchingNbt(slot, enabled);
            c.sendState(player);
        }
    }

    public static final class SetSideMode extends Request {
        public int face, mode;
        public boolean automatic;
        public SetSideMode() {}
        public SetSideMode(ContainerMachine c, int face, int mode, boolean automatic) {
            super(c); this.face = face; this.mode = mode; this.automatic = automatic;
        }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(face); b.writeVarInt(mode); b.writeBoolean(automatic); }
        protected void readBody(PacketBuffer b) { face = b.readVarInt(); mode = b.readVarInt(); automatic = bool(b); }
        protected boolean validBody() { return face >= 0 && face < 6 && face != 2 && mode >= 0 && mode <= 3; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            int bit = 1 << face;
            int inputs = (c.tile.getInputSides() & ~bit) | ((mode & 1) != 0 ? bit : 0);
            int outputs = (c.tile.getOutputSides() & ~bit) | ((mode & 2) != 0 ? bit : 0);
            int auto = (c.tile.getAutoSides() & ~bit) | (automatic && mode != 0 ? bit : 0);
            c.tile.setSideMasks(inputs & ~4, outputs & ~4, auto & ~4);
            c.sendState(player);
        }
    }

    public static final class SetCustomName extends Request {
        public String name = "";
        public SetCustomName() {}
        public SetCustomName(ContainerMachine c, String name) { super(c); this.name = name; }
        protected void writeBody(PacketBuffer b) { b.writeString(name); }
        protected void readBody(PacketBuffer b) { name = b.readString(128); }
        protected boolean validBody() {
            return name.codePointCount(0, name.length()) <= 64
                    && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 167 || (c >= 0xD800 && c <= 0xDFFF));
        }
        protected void apply(EntityPlayerMP player, ContainerMachine c) { c.tile.setCustomName(name); c.sendState(player); }
    }

    public static final class SetCreativeMode extends Request {
        public boolean enabled;
        public SetCreativeMode() {}
        public SetCreativeMode(ContainerMachine c, boolean enabled) { super(c); this.enabled = enabled; }
        protected void writeBody(PacketBuffer b) { b.writeBoolean(enabled); }
        protected void readBody(PacketBuffer b) { enabled = bool(b); }
        protected boolean validBody() { return true; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            if (c.tile.canEnableCreativeMode(player)) { c.tile.setCreativeMode(enabled); c.sendState(player); }
        }
    }

    public static final class Trade extends Request {
        public int count;
        public long offerRevision, requestId;
        public Trade() {}
        public Trade(ContainerMachine c, int count, long requestId) {
            super(c); this.count = count; offerRevision = c.view.offerRevision; this.requestId = requestId;
        }
        protected void writeBody(PacketBuffer b) { b.writeVarInt(count); b.writeLong(offerRevision); b.writeLong(requestId); }
        protected void readBody(PacketBuffer b) { count = b.readVarInt(); offerRevision = b.readLong(); requestId = b.readLong(); }
        protected boolean validBody() {
            return count >= 1 && count <= TradeLimits.MAX_BATCH_SIZE && offerRevision > 0 && requestId > 0;
        }
        protected boolean configurationOnly() { return false; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            applyWithDelay(player, c, 0);
        }
        private void applyWithDelay(EntityPlayerMP player, ContainerMachine c, int retryAfterMillis) {
            if (c.configuration) return;
            TradeExecutor.Outcome result = TradeCompletion.execute(player, c.tradeLedger, requestId, count, offerRevision,
                    () -> TradeExecutor.execute(player, c.tile, count, offerRevision,
                            () -> player.openContainer == c && c.canInteractWith(player) ? null : TradeResultCode.INVALID_REQUEST));
            c.detectAndSendChanges();
            c.sendState(player);
            feedback(player, c, result.completed, result.code, requestId, retryAfterMillis);
        }
    }

    public static final class OpenTrading extends Request {
        public OpenTrading() {}
        public OpenTrading(ContainerMachine c) { super(c); }
        protected void writeBody(PacketBuffer b) {}
        protected void readBody(PacketBuffer b) {}
        protected boolean validBody() { return true; }
        protected void apply(EntityPlayerMP player, ContainerMachine c) {
            player.openGui(TradingNetworkMod.instance, MachineGuiHandler.TRADING, player.world,
                    c.position.getX(), c.position.getY(), c.position.getZ());
        }
    }

    public static final class State implements IMessage {
        public Context context = new Context();
        public NBTTagCompound data = new NBTTagCompound();
        public boolean configuration, valid;
        public State() {}
        State(ContainerMachine c, NBTTagCompound data) { context = new Context(c); configuration = c.configuration; this.data = data; }
        public void toBytes(ByteBuf bytes) {
            PacketBuffer b = new PacketBuffer(bytes);
            context.write(b);
            b.writeBoolean(configuration);
            NBTTagCompound display = data.copy();
            int[] quantities = display.getIntArray("Quantities");
            display.removeTag("Quantities");
            b.writeCompoundTag(display);
            for (int slot = 0; slot < 8; slot++) b.writeVarInt(slot < quantities.length ? quantities[slot] : 0);
        }
        public void fromBytes(ByteBuf bytes) {
            valid = false;
            if (bytes.readableBytes() > 524288) return;
            try {
                PacketBuffer b = new PacketBuffer(bytes);
                context.read(b); configuration = bool(b); data = b.readCompoundTag();
                if (data == null || data.getLong("OfferRevision") <= 0) return;
                int[] quantities = new int[8];
                for (int slot = 0; slot < 8; slot++) {
                    quantities[slot] = b.readVarInt();
                    if (quantities[slot] < 0 || quantities[slot] > 1024) return;
                }
                data.setIntArray("Quantities", quantities);
                valid = data != null && !b.isReadable();
            } catch (IOException | RuntimeException malformed) { valid = false; }
        }
    }

    public static final class Feedback implements IMessage {
        public Context context = new Context();
        public int completed;
        public long requestId;
        public int retryAfterMillis;
        public TradeResultCode code = TradeResultCode.INVALID_REQUEST;
        public boolean valid;
        public Feedback() {}
        Feedback(ContainerMachine c, int count, TradeResultCode code, long requestId, int retryAfterMillis) {
            context = new Context(c); completed = count; this.code = code;
            this.requestId = requestId; this.retryAfterMillis = retryAfterMillis;
        }
        public void toBytes(ByteBuf bytes) {
            PacketBuffer b = new PacketBuffer(bytes);
            context.write(b); b.writeVarInt(completed); b.writeVarInt(code.ordinal());
            b.writeLong(requestId); b.writeVarInt(retryAfterMillis);
        }
        public void fromBytes(ByteBuf bytes) {
            valid = false;
            if (bytes.readableBytes() > 48) return;
            try {
                PacketBuffer b = new PacketBuffer(bytes);
                context.read(b); completed = b.readVarInt(); int id = b.readVarInt();
                requestId = b.readLong(); retryAfterMillis = b.readVarInt();
                valid = !b.isReadable() && completed >= 0 && completed <= TradeLimits.MAX_BATCH_SIZE
                        && requestId >= 0 && retryAfterMillis >= 0 && retryAfterMillis <= 1000
                        && id >= 0 && id < TradeResultCode.values().length;
                if (valid) code = TradeResultCode.values()[id];
            } catch (RuntimeException malformed) { valid = false; }
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        public IMessage onMessage(State message, MessageContext ctx) {
            if (message.valid) TradingNetworkMod.proxy.receiveState(message);
            return null;
        }
    }

    public static final class FeedbackHandler implements IMessageHandler<Feedback, IMessage> {
        public IMessage onMessage(Feedback message, MessageContext ctx) {
            if (message.valid) TradingNetworkMod.proxy.receiveFeedback(message);
            return null;
        }
    }
}
