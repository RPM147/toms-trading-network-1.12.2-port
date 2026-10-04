package com.tom.trading.client;

import com.tom.trading.BuildInfo;
import com.tom.trading.container.ContainerRemoteTrading;
import com.tom.trading.network.RemoteNetwork;
import com.tom.trading.trade.TradeLimits;
import com.tom.trading.directory.DirectoryTab;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Player inventory + public offer only. No owner controls, ghost inputs or remote machine slots. */
@SideOnly(Side.CLIENT)
public final class GuiRemoteTrading extends GuiContainer {
    private static final String PREFIX = "gui.toms_trading_network.";
    private static final ResourceLocation TEXTURE = new ResourceLocation(BuildInfo.MOD_ID, "textures/gui/vending_machine_trading.png");
    private final ContainerRemoteTrading machine;
    private GuiTextField batch;
    private GuiButton buy;
    private INetHandler connection;
    private boolean consumedMouse;
    private boolean totalsVisible;
    private final DirectoryTab returnTab;
    private DirectoryClientState.Bookmark returnBookmark;
    private INetHandler bookmarkConnection;
    public GuiRemoteTrading(ContainerRemoteTrading machine) { this(machine, DirectoryTab.ALL); }
    public GuiRemoteTrading(ContainerRemoteTrading machine, DirectoryTab returnTab) {
        super(machine); this.machine = machine; this.returnTab = java.util.Objects.requireNonNull(returnTab); xSize = 176; ySize = 166;
    }
    public GuiRemoteTrading(ContainerRemoteTrading machine, DirectoryClientState.Bookmark bookmark, INetHandler connection) {
        this(machine, DirectoryTab.ALL); returnBookmark = bookmark; bookmarkConnection = connection;
    }
    private void backToDirectory() {
        if (mc.player == null || mc.getConnection() != connection) { mc.displayGuiScreen(null); return; }
        mc.player.closeScreen();
        mc.displayGuiScreen(returnBookmark == null ? new GuiMachineDirectory(returnTab)
                : new GuiMachineDirectory(returnBookmark, bookmarkConnection));
    }
    private String tr(String key, Object... args) { return I18n.format(PREFIX + key, args); }
    @Override public void initGui() {
        super.initGui();
        if (connection == null) connection = mc.getConnection();
        Keyboard.enableRepeatEvents(true);
        String previous = batch == null ? "1" : batch.getText();
        batch = new GuiTextField(0, fontRenderer, guiLeft + 60, guiTop + 63, 34, 16);
        batch.setMaxStringLength(4);
        batch.setValidator(s -> s.isEmpty() || (s.matches("[0-9]{1,4}") && Integer.parseInt(s) <= TradeLimits.MAX_BATCH_SIZE));
        batch.setText(previous);
        buttonList.clear();
        buy = new GuiButton(1, guiLeft + 100, guiTop + 60, 70, 20, tr("trade"));
        buttonList.add(buy);
        buttonList.add(new GuiButton(2, guiLeft, guiTop - 24, 176, 20, tr("remote.back")));
        updateAccess();
    }
    private void updateAccess() {
        if (buy != null) buy.enabled = machine.view.offerRevision > 0 && batch != null
                && PurchaseTotals.parseBatch(batch.getText()) > 0 && machine.tradeRequests.canSubmit(System.nanoTime());
    }
    @Override public void updateScreen() {
        super.updateScreen();
        if (mc.player == null || mc.world == null || mc.getConnection() != connection || mc.player.openContainer != machine
                || !machine.canInteractWith(mc.player)) { mc.displayGuiScreen(null); return; }
        batch.updateCursorCounter();
        if (machine.tradeRequests.isTimedOut(System.nanoTime())) machine.feedbackKey = PREFIX + "trade_timeout";
        updateAccess();
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 2) { backToDirectory(); return; }
        updateAccess();
        if (button.id != 1 || !buy.enabled) return;
        int count = PurchaseTotals.parseBatch(batch.getText());
        if (count == 0) return;
        long requestId = machine.tradeRequests.begin(System.nanoTime());
        if (requestId == 0) return;
        machine.completedTrades = 0; machine.feedbackKey = PREFIX + "trade_pending";
        RemoteNetwork.buy(machine, count, requestId); updateAccess();
    }
    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { backToDirectory(); return; }
        if (key == Keyboard.KEY_F1) {
            if (!Keyboard.isRepeatEvent()) totalsVisible = !totalsVisible;
            return;
        }
        if (key == Keyboard.KEY_TAB) { batch.setFocused(!batch.isFocused()); return; }
        if (batch.isFocused() && key != Keyboard.KEY_ESCAPE) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { if (!Keyboard.isRepeatEvent()) actionPerformed(buy); }
            else batch.textboxKeyTyped(character, key);
            return;
        }
        super.keyTyped(character, key);
    }
    private int definitionX(int slot) { return (slot < 4 ? 8 : 98) + slot % 4 * 18; }
    private int definitionAt(int x, int y) {
        for (int slot = 0; slot < 8; slot++) if (isPointInRegion(definitionX(slot), 35, 16, 16, x, y)) return slot;
        return -1;
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        consumedMouse = false;
        for (GuiButton control : buttonList) if (control.mousePressed(mc, x, y)) {
            consumedMouse = true;
            if (button == 0) { control.playPressSound(mc.getSoundHandler()); actionPerformed(control); }
            return;
        }
        batch.mouseClicked(x, y, button);
        if (totalsAt(x, y)) {
            consumedMouse = true;
            if (button == 0) totalsVisible = !totalsVisible;
            return;
        }
        if (batch.isFocused() || definitionAt(x, y) >= 0) { consumedMouse = true; return; }
        super.mouseClicked(x, y, button);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (consumedMouse) { consumedMouse = false; return; }
        super.mouseReleased(x, y, button);
    }
    @Override protected void mouseClickMove(int x, int y, int button, long time) {
        if (!consumedMouse) super.mouseClickMove(x, y, button, time);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int x, int y) {
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = machine.view.name.isEmpty() ? I18n.format("tile.toms_trading_network.vending_machine.name") : machine.view.name;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(title, 160), 8, 6, 4210752);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(tr("directory.owner", machine.view.owner), 160), 8, 15, 4210752);
        fontRenderer.drawString(tr("payment"), 8, 24, 4210752);
        fontRenderer.drawString(tr("sale"), 98, 24, 4210752);
        fontRenderer.drawString(tr("batch"), 8, 67, 4210752);
        RenderHelper.enableGUIStandardItemLighting();
        for (int slot = 0; slot < 8; slot++) {
            ItemStack item = machine.view.templates[slot];
            if (item.isEmpty()) continue;
            itemRender.renderItemAndEffectIntoGUI(item, definitionX(slot), 35);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, item, definitionX(slot), 35, Integer.toString(machine.view.quantities[slot]));
        }
        RenderHelper.disableStandardItemLighting();
        PurchaseTotals totals = PurchaseTotalsDisplay.snapshot(machine.view, batch.getText());
        fontRenderer.drawString(PurchaseTotalsDisplay.compact(totals, false), 8, PurchaseTotalsDisplay.ROW_Y, 4210752);
        fontRenderer.drawString(PurchaseTotalsDisplay.compact(totals, true), 98, PurchaseTotalsDisplay.ROW_Y, 4210752);
        fontRenderer.drawString("F1", 82, PurchaseTotalsDisplay.ROW_Y, 0x555555);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.disableLighting(); GlStateManager.disableDepth(); batch.drawTextBox();
        int slot = definitionAt(mouseX, mouseY);
        if (slot >= 0 && !machine.view.templates[slot].isEmpty()) {
            List<String> lines = new ArrayList<>(getItemToolTip(machine.view.templates[slot]));
            PurchaseTotalsDisplay.appendItem(lines, machine.view, slot, PurchaseTotalsDisplay.snapshot(machine.view, batch.getText()));
            if (!totalsVisible) drawHoveringText(lines, mouseX, mouseY);
        } else if (!totalsVisible) renderHoveredToolTip(mouseX, mouseY);
        String feedback = machine.view.offerRevision == 0 ? tr("remote.loading")
                : machine.feedbackKey.isEmpty() ? tr("remote.lifetime") : I18n.format(machine.feedbackKey, machine.completedTrades);
        fontRenderer.drawSplitString(feedback, guiLeft, guiTop + ySize + 4, xSize, 0xFFFFFF);
        if (totalsVisible || totalsAt(mouseX, mouseY)) {
            drawHoveringText(PurchaseTotalsDisplay.details(machine.view,
                            PurchaseTotalsDisplay.snapshot(machine.view, batch.getText()), fontRenderer, width),
                    PurchaseTotalsDisplay.tooltipX(width), totalsVisible ? guiTop + PurchaseTotalsDisplay.ROW_Y : mouseY);
        }
        GlStateManager.enableDepth();
    }
    private boolean totalsAt(int x, int y) {
        return x >= guiLeft + 8 && x < guiLeft + 168
                && y >= guiTop + PurchaseTotalsDisplay.ROW_Y && y < guiTop + PurchaseTotalsDisplay.ROW_Y + 9;
    }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
}
