package com.tom.trading;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.junit.Assert.*;

public class Phase7ResourcesTest {
    private InputStream resource(String path) {
        InputStream input = getClass().getResourceAsStream("/assets/toms_trading_network/" + path);
        assertNotNull(path, input);
        return input;
    }

    @Test public void languagesHaveMatchingKeysAndParameterCounts() throws Exception {
        Properties english = new Properties(), spanish = new Properties();
        try (InputStreamReader reader = new InputStreamReader(resource("lang/en_us.lang"), StandardCharsets.UTF_8)) {
            english.load(reader);
        }
        try (InputStreamReader reader = new InputStreamReader(resource("lang/es_mx.lang"), StandardCharsets.UTF_8)) {
            spanish.load(reader);
        }
        assertEquals(english.stringPropertyNames(), spanish.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            assertFalse(spanish.getProperty(key).isEmpty());
            assertEquals(key, english.getProperty(key).split("%s", -1).length,
                    spanish.getProperty(key).split("%s", -1).length);
        }
    }

    @Test public void recipeAndFilterModelUseLegacyResourceSyntax() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(resource("recipes/vending_machine.json"), StandardCharsets.UTF_8)) {
            JsonObject recipe = new JsonParser().parse(reader).getAsJsonObject();
            assertEquals("forge:ore_shaped", recipe.get("type").getAsString());
            assertEquals(3, recipe.getAsJsonArray("pattern").size());
            assertEquals("ingotIron", recipe.getAsJsonObject("key").getAsJsonObject("i").get("ore").getAsString());
            assertEquals("toms_trading_network:vending_machine", recipe.getAsJsonObject("result").get("item").getAsString());
            assertFalse(recipe.getAsJsonObject("result").has("id"));
        }
        try (InputStreamReader reader = new InputStreamReader(resource("models/item/tag_filter.json"), StandardCharsets.UTF_8)) {
            JsonObject model = new JsonParser().parse(reader).getAsJsonObject();
            assertEquals("minecraft:items/name_tag", model.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }

    @Test public void directoryTranslationsCoverTheNewTurkishScreenAndEveryState() throws Exception {
        Properties english = new Properties(), turkish = new Properties();
        try (InputStreamReader reader = new InputStreamReader(resource("lang/en_us.lang"), StandardCharsets.UTF_8)) { english.load(reader); }
        try (InputStreamReader reader = new InputStreamReader(resource("lang/tr_tr.lang"), StandardCharsets.UTF_8)) { turkish.load(reader); }
        for (String key : english.stringPropertyNames()) {
            if (!key.startsWith("gui.toms_trading_network.directory.") && !key.startsWith("key.")
                    && !key.startsWith("gui.toms_trading_network.remote.")
                    && !key.startsWith("gui.toms_trading_network.totals.")
                    && !key.startsWith("gui.toms_trading_network.book.")) continue;
            assertNotNull(key, turkish.getProperty(key));
            assertEquals(key, english.getProperty(key).split("%s", -1).length, turkish.getProperty(key).split("%s", -1).length);
        }
        for (com.tom.trading.directory.DirectoryPage.State state : com.tom.trading.directory.DirectoryPage.State.values())
            assertNotNull(english.getProperty("gui.toms_trading_network.directory.row." + state.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @Test public void commonBootstrapLoadsWithoutEitherOptionalApi() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertNull(loader.getResource("mezz/jei/api/IModPlugin.class"));
        assertNull(loader.getResource("mcjty/theoneprobe/api/ITheOneProbe.class"));
        assertNull(loader.getResource("com/minecolonies/coremod/MineColonies.class"));
        assertNull(loader.getResource("electroblob/wizardry/Wizardry.class"));
        assertNotNull(Class.forName("com.tom.trading.proxy.CommonProxy", true, loader).newInstance());
        assertNotNull(Class.forName("com.tom.trading.remote.RemoteAccess", true, loader));
        assertNotNull(Class.forName("com.tom.trading.container.ContainerRemoteTrading", true, loader));
    }

    @Test public void metadataDescribesImplementedGameplayNotTheOldScaffold() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/mcmod.info"), StandardCharsets.UTF_8)) {
            String description = new JsonParser().parse(reader).getAsJsonArray().get(0).getAsJsonObject()
                    .get("description").getAsString();
            assertFalse(description.contains("Phase 2"));
            assertFalse(description.contains("intentionally absent"));
            assertTrue(description.toLowerCase(java.util.Locale.ROOT).contains("vending"));
        }
    }
}
