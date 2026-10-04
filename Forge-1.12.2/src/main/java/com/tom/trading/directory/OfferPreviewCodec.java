package com.tom.trading.directory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants;
import java.util.*;

/** Length-delimited public fields only. The optional saved cache has its own format. */
public final class OfferPreviewCodec {
    private OfferPreviewCodec() {}
    public static void write(PacketBuffer target, OfferPreview preview) {
        target.writeShort(preview.encodedSize());
        payload(target, preview);
    }
    public static OfferPreview read(PacketBuffer source) {
        int size = source.readUnsignedShort();
        if (size < 1 || size > OfferPreview.MAX_BYTES) throw new IllegalArgumentException("Invalid preview length");
        PacketBuffer body = new PacketBuffer(source.readSlice(size));
        int known = body.readUnsignedByte();
        OfferPreview preview;
        if (known == 0) preview = OfferPreview.UNKNOWN;
        else if (known == 1) preview = new OfferPreview(readItems(body), readItems(body));
        else throw new IllegalArgumentException("Invalid preview flag");
        if (body.isReadable()) throw new IllegalArgumentException("Trailing preview data");
        return preview;
    }
    private static void payload(PacketBuffer b, OfferPreview preview) {
        b.writeBoolean(preview.known);
        if (preview.known) { writeItems(b, preview.payment); writeItems(b, preview.sale); }
    }
    private static void writeItems(PacketBuffer b, List<OfferPreview.Item> items) {
        b.writeByte(items.size());
        for (OfferPreview.Item item : items) {
            b.writeString(item.itemId); b.writeShort(item.metadata); b.writeShort(item.quantity);
            b.writeByte((item.tagged ? 1 : 0) | (item.matchNbt ? 2 : 0));
            b.writeString(item.name); b.writeString(item.oreName);
        }
    }
    private static List<OfferPreview.Item> readItems(PacketBuffer b) {
        int count = b.readUnsignedByte();
        if (count > com.tom.trading.trade.TradeLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many preview items");
        List<OfferPreview.Item> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = b.readString(OfferPreview.MAX_ITEM_ID);
            int metadata = b.readUnsignedShort(), quantity = b.readUnsignedShort(), flags = b.readUnsignedByte();
            if (flags > 3) throw new IllegalArgumentException("Invalid preview flags");
            items.add(new OfferPreview.Item(id, metadata, quantity, b.readString(128), b.readString(128),
                    (flags & 1) != 0, (flags & 2) != 0));
        }
        return items;
    }
    public static NBTTagCompound save(OfferPreview preview) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Format", 1);
        ByteBuf bytes = Unpooled.buffer(preview.encodedSize() + 2);
        try {
            write(new PacketBuffer(bytes), preview);
            byte[] payload = new byte[bytes.readableBytes()]; bytes.readBytes(payload); tag.setByteArray("PublicOffer", payload);
        } finally { bytes.release(); }
        return tag;
    }
    public static OfferPreview restore(NBTTagCompound tag) {
        // A missing, malformed or future optional preview must not quarantine valid identity records.
        if (!tag.hasKey("Format", Constants.NBT.TAG_INT) || tag.getInteger("Format") != 1
                || !tag.hasKey("PublicOffer", Constants.NBT.TAG_BYTE_ARRAY)) return OfferPreview.UNKNOWN;
        byte[] payload = tag.getByteArray("PublicOffer");
        if (payload.length < 3 || payload.length > OfferPreview.MAX_BYTES + 2) return OfferPreview.UNKNOWN;
        ByteBuf bytes = Unpooled.wrappedBuffer(payload);
        try {
            OfferPreview preview = read(new PacketBuffer(bytes));
            return bytes.isReadable() ? OfferPreview.UNKNOWN : preview;
        } catch (RuntimeException malformed) { return OfferPreview.UNKNOWN; }
        finally { bytes.release(); }
    }
}
