package com.hepdd.backpackenhance.client.overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.hepdd.backpackenhance.Config;
import com.hepdd.backpackenhance.integration.BackpackTab;

/**
 * Client-side overlay chrome: texture-atlas panel/slots/tabs ({@link OverlayGuiTextures}),
 * 9-column slot grid, title controls, and drag-place previews matching server distribute logic.
 * Items and font text remain code-rendered; chrome is overridable via resource packs.
 */
public class BackpackOverlayPanel extends Gui {

    private static final int TITLE_HEIGHT = 16;
    /** Height of each backpack tab button (item icon area). */
    private static final int TAB_BTN_HEIGHT = 18;
    /** Vertical space reserved for the tab strip (button + small padding). */
    private static final int TAB_HEIGHT = TAB_BTN_HEIGHT + 4;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 7;
    /** Gap between tab strip and slot grid (half of previous full PADDING). */
    private static final int TAB_TO_SLOT_GAP = 3;
    private static final int MINIMIZED_SIZE = 20;
    private static final int TAB_WIDTH = 26;
    private static final int TITLE_BTN = 12;
    /** Width of NEI-style prev/next tab arrows. */
    private static final int TAB_ARROW_W = 10;

    /** Title / tab arrow text color (font is not part of the texture atlas). */
    private static final int COL_TEXT = 0xFF404040;
    private static final int COL_TEXT_DIM = 0xFF505050;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final RenderItem renderItem = RenderItem.getInstance();
    private List<BackpackTab> tabs = new ArrayList<BackpackTab>();
    private int activeTabIndex;
    /** 0-based page index; each page shows up to n visible tabs (n = capacity of the strip). */
    private int tabPage;
    private int lastX;
    private int lastY;
    private int lastWidth;
    private int lastHeight;
    private int pendingActiveTabId = -1;
    private int previewTabId = -1;
    private int previewButton;
    private ItemStack previewCursor;
    private final List<Integer> previewSlots = new ArrayList<Integer>();
    /**
     * Last {@link Config#guiPositionKey} applied in {@link #updateLayout}. Null until the GUI has
     * a real size (after initGui). Re-apply when the key changes (new GUI or size).
     */
    private String appliedPositionKey;

    public void setTabs(List<BackpackTab> tabs) {
        int preferredSlot = -1;
        if (activeTabIndex >= 0 && activeTabIndex < this.tabs.size()) {
            preferredSlot = this.tabs.get(activeTabIndex).playerSlot;
        }
        this.tabs = new ArrayList<BackpackTab>(tabs);
        if (preferredSlot >= 0) {
            for (int i = 0; i < this.tabs.size(); i++) {
                if (this.tabs.get(i).playerSlot == preferredSlot) {
                    activeTabIndex = i;
                    preferredSlot = -2;
                    break;
                }
            }
        }
        if (preferredSlot != -2) {
            if (activeTabIndex >= this.tabs.size()) {
                activeTabIndex = Math.max(0, this.tabs.size() - 1);
            }
        }
        clampTabPage();
    }

    public void render(GuiScreen gui, int mouseX, int mouseY) {
        if (tabs.isEmpty()) {
            return;
        }

        updateLayout(gui);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        OverlayGuiTextures.setZLevel(this.zLevel);
        OverlayGuiTextures.bind();

        if (Config.overlayMinimized) {
            renderMinimized(lastX, lastY);
            return;
        }

        // Nine-slice chrome; all widgets are inset so the 2px border stays uncovered.
        OverlayGuiTextures.drawPanel(lastX, lastY, lastWidth, lastHeight);
        OverlayGuiTextures.drawTitleBar(lastX + 2, lastY + 2, lastWidth - 4, TITLE_HEIGHT - 2);
        OverlayGuiTextures.drawTitleButton(
            lastX + 3,
            lastY + 2,
            isMouseInside(mouseX, mouseY, lastX + 3, lastY + 2, TITLE_BTN, TITLE_BTN));

        // Font rebinds the glyph texture — re-bind atlas before further chrome.
        FontRenderer font = minecraft.fontRenderer;
        int titleX = lastX + 3 + TITLE_BTN + 4;
        font.drawString(tr("gui.backpackenhance.title"), titleX, lastY + 4, COL_TEXT);

        OverlayGuiTextures.bind();
        OverlayGuiTextures.drawBodyFill(lastX + 2, lastY + TITLE_HEIGHT, lastWidth - 4, TAB_HEIGHT);
        renderTabs(lastX + PADDING, lastY + TITLE_HEIGHT + 2, lastWidth - PADDING * 2, mouseX, mouseY);
        renderSlotGrid(
            getActiveTab(),
            lastX + PADDING,
            lastY + TITLE_HEIGHT + TAB_HEIGHT + TAB_TO_SLOT_GAP,
            mouseX,
            mouseY);
        // Keep outer bevel on top of any inset content that touches the rim.
        OverlayGuiTextures.bind();
        OverlayGuiTextures.drawPanelFrame(lastX, lastY, lastWidth, lastHeight);
    }

