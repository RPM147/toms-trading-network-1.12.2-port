package com.tom.trading.remote;

import com.tom.trading.BuildInfo;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Exercises the exact Forge 2859 field-sync path from the startup crash, using only temporary config files. */
public class RemoteSettingsConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final Map<Field, Object> original = new HashMap<>();
    private Field minecraftHome;
    private Object originalHome;

    @Before public void rememberSettings() throws Exception {
        // Configuration needs Forge's game-directory bootstrap, isolated here to the temporary test folder.
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome"); minecraftHome.setAccessible(true);
        originalHome = minecraftHome.get(null); minecraftHome.set(null, temp.getRoot());
        for (Field field : RemoteSettings.class.getFields())
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) original.put(field, field.get(null));
    }
    @After public void restoreSettings() throws Exception {
        for (Map.Entry<Field, Object> entry : original.entrySet()) entry.getKey().set(null, entry.getValue());
        if (minecraftHome != null) minecraftHome.set(null, originalHome);
    }
    private void sync(Configuration config, boolean loading) throws Exception {
        sync(config, RemoteSettings.class, loading);
    }
    private void sync(Configuration config, Class<?> configClass, boolean loading) throws Exception {
        // Test-only invocation of the same internal routine called by FMLModContainer -> ConfigManager.sync.
        // The public wrapper requires a running Loader/ASM mod registry and would otherwise skip this class.
        Method sync = ConfigManager.class.getDeclaredMethod("sync", Configuration.class, Class.class,
                String.class, String.class, boolean.class, Object.class);
        sync.setAccessible(true);
        try { sync.invoke(null, config, configClass, BuildInfo.MOD_ID, "general", loading, null); }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception) throw (Exception) ex.getCause();
            throw ex;
        }
    }
    private static int actualHardLimit() throws Exception {
        return RemoteSettings.class.getField("MAX_CHUNKS_PER_TARGET").getInt(null); // Do not rely on a javac-inlined constant.
    }

    public static final class BrokenPort9Config {
        public static final int MAX_CHUNKS_PER_TARGET = 25;
    }

    @Test public void exactForgeSyncReproducesThePort9IllegalAccessWithoutIgnore() throws Exception {
        Configuration config = new Configuration(new File(temp.getRoot(), "port9-reproducer.cfg"));
        try { sync(config, BrokenPort9Config.class, true); fail("Unignored final field must reproduce the startup failure"); }
        catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("MAX_CHUNKS_PER_TARGET"));
            Throwable cause = expected;
            while (cause.getCause() != null) cause = cause.getCause();
            assertTrue(cause instanceof IllegalAccessException);
            assertTrue(cause.getMessage().contains("static final int"));
        }
    }

    @Test public void firstStartupDoesNotSynchronizeTheHardLimitAsAConfigProperty() throws Exception {
        Configuration config = new Configuration(new File(temp.getRoot(), "fresh.cfg"));
        sync(config, true);
        assertEquals(25, actualHardLimit());
        assertFalse(config.hasKey("general", "MAX_CHUNKS_PER_TARGET"));
        assertTrue(config.hasKey("general", "maxLeasedChunks"));
        assertTrue(config.hasKey("general", "remoteTrading"));
        assertEquals("", config.get("general", "specialTabName", "").getString());
    }

    @Test public void savedUserSettingsSurviveStartupAndSubsequentResync() throws Exception {
        File file = new File(temp.getRoot(), "existing.cfg");
        Configuration config = new Configuration(file);
        config.get("general", "remoteTrading", false).set(false);
        config.get("general", "maxTargetChunks", 3).set(3);
        config.get("general", "maxLeasedChunks", 37).set(37);
        config.get("general", "coldLoadIntervalTicks", 10).set(10);
        config.save();
        Configuration reloaded = new Configuration(file); reloaded.load();
        sync(reloaded, true); sync(reloaded, false);
        assertFalse(RemoteSettings.remoteTrading); assertEquals(3, RemoteSettings.chunks());
        assertEquals(37, RemoteSettings.leasedChunks()); assertEquals(10, RemoteSettings.loadInterval());
        assertEquals(25, actualHardLimit());
    }

    @Test public void strayHardLimitConfigEntryCannotChangeTheSecurityCeilingOrCrashStartup() throws Exception {
        Configuration config = new Configuration(new File(temp.getRoot(), "stale.cfg"));
        config.get("general", "MAX_CHUNKS_PER_TARGET", 999999).set(999999);
        sync(config, true); sync(config, false);
        assertEquals(25, actualHardLimit());
    }

    @Test public void customTabNameSurvivesRealForgeSaveLoadAndResync() throws Exception {
        File file = new File(temp.getRoot(), "custom-tab.cfg");
        Configuration config = new Configuration(file);
        config.get("general", "specialTabName", "").set("Sunucu Mağazaları — Shops");
        config.save();
        Configuration reloaded = new Configuration(file); reloaded.load();
        sync(reloaded, true); sync(reloaded, false);
        assertEquals("Sunucu Mağazaları — Shops", RemoteSettings.specialTabLabel());
        assertTrue(RemoteSettings.class.getField("specialTabName").isAnnotationPresent(Config.RequiresMcRestart.class));
    }

    @Test public void tabNameSanitizesControlsBoundsUnicodeAndKeepsBlankFallback() {
        for (String value : new String[]{null, "", " \n\t\u202E\u00a7 "}) {
            RemoteSettings.specialTabName = value; assertEquals("", RemoteSettings.specialTabLabel());
        }
        RemoteSettings.specialTabName = "  Server\n Shops\u202E\uD800  ";
        assertEquals("Server Shops", RemoteSettings.specialTabLabel());
        RemoteSettings.specialTabName = String.join("", java.util.Collections.nCopies(65, "\uD83D\uDED2"));
        String label = RemoteSettings.specialTabLabel();
        assertEquals(64, label.codePointCount(0, label.length()));
        assertTrue(com.tom.trading.directory.DirectoryText.valid(label));
    }

    @Test public void everyPublicStaticFinalFieldIsExcludedFromForgeConfigDiscovery() {
        for (Field field : RemoteSettings.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers))
                assertTrue(field.getName() + " must be @Config.Ignore", field.isAnnotationPresent(Config.Ignore.class));
        }
    }
}
