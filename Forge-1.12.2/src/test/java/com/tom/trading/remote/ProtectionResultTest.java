package com.tom.trading.remote;

import com.tom.trading.trade.TradeResultCode;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class ProtectionResultTest {
    @Test public void detailedDenialsArePreservedAndOldWireOrdinalsDoNotMove() {
        assertEquals(13, TradeResultCode.PROTECTION_UNAVAILABLE.ordinal()); assertEquals(6, RemoteOpenResult.DISABLED.ordinal());
        for (TradeResultCode code : TradeResultCode.values())
            if (code.name().startsWith("PROTECTION_")) assertEquals(code.name(), RemoteOpenResult.denied(code).name());
        assertEquals(RemoteOpenResult.UNAVAILABLE, RemoteOpenResult.denied(TradeResultCode.REMOTE_UNAVAILABLE));
        assertEquals(RemoteOpenResult.ACCESS_DENIED, RemoteOpenResult.denied(TradeResultCode.ACCESS_DENIED));
    }
    @Test public void everyNewOpenAndProtectionResultHasAllThreeTranslations() throws IOException {
        for (String locale : Arrays.asList("en_us", "tr_tr", "es_mx")) {
            Properties translations = new Properties();
            try (InputStream input = getClass().getResourceAsStream("/assets/toms_trading_network/lang/" + locale + ".lang")) {
                assertNotNull(input); translations.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
            for (RemoteOpenResult code : RemoteOpenResult.values()) if (code != RemoteOpenResult.OPENED)
                assertTrue(locale + " " + code, translations.containsKey("gui.toms_trading_network.directory.open_" + code.name().toLowerCase(Locale.ROOT)));
            for (TradeResultCode code : TradeResultCode.values()) if (code.name().startsWith("PROTECTION_"))
                assertTrue(locale + " " + code, translations.containsKey("gui.toms_trading_network.result." + code.name().toLowerCase(Locale.ROOT)));
        }
    }
}
