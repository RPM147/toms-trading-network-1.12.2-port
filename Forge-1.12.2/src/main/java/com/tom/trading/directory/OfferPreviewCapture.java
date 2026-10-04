package com.tom.trading.directory;

import com.tom.trading.item.OreFilters;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.item.ItemStack;
import java.util.*;

/** Called only when observing a loaded machine, never while serving a catalogue query. */
public final class OfferPreviewCapture {
    private OfferPreviewCapture() {}
    public static OfferPreview capture(TileVendingMachine tile) {
        if (!tile.isDataVersionSupported()) return OfferPreview.UNKNOWN;
        long revision = tile.getOfferRevision();
        try {
            List<OfferPreview.Item> payment = new ArrayList<>(), sale = new ArrayList<>();
            for (int slot = 0; slot < TileVendingMachine.DEFINITION_SLOTS; slot++) {
                add(payment, tile.getPaymentTemplate(slot), tile.getPaymentQuantity(slot), tile.isMatchingNbt(slot));
                add(sale, tile.getSaleTemplate(slot), tile.getSaleQuantity(slot), tile.isMatchingNbt(slot + 4));
            }
            OfferPreview preview;
            try { preview = new OfferPreview(payment, sale); }
            catch (IllegalArgumentException oversized) {
                // Retain identities/counts/groups first; clients can resolve ordinary names locally.
                payment.replaceAll(OfferPreview.Item::withoutName); sale.replaceAll(OfferPreview.Item::withoutName);
                preview = new OfferPreview(payment, sale);
            }
            return tile.matchesOfferRevision(revision) && tile.isDataVersionSupported() ? preview : OfferPreview.UNKNOWN;
        } catch (RuntimeException | LinkageError unavailable) {
            // A broken optional item/capability cannot break chunk loading or expose a partial offer.
            return OfferPreview.UNKNOWN;
        }
    }
    private static void add(List<OfferPreview.Item> result, ItemStack template, int quantity, boolean matchNbt) {
        if (template.isEmpty()) return;
        String ore = OreFilters.oreName(template);
        ItemStack sample = OreFilters.isFilter(template) ? OreFilters.sampleOf(template) : template;
        if (sample.isEmpty() || sample.getItem().getRegistryName() == null) throw new IllegalArgumentException("No preview sample");
        String name = "";
        try { name = label(sample.getDisplayName()); }
        catch (RuntimeException | LinkageError unavailable) { /* Use the client registry name fallback. */ }
        result.add(new OfferPreview.Item(sample.getItem().getRegistryName().toString(), sample.getMetadata(), quantity,
                name, label(ore), sample.hasTagCompound(), matchNbt));
    }
    private static String label(String value) {
        if (value == null) return "";
        String bounded = value.substring(0, Math.min(512, value.length()));
        String result = DirectoryText.label(bounded);
        if (value.length() > bounded.length() || bounded.codePoints().filter(DirectoryText::allowed).count() > 64)
            return result.substring(0, result.offsetByCodePoints(0, Math.min(63, result.codePointCount(0, result.length())))) + "…";
        return result;
    }
}
