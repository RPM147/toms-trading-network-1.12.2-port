package com.tom.trading.directory;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DirectoryItemSearchTest {
    private MachineDirectoryEntry entry(OfferPreview preview) {
        return new MachineDirectoryEntry(new MachineAddress(0, 1, 64, 1), UUID.randomUUID(), UUID.randomUUID(),
                "Owner", "Unnamed shop", MachineDirectoryEntry.Evidence.OBSERVED, preview);
    }
    private OfferPreview preview(String saleName) {
        return new OfferPreview(Collections.singletonList(new OfferPreview.Item("coins:copper_coin", 0, 3, "Copper Coin", "coinCopper", false, false)),
                Collections.singletonList(new OfferPreview.Item("minecraft:iron_ingot", 0, 64, saleName, "", false, false)));
    }
    @Test public void searchesSalePaymentRegistryAndGroupsUsingRecordedNormalizedNames() {
        MachineDirectoryEntry e = entry(preview("DEMİR Külçesi"));
        for (String search : Arrays.asList("demir", "külçesi", "copper coin", "iron ingot", "minecraft:iron_ingot", "coincopper", "owner", "unnamed"))
            assertTrue(search, e.matchesSearch(DirectoryText.fold(search)));
        assertFalse(e.matchesSearch("diamond"));
    }
    @Test public void unknownPreviewDoesNotInventItemsAndRebuiltMetadataDropsOldNames() {
        assertFalse(entry(OfferPreview.UNKNOWN).matchesSearch("iron"));
        assertTrue(entry(preview("Iron Special")).matchesSearch("special"));
        assertFalse(entry(preview("New name")).matchesSearch("special"));
        assertTrue(entry(preview("")).matchesSearch("iron ingot"));
    }
    @Test public void itemSearchIsServerWideAndComposesWithSharedTab() {
        MachineDirectoryData data = MachineDirectoryData.create(); MachineDirectoryEntry e = entry(preview("Iron Ingot")); data.observe(e);
        List<DirectoryPage> answers = new ArrayList<>();
        DirectoryQuery query = new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, null, 0, "copper", DirectoryTab.ECONOMY);
        data.pager().submit(UUID.randomUUID(), query, 0, () -> true, answers::add); data.pager().tick(1, d -> true); assertEquals(0, answers.get(0).total);
        data.addSpecial(e.address, e.machineId); answers.clear();
        data.pager().submit(UUID.randomUUID(), query, 0, () -> true, answers::add); data.pager().tick(1, d -> true); assertEquals(1, answers.get(0).total);
    }
}
