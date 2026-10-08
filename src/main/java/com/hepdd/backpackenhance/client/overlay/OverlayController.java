package com.hepdd.backpackenhance.client.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hepdd.backpackenhance.Config;
import com.hepdd.backpackenhance.client.keybind.KeyBindings;
import com.hepdd.backpackenhance.integration.BackpackGuiBlacklist;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackScanner;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.BackpackTabColors;
import com.hepdd.backpackenhance.integration.ForestryModeBridge;
import com.hepdd.backpackenhance.integration.WirelessOverlay;
import com.hepdd.backpackenhance.integration.nei.NeiOverlayIntegration;
import com.hepdd.backpackenhance.integration.nei.OverlayNeiSupport;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.net.packet.PacketCloseOverlay;
import com.hepdd.backpackenhance.net.packet.PacketCreativeCursor;
import com.hepdd.backpackenhance.net.packet.PacketOverlayActiveTab;
import com.hepdd.backpackenhance.net.packet.PacketOverlayClick;
import com.hepdd.backpackenhance.net.packet.PacketOverlayDrag;
import com.hepdd.backpackenhance.net.packet.PacketOverlayForestryModeCycle;
import com.hepdd.backpackenhance.net.packet.PacketOverlaySettings;
import com.hepdd.backpackenhance.net.packet.PacketRequestOverlay;
import com.hepdd.backpackenhance.net.packet.PacketWirelessAction;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class OverlayController {

    private static OverlayController instance;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final RenderItem renderItem = RenderItem.getInstance();
    private final BackpackScanner scanner = new BackpackScanner();
    private final BackpackGuiBlacklist blacklist = new BackpackGuiBlacklist();
    private final BackpackOverlayPanel panel = new BackpackOverlayPanel();
    private final List<BackpackTab> tabs = new ArrayList<BackpackTab>();

    private GuiScreen activeGui;
    private boolean pendingScan;
    private boolean leftMouseDown;
    private boolean rightMouseDown;
    private OverlaySlotClick pendingRightSlotClick;
    private final List<Integer> rightDragSlots = new ArrayList<Integer>();
    private boolean dragging;
    private boolean minimizedDragCandidate;
    private boolean minimizedDragMoved;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private final boolean[] hotbarKeyDown = new boolean[9];
    /** Edge-detect for toggle key (GUI open: KeyBinding.isPressed does not update). */
    private boolean toggleKeyWasDown;
    private long lastLeftSlotClickTime;
    private int lastLeftTabId = -1;
    private int lastLeftSlotIndex = -1;
    private OverlaySlotClick pendingSlotClick;
    private int pendingClickMode;
    private final List<Integer> dragSlots = new ArrayList<Integer>();
    private boolean renderedInContainerPass;
    /** Native wheel timestamp de-duplicates a consumed event if both registered buses deliver it. */
    private long lastHandledWheelEventNanos = Long.MIN_VALUE;
    /** Monotonic session id so a late PacketCloseOverlay cannot wipe a newer Request. */
    private int sessionId;
    private int wirelessSessionId;
    /** Fingerprint of backpack items in the player inventory (layout / dye / identity). */
    private String backpackInventoryFingerprint = "";
    /**
     * Last selected overlay tab id. Survives brief tab-list clears (e.g. NEI closing recipe GUI
     * and reopening the machine mid-transferRecipe).
     */
    private int selectedTabId = -1;
    private int sentActiveTab = Integer.MIN_VALUE;
    private boolean sentMinimized;

    public OverlayController() {
        instance = this;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        // Only close the server session when leaving overlay-eligible GUIs entirely.
        // Switching chest→machine / NEI recipe→machine only re-requests — do NOT clear tabs here.
        // Clearing mid-frame breaks NEI fill (displayGuiScreen → transferRecipe same tick).
        if (isEligibleGui(event.gui)) {
            if (activeGui == null) wirelessSessionId++;
            // Save previous GUI's overlay position before loading the new one.
            if (activeGui != null && activeGui != event.gui && !tabs.isEmpty()) {
                panel.savePosition(activeGui);
            }
            panel.releaseScrollbar();
            leftMouseDown = false;
            rightMouseDown = false;
            dragging = false;
            minimizedDragCandidate = false;
            minimizedDragMoved = false;
            pendingSlotClick = null;
            pendingRightSlotClick = null;
            panel.clearDragPreview();
            dragSlots.clear();
            rightDragSlots.clear();
            lastHandledWheelEventNanos = Long.MIN_VALUE;
            activeGui = event.gui;
            panel.applyGui(event.gui);
            pendingScan = true;
            return;
        }

        if (activeGui != null) {
            if (!tabs.isEmpty()) {
                panel.savePosition(activeGui);
            }
            OverlayNeiSupport.forceSettleIfNeeded();
            NetworkHandler.INSTANCE.sendToServer(new PacketCloseOverlay(sessionId));
            sendWirelessControl(PacketWirelessAction.CLOSE);
            OverlayClientState.clear();
        }
        activeGui = null;
        pendingScan = false;
        leftMouseDown = false;
        rightMouseDown = false;
        dragging = false;
        panel.releaseScrollbar();
        panel.clearModeCyclePending();
        minimizedDragCandidate = false;
        minimizedDragMoved = false;
        pendingSlotClick = null;
        pendingRightSlotClick = null;
        panel.clearDragPreview();
        dragSlots.clear();
        rightDragSlots.clear();
        tabs.clear();
        selectedTabId = -1;
        lastHandledWheelEventNanos = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || minecraft.thePlayer == null) {
            return;
        }

        OverlayClientState.tick();
        // When bookmark auto-craft finishes, settle borrowed tools + unused materials to overlay.
        OverlayNeiSupport.tickAutoCraftSettle();

        // While pendingScan, currentScreen may lag one tick behind GuiOpenEvent — do not close yet.
        if (activeGui != null && minecraft.currentScreen != activeGui && !pendingScan) {
            clearState();
            return;
        }

        if (pendingScan && activeGui != null) {
            pendingScan = false;
            tabs.clear();
            tabs.addAll(
                OverlayClientState.hasState() ? OverlayClientState.getTabs() : scanner.scan(minecraft.thePlayer));
            panel.setTabs(tabs);
            backpackInventoryFingerprint = fingerprintBackpacks(minecraft.thePlayer);
            requestOverlayFromServer();
        }

        // Player moved / dyed / swapped backpack items in their inventory → re-scan + resync.
        if (activeGui != null && minecraft.currentScreen == activeGui && !pendingScan) {
            String fingerprint = fingerprintBackpacks(minecraft.thePlayer);
            if (!fingerprint.equals(backpackInventoryFingerprint)) {
                backpackInventoryFingerprint = fingerprint;
                requestOverlayFromServer();
            }
        }

        // Authoritative server snapshot (including empty = all backpacks removed).
        // Apply tabs + cursor in the same step so place/take never shows both at once.
        if (activeGui != null && OverlayClientState.consumeDirty()) {
            List<BackpackTab> syncedTabs = OverlayClientState.getTabs();
            tabs.clear();
            tabs.addAll(syncedTabs);
            panel.setTabs(tabs);
            OverlayClientState.applyPendingCursor();
            panel.clearModeCyclePending();
            rememberSelectedTabId();
        }

        if (activeGui != null) {
            handleHotkey(activeGui);
        }

        if (activeGui != null && !tabs.isEmpty()) {
            handleMouseInput(activeGui);
            handleHotbarKeys(activeGui);
            // Tab clicks may change selection this tick.
            rememberSelectedTabId();
        }
    }

    private void rememberSelectedTabId() {
        int id = panel.getActiveTabId();
        BackpackTab active = panel.getActiveTabOrNull();
        if (active != null && active.isWireless()
            && (id != selectedTabId || (sentMinimized && !Config.overlayMinimized))) {
            active.wirelessLoading = true;
        }
        if (id != sentActiveTab || Config.overlayMinimized != sentMinimized) {
            sentActiveTab = id;
            sentMinimized = Config.overlayMinimized;
            NetworkHandler.INSTANCE.sendToServer(new PacketOverlayActiveTab(id));
            sendWirelessControl(PacketWirelessAction.SELECT);
        }
        if (id >= 0) {
            selectedTabId = id;
        }
    }

    private void requestOverlayFromServer() {
        if (activeGui == null || !(activeGui instanceof GuiContainer)) {
            return;
        }
        sessionId++;
        OverlayClientState.expectWirelessSession(wirelessSessionId);
        sentActiveTab = Integer.MIN_VALUE;
        sendWirelessControl(PacketWirelessAction.OPEN);
        NetworkHandler.INSTANCE.sendToServer(
            new PacketRequestOverlay(
                activeGui.getClass()
                    .getName(),
                ((GuiContainer) activeGui).inventorySlots.getClass()
                    .getName(),
                Config.overlayMinimized,
                sessionId));
    }

    private void sendWirelessControl(int action) {
        if (minecraft.thePlayer == null) return;
        NetworkHandler.INSTANCE.sendToServer(
            new PacketWirelessAction(
                wirelessSessionId,
                minecraft.thePlayer.openContainer.windowId,
                panel.getActiveTabId(),
                0,
                action,
                0,
                Config.overlayMinimized,
                null));
    }

    private void sendSlotAction(OverlaySlotClick click, int button, int mode) {
        if (WirelessOverlay.isWirelessTab(click.tabId)) {
            int action = mode == PacketOverlayClick.MODE_SHIFT ? PacketWirelessAction.SHIFT
                : mode == PacketOverlayClick.MODE_HOTBAR ? PacketWirelessAction.HOTBAR
                    : mode == PacketOverlayClick.MODE_DOUBLE ? PacketWirelessAction.COLLECT
                        : PacketWirelessAction.CLICK;
            sendWirelessClick(click, action, button);
        } else {
            NetworkHandler.INSTANCE.sendToServer(
                new PacketOverlayClick(
                    click.tabId,
                    click.slotIndex,
                    button,
                    mode,
                    OverlayClientState.beginCursorAction()));
        }
    }

    private void sendWirelessClick(OverlaySlotClick click, int action, int value) {
        NetworkHandler.INSTANCE.sendToServer(
            new PacketWirelessAction(
                wirelessSessionId,
                minecraft.thePlayer.openContainer.windowId,
                click.tabId,
                click.generation,
                action,
                value,
                Config.overlayMinimized,
                click.template));
    }

    /**
     * Stable identity of backpacks in player inventory + cursor, used to detect when the player
     * manipulates backpack items themselves (move, swap, dye, drop) so the overlay can refresh.
     */
    private String fingerprintBackpacks(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(128);
        List<BackpackTab> scanned = scanner.scan(player);
        for (int i = 0; i < scanned.size(); i++) {
            BackpackTab tab = scanned.get(i);
            appendBackpackIdentity(sb, tab.playerSlot, tab.kind, tab.stack);
        }
        ItemStack cursor = player.inventory.getItemStack();
        BackpackKind cursorKind = scanner.detectKind(cursor);
        if (cursorKind != null && Config.isModEnabled(cursorKind.modKey)) {
            appendBackpackIdentity(sb, -1, cursorKind, cursor);
        }
        return sb.toString();
    }

    private static void appendBackpackIdentity(StringBuilder sb, int slot, BackpackKind kind, ItemStack stack) {
        if (stack == null || kind == null) {
            return;
        }
        sb.append(slot)
            .append('#')
            .append(Item.getIdFromItem(stack.getItem()))
            .append('@');
        if (kind != BackpackKind.FORESTRY) {
            sb.append(stack.getItemDamage());
        }
        sb.append('@')
            .append(kind.name())
            .append('@')
            .append(BackpackTabColors.resolve(kind, stack));
        if (stack.hasTagCompound()) {
            sb.append('@')
                .append(stableBodyNbtKey(stack.getTagCompound()));
        }
        sb.append('|');
    }

    private static String stableBodyNbtKey(NBTTagCompound tag) {
        if (tag == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // Brad's UUID on the stack
        if (tag.hasKey("backpack-UID")) {
            sb.append(tag.getString("backpack-UID"));
        }
        // Forestry item-inventory identity
        if (tag.hasKey("UID", 3)) {
            sb.append('f')
                .append(tag.getInteger("UID"));
        }
        // Adventure skin type (root or wearable compound)
        if (tag.hasKey("type")) {
            sb.append('t')
                .append(tag.getByte("type"));
        }
        // AdventureBackpack wearable compound
        if (tag.hasKey("wearableData")) {
            NBTTagCompound wear = tag.getCompoundTag("wearableData");
            if (wear != null && wear.hasKey("type")) {
                sb.append('w')
                    .append(wear.getByte("type"));
            }
        }
        return sb.toString();
    }

    private boolean isEligibleGui(GuiScreen gui) {
        return gui instanceof GuiContainer
            && (!(gui instanceof GuiContainerCreative)
                || isCreativeInventoryTab(((GuiContainerCreative) gui).func_147056_g()))
            && !blacklist.isBlacklisted(gui)
            && scanner.hasSupportedModLoaded();
    }

    private static boolean isCreativeInventoryTab(int selectedTabIndex) {
        return selectedTabIndex == CreativeTabs.tabInventory.getTabIndex();
    }

    public static void synchronizeCreativeCursor() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        OverlayClientState.invalidateCursor();
        NetworkHandler.INSTANCE.sendToServer(new PacketCreativeCursor(mc.thePlayer.inventory.getItemStack()));
    }

    public static void onCreativeTabChanged(GuiContainerCreative gui) {
        if (instance != null && instance.minecraft.currentScreen == gui
            && instance.isEligibleGui(gui) != (instance.activeGui == gui)) {
            instance.onGuiOpen(new GuiOpenEvent(gui));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui == activeGui && !tabs.isEmpty()) {
            // Prefer in-container injects (vanilla / ModularUI1 / ModularUI2). Post is only a
            // fallback when those injects did not run — layering may be wrong on ModularUI2.
            if (!renderedInContainerPass) {
                applyDragPreview();
                panel.render(event.gui, event.mouseX, event.mouseY);
                renderPostFallbackTop(event.gui, event.mouseX, event.mouseY);
            }
            panel.renderLate(event.gui, event.mouseX, event.mouseY);
        }
        renderedInContainerPass = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseEvent(MouseEvent event) {
        if (handleMouseWheelInput(activeGui, event.dwheel, event.nanoseconds)) {
            event.setCanceled(true);
        }
    }

    /**
     * Handles the current native wheel event while a GUI is open. Minecraft 1.7.10 drains GUI
     * mouse events in {@code GuiScreen.handleInput} before Forge posts {@link MouseEvent}, so the
     * GUI mixin calls this method directly; {@link #onMouseEvent} remains as a fallback for screens
     * that allow normal in-game input.
     */
    public static boolean handleMouseWheelInput(GuiScreen gui, int wheelDelta, long eventNanos) {
        if (instance == null || gui == null || wheelDelta == 0) {
            return false;
        }
        return instance.handleMouseWheel(gui, wheelDelta, eventNanos);
    }

    public static boolean handleSearchMouseInput(GuiScreen gui, int mouseX, int mouseY, int button) {
        if (instance == null || gui != instance.activeGui || instance.tabs.isEmpty()) return false;
        if (!instance.panel.handleSearchMouse(gui, mouseX, mouseY, button)) return false;
        if (button == 0) instance.leftMouseDown = true;
        if (button == 1) instance.rightMouseDown = true;
        instance.rememberSelectedTabId();
        return true;
    }

    private boolean handleMouseWheel(GuiScreen gui, int wheelDelta, long eventNanos) {
        if (eventNanos == lastHandledWheelEventNanos || gui != activeGui
            || minecraft.currentScreen != activeGui
            || tabs.isEmpty()) {
            return false;
        }
        // Inventory drag-place and window/scrollbar drags keep their current target stable.
        if (minecraft.thePlayer == null || minecraft.thePlayer.inventory == null
            || Mouse.isButtonDown(0)
            || Mouse.isButtonDown(1)
            || leftMouseDown
            || rightMouseDown
            || dragging
            || panel.isScrollbarDragging()) {
            return false;
        }

        ScaledResolution scaled = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int mouseX = Mouse.getEventX() * scaled.getScaledWidth() / minecraft.displayWidth;
        int mouseY = scaled.getScaledHeight() - Mouse.getEventY() * scaled.getScaledHeight() / minecraft.displayHeight
            - 1;

        return consumeWheelEvent(eventNanos, panel.mouseScrolled(activeGui, mouseX, mouseY, wheelDelta));
    }

    private boolean consumeWheelEvent(long eventNanos, boolean consumed) {
        if (consumed) {
            lastHandledWheelEventNanos = eventNanos;
        }
        return consumed;
    }

    private void handleMouseInput(GuiScreen gui) {
        ScaledResolution scaled = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int mouseX = Mouse.getX() * scaled.getScaledWidth() / minecraft.displayWidth;
        int mouseY = scaled.getScaledHeight() - Mouse.getY() * scaled.getScaledHeight() / minecraft.displayHeight - 1;
        boolean nowDown = Mouse.isButtonDown(0);
        boolean rightNowDown = Mouse.isButtonDown(1);
        if ((nowDown && !leftMouseDown) || (rightNowDown && !rightMouseDown)) {
            if (panel.handleSearchMouse(gui, mouseX, mouseY, nowDown ? 0 : 1)) {
                leftMouseDown = nowDown;
                rightMouseDown = rightNowDown;
                return;
            }
        }

        if (nowDown && !leftMouseDown) {
            OverlaySlotClick slotClick = panel.getSlotClickAt(mouseX, mouseY);
            if (slotClick != null) {
                pendingSlotClick = slotClick;
                pendingClickMode = resolveLeftClickMode(slotClick);
                dragSlots.clear();
                dragSlots.add(slotClick.slotIndex);
                leftMouseDown = true;
                return;
            }

            boolean wasMinimized = Config.overlayMinimized;
            boolean consumed = panel.mousePressed(gui, mouseX, mouseY, 0);
            if (consumed && panel.isScrollbarDragging()) {
                leftMouseDown = true;
                rightMouseDown = rightNowDown;
                return;
            }
            int activeTabId = panel.consumePendingActiveTabId();
            if (activeTabId >= 0) {
                sentActiveTab = Integer.MIN_VALUE;
                NetworkHandler.INSTANCE.sendToServer(new PacketOverlayActiveTab(activeTabId));
            }
            BackpackOverlayPanel.ModeCycleRequest modeRequest = panel.consumePendingModeCycle();
            if (modeRequest != null) {
                boolean hasUid = ForestryModeBridge.hasUid(modeRequest.stack);
                int uid = hasUid ? ForestryModeBridge.uid(modeRequest.stack) : 0;
                NetworkHandler.INSTANCE.sendToServer(
                    new PacketOverlayForestryModeCycle(
                        sessionId,
                        modeRequest.tabId,
                        modeRequest.playerSlot,
                        modeRequest.expectedMode,
                        hasUid,
                        uid));
            }
            // Minimize button consumes the click; do not start title drag (would toggle
            // minimize back on mouse-up via minimizedDragCandidate).
            if (consumed && wasMinimized != Config.overlayMinimized) {
                // Expanded ↔ minimized via title button — sync server session.
                syncOverlaySettings();
            } else if (consumed && panel.isTitleBarDragRegion(mouseX, mouseY)) {
                dragging = true;
                minimizedDragCandidate = Config.overlayMinimized;
                minimizedDragMoved = false;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragOffsetX = mouseX - panel.getX();
                dragOffsetY = mouseY - panel.getY();
            }
        } else if (nowDown && pendingSlotClick != null) {
            OverlaySlotClick slotClick = panel.getSlotClickAt(mouseX, mouseY);
            if (slotClick != null && slotClick.tabId == pendingSlotClick.tabId
                && !dragSlots.contains(slotClick.slotIndex)) {
                dragSlots.add(slotClick.slotIndex);
            }
        } else if (nowDown && panel.isScrollbarDragging()) {
            panel.dragScrollbarTo(mouseY);
        } else if (nowDown && dragging) {
            if (Math.abs(mouseX - dragStartMouseX) > 2 || Math.abs(mouseY - dragStartMouseY) > 2) {
                minimizedDragMoved = true;
            }
            panel.moveTo(gui, mouseX - dragOffsetX, mouseY - dragOffsetY);
        } else if (!nowDown && leftMouseDown) {
            if (pendingSlotClick != null) {
                sendPendingLeftClick();
            } else if (panel.isScrollbarDragging()) {
                panel.releaseScrollbar();
            } else if (dragging) {
                panel.savePosition(gui);
                if (minimizedDragCandidate && !minimizedDragMoved) {
                    boolean before = Config.overlayMinimized;
                    panel.toggleMinimized(gui);
                    if (before != Config.overlayMinimized) {
                        syncOverlaySettings();
                    }
                }
            }
            dragging = false;
            minimizedDragCandidate = false;
            minimizedDragMoved = false;
            pendingSlotClick = null;
            dragSlots.clear();
        }

        if (rightNowDown && !rightMouseDown) {
            OverlaySlotClick slotClick = panel.getSlotClickAt(mouseX, mouseY);
            if (slotClick != null) {
                pendingRightSlotClick = slotClick;
                rightDragSlots.clear();
                rightDragSlots.add(slotClick.slotIndex);
                rightMouseDown = true;
                return;
            }
        } else if (rightNowDown && pendingRightSlotClick != null) {
            OverlaySlotClick slotClick = panel.getSlotClickAt(mouseX, mouseY);
            if (slotClick != null && slotClick.tabId == pendingRightSlotClick.tabId
                && !rightDragSlots.contains(slotClick.slotIndex)) {
                rightDragSlots.add(slotClick.slotIndex);
            }
        } else if (!rightNowDown && rightMouseDown) {
            if (pendingRightSlotClick != null) {
                if (WirelessOverlay.isWirelessTab(pendingRightSlotClick.tabId)) {
                    if (rightDragSlots.size() > 1 && minecraft.thePlayer.inventory.getItemStack() != null) {
                        sendWirelessClick(pendingRightSlotClick, PacketWirelessAction.DRAG, rightDragSlots.size());
                    } else {
                        sendSlotAction(pendingRightSlotClick, 1, PacketOverlayClick.MODE_NORMAL);
                    }
                } else if (rightDragSlots.size() > 1 && minecraft.thePlayer.inventory.getItemStack() != null) {
                    NetworkHandler.INSTANCE.sendToServer(
                        new PacketOverlayDrag(
                            pendingRightSlotClick.tabId,
                            rightDragSlots,
                            1,
                            OverlayClientState.beginCursorAction()));
                } else {
                    int revision = OverlayClientState.beginCursorAction();
                    applyLocalRightClickPrediction(pendingRightSlotClick.tabId, pendingRightSlotClick.slotIndex);
                    NetworkHandler.INSTANCE.sendToServer(
                        new PacketOverlayClick(
                            pendingRightSlotClick.tabId,
                            pendingRightSlotClick.slotIndex,
                            1,
                            PacketOverlayClick.MODE_NORMAL,
                            revision));
                }
            }
            pendingRightSlotClick = null;
            rightDragSlots.clear();
        }

        leftMouseDown = nowDown;
        rightMouseDown = rightNowDown;
    }

    private void clearState() {
        if (activeGui != null && !tabs.isEmpty()) {
            panel.savePosition(activeGui);
        }
        // Return any outstanding auto-craft borrows before wiping overlay tabs.
        OverlayNeiSupport.forceSettleIfNeeded();
        activeGui = null;
        pendingScan = false;
        leftMouseDown = false;
        rightMouseDown = false;
        dragging = false;
        panel.releaseScrollbar();
        panel.clearModeCyclePending();
        minimizedDragCandidate = false;
        minimizedDragMoved = false;
        pendingSlotClick = null;
        pendingRightSlotClick = null;
        panel.clearDragPreview();
        dragSlots.clear();
        rightDragSlots.clear();
        backpackInventoryFingerprint = "";
        selectedTabId = -1;
        lastHandledWheelEventNanos = Long.MIN_VALUE;
        for (int i = 0; i < hotbarKeyDown.length; i++) {
            hotbarKeyDown[i] = false;
        }
        toggleKeyWasDown = false;
        tabs.clear();
        OverlayClientState.clear();
        NetworkHandler.INSTANCE.sendToServer(new PacketCloseOverlay(sessionId));
        sendWirelessControl(PacketWirelessAction.CLOSE);
    }

    private void handleHotbarKeys(GuiScreen gui) {
        if (isTextInputFocused(gui)) {
            for (int i = 0; i < hotbarKeyDown.length; i++) {
                hotbarKeyDown[i] = Keyboard.isKeyDown(Keyboard.KEY_1 + i);
            }
            return;
        }

        ScaledResolution scaled = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int mouseX = Mouse.getX() * scaled.getScaledWidth() / minecraft.displayWidth;
        int mouseY = scaled.getScaledHeight() - Mouse.getY() * scaled.getScaledHeight() / minecraft.displayHeight - 1;
        OverlaySlotClick slotClick = panel.getSlotClickAt(mouseX, mouseY);

        for (int i = 0; i < hotbarKeyDown.length; i++) {
            int key = Keyboard.KEY_1 + i;
            boolean down = Keyboard.isKeyDown(key);
            if (down && !hotbarKeyDown[i] && slotClick != null) {
                sendSlotAction(slotClick, i, PacketOverlayClick.MODE_HOTBAR);
            }
            hotbarKeyDown[i] = down;
        }
    }

    /**
     * Toggle minimize while a container GUI is open. Uses {@link Keyboard#isKeyDown} edge
     * detection because with a screen open Minecraft does not feed {@link KeyBinding#isPressed()}.
     */
    private void handleHotkey(GuiScreen gui) {
        if (KeyBindings.toggleOverlay == null || gui == null) {
            toggleKeyWasDown = false;
            return;
        }
        int keyCode = KeyBindings.toggleOverlay.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE || keyCode == 0) {
            toggleKeyWasDown = false;
            return;
        }
        boolean down = Keyboard.isKeyDown(keyCode);
        if (isTextInputFocused(gui)) {
            toggleKeyWasDown = down;
            return;
        }
        if (down && !toggleKeyWasDown) {
            tryToggleMinimized(gui);
        }
        toggleKeyWasDown = down;
    }

    /**
     * From {@code GuiContainer.keyTyped}: swallow the toggle key so the GUI does not use it.
     * Actual toggle is done on client tick via {@link #handleHotkey} (edge detect).
     *
     * @return true if this key is our overlay toggle bind while the overlay session is active
     */
    public static boolean handleOverlayToggleKey(int keyCode) {
        if (instance == null || instance.activeGui == null || KeyBindings.toggleOverlay == null) {
            return false;
        }
        int bind = KeyBindings.toggleOverlay.getKeyCode();
        if (bind == Keyboard.KEY_NONE || bind == 0 || keyCode != bind) {
            return false;
        }
        return instance.minecraft.currentScreen == instance.activeGui && !instance.tabs.isEmpty()
            && !isTextInputFocused(instance.activeGui);
    }

    public static boolean isTextInputFocused(GuiScreen gui) {
        return (instance != null && instance.panel.isSearchFocused()) || GuiTextInputFocus.hasFocusedTextInput(gui)
            || NeiOverlayIntegration.isTextInputFocused();
    }

    public static boolean handleSearchKey(GuiScreen gui, char character, int key) {
        return instance != null && instance.activeGui == gui
            && !instance.tabs.isEmpty()
            && instance.panel.keyTyped(character, key);
    }

    private void tryToggleMinimized(GuiScreen gui) {
        if (gui == null || tabs.isEmpty()) {
            return;
        }
        boolean before = Config.overlayMinimized;
        panel.toggleMinimized(gui);
        if (before != Config.overlayMinimized) {
            syncOverlaySettings();
        }
    }

    private static void syncOverlaySettings() {
        NetworkHandler.INSTANCE.sendToServer(new PacketOverlaySettings(Config.overlayMinimized));
    }

    public static boolean shouldCancelMouseEvent(int mouseX, int mouseY) {
        if (instance == null || instance.activeGui == null || instance.tabs.isEmpty()) {
            return false;
        }
        return instance.dragging || instance.panel.isScrollbarDragging()
            || instance.pendingSlotClick != null
            || instance.pendingRightSlotClick != null
            || instance.panel.isMouseOverOverlay(mouseX, mouseY);
    }

    public static boolean isActiveFor(GuiScreen gui) {
        return instance != null && instance.activeGui == gui && !instance.tabs.isEmpty();
    }

    /**
     * True while the overlay is expanded with tabs for the current GUI. Used for client
     * prioritize shift-guard — minimized overlay does not intercept shift.
     */
    public static boolean hasActiveOverlaySession() {
        return instance != null && instance.activeGui != null
            && instance.minecraft.currentScreen == instance.activeGui
            && !instance.tabs.isEmpty()
            && !Config.overlayMinimized;
    }

    public static BackpackOverlayPanel getPanel() {
        return instance == null ? null : instance.panel;
    }

    /** Active overlay tab id while the overlay is expanded; {@code -1} if none / minimized. */
    public static int getActiveOverlayTabId() {
        BackpackTab tab = getActiveOverlayTab();
        return tab == null ? -1 : tab.tabId;
    }

    /**
     * Currently selected overlay tab for NEI / shift. Prefer {@link OverlayClientState} contents
     * so NEI still works the tick after a GUI switch clears local lists.
     */
    public static BackpackTab getActiveOverlayTab() {
        if (instance == null || Config.overlayMinimized) {
            return null;
        }
        int preferredId = instance.selectedTabId;
        if (preferredId < 0) {
            preferredId = instance.panel.getActiveTabId();
        }

        List<BackpackTab> source = OverlayClientState.hasState() ? OverlayClientState.getTabs() : instance.tabs;
        if (source.isEmpty()) {
            BackpackTab fromPanel = instance.panel.getActiveTabOrNull();
            return fromPanel;
        }
        if (preferredId >= 0) {
            for (BackpackTab tab : source) {
                if (tab.tabId == preferredId) {
                    return tab;
                }
            }
        }
        // Fall back to panel selection / first tab so NEI is never left with nothing selected.
        BackpackTab fromPanel = instance.panel.getActiveTabOrNull();
        if (fromPanel != null) {
            return fromPanel;
        }
        return source.get(0);
    }

    public static void renderOverlayInContainer(GuiContainer gui, int mouseX, int mouseY) {
        if (instance == null || instance.tabs.isEmpty() || instance.activeGui == null) {
            return;
        }
        // ModularUI2 may pass the current GuiContainer while activeGui is the same screen
        // instance opened via GuiOpenEvent; also accept currentScreen identity.
        GuiScreen current = instance.minecraft.currentScreen;
        if (instance.activeGui != gui && instance.activeGui != current && gui != current) {
            return;
        }
        instance.renderedInContainerPass = true;
        instance.applyDragPreview();
        instance.panel.render(gui, mouseX, mouseY);
    }

    private void applyDragPreview() {
        ItemStack cursor = minecraft.thePlayer == null ? null : minecraft.thePlayer.inventory.getItemStack();
        if (pendingSlotClick != null && cursor != null && dragSlots.size() > 0) {
            // Left drag-place preview only when holding an item (vanilla distribute).
            BackpackTab tab = findTab(pendingSlotClick.tabId);
            if (tab != null && isValidForClientPrediction(tab, cursor)) {
                panel.setDragPreview(pendingSlotClick.tabId, dragSlots, 0, cursor);
            } else {
                panel.clearDragPreview();
            }
        } else if (pendingRightSlotClick != null && cursor != null && rightDragSlots.size() > 0) {
            BackpackTab tab = findTab(pendingRightSlotClick.tabId);
            if (tab != null && isValidForClientPrediction(tab, cursor)) {
                panel.setDragPreview(pendingRightSlotClick.tabId, rightDragSlots, 1, cursor);
            } else {
                panel.clearDragPreview();
            }
        } else {
            panel.clearDragPreview();
        }
    }

    private BackpackTab findTab(int tabId) {
        for (BackpackTab tab : tabs) {
            if (tab.tabId == tabId) {
                return tab;
            }
        }
        return null;
    }

    private void renderPostFallbackTop(GuiScreen gui, int mouseX, int mouseY) {
        if (!(gui instanceof GuiContainer)) {
            return;
        }

        GuiContainer containerGui = (GuiContainer) gui;
        ItemStack cursorStack = minecraft.thePlayer.inventory.getItemStack();
        if (cursorStack != null) {
            renderCursorStack(cursorStack, mouseX - 8, mouseY - 8);
            return;
        }

        Slot slot = getSlotAt(containerGui, mouseX, mouseY);
        if (slot != null && slot.getHasStack()) {
            renderTooltip(slot.getStack(), mouseX, mouseY);
        }
    }

    private Slot getSlotAt(GuiContainer gui, int mouseX, int mouseY) {
        if (!(gui instanceof OverlayGuiContainerAccess)) {
            return null;
        }

        OverlayGuiContainerAccess access = (OverlayGuiContainerAccess) gui;
        int guiLeft = access.backpackenhance$getGuiLeft();
        int guiTop = access.backpackenhance$getGuiTop();
        for (Object object : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            if (slot.func_111238_b() && mouseX >= x && mouseY >= y && mouseX < x + 16 && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private void renderCursorStack(ItemStack stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 700.0F);
        OverlayGlState.beginItemIcon();
        renderItem.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        renderItem.renderItemOverlayIntoGUI(minecraft.fontRenderer, minecraft.getTextureManager(), stack, x, y);
        OverlayGlState.endItemIcon();
        GL11.glPopMatrix();
        OverlayGlState.restoreGuiItemLighting();
    }

    private void renderTooltip(ItemStack stack, int mouseX, int mouseY) {
        List<String> lines = stack.getTooltip(minecraft.thePlayer, minecraft.gameSettings.advancedItemTooltips);
        if (lines.isEmpty()) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        int height = lines.size() == 1 ? 8 : 8 + (lines.size() - 1) * 10;
        if (x + width > minecraft.currentScreen.width) {
            x -= 28 + width;
        }
        if (y + height + 6 > minecraft.currentScreen.height) {
            y = minecraft.currentScreen.height - height - 6;
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 700.0F);
        panel.drawTooltip(lines, x, y, width, height);
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x, y + (i == 0 ? 0 : 2 + i * 10), -1);
        }
        GL11.glPopMatrix();
    }

    private int resolveLeftClickMode(OverlaySlotClick slotClick) {
        if (GuiScreen.isShiftKeyDown()) {
            return PacketOverlayClick.MODE_SHIFT;
        }

        long now = Minecraft.getSystemTime();
        boolean doubleClick = slotClick.tabId == lastLeftTabId && slotClick.slotIndex == lastLeftSlotIndex
            && now - lastLeftSlotClickTime <= 250L;
        lastLeftTabId = slotClick.tabId;
        lastLeftSlotIndex = slotClick.slotIndex;
        lastLeftSlotClickTime = now;
        return doubleClick ? PacketOverlayClick.MODE_DOUBLE : PacketOverlayClick.MODE_NORMAL;
    }

    private void sendPendingLeftClick() {
        if (pendingSlotClick == null) {
            return;
        }
        if (WirelessOverlay.isWirelessTab(pendingSlotClick.tabId)) {
            ItemStack cursor = minecraft.thePlayer.inventory.getItemStack();
            if (pendingClickMode == PacketOverlayClick.MODE_NORMAL && dragSlots.size() > 1 && cursor != null) {
                sendWirelessClick(pendingSlotClick, PacketWirelessAction.DRAG, cursor.stackSize);
            } else {
                sendSlotAction(pendingSlotClick, 0, pendingClickMode);
            }
            return;
        }

        int revision = OverlayClientState.beginCursorAction();
        if (pendingClickMode == PacketOverlayClick.MODE_NORMAL && dragSlots.size() > 1
            && minecraft.thePlayer.inventory.getItemStack() != null) {
            NetworkHandler.INSTANCE.sendToServer(new PacketOverlayDrag(pendingSlotClick.tabId, dragSlots, 0, revision));
        } else {
            // Predict place/take locally so slot + cursor update together (no one-frame flash).
            if (pendingClickMode == PacketOverlayClick.MODE_NORMAL
                || pendingClickMode == PacketOverlayClick.MODE_DOUBLE) {
                applyLocalLeftClickPrediction(pendingSlotClick.tabId, pendingSlotClick.slotIndex, pendingClickMode);
            }
            NetworkHandler.INSTANCE.sendToServer(
                new PacketOverlayClick(
                    pendingSlotClick.tabId,
                    pendingSlotClick.slotIndex,
                    0,
                    pendingClickMode,
                    revision));
        }
    }

    /**
     * Mirror server left-click / double-collect on the client display so the hand and slot never
     * disagree for a frame while waiting for {@link PacketOverlayState}.
     */
    private void applyLocalLeftClickPrediction(int tabId, int slotIndex, int mode) {
        if (minecraft.thePlayer == null) {
            return;
        }
        BackpackTab tab = null;
        for (BackpackTab t : tabs) {
            if (t.tabId == tabId) {
                tab = t;
                break;
            }
        }
        if (tab == null) {
            return;
        }

        ItemStack cursor = minecraft.thePlayer.inventory.getItemStack();
        ItemStack slotStack = tab.getSlotStack(slotIndex);

        if (mode == PacketOverlayClick.MODE_DOUBLE) {
            if (cursor == null || cursor.stackSize >= cursor.getMaxStackSize()) {
                return;
            }
            int limit = cursor.getMaxStackSize();
            ItemStack working = cursor.copy();
            for (int i = 0; i < tab.storageSlots && working.stackSize < limit; i++) {
                ItemStack s = tab.getSlotStack(i);
                if (s == null || !sameStackMerge(working, s) || s.stackSize >= s.getMaxStackSize()) {
                    continue;
                }
                int move = Math.min(s.stackSize, limit - working.stackSize);
                working.stackSize += move;
                s = s.copy();
                s.stackSize -= move;
                tab.setSlotStack(i, s.stackSize <= 0 ? null : s);
            }
            minecraft.thePlayer.inventory.setItemStack(working);
            panel.setTabs(tabs);
            return;
        }

        // MODE_NORMAL — same rules as OverlayClickExecutor.leftClick
        if (cursor == null) {
            if (slotStack != null) {
                minecraft.thePlayer.inventory.setItemStack(slotStack.copy());
                tab.setSlotStack(slotIndex, null);
            }
        } else if (slotStack == null) {
            if (!isValidForClientPrediction(tab, cursor)) {
                return;
            }
            tab.setSlotStack(slotIndex, cursor.copy());
            minecraft.thePlayer.inventory.setItemStack(null);
        } else if (sameStackMerge(slotStack, cursor) && slotStack.stackSize < slotStack.getMaxStackSize()) {
            if (!isValidForClientPrediction(tab, cursor)) {
                return;
            }
            int limit = slotStack.getMaxStackSize();
            int move = Math.min(cursor.stackSize, limit - slotStack.stackSize);
            if (move > 0) {
                ItemStack newSlot = slotStack.copy();
                newSlot.stackSize += move;
                tab.setSlotStack(slotIndex, newSlot);
                ItemStack newCursor = cursor.copy();
                newCursor.stackSize -= move;
                minecraft.thePlayer.inventory.setItemStack(newCursor.stackSize <= 0 ? null : newCursor);
            }
        } else {
            if (!isValidForClientPrediction(tab, cursor)) {
                return;
            }
            tab.setSlotStack(slotIndex, cursor.copy());
            minecraft.thePlayer.inventory.setItemStack(slotStack.copy());
        }
        panel.setTabs(tabs);
    }

    private static boolean isValidForClientPrediction(BackpackTab tab, ItemStack stack) {
        return !tab.isWireless()
            && (tab.kind != BackpackKind.FORESTRY || ForestryModeBridge.isItemValid(tab.stack, stack));
    }

    private static boolean sameStackMerge(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** Mirror server rightClick for instant cursor/slot feedback. */
    private void applyLocalRightClickPrediction(int tabId, int slotIndex) {
        if (minecraft.thePlayer == null) {
            return;
        }
        BackpackTab tab = null;
        for (BackpackTab t : tabs) {
            if (t.tabId == tabId) {
                tab = t;
                break;
            }
        }
        if (tab == null) {
            return;
        }
        ItemStack cursor = minecraft.thePlayer.inventory.getItemStack();
        ItemStack slotStack = tab.getSlotStack(slotIndex);

        if (cursor == null) {
            if (slotStack != null) {
                int take = (slotStack.stackSize + 1) / 2;
                ItemStack taken = slotStack.copy();
                taken.stackSize = take;
                ItemStack left = slotStack.copy();
                left.stackSize -= take;
                minecraft.thePlayer.inventory.setItemStack(taken);
                tab.setSlotStack(slotIndex, left.stackSize <= 0 ? null : left);
            }
        } else if (slotStack == null) {
            if (!isValidForClientPrediction(tab, cursor)) {
                return;
            }
            ItemStack placed = cursor.copy();
            placed.stackSize = 1;
            tab.setSlotStack(slotIndex, placed);
            ItemStack newCursor = cursor.copy();
            newCursor.stackSize--;
            minecraft.thePlayer.inventory.setItemStack(newCursor.stackSize <= 0 ? null : newCursor);
        } else if (sameStackMerge(slotStack, cursor) && slotStack.stackSize < slotStack.getMaxStackSize()) {
            if (!isValidForClientPrediction(tab, cursor)) {
                return;
            }
            ItemStack newSlot = slotStack.copy();
            newSlot.stackSize++;
            tab.setSlotStack(slotIndex, newSlot);
            ItemStack newCursor = cursor.copy();
            newCursor.stackSize--;
            minecraft.thePlayer.inventory.setItemStack(newCursor.stackSize <= 0 ? null : newCursor);
        }
        panel.setTabs(tabs);
    }
}