    public void renderLate(GuiScreen gui, int mouseX, int mouseY) {
        if (tabs.isEmpty() || Config.overlayMinimized) {
            return;
        }

        updateLayout(gui);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 600.0F);
        RenderHelper.disableStandardItemLighting();
        renderTabTooltip(mouseX, mouseY);
        GL11.glPopMatrix();
        OverlayGlState.restoreGuiItemLighting();
    }

    public boolean mousePressed(GuiScreen gui, int mouseX, int mouseY, int button) {
        if (button != 0 || tabs.isEmpty()) {
            return false;
        }
        updateLayout(gui);

        if (Config.overlayMinimized) {
            return isMouseInside(mouseX, mouseY, lastX, lastY, MINIMIZED_SIZE, MINIMIZED_SIZE);
        }

        // Minimize (top-left)
        if (isMouseInside(mouseX, mouseY, lastX + 3, lastY + 2, TITLE_BTN, TITLE_BTN)) {
            Config.overlayMinimized = true;
            Config.saveOverlayStateFromScreen(gui, lastX, lastY, true);
            return true;
        }

        if (handleTabArrowClick(mouseX, mouseY)) {
            return true;
        }

        int clickedTab = getTabIndexAt(mouseX, mouseY);
        if (clickedTab >= 0) {
            activeTabIndex = clickedTab;
            // Keep the current page; do not force-scroll to the active tab (arrows must work freely).
            pendingActiveTabId = getActiveTab().tabId;
            return true;
        }

        if (getSlotClickAt(mouseX, mouseY) != null) {
            return true;
        }

        return isTitleBar(mouseX, mouseY);
    }

    public OverlaySlotClick getSlotClickAt(int mouseX, int mouseY) {
        if (Config.overlayMinimized || tabs.isEmpty()) {
            return null;
        }

        BackpackTab activeTab = getActiveTab();
        int columns = getDisplayColumns();
        int gridX = lastX + PADDING;
        int gridY = lastY + TITLE_HEIGHT + TAB_HEIGHT + TAB_TO_SLOT_GAP;
        int relativeX = mouseX - gridX;
        int relativeY = mouseY - gridY;
        if (relativeX < 0 || relativeY < 0) {
            return null;
        }

        int column = relativeX / SLOT_SIZE;
        int row = relativeY / SLOT_SIZE;
        if (column < 0 || column >= columns || relativeX % SLOT_SIZE >= 17 || relativeY % SLOT_SIZE >= 17) {
            return null;
        }

        int slotIndex = row * columns + column;
        if (slotIndex >= 0 && slotIndex < activeTab.storageSlots) {
            return new OverlaySlotClick(activeTab.tabId, slotIndex);
        }
        return null;
    }

    public boolean isMouseOverOverlay(int mouseX, int mouseY) {
        if (tabs.isEmpty()) {
            return false;
        }
        if (Config.overlayMinimized) {
            return isMouseInside(mouseX, mouseY, lastX, lastY, MINIMIZED_SIZE, MINIMIZED_SIZE);
        }
        return isMouseInside(mouseX, mouseY, lastX, lastY, lastWidth, lastHeight);
    }

    public ItemStack getStackAt(int mouseX, int mouseY) {
        OverlaySlotClick slotClick = getSlotClickAt(mouseX, mouseY);
        if (slotClick == null) {
            return null;
        }

        BackpackTab tab = getActiveTab();
        if (tab == null || tab.tabId != slotClick.tabId) {
            return null;
        }
        return tab.getSlotStack(slotClick.slotIndex);
    }

    public int getWidth() {
        return lastWidth;
    }

    public int getHeight() {
        return Config.overlayMinimized ? MINIMIZED_SIZE : lastHeight;
    }

    public boolean isTitleBar(int mouseX, int mouseY) {
        if (Config.overlayMinimized) {
            return isMouseInside(mouseX, mouseY, lastX, lastY, MINIMIZED_SIZE, MINIMIZED_SIZE);
        }
        return isMouseInside(mouseX, mouseY, lastX, lastY, lastWidth, TITLE_HEIGHT);
    }

    /**
     * Title bar region that starts a window drag. Excludes the minimize button so that click
     * does not arm the "click minimized widget to expand" toggle on mouse-up.
     */
    public boolean isTitleBarDragRegion(int mouseX, int mouseY) {
        if (Config.overlayMinimized) {
            return isMouseInside(mouseX, mouseY, lastX, lastY, MINIMIZED_SIZE, MINIMIZED_SIZE);
        }
        if (!isTitleBar(mouseX, mouseY)) {
            return false;
        }
        return !isMinimizeButton(mouseX, mouseY);
    }

    public boolean isMinimizeButton(int mouseX, int mouseY) {
        if (Config.overlayMinimized) {
            return false;
        }
        return isMouseInside(mouseX, mouseY, lastX + 3, lastY + 2, TITLE_BTN, TITLE_BTN);
    }

    public void moveTo(GuiScreen gui, int x, int y) {
        int width = Config.overlayMinimized ? MINIMIZED_SIZE : lastWidth;
        int height = Config.overlayMinimized ? MINIMIZED_SIZE : lastHeight;
        lastX = clamp(x, 0, Math.max(0, gui.width - width));
        lastY = clamp(y, 0, Math.max(0, gui.height - height));
        // Store as offset from container top-left (guiLeft/guiTop).
        Config.setWorkingFromScreen(gui, lastX, lastY);
    }

    public void savePosition(GuiScreen gui) {
        Config.saveOverlayStateFromScreen(gui, lastX, lastY, Config.overlayMinimized);
    }

    /** Next layout pass reloads coordinates once container size/origin are valid. */
    public void applyGui(GuiScreen gui) {
        appliedPositionKey = null;
        Config.clearWorkingOffset();
    }

    public void toggleMinimized(GuiScreen gui) {
        updateLayout(gui);
        Config.overlayMinimized = !Config.overlayMinimized;
        // Recompute size; keep container-relative offset.
        updateLayout(gui);
        Config.saveOverlayStateFromScreen(gui, lastX, lastY, Config.overlayMinimized);
    }

    public void setDragPreview(int tabId, List<Integer> slots, int button, ItemStack cursor) {
        previewTabId = tabId;
        previewButton = button;
        previewCursor = cursor == null ? null : cursor.copy();
        previewSlots.clear();
        if (slots != null) {
            previewSlots.addAll(slots);
        }
    }

    public void clearDragPreview() {
        previewTabId = -1;
        previewCursor = null;
        previewSlots.clear();
    }

    public int getX() {
        return lastX;
    }

    public int getY() {
        return lastY;
    }

    public int consumePendingActiveTabId() {
        int tabId = pendingActiveTabId;
        pendingActiveTabId = -1;
        return tabId;
    }

    private BackpackTab getActiveTab() {
        if (tabs.isEmpty()) {
            return null;
        }
        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = 0;
        }
        return tabs.get(activeTabIndex);
    }

    /** Active overlay tab id for NEI / shift routing; {@code -1} if none. */
    public int getActiveTabId() {
        BackpackTab tab = getActiveTab();
        return tab == null ? -1 : tab.tabId;
    }

    public BackpackTab getActiveTabOrNull() {
        return getActiveTab();
    }

    private static int getDisplayColumns() {
        return Math.max(1, Math.min(18, Config.overlayColumns));
    }

    private int getDisplayRows(BackpackTab tab) {
        int columns = getDisplayColumns();
        return Math.max(1, (tab.storageSlots + columns - 1) / columns);
    }

    private void updateLayout(GuiScreen gui) {
        BackpackTab activeTab = getActiveTab();
        int columns = getDisplayColumns();
        int rows = activeTab == null ? 1 : getDisplayRows(activeTab);
        lastWidth = Config.overlayWidth > 0 ? Config.overlayWidth : Math.max(128, PADDING * 2 + columns * SLOT_SIZE);
        lastHeight = Config.overlayHeight > 0 ? Config.overlayHeight
            : TITLE_HEIGHT + TAB_HEIGHT + TAB_TO_SLOT_GAP + PADDING + rows * SLOT_SIZE;
        if (Config.overlayMinimized) {
            lastWidth = MINIMIZED_SIZE;
            lastHeight = MINIMIZED_SIZE;
        } else {
            lastWidth = Math.min(lastWidth, Math.max(1, gui.width));
            lastHeight = Math.min(lastHeight, Math.max(1, gui.height));
        }

        // Load container-relative offset once key is stable (after initGui: xSize/ySize/guiLeft).
        String positionKey = Config.guiPositionKey(gui);
        if (positionKey != null && !positionKey.equals(appliedPositionKey)) {
            Config.applyGuiOverlayPosition(gui);
            appliedPositionKey = positionKey;
        }

        // screen = containerOrigin + offset (moves with the machine/chest when resolution changes)
        int desiredX = Config.screenXFromOffset(gui);
        int desiredY = Config.screenYFromOffset(gui);
        if (desiredX < 0) {
            desiredX = Config.defaultScreenX(gui, lastWidth);
        }
        if (desiredY < 0) {
            desiredY = Config.defaultScreenY(gui, lastHeight);
        }
        lastX = clamp(desiredX, 0, Math.max(0, gui.width - lastWidth));
        lastY = clamp(desiredY, 0, Math.max(0, gui.height - lastHeight));
    }

    private void renderMinimized(int x, int y) {
        OverlayGuiTextures.bind();
        OverlayGuiTextures.drawPanel(x, y, MINIMIZED_SIZE, MINIMIZED_SIZE);
        minecraft.fontRenderer.drawString("B", x + 7, y + 6, COL_TEXT);
    }

    private void renderTabs(int x, int y, int availableWidth, int mouseX, int mouseY) {
        boolean paging = needsTabPaging(availableWidth);
        int pageSize = getPageSize(availableWidth, paging);
        clampTabPage(pageSize);

        int tabsX = x;
        if (paging) {
            int pageCount = getPageCount(pageSize);
            boolean canPrev = tabPage > 0;
            boolean canNext = tabPage < pageCount - 1;
            drawTabArrow(x, y, true, canPrev, isMouseInside(mouseX, mouseY, x, y, TAB_ARROW_W, TAB_BTN_HEIGHT));
            int rightX = x + availableWidth - TAB_ARROW_W;
            drawTabArrow(
                rightX,
                y,
                false,
                canNext,
                isMouseInside(mouseX, mouseY, rightX, y, TAB_ARROW_W, TAB_BTN_HEIGHT));
            tabsX = x + TAB_ARROW_W + 1;
        }

        OverlayGuiTextures.bind();
        int offset = tabPage * pageSize;
        int count = Math.min(pageSize, Math.max(0, tabs.size() - offset));
        for (int i = 0; i < count; i++) {
            int tabIndex = offset + i;
            BackpackTab tab = tabs.get(tabIndex);
            int tabX = tabsX + i * TAB_WIDTH;
            boolean active = tabIndex == activeTabIndex;
            OverlayGuiTextures.drawTab(tabX, y, active);
            // Dye accent strip (dynamic color — not in the atlas).
            drawRect(tabX, y, tabX + 2, y + TAB_BTN_HEIGHT, tab.getAccentColor());
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            // Center item icon (no index number).
            renderItem(tab.stack, tabX + (TAB_WIDTH - 2 - 16) / 2, y + 1);
            OverlayGuiTextures.bind();
        }
    }

    private boolean needsTabPaging(int availableWidth) {
        return tabs.size() > Math.max(1, availableWidth / TAB_WIDTH);
    }

    /** How many tabs fit on one page (n). When paging, arrows reserve space on both sides. */
    private int getPageSize(int availableWidth, boolean paging) {
        int width = availableWidth;
        if (paging) {
            width -= TAB_ARROW_W * 2 + 2;
        }
        return Math.max(1, width / TAB_WIDTH);
    }

    private int getPageCount(int pageSize) {
        if (tabs.isEmpty() || pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (tabs.size() + pageSize - 1) / pageSize);
    }

    private int getTabStripAvailableWidth() {
        return lastWidth - PADDING * 2;
    }

    private void clampTabPage() {
        clampTabPage(getPageSize(getTabStripAvailableWidth(), needsTabPaging(getTabStripAvailableWidth())));
    }

    private void clampTabPage(int pageSize) {
        int pageCount = getPageCount(pageSize);
        if (tabPage < 0) {
            tabPage = 0;
        }
        if (tabPage >= pageCount) {
            tabPage = pageCount - 1;
        }
    }

    private boolean handleTabArrowClick(int mouseX, int mouseY) {
        int availableWidth = getTabStripAvailableWidth();
        if (!needsTabPaging(availableWidth)) {
            return false;
        }
        int x = lastX + PADDING;
        int y = lastY + TITLE_HEIGHT + 2;
        int pageSize = getPageSize(availableWidth, true);
        int pageCount = getPageCount(pageSize);

        if (isMouseInside(mouseX, mouseY, x, y, TAB_ARROW_W, TAB_BTN_HEIGHT)) {
            // Always allow paging regardless of which tab is selected.
            if (tabPage > 0) {
                tabPage--;
            }
            return true;
        }
        int rightX = x + availableWidth - TAB_ARROW_W;
        if (isMouseInside(mouseX, mouseY, rightX, y, TAB_ARROW_W, TAB_BTN_HEIGHT)) {
            if (tabPage < pageCount - 1) {
                tabPage++;
            }
            return true;
        }
        return false;
    }

    private void drawTabArrow(int x, int y, boolean left, boolean enabled, boolean hovered) {
        OverlayGuiTextures.bind();
        OverlayGuiTextures.drawArrowButton(x, y, enabled, hovered);
        int color = enabled ? COL_TEXT : COL_TEXT_DIM;
        String glyph = left ? "<" : ">";
        int textX = x + (TAB_ARROW_W - minecraft.fontRenderer.getStringWidth(glyph)) / 2;
        minecraft.fontRenderer.drawString(glyph, textX, y + 5, color);
        OverlayGuiTextures.bind();
    }

    private void renderSlotGrid(BackpackTab tab, int x, int y, int mouseX, int mouseY) {
        int columns = getDisplayColumns();
        Map<Integer, Integer> previewAmounts = computePreviewAmounts(tab);

        OverlayGuiTextures.bind();
        for (int slot = 0; slot < tab.storageSlots; slot++) {
            int column = slot % columns;
            int row = slot / columns;
            int slotX = x + column * SLOT_SIZE;
            int slotY = y + row * SLOT_SIZE;
            OverlayGuiTextures.drawSlot(slotX, slotY);

            ItemStack existing = tab.getSlotStack(slot);
            Integer previewAmt = previewAmounts.get(Integer.valueOf(slot));
            if (previewAmt != null && previewAmt.intValue() > 0 && previewCursor != null) {
                // Show distributed ghost item (like vanilla drag-place), even on empty slots.
                ItemStack ghost = previewCursor.copy();
                ghost.stackSize = previewAmt.intValue();
                if (existing != null) {
                    // Draw base stack then overlay ghost count on top if merging.
                    renderItem(existing, slotX + 1, slotY + 1);
                    renderGhostItem(ghost, slotX + 1, slotY + 1);
                } else {
                    renderGhostItem(ghost, slotX + 1, slotY + 1);
                }
            } else {
                renderItem(existing, slotX + 1, slotY + 1);
            }

            // Vanilla-style white hover highlight over the 16x16 item area.
            if (mouseX >= slotX && mouseY >= slotY && mouseX < slotX + 18 && mouseY < slotY + 18) {
                drawSlotHover(slotX + 1, slotY + 1);
            }
            OverlayGuiTextures.bind();
        }
    }

    private void drawSlotHover(int x, int y) {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        OverlayGuiTextures.bind();
        OverlayGuiTextures.drawSlotHover(x, y);
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        if (lighting) {
            GL11.glEnable(GL11.GL_LIGHTING);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Mirrors {@code OverlayClickExecutor.dragDistribute} so preview counts match the server.
     */
    private Map<Integer, Integer> computePreviewAmounts(BackpackTab tab) {
        Map<Integer, Integer> amounts = new HashMap<Integer, Integer>();
        if (tab.tabId != previewTabId || previewCursor == null || previewSlots.isEmpty()) {
            return amounts;
        }

        ItemStack cursor = previewCursor.copy();
        List<Integer> validSlots = new ArrayList<Integer>();
        for (Integer slotIndex : previewSlots) {
            int slot = slotIndex.intValue();
            if (slot < 0 || slot >= tab.storageSlots || validSlots.contains(slotIndex)) {
                continue;
            }
            ItemStack slotStack = tab.getSlotStack(slot);
            if (slotStack == null || canMerge(slotStack, cursor)) {
                validSlots.add(slotIndex);
            }
        }

        int remainingSlots = validSlots.size();
        ItemStack working = cursor.copy();
        for (Integer slotIndex : validSlots) {
            if (working.stackSize <= 0) {
                break;
            }
            int slot = slotIndex.intValue();
            ItemStack slotStack = tab.getSlotStack(slot);
            int limit = working.getMaxStackSize();
            int space = slotStack == null ? limit : limit - slotStack.stackSize;
            if (space <= 0) {
                remainingSlots--;
                continue;
            }

            int amount = previewButton == 1 ? 1 : Math.max(1, working.stackSize / remainingSlots);
            amount = Math.min(amount, space);
            amount = Math.min(amount, working.stackSize);
            if (amount > 0) {
                amounts.put(Integer.valueOf(slot), Integer.valueOf(amount));
                working.stackSize -= amount;
            }
            remainingSlots--;
        }
        return amounts;
    }

    private static boolean canMerge(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.isItemEqual(b)
            && ItemStack.areItemStackTagsEqual(a, b)
            && a.stackSize < a.getMaxStackSize();
    }

    private void renderItem(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }

        GL11.glPushMatrix();
        OverlayGlState.beginItemIcon();
        renderItem.zLevel = 100.0F;
        renderItem.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        renderItem.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        renderItem.zLevel = 0.0F;
        OverlayGlState.endItemIcon();
        GL11.glPopMatrix();
        // Flat chrome after icons
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderGhostItem(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        GL11.glPushMatrix();
        OverlayGlState.beginItemIcon();
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.7F);
        renderItem.zLevel = 110.0F;
        renderItem.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        renderItem.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        renderItem.zLevel = 0.0F;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        OverlayGlState.endItemIcon();
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderTabTooltip(int mouseX, int mouseY) {
        int tabTop = lastY + TITLE_HEIGHT;
        int tabBottom = tabTop + TAB_HEIGHT;
        if (mouseY < tabTop || mouseY > tabBottom) {
            return;
        }

        int index = getTabIndexAt(mouseX, mouseY);
        if (index >= 0 && index < tabs.size()) {
            List<String> lines = new ArrayList<String>();
            BackpackTab hovered = tabs.get(index);
            lines.add(hovered.displayName);
            lines.add(
                tr("gui.backpackenhance.tooltip.player_slot") + " "
                    + hovered.playerSlot
                    + " - "
                    + hovered.storageSlots
                    + " "
                    + tr("gui.backpackenhance.tooltip.slots"));
            drawSimpleTooltip(lines, mouseX, mouseY);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private int getTabIndexAt(int mouseX, int mouseY) {
        int tabTop = lastY + TITLE_HEIGHT + 2;
        int tabBottom = tabTop + TAB_BTN_HEIGHT;
        int stripLeft = lastX + PADDING;
        int availableWidth = getTabStripAvailableWidth();
        int stripRight = stripLeft + availableWidth;
        if (mouseY < tabTop || mouseY >= tabBottom || mouseX < stripLeft || mouseX >= stripRight) {
            return -1;
        }

        boolean paging = needsTabPaging(availableWidth);
        int tabsX = stripLeft;
        if (paging) {
            // Clicks on arrows are not tab hits.
            if (mouseX < stripLeft + TAB_ARROW_W || mouseX >= stripRight - TAB_ARROW_W) {
                return -1;
            }
            tabsX = stripLeft + TAB_ARROW_W + 1;
        }

        int pageSize = getPageSize(availableWidth, paging);
        int local = (mouseX - tabsX) / TAB_WIDTH;
        if (local < 0 || local >= pageSize) {
            return -1;
        }
        int index = tabPage * pageSize + local;
        if (index >= 0 && index < tabs.size()) {
            return index;
        }
        return -1;
    }

    private void drawSimpleTooltip(List<String> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, minecraft.fontRenderer.getStringWidth(line));
        }

        int x = mouseX + 8;
        int y = mouseY + 8;
        int height = lines.size() * 10 + 4;
        drawTooltip(lines, x, y, width, height);
        for (int i = 0; i < lines.size(); i++) {
            minecraft.fontRenderer.drawString(lines.get(i), x, y + i * 10, 0xFFFFFF);
        }
    }

    public void drawTooltip(List<String> lines, int x, int y, int width, int height) {
        OverlayGuiTextures.bind();
        OverlayGuiTextures.drawTooltipBackground(x - 3, y - 3, width + 6, height + 3);
    }

    private static boolean isMouseInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
