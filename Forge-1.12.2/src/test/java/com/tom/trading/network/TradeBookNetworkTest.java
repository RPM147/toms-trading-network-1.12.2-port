package com.tom.trading.network;

import com.tom.trading.book.TradeBookBinding;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.EnumHand;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TradeBookNetworkTest {
    private TradeBookNetwork.Open query() { return new TradeBookNetwork.Open(UUID.randomUUID(), -7, EnumHand.OFF_HAND); }
    private TradeBookBinding book() { return new TradeBookBinding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()); }
    @Test public void fixedRequestsRejectTruncationTrailingBytesAndUnknownHands() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            new TradeBookNetwork.Open(query().requestId, 0, EnumHand.MAIN_HAND).toBytes(bytes);
            TradeBookNetwork.Open full = new TradeBookNetwork.Open(); full.fromBytes(bytes.duplicate()); assertTrue(full.valid);
            for (int size = 0; size < bytes.readableBytes(); size++) { TradeBookNetwork.Open cut = new TradeBookNetwork.Open(); cut.fromBytes(bytes.slice(0, size)); assertFalse(cut.valid); }
            bytes.setByte(20, 255); TradeBookNetwork.Open unknown = new TradeBookNetwork.Open(); unknown.fromBytes(bytes.duplicate()); assertFalse(unknown.valid);
            bytes.setByte(20, 0); bytes.writeByte(0); TradeBookNetwork.Open trailing = new TradeBookNetwork.Open(); trailing.fromBytes(bytes.duplicate()); assertFalse(trailing.valid);
        } finally { bytes.release(); }
    }
    @Test public void repliesRoundTripUtf8IdentityAndRejectEveryTruncationAndMalformedBoolean() {
        ByteBuf bytes = Unpooled.buffer(); TradeBookBinding book = book();
        try {
            new TradeBookNetwork.Opened(query(), book, TradeBookNetwork.Result.OK, Arrays.asList("Alıcı -> Satıcı | 64 Demir <- 3 Bakır", "工具 -> Owner | 1 Test <- 2 Coin")).toBytes(bytes);
            TradeBookNetwork.Opened full = new TradeBookNetwork.Opened(); full.fromBytes(bytes.duplicate()); assertTrue(full.valid); assertEquals(book, full.binding); assertEquals(2, full.rows.size());
            for (int size = 0; size < bytes.readableBytes(); size++) { TradeBookNetwork.Opened cut = new TradeBookNetwork.Opened(); cut.fromBytes(bytes.slice(0, size)); assertFalse(cut.valid); }
            bytes.setByte(21, 2); TradeBookNetwork.Opened booleanError = new TradeBookNetwork.Opened(); booleanError.fromBytes(bytes.duplicate()); assertFalse(booleanError.valid);
        } finally { bytes.release(); }
    }
    @Test public void oversizedAndInvalidHistoryCannotBecomeAReply() {
        for (List<String> rows : Arrays.asList(Collections.nCopies(101, "trade"), Collections.singletonList("forged\nrecord"), Collections.nCopies(10, String.join("", Collections.nCopies(4000, "ş"))))) {
            try { new TradeBookNetwork.Opened(query(), book(), TradeBookNetwork.Result.OK, rows); fail(); } catch (IllegalArgumentException expected) { }
        }
        ByteBuf bytes = Unpooled.buffer().writeZero(TradeBookNetwork.RESPONSE_BYTES + 1);
        try { TradeBookNetwork.Opened oversized = new TradeBookNetwork.Opened(); oversized.fromBytes(bytes); assertFalse(oversized.valid); } finally { bytes.release(); }
    }
}
