package com.tom.trading.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class PacketFuzzTest {
    @Test public void actualTestClassloaderUsesTheCorrectedForgeSides() {
        assertArrayEquals(new net.minecraftforge.fml.relauncher.Side[] {
                net.minecraftforge.fml.relauncher.Side.CLIENT, net.minecraftforge.fml.relauncher.Side.SERVER
        }, net.minecraftforge.fml.relauncher.Side.values());
    }

    private static final List<Supplier<MachineNetwork.Request>> REQUESTS = Arrays.asList(
            MachineNetwork.RequestState::new, MachineNetwork.SetTemplate::new,
            MachineNetwork.SetQuantity::new, MachineNetwork.SetMatchNbt::new,
            MachineNetwork.SetSideMode::new, MachineNetwork.SetCustomName::new,
            MachineNetwork.SetCreativeMode::new, MachineNetwork.Trade::new,
            MachineNetwork.OpenTrading::new, MachineNetwork.SetOreFilter::new,
            MachineNetwork.SetGhostTemplate::new);

    @Test public void allRequestTypesRejectEveryTruncationAndTrailingByte() {
        for (Supplier<MachineNetwork.Request> factory : REQUESTS) {
            MachineNetwork.Request request = factory.get();
            request.context.window = 1; request.context.session = 123;
            if (request instanceof MachineNetwork.SetQuantity) ((MachineNetwork.SetQuantity) request).count = 1024;
            if (request instanceof MachineNetwork.Trade) {
                ((MachineNetwork.Trade) request).count = 1024;
                ((MachineNetwork.Trade) request).offerRevision = 1;
                ((MachineNetwork.Trade) request).requestId = 1;
            }
            if (request instanceof MachineNetwork.SetGhostTemplate)
                ((MachineNetwork.SetGhostTemplate) request).itemId = "minecraft:stone";
            ByteBuf encoded = Unpooled.buffer();
            try {
                request.toBytes(encoded);
                MachineNetwork.Request roundTrip = factory.get();
                roundTrip.fromBytes(encoded.duplicate());
                assertTrue(request.getClass().getSimpleName(), roundTrip.valid);
                for (int length = 0; length < encoded.readableBytes(); length++) {
                    MachineNetwork.Request truncated = factory.get();
                    truncated.fromBytes(encoded.slice(0, length));
                    assertFalse(request.getClass().getSimpleName() + " prefix " + length, truncated.valid);
                }
                encoded.writeByte(0);
                MachineNetwork.Request extra = factory.get();
                extra.fromBytes(encoded.duplicate());
                assertFalse(extra.valid);
            } finally { encoded.release(); }
        }
    }

    @Test public void deterministicMalformedInputsNeverEscapeDecoderBoundaries() {
        Random random = new Random(0x54544eL);
        for (int attempt = 0; attempt < 1000; attempt++) {
            byte[] data = new byte[random.nextInt(350)];
            random.nextBytes(data);
            for (Supplier<MachineNetwork.Request> factory : REQUESTS) decode(factory.get(), data);
            decode(new MachineNetwork.State(), data);
            decode(new MachineNetwork.Feedback(), data);
        }
    }

    private static void decode(IMessage message, byte[] data) {
        ByteBuf buffer = Unpooled.wrappedBuffer(data);
        try { message.fromBytes(buffer); }
        finally { buffer.release(); }
    }
}
