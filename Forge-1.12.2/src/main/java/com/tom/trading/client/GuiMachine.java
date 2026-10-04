package com.tom.trading.client;

import com.tom.trading.BuildInfo;
import com.tom.trading.container.ContainerMachine;
import com.tom.trading.network.MachineNetwork;
import com.tom.trading.item.OreFilters;
import com.tom.trading.trade.TradeLimits;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class GuiMachine extends GuiContainer {
    private static final String PREFIX = "gui." + BuildInfo.MOD_ID + ".";
    private static final String[] FACES = {"bottom", "top", "front", "left", "back", "right"};
    private static final String[] MODES = {"-", "I", "O", "IO"};
    private static final int[] FACE_X = {1, 1, 1, 0, 2, 2};
    private static final int[] FACE_Y = {2, 0, 1, 1, 2, 1};
    private final ContainerMachine machine;
    private final ResourceLocation texture;
    private GuiTextField nameField, batchField, quantityField;
    private int popupSlot = -1, tick, nameDirtyAt = -1, lastRevision = -1;
    private boolean consumedMouse;

    public GuiMachine(ContainerMachine machine) {
        super(machine);
        this.machine = machine;
        xSize = 176;
        ySize = machine.configuration ? 211 : 166;
        texture = new ResourceLocation(BuildInfo.MOD_ID, "textures/gui/vending_machine_"
                + (machine.configuration ? "config" : "trading") + ".png");
    }

    private String tr(String key, Object... values) { return I18n.format(PREFIX + key, values); }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        if (machine.configuration) {
            nameField = new GuiTextField(0, fontRenderer, guiLeft + 7, guiTop + 7, 110, 16);
            nameField.setMaxStringLength(128);
            nameField.setValidator(s -> s.codePointCount(0, s.length()) <= 64
                    && s.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 167));
            nameField.setText(machine.view.name);
        } else {
            batchField = new GuiTextField(1, fontRenderer, guiLeft + 60, guiTop + 63, 34, 16);
            batchField.setMaxStringLength(Integer.toString(TradeLimits.MAX_BATCH_SIZE).length());
            batchField.setValidator(s -> s.isEmpty() || (s.matches("[0-9]{1,4}")
                    && Integer.parseInt(s) <= TradeLimits.MAX_BATCH_SIZE));
            batchField.setText("1");
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonList.clear();
        if (popupSlot >= 0) {
            buttonList.add(new GuiButton(30, guiLeft + 23, guiTop + 83, 130, 20,
                    tr("nbt", (machine.view.matchNbt & (1 << popupSlot)) != 0 ? tr("yes") : tr("no"))));
            buttonList.add(new GuiButton(31, guiLeft + 23, guiTop + 107, 63, 20, tr("apply")));
            buttonList.add(new GuiButton(32, guiLeft + 90, guiTop + 107, 63, 20, tr("clear")));
            if (popupSlot < 4) {
                String selected = OreFilters.oreName(machine.view.templates[popupSlot]);
                buttonList.add(new GuiButton(34, guiLeft + 23, guiTop + 131, 130, 20,
                        fontRenderer.trimStringToWidth(tr("ore_group", selected.isEmpty() ? tr("direct_item") : selected), 122)));
            }
            buttonList.add(new GuiButton(33, guiLeft + 23, guiTop + (popupSlot < 4 ? 155 : 131), 130, 20, tr("close")));
        } else if (machine.configuration) {
            buttonList.add(new GuiButton(0, guiLeft + 120, guiTop + 7, 50, 20, tr("trade_view")));
            for (int face = 0; face < 6; face++) {
                if (face == 2) continue;
                int bit = 1 << face;
                int mode = ((machine.view.inputs & bit) != 0 ? 1 : 0) | ((machine.view.outputs & bit) != 0 ? 2 : 0);
                buttonList.add(new GuiButton(10 + face, guiLeft + 118 + FACE_X[face] * 16,
                        guiTop + 30 + FACE_Y[face] * 16, 16, 16,
                        MODES[mode] + ((machine.view.automatic & bit) != 0 ? "*" : "")));
            }
            if (mc.player.capabilities.isCreativeMode) {
                buttonList.add(new GuiButton(2, guiLeft + 64, guiTop - 22, 112, 20,
                        tr("creative", machine.view.creative ? tr("yes") : tr("no"))));
            }
        } else {
            buttonList.add(new GuiButton(1, guiLeft + 100, guiTop + 60, 70, 20, tr("trade")));
        }
        updateButtonAccess();
    }

    private void updateButtonAccess() {
        for (GuiButton button : buttonList) {
            button.enabled = machine.session != 0 && (button.id != 1 || (machine.view.offerRevision > 0
                    && machine.tradeRequests.canSubmit(System.nanoTime())))
                    && (button.id != 30 || !OreFilters.isFilter(machine.view.templates[popupSlot]))
                    && (button.id != 34 || !OreFilters.namesFor(machine.view.templates[popupSlot]).isEmpty());
        }
    }

    public boolean canAcceptGhost(ItemStack stack) {
        return machine.configuration && machine.session != 0 && popupSlot < 0 && mc.currentScreen == this
                && !stack.isEmpty() && !OreFilters.isFilter(stack) && !stack.hasTagCompound()
                && stack.serializeNBT().getCompoundTag("ForgeCaps").isEmpty()
                && stack.getItem().getRegistryName() != null
                && MachineNetwork.validGhostIdentity(stack.getItem().getRegistryName().toString(), stack.getMetadata());
    }

    public Rectangle ghostArea(int slot) {
        return new Rectangle(guiLeft + definitionX(slot), guiTop + definitionY(slot), 16, 16);
    }

    public void acceptGhost(int slot, ItemStack stack) {
        if (slot >= 0 && slot < 8 && canAcceptGhost(stack)) {
            MachineNetwork.send(new MachineNetwork.SetGhostTemplate(machine, slot, stack));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tick++;
        if (machine.session == 0 && (tick == 1 || tick % 20 == 0)) {
            MachineNetwork.send(new MachineNetwork.RequestState(machine));
        }
        if (nameField != null) {
            nameField.updateCursorCounter();
            if (nameDirtyAt >= 0 && tick - nameDirtyAt >= 10 && machine.session != 0) sendName();
        }
        if (batchField != null) batchField.updateCursorCounter();
        if (quantityField != null) quantityField.updateCursorCounter();
        if (machine.tradeRequests.isTimedOut(System.nanoTime())) machine.feedbackKey = PREFIX + "trade_timeout";
        if (lastRevision != machine.viewRevision) {
            lastRevision = machine.viewRevision;
            if (nameField != null && nameDirtyAt < 0 && !nameField.isFocused()) nameField.setText(machine.view.name);
            rebuildButtons();
        }
        updateButtonAccess();
    }

    private void sendName() {
        String name = nameField.getText();
        if (name.codePointCount(0, name.length()) <= 64) {
            MachineNetwork.send(new MachineNetwork.SetCustomName(machine, name));
        }
        nameDirtyAt = -1;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        updateButtonAccess(); // Enter and mouse must use the same current gate.
        if (machine.session == 0 || !button.enabled) return;
        if (button.id == 0) {
            if (nameDirtyAt >= 0) sendName();
            MachineNetwork.send(new MachineNetwork.OpenTrading(machine));
        } else if (button.id == 1) {
            int count = boundedNumber(batchField.getText(), TradeLimits.MAX_BATCH_SIZE);
            if (count != 0) {
                long requestId = machine.tradeRequests.begin(System.nanoTime());
                if (requestId == 0) return;
                machine.completedTrades = 0;
                machine.feedbackKey = PREFIX + "trade_pending";
                MachineNetwork.send(new MachineNetwork.Trade(machine, count, requestId));
                updateButtonAccess();
            }
        } else if (button.id == 2) {
            MachineNetwork.send(new MachineNetwork.SetCreativeMode(machine, !machine.view.creative));
        } else if (button.id >= 10 && button.id < 16) {
            sendSide(button.id - 10, false);
        } else if (button.id == 30) {
            MachineNetwork.send(new MachineNetwork.SetMatchNbt(machine, popupSlot,
                    (machine.view.matchNbt & (1 << popupSlot)) == 0));
        } else if (button.id == 31) {
            int count = boundedNumber(quantityField.getText(), 1024);
            if (count != 0) {
                MachineNetwork.send(new MachineNetwork.SetQuantity(machine, popupSlot, count));
                closePopup();
            }
        } else if (button.id == 32) {
            MachineNetwork.send(new MachineNetwork.SetTemplate(machine, popupSlot, true));
            closePopup();
        } else if (button.id == 33) closePopup();
        else if (button.id == 34 && popupSlot >= 0 && popupSlot < 4) {
            ItemStack definition = machine.view.templates[popupSlot];
            List<String> choices = OreFilters.namesFor(definition);
            int next = choices.indexOf(OreFilters.oreName(definition)) + 1;
            MachineNetwork.send(new MachineNetwork.SetOreFilter(machine, popupSlot,
                    next < choices.size() ? choices.get(next) : ""));
        }
    }

    private static int boundedNumber(String text, int max) {
        try {
            int value = Integer.parseInt(text);
            return value >= 1 && value <= max ? value : 0;
        } catch (NumberFormatException ignored) { return 0; }
    }

    private void sendSide(int face, boolean toggleAutomatic) {
        int bit = 1 << face;
        int mode = ((machine.view.inputs & bit) != 0 ? 1 : 0) | ((machine.view.outputs & bit) != 0 ? 2 : 0);
        boolean automatic = (machine.view.automatic & bit) != 0;
        if (toggleAutomatic) automatic = !automatic;
        else mode = (mode + 1) % 4;
        MachineNetwork.send(new MachineNetwork.SetSideMode(machine, face, mode, automatic && mode != 0));
    }

    private int definitionX(int slot) {
        return machine.configuration ? (slot < 4 ? 8 : 76) + (slot % 4 % 2) * 18
                : (slot < 4 ? 8 : 98) + slot % 4 * 18;
    }

    private int definitionY(int slot) {
        return machine.configuration ? 35 + slot % 4 / 2 * 18 : 35;
    }

    private int definitionAt(int mouseX, int mouseY) {
        for (int slot = 0; slot < 8; slot++) {
            if (isPointInRegion(definitionX(slot), definitionY(slot), 16, 16, mouseX, mouseY)) return slot;
        }
        return -1;
    }

    private void openPopup(int slot) {
        popupSlot = slot;
        nameField.setFocused(false);
        quantityField = new GuiTextField(3, fontRenderer, guiLeft + 86, guiTop + 61, 65, 16);
        quantityField.setMaxStringLength(4);
        quantityField.setValidator(s -> s.isEmpty() || s.matches("[0-9]{1,4}"));
        quantityField.setText(Integer.toString(machine.view.quantities[slot]));
        quantityField.setFocused(true);
        rebuildButtons();
    }

    private void closePopup() {
        popupSlot = -1;
        quantityField = null;
        rebuildButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        consumedMouse = false;
        for (GuiButton control : buttonList) {
            if (control.mousePressed(mc, mouseX, mouseY)) {
                consumedMouse = true;
                if (button == 0) {
                    control.playPressSound(mc.getSoundHandler());
                    actionPerformed(control);
                } else if (button == 1 && control.id >= 10 && control.id < 16) sendSide(control.id - 10, true);
                return;
            }
        }
        if (popupSlot >= 0) {
            consumedMouse = true;
            quantityField.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (nameField != null) {
            nameField.mouseClicked(mouseX, mouseY, button);
            if (nameField.isFocused()) { consumedMouse = true; return; }
        }
        if (batchField != null) {
            batchField.mouseClicked(mouseX, mouseY, button);
            if (batchField.isFocused()) { consumedMouse = true; return; }
        }
        int slot = definitionAt(mouseX, mouseY);
        if (slot >= 0) {
            consumedMouse = true;
            if (!machine.configuration || machine.session == 0) return;
            if (button == 1 && !machine.view.templates[slot].isEmpty()) openPopup(slot);
            else if (button == 0) {
                MachineNetwork.send(new MachineNetwork.SetTemplate(machine, slot, mc.player.inventory.getItemStack().isEmpty()));
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        if (consumedMouse || popupSlot >= 0) { consumedMouse = false; return; }
        super.mouseReleased(x, y, button);
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        if (!consumedMouse && popupSlot < 0) super.mouseClickMove(x, y, button, time);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (popupSlot >= 0) {
            if (key == Keyboard.KEY_ESCAPE) closePopup();
            else if (key == Keyboard.KEY_RETURN) actionPerformed(buttonList.get(1));
            else quantityField.textboxKeyTyped(character, key);
            return;
        }
        if (nameField != null && nameField.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
                if (nameDirtyAt >= 0) sendName();
                nameField.setFocused(false);
            } else if (nameField.textboxKeyTyped(character, key)) nameDirtyAt = tick;
            return;
        }
        if (batchField != null && batchField.isFocused() && key != Keyboard.KEY_ESCAPE) {
            if (key == Keyboard.KEY_RETURN) actionPerformed(buttonList.get(0));
            else batchField.textboxKeyTyped(character, key);
            return;
        }
        if (nameField != null && nameDirtyAt >= 0 && machine.session != 0
                && (key == Keyboard.KEY_ESCAPE || key == mc.gameSettings.keyBindInventory.getKeyCode())) sendName();
        super.keyTyped(character, key);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(texture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        if (machine.configuration) {
            mc.getTextureManager().bindTexture(new ResourceLocation(BuildInfo.MOD_ID, "textures/blocks/vending_machine_front.png"));
            drawModalRectWithCustomSizedTexture(guiLeft + 134, guiTop + 46, 0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (machine.configuration) {
            fontRenderer.drawString(tr("payment"), 8, 26, 4210752);
            fontRenderer.drawString(tr("sale"), 76, 26, 4210752);
            fontRenderer.drawString(tr("stock"), 8, 72, 4210752);
            fontRenderer.drawString(tr("earnings"), 98, 72, 4210752);
            fontRenderer.drawString(I18n.format("container.inventory"), 8, 119, 4210752);
        } else {
            String title = machine.view.name.isEmpty()
                    ? I18n.format("tile.toms_trading_network.vending_machine.name") : machine.view.name;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(title, 160), 8, 6, 4210752);
            fontRenderer.drawString(tr("payment"), 8, 24, 4210752);
            fontRenderer.drawString(tr("sale"), 98, 24, 4210752);
            fontRenderer.drawString(tr("batch"), 8, 67, 4210752);
        }
        RenderHelper.enableGUIStandardItemLighting();
        for (int slot = 0; slot < 8; slot++) {
            ItemStack stack = machine.view.templates[slot];
            if (stack.isEmpty()) continue;
            int x = definitionX(slot), y = definitionY(slot);
            itemRender.renderItemAndEffectIntoGUI(stack, x, y);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, Integer.toString(machine.view.quantities[slot]));
        }
        RenderHelper.disableStandardItemLighting();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        if (popupSlot >= 0) {
            // Draw the modal above the inventory, then redraw its controls over the panel.
            drawRect(guiLeft + 18, guiTop + 38, guiLeft + 158, guiTop + (popupSlot < 4 ? 181 : 157), 0xFF303030);
            fontRenderer.drawStringWithShadow(tr("definition", popupSlot + 1), guiLeft + 23, guiTop + 45, 0xFFFFFF);
            fontRenderer.drawStringWithShadow(tr("quantity"), guiLeft + 23, guiTop + 65, 0xFFFFFF);
            quantityField.drawTextBox();
            for (GuiButton button : buttonList) button.drawButton(mc, mouseX, mouseY, partialTicks);
            for (GuiButton button : buttonList) {
                if (button.id == 34 && button.isMouseOver()) {
                    List<String> lines = new ArrayList<>();
                    lines.add(tr("ore_hint"));
                    String selected = OreFilters.oreName(machine.view.templates[popupSlot]);
                    lines.add(selected.isEmpty() ? tr("direct_item") : selected);
                    drawHoveringText(lines, mouseX, mouseY);
                }
            }
        } else {
            if (nameField != null) nameField.drawTextBox();
            if (batchField != null) batchField.drawTextBox();
            int slot = definitionAt(mouseX, mouseY);
            if (slot >= 0) {
                List<String> lines = new ArrayList<>();
                if (!machine.view.templates[slot].isEmpty()) lines.addAll(getItemToolTip(machine.view.templates[slot]));
                if (machine.configuration) {
                    lines.add(tr("definition_hint"));
                    lines.add(tr("ghost_hint"));
                }
                else lines.add(tr("required_count", machine.view.quantities[slot]));
                drawHoveringText(lines, mouseX, mouseY);
            } else {
                renderHoveredToolTip(mouseX, mouseY);
                for (GuiButton button : buttonList) {
                    if (button.isMouseOver() && button.id >= 10 && button.id < 16) {
                        List<String> lines = new ArrayList<>();
                        lines.add(tr("face." + FACES[button.id - 10]));
                        lines.add(tr("side_hint"));
                        drawHoveringText(lines, mouseX, mouseY);
                    }
                }
            }
        }
        if (!machine.feedbackKey.isEmpty()) {
            fontRenderer.drawSplitString(I18n.format(machine.feedbackKey, machine.completedTrades),
                    guiLeft, guiTop + ySize + 4, xSize, 0xFFFFFF);
        }
        GlStateManager.enableDepth();
    }

    @Override
    public void onGuiClosed() {
        // Best effort for non-keyboard closure; normal close keys flush before sending the close packet.
        if (nameField != null && nameDirtyAt >= 0 && machine.session != 0) sendName();
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }
}
