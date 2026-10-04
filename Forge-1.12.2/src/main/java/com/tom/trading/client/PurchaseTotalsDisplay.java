package com.tom.trading.client;

import com.tom.trading.container.MachineView;
import com.tom.trading.directory.DirectoryText;
import com.tom.trading.item.OreFilters;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared presentation for physical and remote shopping; definition icons remain per-trade quantities. */
@SideOnly(Side.CLIENT)
final class PurchaseTotalsDisplay {
    static final int ROW_Y = 51;
    private static final String PREFIX = "gui.toms_trading_network.totals.";
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);

    private PurchaseTotalsDisplay() {}

    static PurchaseTotals snapshot(MachineView view, String batch) {
        int[] quantities = new int[8];
        if (view.offerRevision > 0) for (int slot = 0; slot < quantities.length; slot++) {
            if (!view.templates[slot].isEmpty()) quantities[slot] = view.quantities[slot];
        }
        return PurchaseTotals.calculate(batch, quantities);
    }

    static String compact(PurchaseTotals totals, boolean delivery) {
        // Fits within each 72-pixel offer column even at four maximum-sized definitions and batch 1024.
        return totals.valid() ? "=" + number(totals.total(delivery)) : "=-";
    }

    static List<String> details(MachineView view, PurchaseTotals totals, FontRenderer font, int screenWidth) {
        int maxWidth = tooltipWidth(screenWidth);
        List<String> lines = new ArrayList<>();
        lines.add(fit(font, tr("title", totals.batch()), maxWidth));
        if (!totals.valid()) {
            lines.add(fit(font, tr(totals.batch() == 0 ? "invalid_batch" : "unavailable"), maxWidth));
        } else {
            for (boolean delivery : new boolean[]{false, true}) {
                lines.add(fit(font, tr(delivery ? "receive" : "pay"), maxWidth));
                for (int slot = delivery ? 4 : 0, end = slot + 4; slot < end; slot++) {
                    if (totals.quantity(slot) > 0) lines.add(fit(font,
                            "  " + tr("line", number(totals.quantity(slot)), label(view.templates[slot])), maxWidth));
                }
            }
            // A maximum of eight single-line definitions keeps this readable at 320x240 GUI scale.
            // Full item names/NBT tooltips remain on their individual icons.
            lines.add(fit(font, tr("counts"), maxWidth));
            List<String> note = font.listFormattedStringToWidth(tr("preview"), maxWidth);
            for (int line = 0; line < Math.min(2, note.size()); line++)
                lines.add(fit(font, note.get(line) + (line == 1 && note.size() > 2 ? "..." : ""), maxWidth));
        }
        lines.add(fit(font, tr("hint"), maxWidth));
        return lines;
    }

    static void appendItem(List<String> lines, MachineView view, int slot, PurchaseTotals totals) {
        lines.add(I18n.format("gui.toms_trading_network.required_count", view.quantities[slot]));
        if (totals.valid()) lines.add(tr("item", number(totals.quantity(slot)), totals.batch()));
        else lines.add(tr(totals.batch() == 0 ? "invalid_batch" : "unavailable"));
        String ore = OreFilters.oreName(view.templates[slot]);
        if (!ore.isEmpty()) lines.add(I18n.format("gui.toms_trading_network.ore_group", ore));
        lines.add(tr("hint"));
    }

    private static String label(ItemStack item) {
        String ore = OreFilters.oreName(item);
        if (!ore.isEmpty()) return I18n.format("gui.toms_trading_network.ore_group", ore);
        try {
            return DirectoryText.label(item.getDisplayName());
        } catch (RuntimeException | LinkageError ignored) {
            // A broken display-name callback must not make the new quote tooltip crash the client.
            ResourceLocation id = item.getItem().getRegistryName();
            return id == null ? "?" : DirectoryText.label(id.toString());
        }
    }
    private static String fit(FontRenderer font, String text, int maxWidth) {
        if (font.getStringWidth(text) <= maxWidth) return text;
        return font.trimStringToWidth(text, Math.max(0, maxWidth - font.getStringWidth("..."))) + "...";
    }
    /** Anchor the complete quote in the middle so Forge cannot re-wrap it into an over-tall narrow mouse tooltip. */
    static int tooltipX(int screenWidth) { return Math.max(4, (screenWidth - tooltipWidth(screenWidth)) / 2 - 12); }
    private static int tooltipWidth(int screenWidth) { return Math.max(80, Math.min(400, screenWidth - 40)); }
    private static String number(long value) { return NUMBERS.format(value); }
    private static String tr(String key, Object... args) { return I18n.format(PREFIX + key, args); }
}
