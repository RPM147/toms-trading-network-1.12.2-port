package com.tom.trading.trade;

import com.tom.trading.BuildInfo;
import com.tom.trading.remote.RemoteSettings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import org.apache.logging.log4j.LogManager;

import java.util.function.Supplier;

/** The single local/remote post-record publication route. No client-supplied receipt fields. */
public final class TradeCompletion {
    private TradeCompletion() {}

    public static TradeExecutor.Outcome execute(EntityPlayerMP player, TradeRequestLedger ledger,
                                                long id, int count, long revision,
                                                Supplier<TradeExecutor.Outcome> operation) {
        return ledger.execute(id, count, revision, operation, receipt -> publish(player.getServer(), receipt));
    }

    private static void publish(MinecraftServer server, TradeReceipt receipt) {
        if (!RemoteSettings.publicTradeAnnouncements || server == null) return;
        ITextComponent message = receipt.createAnnouncement();
        SPacketChat packet = new SPacketChat(message, ChatType.CHAT);
        // PlayerList spans all dimensions; vanilla NetHandlerPlayServer respects chat visibility.
        // Isolate recipients so a broken/disconnected connection cannot suppress the others.
        for (EntityPlayerMP recipient : server.getPlayerList().getPlayers()) {
            try { recipient.connection.sendPacket(packet); }
            catch (RuntimeException | LinkageError failure) {
                LogManager.getLogger(BuildInfo.MOD_ID).warn(
                        "Committed trade announcement could not reach player {}", recipient.getUniqueID(), failure);
            }
        }
        server.sendMessage(message);
    }
}
