package com.hepdd.backpackenhance.integration.ae2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.BeforeClass;
import org.junit.Test;

import com.hepdd.backpackenhance.integration.WirelessOverlay;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;

public class WirelessOverlayServiceTest {

    private static Item item;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        item = new Item();
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 29981, "backpackenhance:wireless_service_test", item);
    }

    @Test
    public void shouldFlushOnTickAllowsImmediateAndLoadingTicksThenThrottles() {
        // Odd tick: allowed during fullUpdate or loading, throttled afterward.
        assertTrue(WirelessOverlayService.shouldFlushOnTick(1, true, true));
        assertTrue(WirelessOverlayService.shouldFlushOnTick(1, true, false));
        assertTrue(WirelessOverlayService.shouldFlushOnTick(1, false, true));
        assertFalse(WirelessOverlayService.shouldFlushOnTick(1, false, false));

        // Even tick: always allowed.
        assertTrue(WirelessOverlayService.shouldFlushOnTick(2, false, false));
    }

    @Test
    public void openTargetingWirelessTabListensAndFlushesOnFirstOddTick() {
        List<IAEItemStack> entries = buildEntries(12);
        IMEMonitor<IAEItemStack> monitor = fakeMonitor(entries);
        WirelessOverlayService.SyncState sync = new WirelessOverlayService.SyncState();
        List<PacketWirelessState> sent = new ArrayList<PacketWirelessState>();

        int wirelessTabId = WirelessOverlay.TAB_BASE;
        // OPEN sets session.activeTab = wirelessTabId, minimized = false, and connection metadata sets visible = true.
        sync.visible = true;
        boolean active = WirelessOverlayService.isConnectionActive(sync.visible, wirelessTabId, wirelessTabId, false);
        assertTrue(active);

        // First server tick happens to be odd (tick 1): must listen and flush immediately.
        WirelessOverlayService.stepConnection(
            1,
            active,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(7, wirelessTabId, 3, monitor, sent::add));

        assertTrue(sync.listening);
        assertFalse(sync.loading);
        assertEquals(1, sent.size());
        PacketWirelessState first = sent.get(0);
        assertEquals(7, first.sessionId);
        assertEquals(wirelessTabId, first.tabId);
        assertEquals(3, first.generation);
        assertTrue(first.reset);
        assertTrue(first.complete);
        assertEquals(12, first.entries.size());
    }

    @Test
    public void initialChunkingFlushesEveryTickUntilCompleteThenResumesTwoTickThrottle() {
        List<IAEItemStack> entries = buildEntries(600);
        IMEMonitor<IAEItemStack> monitor = fakeMonitor(entries);
        WirelessOverlayService.SyncState sync = new WirelessOverlayService.SyncState();
        List<PacketWirelessState> sent = new ArrayList<PacketWirelessState>();
        int tabId = WirelessOverlay.TAB_BASE + 2;
        sync.visible = true;
        boolean active = WirelessOverlayService.isConnectionActive(sync.visible, tabId, tabId, false);

        // Tick 1 (odd): listen + first chunk (256)
        WirelessOverlayService.stepConnection(
            1,
            active,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).reset);
        assertFalse(sent.get(0).complete);
        assertEquals(256, sent.get(0).entries.size());
        assertTrue(sync.loading);

        // Tick 2 (even): second chunk (256)
        WirelessOverlayService.stepConnection(
            2,
            active,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertEquals(2, sent.size());
        assertFalse(sent.get(1).reset);
        assertFalse(sent.get(1).complete);
        assertEquals(256, sent.get(1).entries.size());
        assertTrue(sync.loading);

        // Tick 3 (odd): initial loading is still incomplete, so must NOT be delayed by ticks % 2 == 0!
        WirelessOverlayService.stepConnection(
            3,
            active,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertEquals(3, sent.size());
        assertFalse(sent.get(2).reset);
        assertTrue(sent.get(2).complete);
        assertEquals(88, sent.get(2).entries.size());
        assertFalse(sync.loading);

        // Incremental phase: postChange arrives before odd tick 5 -> must wait for even tick 6.
        sync.postChange(Collections.singletonList(entries.get(0)));
        WirelessOverlayService.stepConnection(
            5,
            active,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertEquals(3, sent.size());

        WirelessOverlayService.stepConnection(
            6,
            active,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertEquals(4, sent.size());
        assertFalse(sent.get(3).reset);
        assertTrue(sent.get(3).complete);
        assertEquals(1, sent.get(3).entries.size());
    }

    @Test
    public void minimizedOrUnselectedTabDoesNotListenOrFlush() {
        List<IAEItemStack> entries = buildEntries(5);
        IMEMonitor<IAEItemStack> monitor = fakeMonitor(entries);
        WirelessOverlayService.SyncState sync = new WirelessOverlayService.SyncState();
        List<PacketWirelessState> sent = new ArrayList<PacketWirelessState>();
        int tabId = WirelessOverlay.TAB_BASE;
        sync.visible = true;

        boolean minimizedActive = WirelessOverlayService.isConnectionActive(sync.visible, tabId, tabId, true);
        assertFalse(minimizedActive);
        WirelessOverlayService.stepConnection(
            2,
            minimizedActive,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertFalse(sync.listening);
        assertTrue(sent.isEmpty());

        boolean otherTabActive = WirelessOverlayService.isConnectionActive(sync.visible, tabId, 0, false);
        assertFalse(otherTabActive);
        WirelessOverlayService.stepConnection(
            2,
            otherTabActive,
            true,
            sync,
            sync::startListening,
            sync::stopListening,
            () -> sync.flush(1, tabId, 1, monitor, sent::add));
        assertFalse(sync.listening);
        assertTrue(sent.isEmpty());
    }

    private static List<IAEItemStack> buildEntries(int count) {
        List<IAEItemStack> result = new ArrayList<IAEItemStack>(count);
        for (int i = 0; i < count; i++) {
            result.add(fakeAeStack(i, 64));
        }
        return result;
    }

    private static IAEItemStack fakeAeStack(int variant, long amount) {
        ItemStack stack = new ItemStack(item, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("variant", variant);
        stack.setTagCompound(tag);
        return (IAEItemStack) Proxy.newProxyInstance(
            IAEItemStack.class.getClassLoader(),
            new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getStackSize".equals(name)) return Long.valueOf(amount);
                if ("getItemStack".equals(name)) return stack.copy();
                if ("copy".equals(name)) return proxy;
                return null;
            });
    }

    @SuppressWarnings("unchecked")
    private static IMEMonitor<IAEItemStack> fakeMonitor(List<IAEItemStack> items) {
        IItemList<IAEItemStack> list = (IItemList<IAEItemStack>) Proxy.newProxyInstance(
            IItemList.class.getClassLoader(),
            new Class<?>[] { IItemList.class },
            (ignored, method, args) -> {
                String name = method.getName();
                if ("iterator".equals(name)) return items.iterator();
                if ("findPrecise".equals(name) && args != null && args.length == 1 && args[0] instanceof IAEItemStack) {
                    ItemStack target = ((IAEItemStack) args[0]).getItemStack();
                    for (IAEItemStack candidate : items) {
                        ItemStack s = candidate.getItemStack();
                        if (s.isItemEqual(target) && ItemStack.areItemStackTagsEqual(s, target)) {
                            return candidate;
                        }
                    }
                    return null;
                }
                return null;
            });
        return (IMEMonitor<IAEItemStack>) Proxy.newProxyInstance(
            IMEMonitor.class.getClassLoader(),
            new Class<?>[] { IMEMonitor.class },
            (ignored, method, args) -> {
                if ("getStorageList".equals(method.getName())) return list;
                return null;
            });
    }
}
