package com.tom.trading.trade;

import com.tom.trading.BuildInfo;
import com.tom.trading.remote.RemoteSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** One bounded audit log per world/server session, independent of public chat announcements. */
public final class TradeTransactionLog {
    private static final long FILE_BYTES = 10L * 1024 * 1024;
    private static final int ARCHIVES = 5;
    private static MinecraftServer owner;
    private static RotatingTradeLog log;

    private TradeTransactionLog() {}

    public static void start(MinecraftServer server) {
        stop();
        if (!RemoteSettings.transactionLog || server == null) return;
        try {
            WorldServer world = server.getWorld(0);
            if (world == null) throw new IOException("Overworld is not available");
            Path folder = world.getSaveHandler().getWorldDirectory().getCanonicalFile().toPath()
                    .resolve("toms_trading_network");
            log = new RotatingTradeLog(folder, FILE_BYTES, ARCHIVES);
            owner = server;
        } catch (IOException | RuntimeException failure) {
            disable(failure);
        }
    }

    static void record(MinecraftServer server, TradeReceipt receipt) {
        if (!RemoteSettings.transactionLog || log == null || server == null || server != owner) return;
        try {
            log.append(TradeLogEntry.encode(receipt, Instant.now()));
        } catch (IOException | RuntimeException failure) {
            disable(failure);
        }
    }

    private static void disable(Exception failure) {
        LogManager.getLogger(BuildInfo.MOD_ID).error(
                "Transaction logging is disabled for this server session after an I/O/serialization failure. "
                        + "Trades remain enabled; fix the log path/disk and restart the server to resume logging.", failure);
        stop();
    }

    public static void stop() {
        RotatingTradeLog previous = log;
        log = null;
        owner = null;
        if (previous == null) return;
        try { previous.close(); }
        catch (IOException failure) {
            LogManager.getLogger(BuildInfo.MOD_ID).error("Could not close the transaction log", failure);
        }
    }
}
