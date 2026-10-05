package com.tom.trading.network;

import com.tom.trading.TradingNetworkMod;
import com.tom.trading.book.TradeBookBinding;
import com.tom.trading.book.TradeBookData;
import com.tom.trading.book.TradeBooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Held-item authority only. Clients cannot request a machine UUID, token, arbitrary NBT or another player's history. */
public final class TradeBookNetwork {
    public static final int REQUEST_BYTES = 21, RESPONSE_BYTES = 32 * 1024;
    public enum Result { OK, UNAVAILABLE }
    private static final Set<EntityPlayerMP> QUEUED = Collections.newSetFromMap(new WeakHashMap<>());
    private TradeBookNetwork() {}
    static int register(SimpleNetworkWrapper channel, int id) {
        channel.registerMessage(OpenHandler.class, Open.class, id++, Side.SERVER);
        channel.registerMessage(OpenedHandler.class, Opened.class, id++, Side.CLIENT); return id;
    }
    public static boolean accept(EntityPlayerMP player) { return MachineNetwork.acceptDirectory(player); }
    public static void request(Open request) { MachineNetwork.CHANNEL.sendToServer(request); }
    public static final class Open implements IMessage {
        public UUID requestId;
        public int dimension;
        public EnumHand hand;
        public boolean valid;
        public Open() {}
        public Open(UUID requestId, int dimension, EnumHand hand) {
            this.requestId = Objects.requireNonNull(requestId); this.dimension = dimension; this.hand = Objects.requireNonNull(hand);
        }
        @Override public void toBytes(ByteBuf bytes) { writeRequest(new PacketBuffer(bytes), this); }
        @Override public void fromBytes(ByteBuf bytes) {
            valid = false; if (bytes.readableBytes() != REQUEST_BYTES) return;
            try { Open decoded = readRequest(new PacketBuffer(bytes)); requestId = decoded.requestId;
                dimension = decoded.dimension; hand = decoded.hand; valid = !bytes.isReadable(); }
            catch (RuntimeException malformed) { valid = false; }
        }
    }
    public static final class Opened implements IMessage {
        public Open request;
        public TradeBookBinding binding;
        public Result result;
        public List<String> rows;
        public boolean valid;
        public Opened() {}
        public Opened(Open request, TradeBookBinding binding, Result result, List<String> rows) {
            validate(binding, result, rows); this.request = Objects.requireNonNull(request);
            this.binding = binding; this.result = result; this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }
        @Override public void toBytes(ByteBuf bytes) {
            validate(binding, result, rows); int start = bytes.writerIndex(); PacketBuffer buffer = new PacketBuffer(bytes);
            writeRequest(buffer, request); buffer.writeBoolean(binding != null);
            if (binding != null) { buffer.writeUniqueId(binding.world); buffer.writeUniqueId(binding.machine); buffer.writeUniqueId(binding.key); }
            buffer.writeByte(result.ordinal()); buffer.writeVarInt(rows.size()); for (String row : rows) buffer.writeString(row);
            if (bytes.writerIndex() - start > RESPONSE_BYTES) throw new IllegalArgumentException("Book response too large");
        }
        @Override public void fromBytes(ByteBuf bytes) {
            valid = false; rows = null; if (bytes.readableBytes() > RESPONSE_BYTES) return;
            try {
                PacketBuffer buffer = new PacketBuffer(bytes); Open query = readRequest(buffer);
                int hasBinding = buffer.readUnsignedByte(); if (hasBinding > 1) return;
                TradeBookBinding identity = hasBinding == 0 ? null : new TradeBookBinding(buffer.readUniqueId(), buffer.readUniqueId(), buffer.readUniqueId());
                int code = buffer.readUnsignedByte(); if (code >= Result.values().length) return;
                int count = buffer.readVarInt(); if (count < 0 || count > TradeBookData.PER_MACHINE) return;
                List<String> records = new ArrayList<>(); int totalBytes = 0;
                for (int i = 0; i < count; i++) {
                    String row = buffer.readString(TradeBookData.MAX_ENTRY); totalBytes += row.getBytes(StandardCharsets.UTF_8).length + 5;
                    if (!TradeBookData.validText(row) || totalBytes > TradeBookData.SNAPSHOT_BYTES) return;
                    records.add(row);
                }
                Opened decoded = new Opened(query, identity, Result.values()[code], records);
                request = decoded.request; binding = decoded.binding; result = decoded.result; rows = decoded.rows; valid = !buffer.isReadable();
            } catch (RuntimeException malformed) { valid = false; }
        }
        private static void validate(TradeBookBinding binding, Result result, List<String> rows) {
            Objects.requireNonNull(result); Objects.requireNonNull(rows);
            if ((result == Result.OK && binding == null) || (result != Result.OK && !rows.isEmpty())
                    || rows.size() > TradeBookData.PER_MACHINE) throw new IllegalArgumentException("Invalid book response");
            int bytes = 0;
            for (String row : rows) {
                if (!TradeBookData.validText(row)) throw new IllegalArgumentException("Invalid book text");
                bytes += row.getBytes(StandardCharsets.UTF_8).length + 5;
            }
            if (bytes > TradeBookData.SNAPSHOT_BYTES) throw new IllegalArgumentException("Oversized book text");
        }
    }
    private static void writeRequest(PacketBuffer buffer, Open request) {
        buffer.writeUniqueId(request.requestId); buffer.writeInt(request.dimension); buffer.writeByte(request.hand.ordinal());
    }
    private static Open readRequest(PacketBuffer buffer) {
        UUID id = buffer.readUniqueId(); int dimension = buffer.readInt(), hand = buffer.readUnsignedByte();
        if (hand >= EnumHand.values().length) throw new IllegalArgumentException("Invalid hand");
        return new Open(id, dimension, EnumHand.values()[hand]);
    }
    public static final class OpenHandler implements IMessageHandler<Open, IMessage> {
        @Override public IMessage onMessage(Open request, MessageContext context) {
            if (!request.valid) return null;
            EntityPlayerMP player = context.getServerHandler().player;
            synchronized (QUEUED) { if (QUEUED.contains(player) || !accept(player)) return null; QUEUED.add(player); }
            try {
                player.getServerWorld().addScheduledTask(() -> {
                    try {
                        if (!TradeBooks.active(player) || player.dimension != request.dimension) return;
                        TradeBookBinding binding = TradeBookBinding.read(player.getHeldItem(request.hand).getTagCompound());
                        Opened response;
                        try {
                            if (binding == null) throw new IllegalArgumentException("Not a trade book");
                            response = new Opened(request, binding, Result.OK, TradeBooks.open(player, request.hand, binding));
                        } catch (IllegalArgumentException unavailable) {
                            response = new Opened(request, binding, Result.UNAVAILABLE, Collections.emptyList());
                        }
                        MachineNetwork.CHANNEL.sendTo(response, player);
                    } finally { synchronized (QUEUED) { QUEUED.remove(player); } }
                });
            } catch (RuntimeException stopped) { synchronized (QUEUED) { QUEUED.remove(player); } }
            return null;
        }
    }
    public static final class OpenedHandler implements IMessageHandler<Opened, IMessage> {
        @Override public IMessage onMessage(Opened message, MessageContext context) {
            if (message.valid) TradingNetworkMod.proxy.receiveTradeBook(message, context.netHandler); return null;
        }
    }
}
