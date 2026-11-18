package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hepdd.backpackenhance.Config;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.WirelessItemEntry;
import com.hepdd.backpackenhance.integration.WirelessOverlay;
import com.hepdd.backpackenhance.net.packet.PacketOverlayActiveTab;
import com.hepdd.backpackenhance.net.packet.PacketRequestOverlay;
import com.hepdd.backpackenhance.net.packet.PacketWirelessAction;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;
import com.hepdd.backpackenhance.server.overlay.OverlaySlotSnapshot;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;

public class OverlayControllerLifecycleTest {

    private static Item item;

    private OverlayController controller;
    private OverlayController previousInstance;
    private boolean previousMinimized;
    private boolean hasPlayer;
    private GuiScreen currentScreen;
    private Container openContainer;
    private Container inventoryContainer;
    private final List<IMessage> sentPackets = new ArrayList<IMessage>();

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        item = new Item() {

            @Override
            public String getItemStackDisplayName(ItemStack stack) {
                return "Test Item";
            }
        };
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 29997, "backpackenhance:controller_lifecycle_test", item);
    }

    @Before
    public void setUp() throws ReflectiveOperationException {
        previousMinimized = Config.overlayMinimized;
        Config.overlayMinimized = false;
        previousInstance = (OverlayController) getStatic("instance");
        OverlayClientState.clear();
        sentPackets.clear();
        hasPlayer = false;
        currentScreen = null;
        inventoryContainer = newContainer(0);
        openContainer = inventoryContainer;
        controller = new OverlayController() {

            @Override
            boolean hasClientPlayer() {
                return hasPlayer;
            }

            @Override
            GuiScreen currentScreen() {
                return currentScreen;
            }

            @Override
            Container currentOpenContainer() {
                return openContainer;
            }

            @Override
            Container playerInventoryContainer() {
                return inventoryContainer;
            }

            @Override
            void sendToServer(IMessage packet) {
                sentPackets.add(packet);
            }
        };
    }

    @After
    public void tearDown() throws ReflectiveOperationException {
        OverlayClientState.clear();
        Config.overlayMinimized = previousMinimized;
        setStatic("instance", previousInstance);
    }

    @Test
    public void resolveOpenWirelessTabIdPrefersWirelessPreferredTabWhenActiveClamped()
        throws ReflectiveOperationException {
        BackpackOverlayPanel panel = (BackpackOverlayPanel) getField(controller, "panel");
        setField(panel, "preferredTabId", WirelessOverlay.TAB_BASE);
        assertEquals(-1, panel.getActiveTabId());
        assertEquals(WirelessOverlay.TAB_BASE, panel.getPreferredTabId());
        assertEquals(WirelessOverlay.TAB_BASE, resolveOpenWirelessTabId());

        BackpackTab physical = new BackpackTab(0, 9, BackpackKind.FORESTRY, new ItemStack(item), 5, 15);
        panel.setTabs(Collections.singletonList(physical));
        assertEquals(0, panel.getActiveTabId());
        assertEquals(WirelessOverlay.TAB_BASE, resolveOpenWirelessTabId());

        BackpackTab wireless = new BackpackTab(
            WirelessOverlay.TAB_BASE + 1,
            1,
            BackpackKind.AE2_WIRELESS,
            new ItemStack(item),
            9,
            0);
        panel.setTabs(Collections.singletonList(wireless));
        assertEquals(WirelessOverlay.TAB_BASE + 1, resolveOpenWirelessTabId());

        setField(panel, "preferredTabId", 0);
        panel.setTabs(Collections.singletonList(physical));
        assertEquals(0, resolveOpenWirelessTabId());
    }

    @Test
    public void consumeIncomingStateKeepsWirelessOpenAndAvoidsRelockingCompletedTab()
        throws ReflectiveOperationException {
        BackpackOverlayPanel panel = (BackpackOverlayPanel) getField(controller, "panel");
        setField(panel, "preferredTabId", WirelessOverlay.TAB_BASE);
        setField(controller, "sentActiveTab", Integer.MIN_VALUE);
        setField(controller, "lastSentWirelessTabId", WirelessOverlay.TAB_BASE);
        setField(controller, "lastSentWirelessMinimized", false);
        setField(controller, "pendingPreferredWirelessTabId", WirelessOverlay.TAB_BASE);
        OverlayClientState.expectWirelessSession(7);

        OverlayTabSnapshot physical = new OverlayTabSnapshot(
            0,
            9,
            BackpackKind.FORESTRY,
            "Backpack",
            5,
            15,
            new ItemStack(item),
            Collections.<OverlaySlotSnapshot>emptyList());
        OverlayClientState.setState(Collections.singletonList(physical), null, false);
        consumeIncomingState();

        assertEquals(0, panel.getActiveTabId());
        assertEquals(WirelessOverlay.TAB_BASE, getField(controller, "pendingPreferredWirelessTabId"));
        assertEquals(WirelessOverlay.TAB_BASE, getField(controller, "lastSentWirelessTabId"));
        assertEquals(1, sentPackets.size());
        assertTrue(sentPackets.get(0) instanceof PacketOverlayActiveTab);

        PacketWirelessState metadata = new PacketWirelessState(7, WirelessOverlay.TAB_BASE, 1, new ItemStack(item));
        PacketWirelessState items = new PacketWirelessState();
        items.sessionId = 7;
        items.tabId = WirelessOverlay.TAB_BASE;
        items.generation = 1;
        items.reset = true;
        items.complete = true;
        items.entries.add(new WirelessItemEntry(new ItemStack(item), 64L));

        OverlayClientState.enqueueWireless(metadata);
        OverlayClientState.enqueueWireless(items);
        consumeIncomingState();

        assertEquals(WirelessOverlay.TAB_BASE, panel.getActiveTabId());
        assertEquals(-1, getField(controller, "pendingPreferredWirelessTabId"));
        assertEquals(WirelessOverlay.TAB_BASE, getField(controller, "lastSentWirelessTabId"));
        assertEquals(2, sentPackets.size());
        assertTrue(sentPackets.get(1) instanceof PacketOverlayActiveTab);
        for (IMessage packet : sentPackets) {
            assertFalse(packet instanceof PacketWirelessAction);
        }

        BackpackTab activeTab = panel.getActiveTabOrNull();
        assertNotNull(activeTab);
        assertTrue(activeTab.isWireless());
        assertFalse(activeTab.wirelessLoading);
        assertNotNull(activeTab.getSlotStack(0));
        assertEquals(64L, activeTab.getStoredAmount(0));

        @SuppressWarnings("unchecked")
        List<BackpackTab> controllerTabs = (List<BackpackTab>) getField(controller, "tabs");
        assertEquals(2, controllerTabs.size());
    }

    @Test
    public void lifecycleEventHandlersIgnoreNullAndInactiveScreens() throws ReflectiveOperationException {
        controller.onInitGuiPost(null);
        controller.onDrawScreenPre(null);

        setField(controller, "activeGui", new GuiScreen() {});
        setField(controller, "pendingScan", true);
        controller.onInitGuiPost(null);
        controller.onDrawScreenPre(null);
        assertTrue((Boolean) getField(controller, "pendingScan"));
    }

    @Test
    public void containerWindowReadinessDistinguishesPlayerInventoryAndAssignedServerWindows() {
        Container playerInv = newContainer(0);
        GuiContainer inventoryGui = newGuiContainer(playerInv);
        assertTrue(OverlayController.isContainerWindowReady(inventoryGui, playerInv, playerInv));

        Container chestUnassigned = newContainer(0);
        GuiContainer chestGui = newGuiContainer(chestUnassigned);
        assertFalse(OverlayController.isContainerWindowReady(chestGui, chestUnassigned, playerInv));
        assertFalse(OverlayController.isContainerWindowReady(chestGui, playerInv, playerInv));

        chestUnassigned.windowId = 12;
        assertTrue(OverlayController.isContainerWindowReady(chestGui, chestUnassigned, playerInv));

        assertFalse(OverlayController.isContainerWindowReady(new GuiScreen() {}, playerInv, playerInv));
        assertFalse(OverlayController.isContainerWindowReady(chestGui, null, playerInv));
    }

    @Test
    public void playerInventoryOpensImmediatelyOnInitGuiPostWithZeroWindowId() throws ReflectiveOperationException {
        hasPlayer = true;
        GuiContainer inventoryGui = newGuiContainer(inventoryContainer);
        currentScreen = inventoryGui;
        openContainer = inventoryContainer;
        setField(controller, "activeGui", inventoryGui);
        setField(controller, "wirelessSessionId", 3);
        setField(controller, "pendingScan", true);

        controller.onInitGuiPost(new GuiScreenEvent.InitGuiEvent.Post(inventoryGui, new ArrayList<GuiButton>()));

        assertFalse((Boolean) getField(controller, "pendingScan"));
        assertEquals(2, sentPackets.size());
        PacketWirelessAction open = (PacketWirelessAction) sentPackets.get(0);
        assertEquals(PacketWirelessAction.OPEN, open.action);
        assertEquals(3, open.sessionId);
        assertEquals(0, open.windowId);
        assertTrue(sentPackets.get(1) instanceof PacketRequestOverlay);
    }

    @Test
    public void serverOpenedContainerDefersInitGuiPostAndSendsAssignedWindowIdOnDrawOrTick()
        throws ReflectiveOperationException {
        hasPlayer = true;
        BackpackOverlayPanel panel = (BackpackOverlayPanel) getField(controller, "panel");
        setField(panel, "preferredTabId", WirelessOverlay.TAB_BASE + 2);

        Container chestContainer = newContainer(0);
        GuiContainer chestGui = newGuiContainer(chestContainer);
        currentScreen = chestGui;
        openContainer = chestContainer;
        setField(controller, "activeGui", chestGui);
        setField(controller, "wirelessSessionId", 5);
        setField(controller, "pendingScan", true);

        // During S2DPacketOpenWindow / FML OpenGuiHandler displayGuiScreen, windowId is still 0.
        controller.onInitGuiPost(new GuiScreenEvent.InitGuiEvent.Post(chestGui, new ArrayList<GuiButton>()));
        assertTrue((Boolean) getField(controller, "pendingScan"));
        assertTrue(sentPackets.isEmpty());

        // Packet handler assigns server windowId before first DrawScreenEvent.Pre in the same frame.
        chestContainer.windowId = 17;
        controller.onDrawScreenPre(new GuiScreenEvent.DrawScreenEvent.Pre(chestGui, 0, 0, 0.0F));

        assertFalse((Boolean) getField(controller, "pendingScan"));
        assertEquals(2, sentPackets.size());
        PacketWirelessAction drawOpen = (PacketWirelessAction) sentPackets.get(0);
        assertEquals(PacketWirelessAction.OPEN, drawOpen.action);
        assertEquals(5, drawOpen.sessionId);
        assertEquals(17, drawOpen.windowId);
        assertEquals(WirelessOverlay.TAB_BASE + 2, drawOpen.tabId);
        assertTrue(sentPackets.get(1) instanceof PacketRequestOverlay);

        // Also verify the ClientTickEvent.END path when runTick finishes before rendering.
        sentPackets.clear();
        Container machineContainer = newContainer(0);
        GuiContainer machineGui = newGuiContainer(machineContainer);
        currentScreen = machineGui;
        openContainer = machineContainer;
        setField(controller, "activeGui", machineGui);
        setField(controller, "wirelessSessionId", 6);
        setField(controller, "pendingScan", true);

        controller.onInitGuiPost(new GuiScreenEvent.InitGuiEvent.Post(machineGui, new ArrayList<GuiButton>()));
        assertTrue((Boolean) getField(controller, "pendingScan"));
        assertTrue(sentPackets.isEmpty());

        machineContainer.windowId = 29;
        controller.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));

        assertFalse((Boolean) getField(controller, "pendingScan"));
        assertEquals(2, sentPackets.size());
        PacketWirelessAction tickOpen = (PacketWirelessAction) sentPackets.get(0);
        assertEquals(PacketWirelessAction.OPEN, tickOpen.action);
        assertEquals(6, tickOpen.sessionId);
        assertEquals(29, tickOpen.windowId);
        assertTrue(sentPackets.get(1) instanceof PacketRequestOverlay);
    }

    @Test
    public void reusedNonZeroContainerOpensOnInitAndWindowIdChangeReopensWireless()
        throws ReflectiveOperationException {
        hasPlayer = true;
        Container reusedContainer = newContainer(9);
        GuiContainer reusedGui = newGuiContainer(reusedContainer);
        currentScreen = reusedGui;
        openContainer = reusedContainer;
        setField(controller, "activeGui", reusedGui);
        setField(controller, "wirelessSessionId", 8);
        setField(controller, "pendingScan", true);

        controller.onInitGuiPost(new GuiScreenEvent.InitGuiEvent.Post(reusedGui, new ArrayList<GuiButton>()));

        assertFalse((Boolean) getField(controller, "pendingScan"));
        assertEquals(2, sentPackets.size());
        PacketWirelessAction initialOpen = (PacketWirelessAction) sentPackets.get(0);
        assertEquals(PacketWirelessAction.OPEN, initialOpen.action);
        assertEquals(9, initialOpen.windowId);

        sentPackets.clear();
        controller.onDrawScreenPre(new GuiScreenEvent.DrawScreenEvent.Pre(reusedGui, 0, 0, 0.0F));
        assertTrue(sentPackets.isEmpty());

        reusedContainer.windowId = 23;
        controller.onDrawScreenPre(new GuiScreenEvent.DrawScreenEvent.Pre(reusedGui, 0, 0, 0.0F));
        assertEquals(1, sentPackets.size());
        PacketWirelessAction reopenedOnDraw = (PacketWirelessAction) sentPackets.get(0);
        assertEquals(PacketWirelessAction.OPEN, reopenedOnDraw.action);
        assertEquals(8, reopenedOnDraw.sessionId);
        assertEquals(23, reopenedOnDraw.windowId);

        sentPackets.clear();
        reusedContainer.windowId = 31;
        controller.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        assertEquals(1, sentPackets.size());
        PacketWirelessAction reopenedOnTick = (PacketWirelessAction) sentPackets.get(0);
        assertEquals(PacketWirelessAction.OPEN, reopenedOnTick.action);
        assertEquals(8, reopenedOnTick.sessionId);
        assertEquals(31, reopenedOnTick.windowId);
    }

    private static Container newContainer(int windowId) {
        Container container = new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        };
        container.windowId = windowId;
        return container;
    }

    private static GuiContainer newGuiContainer(Container container) {
        return new GuiContainer(container) {

            @Override
            protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}
        };
    }

    private int resolveOpenWirelessTabId() throws ReflectiveOperationException {
        Method method = OverlayController.class.getDeclaredMethod("resolveOpenWirelessTabId");
        method.setAccessible(true);
        return ((Integer) method.invoke(controller)).intValue();
    }

    private void consumeIncomingState() throws ReflectiveOperationException {
        Method method = OverlayController.class.getDeclaredMethod("consumeIncomingState");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target instanceof OverlayController ? OverlayController.class : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Class<?> type = target instanceof OverlayController ? OverlayController.class : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getStatic(String name) throws ReflectiveOperationException {
        Field field = OverlayController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStatic(String name, Object value) throws ReflectiveOperationException {
        Field field = OverlayController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
