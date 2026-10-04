package com.tom.trading.network;

import com.tom.trading.directory.MachineAddress;
import com.tom.trading.remote.*;
import com.tom.trading.trade.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class RemoteNetworkTest {
    private final UUID world = UUID.randomUUID(), machine = UUID.randomUUID();
    private final MachineAddress address = new MachineAddress(66, -3200, 70, 9000);
    private RemoteOpenRequest opening() { return new RemoteOpenRequest(UUID.randomUUID(), 3, world, 7, -1, address, machine); }
    private RemoteContext context() { return new RemoteContext(100, -1, UUID.randomUUID(), world, machine, address); }
    private <T extends RemoteNetwork.Packet> T decode(RemoteNetwork.Packet outgoing, T incoming) {
        ByteBuf bytes = Unpooled.buffer();
        try { outgoing.toBytes(bytes); incoming.fromBytes(bytes); return incoming; } finally { bytes.release(); }
    }
    @Test public void openAndCancelPreserveTheCompleteSelectedIdentity() {
        RemoteOpenRequest request = opening();
        RemoteNetwork.Open decoded = decode(new RemoteNetwork.Open(request), new RemoteNetwork.Open());
        assertTrue(decoded.valid); assertTrue(request.matches(decoded.request));
        assertTrue(decode(new RemoteNetwork.Cancel(request), new RemoteNetwork.Cancel()).valid);
        RemoteOpenRequest legacy = new RemoteOpenRequest(request.screenId, 3, world, 7, -1, address, null);
        RemoteNetwork.Open hint = decode(new RemoteNetwork.Open(legacy), new RemoteNetwork.Open());
        assertTrue(hint.valid); assertNull(hint.request.machineId);
    }
    @Test public void openedResponseCannotSubstituteTargetOrPlayerDimension() {
        RemoteOpenRequest request = opening(); RemoteContext context = context();
        RemoteNetwork.Opened good = decode(new RemoteNetwork.Opened(request, RemoteOpenResult.OPENED, context), new RemoteNetwork.Opened());
        assertTrue(good.valid); assertTrue(context.matches(good.context));
        RemoteContext bad = new RemoteContext(1, 0, UUID.randomUUID(), world, machine, address);
        assertFalse(decode(new RemoteNetwork.Opened(request, RemoteOpenResult.OPENED, bad), new RemoteNetwork.Opened()).valid);
        bad = new RemoteContext(1, -1, UUID.randomUUID(), world, UUID.randomUUID(), address);
        assertFalse(decode(new RemoteNetwork.Opened(request, RemoteOpenResult.OPENED, bad), new RemoteNetwork.Opened()).valid);
        for (RemoteOpenResult result : RemoteOpenResult.values()) if (result != RemoteOpenResult.OPENED)
            assertTrue(decode(new RemoteNetwork.Opened(request, result, null), new RemoteNetwork.Opened()).valid);
    }
    @Test public void everyContextFieldIsRequiredIncludingTheSeparateDimensions() {
        RemoteContext base = context();
        assertFalse(base.matches(new RemoteContext(99, -1, base.sessionId, world, machine, address)));
        assertFalse(base.matches(new RemoteContext(100, 66, base.sessionId, world, machine, address)));
        assertFalse(base.matches(new RemoteContext(100, -1, UUID.randomUUID(), world, machine, address)));
        assertFalse(base.matches(new RemoteContext(100, -1, base.sessionId, UUID.randomUUID(), machine, address)));
        assertFalse(base.matches(new RemoteContext(100, -1, base.sessionId, world, UUID.randomUUID(), address)));
        assertFalse(base.matches(new RemoteContext(100, -1, base.sessionId, world, machine, new MachineAddress(-1, -3200, 70, 9000))));
    }
    @Test public void purchasesAndResultsRetain1024ButRejectInvalidBounds() {
        for (int count : new int[] {1, 64, 65, 128, 1024, 0, -1, 1025, Integer.MAX_VALUE}) {
            RemoteNetwork.Buy read = decode(new RemoteNetwork.Buy(context(), count, 9, 5), new RemoteNetwork.Buy());
            assertEquals(count >= 1 && count <= 1024, read.valid);
        }
        assertFalse(decode(new RemoteNetwork.Buy(context(), 1, 0, 1), new RemoteNetwork.Buy()).valid);
        assertFalse(decode(new RemoteNetwork.Buy(context(), 1, 1, -1), new RemoteNetwork.Buy()).valid);
        RemoteNetwork.Feedback result = decode(new RemoteNetwork.Feedback(context(), 1,
                new TradeExecutor.Outcome(1024, TradeResultCode.SUCCESS), 1000), new RemoteNetwork.Feedback());
        assertTrue(result.valid); assertEquals(1024, result.completed);
        assertFalse(decode(new RemoteNetwork.Feedback(context(), 1, new TradeExecutor.Outcome(1025, TradeResultCode.SUCCESS), 0), new RemoteNetwork.Feedback()).valid);
    }
    @Test public void stateKeepsIntegerQuantitiesAndDoesNotMutateItsSource() {
        NBTTagCompound display = new NBTTagCompound(); display.setLong("OfferRevision", 3);
        int[] amounts = {0, 1, 64, 65, 127, 128, 256, 1024}; display.setIntArray("Quantities", amounts);
        RemoteNetwork.State read = decode(new RemoteNetwork.State(context(), display), new RemoteNetwork.State());
        assertTrue(read.valid); assertArrayEquals(amounts, read.data.getIntArray("Quantities"));
        assertArrayEquals(amounts, display.getIntArray("Quantities"));
    }
    @Test public void detailedProtectionFailuresRoundTripWithoutBecomingSuccess() {
        for (TradeResultCode code : TradeResultCode.values()) if (code.name().startsWith("PROTECTION_")) {
            RemoteNetwork.Feedback read = decode(new RemoteNetwork.Feedback(context(), 1,
                    new TradeExecutor.Outcome(0, code), 0), new RemoteNetwork.Feedback());
            assertTrue(read.valid); assertEquals(code, read.code); assertEquals(0, read.completed);
        }
    }
    @Test public void truncatedTrailingOversizedAndMalformedPacketsAreRejected() {
        RemoteNetwork.Open outgoing = new RemoteNetwork.Open(opening());
        ByteBuf bytes = Unpooled.buffer();
        try {
            outgoing.toBytes(bytes); bytes.writeByte(1);
            RemoteNetwork.Open trailing = new RemoteNetwork.Open(); trailing.fromBytes(bytes); assertFalse(trailing.valid);
            bytes.clear(); bytes.writeZero(257);
            RemoteNetwork.Open huge = new RemoteNetwork.Open(); huge.fromBytes(bytes); assertFalse(huge.valid);
            bytes.clear(); bytes.writeByte(0);
            RemoteNetwork.Buy shortPacket = new RemoteNetwork.Buy(); shortPacket.fromBytes(bytes); assertFalse(shortPacket.valid);
        } finally { bytes.release(); }
    }
    @Test public void openBrowseAndLocalRemoteBuysShareOneIngressBudget() {
        RequestBudget budget = new RequestBudget(); long now = 10_000_000_000L;
        assertTrue(budget.admitRemoteOpen(now).accepted); assertTrue(budget.admitRemoteOpen(now).accepted);
        assertFalse(budget.admitRemoteOpen(now).accepted);
        assertTrue(budget.admitDirectory(now).accepted); assertTrue(budget.admitDirectory(now).accepted);
        assertTrue(budget.admit(now, 600).accepted); // local
        assertTrue(budget.admit(now, 424).accepted); // remote
        assertFalse(budget.admit(now, 1).accepted);
        for (int i = 0; i < 34; i++) assertTrue(budget.admit(now, 0).accepted);
        assertFalse(budget.admit(now, 0).accepted);
        assertTrue(budget.admitRemoteOpen(now + 1_000_000_000L).accepted);
    }
}
