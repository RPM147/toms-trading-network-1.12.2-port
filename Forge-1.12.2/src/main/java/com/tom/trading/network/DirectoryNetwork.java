package com.tom.trading.network;

import com.tom.trading.TradingNetworkMod;
import com.tom.trading.BuildInfo;
import com.tom.trading.directory.*;
import com.tom.trading.remote.RemoteSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

/** Appended to the existing protocol; independent of machine containers, not of ingress budgets. */
public final class DirectoryNetwork {
    public static final int MAX_REQUEST_BYTES = 400, MAX_PAGE_BYTES = 64 * 1024;
    // Contains no value->key references. One scheduled ingress task per connection, even if the server stalls.
    private static final Set<EntityPlayerMP> QUEUED = Collections.newSetFromMap(new WeakHashMap<>());
    private DirectoryNetwork() {}
    static int register(SimpleNetworkWrapper channel, int firstId) {
        channel.registerMessage(RequestHandler.class, RequestPage.class, firstId++, Side.SERVER);
        channel.registerMessage(PageHandler.class, Page.class, firstId++, Side.CLIENT);
        return firstId;
    }
    public static void request(DirectoryQuery query) { MachineNetwork.CHANNEL.sendToServer(new RequestPage(query)); }

    public static final class RequestPage implements IMessage {
        public DirectoryQuery query;
        public boolean valid;
        public RequestPage() {}
        public RequestPage(DirectoryQuery query) { this.query = query; }
        @Override public void toBytes(ByteBuf bytes) { writeQuery(new PacketBuffer(bytes), query); }
        @Override public void fromBytes(ByteBuf bytes) {
            valid = false; query = null;
            if (bytes.readableBytes() > MAX_REQUEST_BYTES) return;
            try { PacketBuffer b = new PacketBuffer(bytes); query = readQuery(b); valid = !b.isReadable(); }
            catch (RuntimeException malformed) { valid = false; }
        }
    }
    public static final class Page implements IMessage {
        public DirectoryPage page;
        public boolean valid;
        public Page() {}
        public Page(DirectoryPage page) { this.page = page; }
        @Override public void toBytes(ByteBuf bytes) {
            int start = bytes.writerIndex();
            PacketBuffer b = new PacketBuffer(bytes);
            writeQuery(b, page.query); b.writeByte(page.result.ordinal()); optionalUuid(b, page.worldId);
            b.writeLong(page.revision); b.writeVarInt(page.total); b.writeBoolean(page.backfillComplete);
            b.writeString(page.specialTabName);
            b.writeVarInt(page.rows.size());
            for (DirectoryPage.Row row : page.rows) {
                MachineDirectoryEntry entry = row.entry;
                b.writeInt(entry.address.dimension); b.writeInt(entry.address.x);
                b.writeInt(entry.address.y); b.writeInt(entry.address.z);
                optionalUuid(b, entry.machineId); optionalUuid(b, entry.ownerId);
                b.writeString(entry.ownerName); b.writeString(entry.name);
                b.writeByte(entry.evidence.ordinal()); b.writeByte(row.state.ordinal());
                OfferPreviewCodec.write(b, entry.preview);
            }
            if (bytes.writerIndex() - start > MAX_PAGE_BYTES) throw new IllegalArgumentException("Directory page exceeds limit");
        }
        @Override public void fromBytes(ByteBuf bytes) {
            valid = false; page = null;
            if (bytes.readableBytes() > MAX_PAGE_BYTES) return;
            try {
                PacketBuffer b = new PacketBuffer(bytes);
                DirectoryQuery query = readQuery(b);
                DirectoryPage.Result result = enumeration(b, DirectoryPage.Result.values());
                UUID worldId = optionalUuid(b); long revision = b.readLong(); int total = b.readVarInt();
                boolean complete = bool(b); String specialTabName = label(b); int count = b.readVarInt();
                if (count < 0 || count > DirectoryQuery.PAGE_SIZE) return;
                List<DirectoryPage.Row> rows = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    MachineAddress address = new MachineAddress(b.readInt(), b.readInt(), b.readInt(), b.readInt());
                    UUID machineId = optionalUuid(b), ownerId = optionalUuid(b);
                    String owner = label(b), name = label(b);
                    MachineDirectoryEntry.Evidence evidence = enumeration(b, MachineDirectoryEntry.Evidence.values());
                    DirectoryPage.State state = enumeration(b, DirectoryPage.State.values());
                    OfferPreview preview = OfferPreviewCodec.read(b);
                    rows.add(new DirectoryPage.Row(new MachineDirectoryEntry(address, machineId, ownerId, owner, name, evidence, preview), state));
                }
                page = new DirectoryPage(query, result, worldId, revision, total, complete, rows, specialTabName);
                valid = !b.isReadable();
            } catch (RuntimeException malformed) { valid = false; }
        }
    }
    private static void writeQuery(PacketBuffer b, DirectoryQuery query) {
        b.writeUniqueId(query.screenId); b.writeLong(query.requestId); b.writeInt(query.playerDimension);
        b.writeVarInt(query.page); optionalUuid(b, query.expectedWorld); b.writeLong(query.expectedRevision); b.writeString(query.search);
        b.writeByte(query.tab.ordinal());
    }
    private static DirectoryQuery readQuery(PacketBuffer b) {
        return new DirectoryQuery(b.readUniqueId(), b.readLong(), b.readInt(), b.readVarInt(),
                optionalUuid(b), b.readLong(), label(b), enumeration(b, DirectoryTab.values()));
    }
    private static String label(PacketBuffer b) {
        String value = b.readString(DirectoryText.MAX_CHARACTERS * 2);
        if (!DirectoryText.valid(value)) throw new IllegalArgumentException("Invalid public text");
        return value;
    }
    private static void optionalUuid(PacketBuffer b, UUID id) { b.writeBoolean(id != null); if (id != null) b.writeUniqueId(id); }
    private static UUID optionalUuid(PacketBuffer b) { return bool(b) ? b.readUniqueId() : null; }
    private static boolean bool(PacketBuffer b) {
        int value = b.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid boolean");
        return value == 1;
    }
    private static <E> E enumeration(PacketBuffer b, E[] values) {
        int index = b.readUnsignedByte();
        if (index >= values.length) throw new IllegalArgumentException("Invalid enum");
        return values[index];
    }
    private static boolean active(EntityPlayerMP player, DirectoryQuery query) {
        return !player.hasDisconnected() && player.isEntityAlive() && !player.isSpectator()
                && player.dimension == query.playerDimension && player.openContainer == player.inventoryContainer;
    }
    public static final class RequestHandler implements IMessageHandler<RequestPage, IMessage> {
        @Override public IMessage onMessage(RequestPage message, MessageContext ctx) {
            if (!message.valid) return null;
            EntityPlayerMP player = ctx.getServerHandler().player;
            synchronized (QUEUED) {
                if (QUEUED.contains(player) || !MachineNetwork.acceptDirectory(player)) return null;
                QUEUED.add(player);
            }
            try {
                player.getServerWorld().addScheduledTask(() -> {
                    try {
                        DirectoryQuery query = message.query;
                        if (!active(player, query)) return;
                        MachineDirectoryData data = MachineDirectory.get(player.getServerWorld());
                        if (data == null) {
                            sendIfActive(player, new DirectoryPage(query, DirectoryPage.Result.UNAVAILABLE, null, 0,
                                    0, false, Collections.emptyList()));
                            return;
                        }
                        data.pager().submit(player.getUniqueID(), query, System.nanoTime(), () -> active(player, query),
                                result -> sendIfActive(player, result));
                    } finally { synchronized (QUEUED) { QUEUED.remove(player); } }
                });
            } catch (RuntimeException stopped) { synchronized (QUEUED) { QUEUED.remove(player); } }
            return null;
        }
    }
    private static void sendIfActive(EntityPlayerMP player, DirectoryPage page) {
        if (!active(player, page.query)) return;
        // Stamp every reply, including empty/error pages, from the server configuration only.
        try { MachineNetwork.CHANNEL.sendTo(new Page(page.withSpecialTabName(RemoteSettings.specialTabLabel())), player); }
        catch (RuntimeException failure) {
            // Never retry a reply or abort the world tick; retain diagnostics for unexpected codec failures too.
            org.apache.logging.log4j.LogManager.getLogger(BuildInfo.MOD_ID).debug("Directory reply was not delivered", failure);
        }
    }
    public static final class PageHandler implements IMessageHandler<Page, IMessage> {
        @Override public IMessage onMessage(Page message, MessageContext ctx) {
            if (message.valid) TradingNetworkMod.proxy.receiveDirectoryPage(message.page, ctx.netHandler);
            return null;
        }
    }
}
