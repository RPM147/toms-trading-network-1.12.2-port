package com.tom.trading.client;

import com.tom.trading.book.TradeBookBinding;
import com.tom.trading.network.TradeBookNetwork;
import net.minecraft.util.EnumHand;
import java.util.UUID;

/** Single outstanding book opening, bound to dimension, hand and the physical held bearer key. */
public final class TradeBookClientState {
    private static final long TIMEOUT = 15_000_000_000L, SPACING = 550_000_000L;
    private TradeBookNetwork.Open pending;
    private TradeBookBinding expected;
    private long deadline, next;
    private boolean sent;
    public TradeBookNetwork.Open begin(TradeBookBinding binding, int dimension, EnumHand hand, long now) {
        if (binding == null || (sent && now - next < 0) || (pending != null && now - deadline < 0
                && expected.equals(binding) && pending.dimension == dimension && pending.hand == hand)) return null;
        expected = binding; pending = new TradeBookNetwork.Open(UUID.randomUUID(), dimension, hand);
        deadline = now + TIMEOUT; next = now + SPACING; sent = true; return pending;
    }
    public boolean accept(TradeBookNetwork.Opened response, TradeBookBinding held, int dimension, long now) {
        if (pending == null || now - deadline >= 0 || !expected.equals(held) || dimension != pending.dimension
                || !pending.requestId.equals(response.request.requestId) || pending.dimension != response.request.dimension
                || pending.hand != response.request.hand || (response.result == TradeBookNetwork.Result.OK && !expected.equals(response.binding))) return false;
        pending = null; expected = null; return true;
    }
    public void clear() { pending = null; expected = null; sent = false; }
}
