package com.tom.trading.network;

import com.tom.trading.directory.*;
import com.tom.trading.remote.*;
import com.tom.trading.trade.TradeExecutor;
import com.tom.trading.trade.TradeResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Consolidated RT-5 decoder coverage, without starting a server or authorizing any request. */
public class RemotePacketFuzzTest {
    private static final List<Supplier<IMessage>> TYPES = Arrays.asList(
            DirectoryNetwork.RequestPage::new, DirectoryNetwork.Page::new,
            RemoteNetwork.Open::new, RemoteNetwork.Cancel::new, RemoteNetwork.Opened::new,
            RemoteNetwork.Buy::new, RemoteNetwork.State::new, RemoteNetwork.Feedback::new);

    @Test public void everyExtensionPacketRejectsAllTruncationsAndTrailingBytes() {
        UUID world = UUID.randomUUID(), machine = UUID.randomUUID();
        MachineAddress address = new MachineAddress(66, -3200, 70, 9000);
        DirectoryQuery query = new DirectoryQuery(UUID.randomUUID(), 1, -1, 0, world, 7, "Shop");
        MachineDirectoryEntry entry = new MachineDirectoryEntry(address, machine, UUID.randomUUID(), "Owner", "Shop",
                MachineDirectoryEntry.Evidence.OBSERVED);
        DirectoryPage page = new DirectoryPage(query, DirectoryPage.Result.OK, world, 7, 1, false,
                Collections.singletonList(new DirectoryPage.Row(entry, DirectoryPage.State.RECORDED)));
        RemoteOpenRequest request = new RemoteOpenRequest(query.screenId, 3, world, 7, -1, address, machine);
        RemoteContext context = new RemoteContext(100, -1, UUID.randomUUID(), world, machine, address);
        NBTTagCompound display = new NBTTagCompound(); display.setLong("OfferRevision", 9);
        display.setIntArray("Quantities", new int[] {1, 64, 65, 128, 256, 1024, 0, 1});
        List<IMessage> packets = Arrays.asList(new DirectoryNetwork.RequestPage(query), new DirectoryNetwork.Page(page),
                new RemoteNetwork.Open(request), new RemoteNetwork.Cancel(request),
                new RemoteNetwork.Opened(request, RemoteOpenResult.OPENED, context),
                new RemoteNetwork.Buy(context, 1024, 9, 1), new RemoteNetwork.State(context, display),
                new RemoteNetwork.Feedback(context, 1, new TradeExecutor.Outcome(128, TradeResultCode.MACHINE_MISSING_INPUT), 1000));
        for (int index = 0; index < packets.size(); index++) {
            IMessage packet = packets.get(index); Supplier<IMessage> factory = TYPES.get(index);
            ByteBuf bytes = Unpooled.buffer();
            try {
                packet.toBytes(bytes); assertTrue(packet.getClass().getName(), decode(factory, bytes.duplicate()));
                for (int size = 0; size < bytes.readableBytes(); size++)
                    assertFalse(packet.getClass().getName() + " prefix " + size, decode(factory, bytes.slice(0, size)));
                bytes.writeByte(0); assertFalse(decode(factory, bytes.duplicate()));
            } finally { bytes.release(); }
        }
    }

    @Test public void randomAndOversizedFramesStayInsideAllEightDecoderBoundaries() {
        Random random = new Random(0x54544e525435L);
        for (int attempt = 0; attempt < 1000; attempt++) {
            byte[] data = new byte[random.nextInt(1024)]; random.nextBytes(data);
            ByteBuf bytes = Unpooled.wrappedBuffer(data);
            try { for (Supplier<IMessage> type : TYPES) decode(type, bytes.duplicate()); }
            finally { bytes.release(); }
        }
        ByteBuf huge = Unpooled.buffer(524289).writeZero(524289);
        try { for (Supplier<IMessage> type : TYPES) assertFalse(decode(type, huge.duplicate())); }
        finally { huge.release(); }
    }

    private static boolean decode(Supplier<IMessage> factory, ByteBuf bytes) {
        IMessage message = factory.get(); message.fromBytes(bytes);
        if (message instanceof RemoteNetwork.Packet) return ((RemoteNetwork.Packet) message).valid;
        if (message instanceof DirectoryNetwork.RequestPage) return ((DirectoryNetwork.RequestPage) message).valid;
        return ((DirectoryNetwork.Page) message).valid;
    }
}
