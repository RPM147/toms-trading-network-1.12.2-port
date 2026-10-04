package com.tom.trading.remote;

import com.tom.trading.BuildInfo;
import com.tom.trading.directory.DirectoryText;
import net.minecraftforge.common.config.Config;

/** Server values are authoritative. Hard ceilings cannot be expanded by malformed config values. */
@Config(modid = BuildInfo.MOD_ID)
public final class RemoteSettings {
    @Config.RequiresMcRestart
    @Config.Comment({"Server-wide display name of the administrator-selected directory tab (for example Server Shops).",
            "Blank keeps the translated Currency & Tax default. Sent by the server; client config cannot override it.",
            "Plain text only, limited to 64 Unicode characters. Restart the server/host after editing."})
    public static String specialTabName = "";
    @Config.RequiresMcRestart
    @Config.Comment("Allow trade-only remote access from the machine directory. Does not grant owner access.")
    public static boolean remoteTrading = true;
    @Config.RequiresMcRestart
    @Config.Comment("Announce completed local and remote player trades to all players. Respects their chat visibility setting.")
    public static boolean publicTradeAnnouncements = true;
    @Config.RangeInt(min = 1, max = 8) @Config.RequiresMcRestart
    public static int maxTargetChunks = 8;
    @Config.RangeInt(min = 1, max = 64) @Config.RequiresMcRestart
    @Config.Comment("Server-wide shared chunk budget, including pending target/protection reservations. Never generates missing terrain.")
    public static int maxLeasedChunks = 64;
    @Config.RangeInt(min = 5, max = 100) @Config.RequiresMcRestart
    public static int coldLoadIntervalTicks = 5;
    @Config.RangeInt(min = 5, max = 30) @Config.RequiresMcRestart
    public static int idleSeconds = 30;
    @Config.RangeInt(min = 30, max = 120) @Config.RequiresMcRestart
    public static int absoluteSeconds = 120;
    private RemoteSettings() {}
    public static String specialTabLabel() { return DirectoryText.label(specialTabName).trim(); }
    public static int chunks() { return Math.max(1, Math.min(8, maxTargetChunks)); }
    public static int leasedChunks() { return Math.max(1, Math.min(64, maxLeasedChunks)); }
    // Forge 1.12 scans public static fields, including final constants, unless explicitly ignored.
    @Config.Ignore
    public static final int MAX_CHUNKS_PER_TARGET = 25;
    public static int loadInterval() { return Math.max(5, Math.min(100, coldLoadIntervalTicks)); }
    public static long idleNanos() { return Math.max(5, Math.min(30, idleSeconds)) * 1_000_000_000L; }
    public static long absoluteNanos() { return Math.max(30, Math.min(120, absoluteSeconds)) * 1_000_000_000L; }
}
