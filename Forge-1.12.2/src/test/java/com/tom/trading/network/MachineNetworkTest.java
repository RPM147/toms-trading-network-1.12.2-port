package com.tom.trading.network;

import com.tom.trading.trade.TradeResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class MachineNetworkTest {
    @Test
    public void oreFilterRequestIsPaymentOnlyBoundedAndControlFree() {
        MachineNetwork.SetOreFilter request = new MachineNetwork.SetOreFilter();
        request.slot = 3; request.oreName = "ingotIron";
        assertTrue(decode(request, new MachineNetwork.SetOreFilter()).valid);
        request.oreName = ""; // Explicit return to exact sample matching.
        assertTrue(decode(request, new MachineNetwork.SetOreFilter()).valid);
        request.slot = 4;
        assertFalse(decode(request, new MachineNetwork.SetOreFilter()).valid);
        request.slot = 0; request.oreName = "ingot\nIron";
        assertFalse(decode(request, new MachineNetwork.SetOreFilter()).valid);
        request.oreName = String.join("", java.util.Collections.nCopies(129, "x"));
        assertFalse(decode(request, new MachineNetwork.SetOreFilter()).valid);
    }

    @Test
    public void ghostRequestAcceptsOnlyBoundedRegistryIdentityAndConcreteMetadata() {
        MachineNetwork.SetGhostTemplate request = new MachineNetwork.SetGhostTemplate();
        request.slot = 7; request.itemId = "minecraft:stone"; request.metadata = 1;
        assertTrue(decode(request, new MachineNetwork.SetGhostTemplate()).valid);
        request.metadata = 32767;
        assertFalse(decode(request, new MachineNetwork.SetGhostTemplate()).valid);
        request.metadata = -1;
        assertFalse(decode(request, new MachineNetwork.SetGhostTemplate()).valid);
        request.metadata = 0; request.slot = 8;
        assertFalse(decode(request, new MachineNetwork.SetGhostTemplate()).valid);
        request.slot = 0; request.itemId = "minecraft:stone\n";
        assertFalse(decode(request, new MachineNetwork.SetGhostTemplate()).valid);
        request.itemId = "minecraft:stone"; context(request);
        ByteBuf bytes = Unpooled.buffer();
        try {
            request.toBytes(bytes); bytes.writeByte(1); // No extra item/NBT payload is accepted.
            MachineNetwork.SetGhostTemplate extra = new MachineNetwork.SetGhostTemplate();
            extra.fromBytes(bytes); assertFalse(extra.valid);
        } finally { bytes.release(); }
    }

    @Test
    public void displayQuantitiesRoundTripAsIntegersIncluding1024() {
        MachineNetwork.State state = new MachineNetwork.State();
        state.context.window = 4;
        state.data.setLong("OfferRevision", 7);
        int[] counts = {0, 1, 64, 127, 128, 255, 256, 1024};
        state.data.setIntArray("Quantities", counts);
        ByteBuf bytes = Unpooled.buffer();
        try {
            state.toBytes(bytes);
            MachineNetwork.State decoded = new MachineNetwork.State();
            decoded.fromBytes(bytes);
            assertTrue(decoded.valid);
            assertArrayEquals(counts, decoded.data.getIntArray("Quantities"));
        } finally { bytes.release(); }
    }
    private static void context(MachineNetwork.Request request) {
        request.context.window = 4;
        request.context.dimension = -1;
        request.context.position = new BlockPos(5, 64, -8);
        request.context.session = 123456;
    }

    private static <T extends MachineNetwork.Request> T decode(MachineNetwork.Request outgoing, T incoming) {
        context(outgoing);
        ByteBuf bytes = Unpooled.buffer();
        try { outgoing.toBytes(bytes); incoming.fromBytes(bytes); return incoming; }
        finally { bytes.release(); }
    }

    @Test
    public void quantityAndBatchBoundsRejectOverflowAndNegativeInputs() {
        for (int count : new int[] {1, 64, 127, 128, 255, 256, 1024}) {
            MachineNetwork.SetQuantity request = new MachineNetwork.SetQuantity();
            request.slot = 7; request.count = count;
            assertTrue(decode(request, new MachineNetwork.SetQuantity()).valid);
        }
        for (int count : new int[] {Integer.MIN_VALUE, -1, 0, 1025, Integer.MAX_VALUE}) {
            MachineNetwork.SetQuantity request = new MachineNetwork.SetQuantity();
            request.count = count;
            assertFalse(decode(request, new MachineNetwork.SetQuantity()).valid);
        }
        for (int count : new int[] {Integer.MIN_VALUE, -1, 0, 1025, Integer.MAX_VALUE}) {
            MachineNetwork.Trade request = new MachineNetwork.Trade();
            request.offerRevision = 1;
            request.requestId = 1;
            request.count = count;
            assertFalse(decode(request, new MachineNetwork.Trade()).valid);
        }
        for (int count : new int[] {1, 64, 65, 127, 128, 255, 256, 1024}) {
            MachineNetwork.Trade trade = new MachineNetwork.Trade();
            trade.offerRevision = 1;
            trade.requestId = 1;
            trade.count = count;
            MachineNetwork.Trade decoded = decode(trade, new MachineNetwork.Trade());
            assertTrue(decoded.valid);
            assertEquals(count, decoded.count);
        }
    }

    @Test
    public void feedbackPreservesLargeCompletedCountsAndRejectsOverflow() {
        for (int count : new int[] {0, 1, 64, 65, 127, 128, 255, 256, 1024, -1, 1025, Integer.MAX_VALUE}) {
            MachineNetwork.Feedback feedback = new MachineNetwork.Feedback();
            feedback.context.window = 4;
            feedback.completed = count;
            feedback.code = TradeResultCode.SUCCESS;
            ByteBuf bytes = Unpooled.buffer();
            try {
                feedback.toBytes(bytes);
                MachineNetwork.Feedback decoded = new MachineNetwork.Feedback();
                decoded.fromBytes(bytes);
                assertEquals(count >= 0 && count <= 1024, decoded.valid);
                if (decoded.valid) {
                    assertEquals(count, decoded.completed);
                    assertEquals(TradeResultCode.SUCCESS, decoded.code);
                }
            } finally { bytes.release(); }
        }
    }

    @Test
    public void malformedPacketsAndForbiddenFacesAreRejected() {
        MachineNetwork.SetTemplate request = new MachineNetwork.SetTemplate();
        request.slot = 8;
        assertFalse(decode(request, new MachineNetwork.SetTemplate()).valid);
        MachineNetwork.SetSideMode side = new MachineNetwork.SetSideMode();
        side.face = 2; side.mode = 3;
        assertFalse(decode(side, new MachineNetwork.SetSideMode()).valid);
        side.face = 5; side.mode = 4;
        assertFalse(decode(side, new MachineNetwork.SetSideMode()).valid);

        request.slot = 0; context(request);
        ByteBuf valid = Unpooled.buffer();
        try {
            request.toBytes(valid);
            for (int length = 0; length < valid.readableBytes(); length++) {
                MachineNetwork.SetTemplate truncated = new MachineNetwork.SetTemplate();
                truncated.fromBytes(valid.slice(0, length));
                assertFalse(truncated.valid);
            }
            valid.setByte(valid.writerIndex() - 1, 2);
            MachineNetwork.SetTemplate invalidBoolean = new MachineNetwork.SetTemplate();
            invalidBoolean.fromBytes(valid.duplicate());
            assertFalse(invalidBoolean.valid);
            valid.setByte(valid.writerIndex() - 1, 0);
            valid.writeByte(42);
            MachineNetwork.SetTemplate trailing = new MachineNetwork.SetTemplate();
            trailing.fromBytes(valid.duplicate());
            assertFalse(trailing.valid);
        } finally { valid.release(); }
        ByteBuf large = Unpooled.buffer(301).writeZero(301);
        try {
            MachineNetwork.Trade oversized = new MachineNetwork.Trade();
            oversized.fromBytes(large);
            assertFalse(oversized.valid);
        } finally { large.release(); }
    }

    @Test
    public void namesAreBoundedByCharactersAndRejectControlFormatting() {
        MachineNetwork.SetCustomName name = new MachineNetwork.SetCustomName();
        name.name = new String(new char[64]).replace('\0', 'x');
        assertTrue(decode(name, new MachineNetwork.SetCustomName()).valid);
        name.name += "x";
        assertFalse(decode(name, new MachineNetwork.SetCustomName()).valid);
        name.name = "machine\ncommand";
        assertFalse(decode(name, new MachineNetwork.SetCustomName()).valid);
        name.name = "\u00a7khidden";
        assertFalse(decode(name, new MachineNetwork.SetCustomName()).valid);
        name.name = "Alışveriş";
        assertTrue(decode(name, new MachineNetwork.SetCustomName()).valid);
    }

    @Test
    public void staleSessionWindowDimensionOrPositionCannotMatch() {
        MachineNetwork.Context expected = new MachineNetwork.Context();
        expected.window = 4; expected.dimension = 2;
        expected.position = new BlockPos(4, 64, 5); expected.session = 100;
        MachineNetwork.Context request = new MachineNetwork.Context();
        request.window = 4; request.dimension = 2;
        request.position = expected.position; request.session = 100;
        assertTrue(request.matches(expected, true));
        request.window++;
        assertFalse(request.matches(expected, true));
        request.window--; request.dimension++;
        assertFalse(request.matches(expected, true));
        request.dimension--; request.position = request.position.up();
        assertFalse(request.matches(expected, true));
        request.position = expected.position; request.session--;
        assertFalse(request.matches(expected, true));
        assertTrue(request.matches(expected, false)); // Only read-only bootstrap omits the session check.
    }

    @Test
    public void playerBudgetBoundsQueueAndTradesAndRecoversNextSecond() {
        RequestBudget budget = new RequestBudget();
        long now = 10_000_000_000L;
        for (int i = 0; i < 4; i++) assertTrue(budget.accept(now, 64));
        assertFalse(budget.accept(now, 64));
        for (int i = 4; i < 40; i++) assertTrue(budget.accept(now, 0));
        assertFalse(budget.accept(now, 0));
        assertTrue(budget.accept(now + 1_000_000_000L, 64));
    }

    @Test
    public void playerBudgetChargesRequestedUnitsBeforeQueueingAndResets() {
        RequestBudget budget = new RequestBudget();
        long now = 10_000_000_000L;
        for (int count : new int[] {-1, 1025, Integer.MAX_VALUE}) assertFalse(budget.accept(now, count));
        assertTrue(budget.accept(now, 768));
        assertFalse(budget.accept(now, 512)); // Rejected work must not consume the remaining budget.
        assertTrue(budget.accept(now, 256));
        assertFalse(budget.accept(now, 1));
        assertTrue(budget.accept(now, 0)); // Configuration remains usable after a large trade.
        assertFalse(budget.accept(now + 999_999_999L, 1));
        assertTrue(budget.accept(now + 1_000_000_000L, 1024));
        assertFalse(budget.accept(now + 1_000_000_000L, 1));
        assertTrue(budget.accept(now + 2_000_000_000L, 1024));
    }

    @Test public void exhaustedBudgetProvidesBoundedRejectionNoticesAndRetryDelay() {
        RequestBudget budget = new RequestBudget();
        long now = 10_000_000_000L;
        RequestBudget.Admission first = budget.admit(now, 1024);
        assertTrue(first.accepted);
        assertEquals(1000, first.retryAfterMillis);
        for (int i = 0; i < 100; i++) {
            RequestBudget.Admission rejected = budget.admit(now + 300_000_000L, 1);
            assertFalse(rejected.accepted);
            assertEquals(i < 4, rejected.notifyRejection);
            assertEquals(700, rejected.retryAfterMillis);
        }
        assertTrue(budget.admit(now + 1_000_000_000L, 1024).accepted);
        assertTrue(budget.admit(now + 1_000_000_000L, 1).notifyRejection);
    }

    @Test public void offerRevisionAndRequestIdAreRequiredAndRoundTripWithoutTruncation() {
        MachineNetwork.Trade trade = new MachineNetwork.Trade();
        trade.count = 1024; trade.offerRevision = Long.MAX_VALUE - 1; trade.requestId = Long.MAX_VALUE;
        MachineNetwork.Trade read = decode(trade, new MachineNetwork.Trade());
        assertTrue(read.valid);
        assertEquals(trade.offerRevision, read.offerRevision);
        assertEquals(trade.requestId, read.requestId);
        for (long bad : new long[] {0, -1, Long.MIN_VALUE}) {
            trade.offerRevision = bad;
            assertFalse(decode(trade, new MachineNetwork.Trade()).valid);
            trade.offerRevision = 1; trade.requestId = bad;
            assertFalse(decode(trade, new MachineNetwork.Trade()).valid);
            trade.requestId = 1;
        }
    }

    @Test public void stateRequiresAnOfferRevisionAndFeedbackRejectsEveryTruncation() {
        for (long revision : new long[] {0, -1, 1, Long.MAX_VALUE}) {
            MachineNetwork.State state = new MachineNetwork.State();
            state.context.window = 1; state.data.setLong("OfferRevision", revision);
            ByteBuf bytes = Unpooled.buffer();
            try {
                state.toBytes(bytes);
                MachineNetwork.State decoded = new MachineNetwork.State(); decoded.fromBytes(bytes);
                assertEquals(revision > 0, decoded.valid);
            } finally { bytes.release(); }
        }
        MachineNetwork.Feedback feedback = new MachineNetwork.Feedback();
        feedback.context.window = 1; feedback.requestId = 7;
        feedback.retryAfterMillis = 1000; feedback.code = TradeResultCode.OFFER_CHANGED;
        ByteBuf bytes = Unpooled.buffer();
        try {
            feedback.toBytes(bytes);
            for (int length = 0; length < bytes.readableBytes(); length++) {
                MachineNetwork.Feedback cut = new MachineNetwork.Feedback(); cut.fromBytes(bytes.slice(0, length));
                assertFalse("prefix " + length, cut.valid);
            }
            bytes.writeByte(0);
            MachineNetwork.Feedback extra = new MachineNetwork.Feedback(); extra.fromBytes(bytes);
            assertFalse(extra.valid);
        } finally { bytes.release(); }
    }

    @Test public void feedbackCorrelatesTheRequestAndRejectsUnboundedWaits() {
        for (int delay : new int[] {-1, 0, 1, 700, 1000, 1001, Integer.MAX_VALUE}) {
            MachineNetwork.Feedback feedback = new MachineNetwork.Feedback();
            feedback.context.window = 1; feedback.requestId = Long.MAX_VALUE;
            feedback.code = TradeResultCode.RATE_LIMITED; feedback.retryAfterMillis = delay;
            ByteBuf bytes = Unpooled.buffer();
            try {
                feedback.toBytes(bytes);
                MachineNetwork.Feedback read = new MachineNetwork.Feedback();
                read.fromBytes(bytes);
                assertEquals(delay >= 0 && delay <= 1000, read.valid);
                if (read.valid) {
                    assertEquals(feedback.requestId, read.requestId);
                    assertEquals(delay, read.retryAfterMillis);
                    assertEquals(TradeResultCode.RATE_LIMITED, read.code);
                }
            } finally { bytes.release(); }
        }
    }
}
