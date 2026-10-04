package com.tom.trading.client;

import com.tom.trading.directory.*;
import com.tom.trading.network.DirectoryNetwork;
import com.tom.trading.network.RemoteNetwork;
import com.tom.trading.remote.RemoteOpenRequest;
import com.tom.trading.remote.RemoteOpenResult;
import com.tom.trading.container.ContainerRemoteTrading;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.util.*;

/** Metadata list and correlated trade-only opening. Never looks up a client target TileEntity. */
@SideOnly(Side.CLIENT)
public final class GuiMachineDirectory extends GuiScreen {
    private static final String PREFIX = "gui.toms_trading_network.directory.";
    private DirectoryClientState state;
    private INetHandler connection;
    private GuiTextField search;
    private Entries entries;
    private DirectoryPage renderedPage;
    private int panelLeft, panelWidth, listTop, listBottom;
    private GuiButton previous, next, refresh, allTab, economyTab;
    private final DirectoryTab initialTab;
    private final DirectoryClientState.Bookmark returnBookmark;
    private final INetHandler bookmarkConnection;
    private int focus = -1, detailPage;
    private boolean details;
    private List<String> cachedDetails;
    private String cachedFeedback;
    private DirectoryPage cachedDetailsPage;
    private DirectoryPage.Row cachedDetailsSelection;
    private int cachedDetailsWidth;
    private final RemoteOpenTracker opening = new RemoteOpenTracker();
    private String openStatus = "open_hint";
    private DirectoryPreviewLayout previewLayout;
    private final Map<OfferPreview.Item, ItemStack> previewIcons = new HashMap<>();
    private OfferPreview.Item hoveredPreview;
    private boolean hoveredSale;
    private int previewMatrixDepth;

    public GuiMachineDirectory() { this(DirectoryTab.ALL); }
    public GuiMachineDirectory(DirectoryTab tab) { initialTab = Objects.requireNonNull(tab); returnBookmark = null; bookmarkConnection = null; }
    public GuiMachineDirectory(DirectoryClientState.Bookmark bookmark, INetHandler connection) {
        initialTab = DirectoryTab.ALL; returnBookmark = bookmark; bookmarkConnection = connection;
    }

    private static String tr(String key, Object... args) { return I18n.format(PREFIX + key, args); }
    private static String machineName(MachineDirectoryEntry entry) {
        return entry.name.isEmpty() ? I18n.format("tile.toms_trading_network.vending_machine.name") : entry.name;
    }
    private static String ownerName(MachineDirectoryEntry entry) {
        if (entry.ownerId == null) return tr("ownerless");
        return entry.ownerName.isEmpty() ? entry.ownerId.toString().substring(0, 8) : entry.ownerName;
    }
    private static String status(DirectoryPage.Row row) { return tr("row." + row.state.name().toLowerCase(Locale.ROOT)); }
    private String specialTabName() {
        return state.specialTabName().isEmpty() ? tr("tab.economy") : state.specialTabName();
    }
    private String fit(String value, int width) {
        if (width <= 0) return "";
        return fontRenderer.getStringWidth(value) <= width ? value
                : fontRenderer.trimStringToWidth(value, Math.max(0, width - fontRenderer.getStringWidth("..."))) + "...";
    }
    private void text(String value, int x, int y, int width, int color) {
        if (width <= 0) return;
        fontRenderer.drawString(fit(value, width), x, y, color);
    }
    @Override public void initGui() {
        if (mc.player == null || mc.world == null || mc.getConnection() == null) { mc.displayGuiScreen(null); return; }
        if (state == null) {
            state = new DirectoryClientState(mc.player.dimension, System.nanoTime());
            state.setTab(initialTab, System.nanoTime()); connection = mc.getConnection();
            if (connection == bookmarkConnection) state.restore(returnBookmark, System.nanoTime());
        }
        int oldScroll = entries == null ? 0 : entries.getAmountScrolled();
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.min(700, width - 24); panelLeft = (width - panelWidth) / 2;
        previewLayout = new DirectoryPreviewLayout(panelWidth - 24);
        listTop = 94; listBottom = Math.max(listTop + 36, height - 76);
        search = new GuiTextField(0, fontRenderer, panelLeft, 54, panelWidth, 18);
        search.setMaxStringLength(128); search.setValidator(DirectoryText::valid); search.setText(state.search());
        search.setFocused(focus == -1);
        entries = new Entries();
        renderedPage = null; entries.scrollBy(oldScroll);
        buttonList.clear();
        int tabWidth = (panelWidth - 4) / 2;
        allTab = new GuiButton(5, panelLeft, 28, tabWidth, 20, tr("tab.all"));
        economyTab = new GuiButton(6, panelLeft + tabWidth + 4, 28, panelWidth - tabWidth - 4, 20, tr("tab.economy"));
        buttonList.add(allTab); buttonList.add(economyTab);
        previous = new GuiButton(1, panelLeft, height - 26, 54, 20, tr("previous"));
        next = new GuiButton(2, panelLeft + 58, height - 26, 54, 20, tr("next"));
        refresh = new GuiButton(3, panelLeft + panelWidth - 138, height - 26, 72, 20, tr("refresh"));
        buttonList.add(previous); buttonList.add(next); buttonList.add(refresh);
        buttonList.add(new GuiButton(4, panelLeft + panelWidth - 62, height - 26, 62, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(7, panelLeft + panelWidth - 90, 75, 90, 16, tr("details")));
        buttonList.add(new GuiButton(8, width / 2 - 40, height - 36, 80, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(9, panelLeft + 8, height - 36, 70, 20, tr("previous")));
        buttonList.add(new GuiButton(10, panelLeft + panelWidth - 78, height - 36, 70, 20, tr("next")));
        updateButtons();
    }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void updateScreen() {
        if (state == null) return;
        if (mc.player == null || mc.world == null || mc.getConnection() != connection
                || mc.player.dimension != state.dimension || !mc.player.isEntityAlive() || mc.player.isSpectator()) {
            mc.displayGuiScreen(null); return;
        }
        search.updateCursorCounter();
        RemoteOpenRequest expired = opening.expire(System.nanoTime());
        if (expired != null) { RemoteNetwork.cancel(expired); openStatus = "open_timeout"; }
        DirectoryQuery query = state.poll(System.nanoTime());
        if (query != null) DirectoryNetwork.request(query);
        updateButtons();
    }
    public boolean receiveOpen(RemoteNetwork.Opened response) {
        if (state == null || !opening.accept(response.request)) return false;
        if (response.result == RemoteOpenResult.OPENED) {
            ContainerRemoteTrading container = new ContainerRemoteTrading(mc.player, response.context, null);
            mc.player.openContainer = container;
            mc.displayGuiScreen(new GuiRemoteTrading(container, state.bookmark(entries.getAmountScrolled()), connection));
        } else {
            openStatus = "open_" + response.result.name().toLowerCase(Locale.ROOT);
            if (response.result == RemoteOpenResult.STALE) { state.refresh(System.nanoTime()); renderedPage = null; }
            updateButtons();
        }
        return true;
    }
    public void receive(DirectoryPage page) {
        if (state != null && state.accept(page, System.nanoTime())) {
            renderedPage = null;
            cachedDetails = null;
            previewIcons.clear();
            search.setText(state.search());
            if (entries != null) { entries.scrollBy(-100000); entries.scrollBy(state.consumeRestoredScroll()); }
            updateButtons();
        }
    }
    private void updateButtons() {
        if (state == null || previous == null) return;
        DirectoryPage page = state.page();
        previous.enabled = page != null && !state.waiting() && !opening.waiting() && page.query.page > 0;
        next.enabled = page != null && !state.waiting() && !opening.waiting() && page.query.page + 1 < page.pageCount();
        refresh.enabled = !state.waiting() && !opening.waiting();
        allTab.enabled = !opening.waiting() && state.tab() != DirectoryTab.ALL;
        economyTab.enabled = !opening.waiting() && state.tab() != DirectoryTab.ECONOMY;
        allTab.displayString = (state.tab() == DirectoryTab.ALL ? "> " : "") + tr("tab.all");
        economyTab.displayString = fit((state.tab() == DirectoryTab.ECONOMY ? "> " : "") + specialTabName(), economyTab.width - 12);
        for (GuiButton button : buttonList) button.visible = details ? button.id >= 8 : button.id < 8;
        for (GuiButton button : buttonList) {
            if (button.id == 9) button.enabled = detailPage > 0;
            if (button.id == 10) button.enabled = details && detailPage + 1 < detailPages();
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        updateButtons();
        if (!button.enabled || state == null) return;
        if (button.id == 7) { details = true; detailPage = 0; setFocus(8); updateButtons(); return; }
        if (button.id == 8) { details = false; setFocus(-2); updateButtons(); return; }
        if (button.id == 9 || button.id == 10) { detailPage += button.id == 9 ? -1 : 1; updateButtons(); return; }
        if (button.id == 4) { mc.displayGuiScreen(null); return; }
        if (button.id == 1) state.turnPage(-1, System.nanoTime());
        if (button.id == 2) state.turnPage(1, System.nanoTime());
        if (button.id == 3) state.refresh(System.nanoTime());
        if (button.id == 5 || button.id == 6) {
            state.setTab(button.id == 5 ? DirectoryTab.ALL : DirectoryTab.ECONOMY, System.nanoTime());
            openStatus = "open_hint"; previewIcons.clear(); entries.scrollBy(-100000);
        }
        openStatus = "open_hint"; renderedPage = null; updateButtons();
    }
    private void setFocus(int next) { focus = next; if (search != null) search.setFocused(next == -1 && !details); }
    private void cycleFocus(boolean backwards) {
        List<Integer> order = new ArrayList<>();
        if (!details) { order.add(-1); order.add(-2); }
        for (GuiButton button : buttonList) if (button.visible && button.enabled) order.add(button.id);
        if (order.isEmpty()) return;
        int current = order.indexOf(focus);
        setFocus(order.get(Math.floorMod(current + (backwards ? -1 : 1), order.size())));
    }
    private void openSelected() {
        if (opening.waiting() || renderedPage == null || renderedPage != state.page() || state.waiting()) return;
        DirectoryPage.Row row = state.selected();
        if (row == null || !renderedPage.rows.contains(row)) return;
        if (row.state == DirectoryPage.State.IDENTITY_CONFLICT || row.state == DirectoryPage.State.UNSUPPORTED
                || row.state == DirectoryPage.State.REMOVED_DIMENSION) {
            openStatus = "row." + row.state.name().toLowerCase(Locale.ROOT); return;
        }
        RemoteOpenRequest request = opening.begin(renderedPage, row, System.nanoTime());
        if (request != null) { openStatus = "open_hint"; RemoteNetwork.request(request); updateButtons(); }
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (state == null) return;
        if (keyCode == Keyboard.KEY_F1 && !Keyboard.isRepeatEvent()) {
            details = !details; detailPage = 0; setFocus(details ? 8 : -2); updateButtons(); return;
        }
        if (keyCode == Keyboard.KEY_TAB) { cycleFocus(isShiftKeyDown()); return; }
        if (details) {
            if (keyCode == Keyboard.KEY_ESCAPE) { details = false; setFocus(-2); }
            else if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_PRIOR) detailPage = Math.max(0, detailPage - 1);
            else if (keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_NEXT) detailPage = Math.min(detailPages() - 1, detailPage + 1);
            else if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && !Keyboard.isRepeatEvent())
                for (GuiButton button : buttonList) if (button.id == focus && button.visible) { actionPerformed(button); break; }
            updateButtons(); return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (opening.waiting()) return;
        if (search == null) return;
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_F) { setFocus(-1); return; }
        if (focus == -2) {
            boolean moved = false;
            if (keyCode == Keyboard.KEY_UP) moved = state.moveSelection(-1);
            if (keyCode == Keyboard.KEY_DOWN) moved = state.moveSelection(1);
            if (keyCode == Keyboard.KEY_HOME) moved = state.selectIndex(0);
            if (keyCode == Keyboard.KEY_END && state.page() != null) moved = state.selectIndex(state.page().rows.size() - 1);
            if (moved) entries.showSelected();
            if (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT) {
                state.turnPage(keyCode == Keyboard.KEY_PRIOR ? -1 : 1, System.nanoTime()); renderedPage = state.page(); openStatus = "open_hint";
            }
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (Keyboard.isRepeatEvent()) return;
            if (focus == -2) openSelected();
            else if (focus == -1) { setFocus(-2); if (state.selected() == null) state.selectIndex(0); entries.showSelected(); }
            else for (GuiButton button : buttonList) if (button.id == focus && button.visible) { actionPerformed(button); break; }
            return;
        }
        String before = search.getText();
        if (search.textboxKeyTyped(typedChar, keyCode)) {
            if (!before.equals(search.getText())) { state.setSearch(search.getText(), System.nanoTime()); renderedPage = null; openStatus = "open_hint"; }
        }
    }
    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!details && search != null) {
            search.mouseClicked(mouseX, mouseY, mouseButton);
            if (search.isFocused()) setFocus(-1);
            else if (mouseY >= listTop && mouseY < listBottom) setFocus(-2);
        }
        for (GuiButton button : buttonList) if (button.mousePressed(mc, mouseX, mouseY)) { setFocus(button.id); break; }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (entries != null && !details) entries.handleMouseInput();
    }
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        RemoteOpenRequest cancelled = opening.close();
        if (cancelled != null && mc.getConnection() == connection) RemoteNetwork.cancel(cancelled);
        if (state != null) state.close();
        renderedPage = null;
        previewIcons.clear();
        super.onGuiClosed();
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (state == null || entries == null) return;
        renderedPage = state.page();
        hoveredPreview = null;
        previewMatrixDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        if (!details) entries.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRenderer, tr("title"), width / 2, 10, 0xFFFFFF);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) text(tr("search_hint"), panelLeft + 5, 59, panelWidth - 10, 0x888888);
        if (renderedPage != null) {
            text(tr("page", renderedPage.query.page + 1, renderedPage.pageCount(), renderedPage.total), panelLeft, 78, panelWidth - 94, 0xDDDDDD);
        } else text(tr(state.status()), panelLeft, 78, panelWidth - 94, 0xD5B879);
        if (renderedPage != null && renderedPage.rows.isEmpty()) {
            String key = state.tab() == DirectoryTab.ECONOMY && state.search().isEmpty() ? "tab.economy_empty" : "empty";
            fontRenderer.drawSplitString(tr(key), panelLeft + 8, listTop + 10, panelWidth - 24, 0xAAAAAA);
        }
        DirectoryPage.Row selected = state.selected();
        text(selected == null ? tr("select_hint") : tr("selected", machineName(selected.entry), ownerName(selected.entry)),
                panelLeft, listBottom + 5, panelWidth, selected == null ? 0xAAAAAA : 0x99DDFF);
        List<String> feedback = fontRenderer.listFormattedStringToWidth(feedbackText(), panelWidth);
        for (int i = 0; i < Math.min(3, feedback.size()); i++)
            text(feedback.get(i) + (i == 2 && feedback.size() > 3 ? " [F1]" : ""), panelLeft, listBottom + 17 + i * 10, panelWidth, 0xD5B879);
        if (details) drawDetails();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (focus == -2 && !details) {
            drawRect(panelLeft, listTop, panelLeft + 2, listBottom, 0xFF99DDFF);
        } else for (GuiButton button : buttonList) if (button.id == focus && button.visible)
            drawRect(button.x, button.y + button.height - 2, button.x + button.width, button.y + button.height, 0xFF99DDFF);
        if (details) return;
        if (economyTab.visible && mouseX >= economyTab.x && mouseX < economyTab.x + economyTab.width
                && mouseY >= economyTab.y && mouseY < economyTab.y + economyTab.height) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(specialTabName(), Math.max(80, panelWidth - 24)), mouseX, mouseY);
            return;
        }
        if (hoveredPreview != null) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(previewName(hoveredPreview));
            tooltip.add(tr(hoveredSale ? "preview_receive" : "preview_pay", hoveredPreview.quantity));
            tooltip.add(hoveredPreview.itemId + " @ " + hoveredPreview.metadata);
            if (!hoveredPreview.oreName.isEmpty()) tooltip.add(tr("preview_group", hoveredPreview.oreName));
            else tooltip.add(tr(hoveredPreview.matchNbt ? "preview_nbt_exact" : "preview_nbt_any"));
            if (hoveredPreview.tagged) tooltip.add(tr("preview_generic_icon"));
            tooltip.add(tr("preview_cached"));
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }
        if (mouseY >= listTop && mouseY < listBottom && renderedPage != null) {
            int index = entries.getSlotIndexFromScreenCoords(mouseX, mouseY);
            if (index >= 0 && index < renderedPage.rows.size()) {
                DirectoryPage.Row row = renderedPage.rows.get(index);
                MachineDirectoryEntry e = row.entry;
                List<String> tooltip = new ArrayList<>(Arrays.asList(machineName(e), tr("owner", ownerName(e)),
                        tr("owner_id", e.ownerId == null ? "-" : e.ownerId.toString()),
                        tr("machine_id", e.machineId == null ? tr("pending_identity") : e.machineId.toString()),
                        tr("location", e.address.dimension, e.address.x, e.address.y, e.address.z), status(row),
                        tr(e.preview.known ? "preview_cached" : "preview_unknown_hint"), tr("access_unchecked")));
                appendOfferLines(tooltip, e.preview);
                drawHoveringText(tooltip, mouseX, mouseY);
            }
        }
    }
    private String feedbackText() {
        return tr(opening.waiting() ? "open_pending" : !openStatus.equals("open_hint") ? openStatus
                : state.page() == null ? state.status() : "open_hint");
    }
    private void appendOfferLines(List<String> lines, OfferPreview preview) {
        if (!preview.known) return;
        for (OfferPreview.Item item : preview.sale) lines.add(tr("product", item.quantity + " x " + previewName(item)));
        for (OfferPreview.Item item : preview.payment) lines.add(tr("price", item.quantity + " x " + previewName(item)));
    }
    private List<String> detailLines() {
        String feedback = feedbackText();
        if (cachedDetails != null && panelWidth == cachedDetailsWidth && feedback.equals(cachedFeedback)
                && cachedDetailsPage == state.page() && cachedDetailsSelection == state.selected()) return cachedDetails;
        List<String> text = new ArrayList<>();
        text.add(feedback); text.add(tr("keyboard_help")); text.add(tr("search_help"));
        text.add(tr("tab.shared_name", specialTabName()));
        if (state.page() != null) text.add(tr(state.page().backfillComplete ? "coverage_snapshot" : "coverage_incomplete"));
        DirectoryPage.Row selected = state.selected();
        if (selected != null) {
            text.add(machineName(selected.entry)); text.add(tr("owner", ownerName(selected.entry))); text.add(status(selected));
            text.add(tr("location", selected.entry.address.dimension, selected.entry.address.x, selected.entry.address.y, selected.entry.address.z));
            appendOfferLines(text, selected.entry.preview); text.add(tr("preview_cached"));
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : text) { wrapped.addAll(fontRenderer.listFormattedStringToWidth(line, panelWidth - 32)); wrapped.add(""); }
        cachedDetails = wrapped; cachedDetailsWidth = panelWidth; cachedFeedback = feedback;
        cachedDetailsPage = state.page(); cachedDetailsSelection = state.selected(); return wrapped;
    }
    private int detailRows() { return Math.max(1, (height - 100) / 10); }
    private int detailPages() { return Math.max(1, (detailLines().size() + detailRows() - 1) / detailRows()); }
    private void drawDetails() {
        drawRect(panelLeft, 24, panelLeft + panelWidth, height - 12, 0xFF181818);
        List<String> lines = detailLines(); int rows = detailRows();
        detailPage = Math.max(0, Math.min(detailPage, detailPages() - 1));
        drawCenteredString(fontRenderer, tr("details_title", detailPage + 1, detailPages()), width / 2, 32, 0xFFFFFF);
        for (int i = 0, start = detailPage * rows; i < rows && start + i < lines.size(); i++)
            fontRenderer.drawString(lines.get(start + i), panelLeft + 16, 52 + i * 10, 0xDDDDDD);
    }
    private ItemStack previewIcon(OfferPreview.Item preview) {
        return previewIcons.computeIfAbsent(preview, key -> {
            try {
                ResourceLocation id = new ResourceLocation(key.itemId);
                // RegistryDefaulted must not turn an absent mod item into a misleading default icon.
                Item item = Item.REGISTRY.containsKey(id) ? Item.REGISTRY.getObject(id) : null;
                return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, key.metadata);
            } catch (RuntimeException | LinkageError unavailable) { return ItemStack.EMPTY; }
        });
    }
    private String previewName(OfferPreview.Item preview) {
        if (!preview.name.isEmpty()) return preview.name;
        // Same fallback as the server search; no client-language-only label that cannot be searched.
        return preview.itemId;
    }
    private void drawPreview(OfferPreview preview, int x, int y, int mouseX, int mouseY) {
        if (!preview.known) { text(tr("preview_unknown"), x, y + 17, previewLayout.previewWidth, 0x999999); return; }
        if (preview.payment.isEmpty() && preview.sale.isEmpty()) {
            text(tr("preview_unconfigured"), x, y + 17, previewLayout.previewWidth, 0x999999); return;
        }
        text("->", previewLayout.arrowX(x), y + 19, 16, 0xE3C17D);
        if (preview.payment.isEmpty()) text(tr("preview_none"), x + 4, y + 19, previewLayout.columns * 28 - 8, 0xAAAAAA);
        if (preview.sale.isEmpty()) text(tr("preview_none"), previewLayout.iconX(x, true, 0), y + 19,
                previewLayout.columns * 28 - 8, 0xAAAAAA);
        drawPreviewItems(preview.payment, false, x, y, mouseX, mouseY);
        drawPreviewItems(preview.sale, true, x, y, mouseX, mouseY);
    }
    private void drawPreviewItems(List<OfferPreview.Item> items, boolean sale, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < items.size(); i++) {
            OfferPreview.Item preview = items.get(i);
            int ix = previewLayout.iconX(x, sale, i), iy = previewLayout.iconY(y, i);
            // GuiSlot has no scissor; never let a partly clipped icon cover the search/footer controls.
            if (iy < listTop || iy + 18 > listBottom) continue;
            drawRect(ix - 5, iy - 1, ix + 22, iy + 18, 0x50202020);
            boolean drawn = false;
            float previousZ = itemRender.zLevel;
            try {
                ItemStack stack = previewIcon(preview);
                if (!stack.isEmpty()) {
                    RenderHelper.enableGUIStandardItemLighting(); GlStateManager.enableDepth();
                    GlStateManager.color(1, 1, 1, 1);
                    itemRender.renderItemAndEffectIntoGUI(stack, ix, iy);
                    itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, ix, iy, Integer.toString(preview.quantity));
                    drawn = true;
                }
            } catch (RuntimeException | LinkageError unavailable) {
                // Some mod renderers require private NBT. Keep the list usable with a labeled fallback.
                previewIcons.put(preview, ItemStack.EMPTY);
                // Vanilla's renderer does not unwind its matrix/zLevel on a rendering exception.
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                while (depth-- > previewMatrixDepth) GlStateManager.popMatrix();
                mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
            } finally {
                itemRender.zLevel = previousZ;
                RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth();
                GlStateManager.disableRescaleNormal(); GlStateManager.enableAlpha();
                GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.color(1, 1, 1, 1);
            }
            if (!drawn) {
                fontRenderer.drawString("?", ix + 5, iy, 0xD5B879);
                fontRenderer.drawString(Integer.toString(preview.quantity), ix - 4, iy + 9, 0xFFFFFF);
            }
            if (previewLayout.contains(ix, iy, mouseX, mouseY)) { hoveredPreview = preview; hoveredSale = sale; }
        }
    }
    private final class Entries extends GuiSlot {
        Entries() { super(GuiMachineDirectory.this.mc, GuiMachineDirectory.this.width, GuiMachineDirectory.this.height, listTop, listBottom, previewLayout.rowHeight); }
        void showSelected() {
            int index = state.selectedIndex(); if (index < 0) return;
            int rowTop = index * slotHeight + 4, visible = listBottom - listTop;
            if (rowTop < amountScrolled) scrollBy(rowTop - (int) amountScrolled);
            else if (rowTop + slotHeight > amountScrolled + visible) scrollBy(rowTop + slotHeight - (int) amountScrolled - visible);
        }
        @Override protected int getSize() { return state.page() == null ? 0 : state.page().rows.size(); }
        @Override public int getListWidth() { return panelWidth - 14; }
        @Override protected int getScrollBarX() { return panelLeft + panelWidth - 6; }
        @Override protected void drawBackground() { }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            if (!details && !opening.waiting() && renderedPage != null && index >= 0 && index < renderedPage.rows.size()) {
                DirectoryPage.Row row = renderedPage.rows.get(index);
                if (!state.select(renderedPage, row)) return;
                setFocus(-2); openSelected();
            }
        }
        @Override protected boolean isSelected(int index) {
            return renderedPage != null && index >= 0 && index < renderedPage.rows.size()
                    && renderedPage.rows.get(index).sameTarget(state.selected());
        }
        @Override protected void drawSlot(int index, int x, int y, int height, int mouseX, int mouseY, float partialTicks) {
            DirectoryPage.Row row = renderedPage.rows.get(index); MachineDirectoryEntry e = row.entry;
            int space = getListWidth() - 10;
            int detailsWidth = Math.max(0, space - previewLayout.previewWidth - 6);
            // Keep rows quiet; full offer/status text remains in hover details and F1.
            text(machineName(e), x + 3, y + 18, detailsWidth, 0xFFFFFF);
            text(tr("owner", ownerName(e)), x + 3, y + 32, detailsWidth, 0xB0D9EE);
            drawPreview(e.preview, x + 3 + space - previewLayout.previewWidth, y, mouseX, mouseY);
        }
    }
}
