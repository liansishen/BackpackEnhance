package com.hepdd.backpackenhance.integration.ae2;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.backpackenhance.Config;
import com.hepdd.backpackenhance.integration.WirelessItemEntry;
import com.hepdd.backpackenhance.integration.WirelessOverlay;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.net.packet.PacketWirelessAction;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;
import com.hepdd.backpackenhance.server.overlay.OverlayClickExecutor;
import com.hepdd.backpackenhance.server.overlay.OverlaySessionTracker;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AEConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class WirelessOverlayService implements WirelessOverlay.Backend {

    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private final Queue<Runnable> requests = new ConcurrentLinkedQueue<Runnable>();
    private final OverlayClickExecutor transfers = new OverlayClickExecutor();
    private int nextGeneration;
    private int ticks;

    public static void init() {
        WirelessOverlayService service = new WirelessOverlayService();
        WirelessOverlay.backend = service;
        FMLCommonHandler.instance()
            .bus()
            .register(service);
    }

    @Override
    public void enqueue(EntityPlayerMP player, PacketWirelessAction action) {
        requests.add(() -> handle(player, action));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        for (int i = 0; i < 256; i++) {
            Runnable request = requests.poll();
            if (request == null) break;
            request.run();
        }
        Iterator<Session> iterator = sessions.values()
            .iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.player.isDead || !session.player.playerNetServerHandler.netManager.isChannelOpen()
                || session.player.openContainer == null) {
                session.close();
                iterator.remove();
                continue;
            }
            if (session.player.openContainer.windowId != session.windowId) {
                if (++session.windowMismatchTicks > 10) {
                    session.close();
                    iterator.remove();
                }
                continue;
            }
            session.windowMismatchTicks = 0;
            if (ticks % 10 == 0) scan(session);
            for (Connection connection : session.connections.values()) {
                boolean active = connection.visible && connection.tabId == session.activeTab && !session.minimized;
                if (!active) {
                    connection.unlisten();
                    continue;
                }
                if (!connection.listening && connection.availableForOverlay()) connection.listen();
                if (ticks % 2 == 0 && connection.listening) connection.flush();
                if (ticks % 10 == 0 && connection.valid() && !connection.nativeContainerUsesTerminal()) {
                    double drain = AEConfig.instance.wireless_getDrainRate(connection.host.getRange()) * 10;
                    connection.host.extractAEPower(drain, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    session.player.inventory.markDirty();
                    if (!connection.valid()) invalidate(connection);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        Session session = sessions.remove(event.player.getUniqueID());
        if (session != null) session.close();
    }

    private void handle(EntityPlayerMP player, PacketWirelessAction message) {
        Session session = sessions.get(player.getUniqueID());
        if (message.action == PacketWirelessAction.CLOSE) {
            if (session != null && session.id == message.sessionId) {
                session.close();
                sessions.remove(player.getUniqueID());
            }
            return;
        }
        if (player.isDead || !player.playerNetServerHandler.netManager.isChannelOpen()) return;
        if (player.openContainer == null || player.openContainer.windowId != message.windowId
            || !player.openContainer.canInteractWith(player)) return;
        if (message.action == PacketWirelessAction.OPEN) {
            if (session == null || session.player != player) {
                if (session != null) session.close();
                session = new Session(player);
                sessions.put(player.getUniqueID(), session);
            }
            if (message.sessionId < session.id) return;
            boolean newId = session.id != message.sessionId;
            session.id = message.sessionId;
            session.windowId = message.windowId;
            session.activeTab = message.tabId;
            session.minimized = message.minimized;
            scan(session);
            if (newId) {
                for (Connection connection : session.connections.values()) {
                    if (connection.availableForOverlay()) {
                        connection.metadata();
                        if (connection.listening) connection.fullUpdate = true;
                    }
                }
            }
            return;
        }
        if (session == null || session.id != message.sessionId) return;
        if (message.action == PacketWirelessAction.SELECT) {
            if (session.activeTab != message.tabId || session.minimized != message.minimized) {
                Connection previous = session.connections.get(session.activeTab - WirelessOverlay.TAB_BASE);
                if (previous != null) previous.unlisten();
            }
            session.activeTab = message.tabId;
            session.minimized = message.minimized;
            OverlaySessionTracker.setActiveTab(player, message.tabId);
            return;
        }
        Connection connection = session.connections.get(message.tabId - WirelessOverlay.TAB_BASE);
        if (connection == null || connection.generation != message.generation
            || session.minimized
            || session.activeTab != message.tabId
            || !connection.availableForOverlay()) {
            if (connection != null && !connection.availableForOverlay()) invalidate(connection);
            acknowledge(session, message.tabId, message.generation);
            return;
        }
        if (!connection.listening || connection.fullUpdate || connection.loading) {
            acknowledge(session, message.tabId, message.generation);
            return;
        }
        ItemStack cursor = player.inventory.getItemStack();
        if (message.action == PacketWirelessAction.CLICK && (message.value == 0 || message.value == 1)) {
            if (cursor != null) {
                int amount = message.value == 1 ? 1 : cursor.stackSize;
                storeCursor(connection, amount);
            } else if (message.template != null) {
                IAEItemStack available = connection.monitor.getStorageList()
                    .findPrecise(
                        AEApi.instance()
                            .storage()
                            .createItemStack(message.template));
                if (available != null) {
                    int amount = (int) Math.min(available.getStackSize(), message.template.getMaxStackSize());
                    if (message.value == 1) amount = (amount + 1) / 2;
                    player.inventory.setItemStack(connection.extract(message.template, amount));
                }
            }
        } else if (message.action == PacketWirelessAction.COLLECT && cursor != null) {
            ItemStack taken = connection.extract(cursor, cursor.getMaxStackSize() - cursor.stackSize);
            if (taken != null) cursor.stackSize += taken.stackSize;
        } else if (message.action == PacketWirelessAction.DRAG && cursor != null
            && message.value >= 1
            && message.value <= 256) {
                storeCursor(connection, Math.min(cursor.stackSize, message.value));
            } else if (message.action == PacketWirelessAction.SHIFT && cursor == null && message.template != null) {
                int capacity = transfers.getOpenContainerCapacity(player, message.template);
                ItemStack taken = connection.extract(message.template, capacity);
                if (taken != null) {
                    ItemStack remaining = transfers.moveToOpenContainer(player, taken);
                    if (remaining != null) {
                        ItemStack returned = connection.insert(remaining);
                        player.inventory.setItemStack(returned);
                    }
                }
            } else if (message.action == PacketWirelessAction.HOTBAR && cursor == null
                && message.value >= 0
                && message.value < 9
                && message.value != connection.playerSlot
                && message.template != null) {
                    ItemStack previous = player.inventory.mainInventory[message.value];
                    if (previous != null) player.inventory.mainInventory[message.value] = connection.insert(previous);
                    if (player.inventory.mainInventory[message.value] == null) {
                        player.inventory.mainInventory[message.value] = connection
                            .extract(message.template, message.template.getMaxStackSize());
                    }
                }
        player.inventory.markDirty();
        player.openContainer.detectAndSendChanges();
        player.updateHeldItem();
        acknowledge(session, message.tabId, message.generation);
    }

    private void storeCursor(Connection connection, int amount) {
        ItemStack cursor = connection.session.player.inventory.getItemStack();
        ItemStack moving = cursor.copy();
        moving.stackSize = amount;
        ItemStack remainder = connection.insert(moving);
        cursor.stackSize -= amount - (remainder == null ? 0 : remainder.stackSize);
        if (cursor.stackSize <= 0) connection.session.player.inventory.setItemStack(null);
    }

    private void acknowledge(Session session, int tabId, int generation) {
        PacketWirelessState packet = new PacketWirelessState(session.id, tabId, generation, null);
        packet.updateCursor = true;
        ItemStack cursor = session.player.inventory.getItemStack();
        packet.cursor = cursor == null ? null : cursor.copy();
        NetworkHandler.INSTANCE.sendTo(packet, session.player);
    }

    @Override
    public boolean shiftInto(EntityPlayerMP player, Slot source) {
        Session session = sessions.get(player.getUniqueID());
        if (session == null || session.minimized
            || player.openContainer.windowId != session.windowId
            || !WirelessOverlay.isWirelessTab(session.activeTab)) return false;
        Connection connection = session.connections.get(session.activeTab - WirelessOverlay.TAB_BASE);
        if (connection == null || !connection.availableForOverlay() || !source.canTakeStack(player)) return false;
        ItemStack original = source.getStack();
        if (original == null || original == connection.terminal) return false;
        ItemStack remainder = connection.insert(original.copy());
        if (remainder != null && remainder.stackSize == original.stackSize) return false;
        source.putStack(remainder);
        source.onSlotChanged();
        player.inventory.markDirty();
        player.openContainer.detectAndSendChanges();
        return true;
    }

    private void scan(Session session) {
        int first = Config.overlayScanHotbar ? 0 : 9;
        for (int slot = 0; slot < 36; slot++) {
            Connection existing = session.connections.get(slot);
            ItemStack terminal = session.player.inventory.mainInventory[slot];
            if (existing != null && existing.terminal == terminal && existing.valid()) {
                if (existing.nativeContainerUsesNetwork()) invalidate(existing);
                else if (!existing.visible) existing.metadata();
                continue;
            }
            if (existing != null) {
                invalidate(existing);
                existing.unlisten();
                session.connections.remove(slot);
            }
            if (slot < first || terminal == null || !Config.isModEnabled("appliedenergistics2")) continue;
            IWirelessTermHandler handler = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            if (handler == null) continue;
            String key = handler.getEncryptionKey(terminal);
            if (!numericKey(key)) continue;
            Connection connection = new Connection(session, slot, terminal, handler, key);
            if (connection.valid()) {
                session.connections.put(slot, connection);
                if (!connection.nativeContainerUsesNetwork()) connection.metadata();
            }
        }
    }

    static boolean isSameTerminalNetwork(Object target, IGridNode containerNode, IGrid network) {
        IGridNode node = target instanceof IActionHost ? ((IActionHost) target).getActionableNode()
            : target instanceof IGridHost ? ((IGridHost) target).getGridNode(ForgeDirection.UNKNOWN) : containerNode;
        return node != null && network != null && node.getGrid() == network;
    }

    static boolean canAccessStorage(ISecurityGrid security, EntityPlayerMP player) {
        return security.hasPermission(player, SecurityPermissions.INJECT)
            || security.hasPermission(player, SecurityPermissions.EXTRACT);
    }

    private static boolean numericKey(String key) {
        if (key == null || key.isEmpty()) return false;
        try {
            Long.parseLong(key);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void invalidate(Connection connection) {
        connection.unlisten();
        if (!connection.visible) return;
        connection.visible = false;
        PacketWirelessState packet = new PacketWirelessState(
            connection.session.id,
            connection.tabId,
            connection.generation,
            null);
        packet.removed = true;
        NetworkHandler.INSTANCE.sendTo(packet, connection.session.player);
    }

    private final class Session {

        private final EntityPlayerMP player;
        private final Map<Integer, Connection> connections = new LinkedHashMap<Integer, Connection>();
        private int id = -1;
        private int windowId;
        private int windowMismatchTicks;
        private int activeTab = -1;
        private boolean minimized;

        private Session(EntityPlayerMP player) {
            this.player = player;
        }

        private void close() {
            for (Connection connection : connections.values()) connection.unlisten();
            connections.clear();
        }
    }

    private final class Connection implements IMEMonitorHandlerReceiver<IAEItemStack> {

        private final Session session;
        private final int playerSlot;
        private final int tabId;
        private final int generation;
        private final ItemStack terminal;
        private final IWirelessTermHandler handler;
        private final String key;
        private final boolean infiniteRange;
        private final boolean infinitePower;
        private final WirelessTerminalGuiObject host;
        private final PlayerSource source;
        private final IMEMonitor<IAEItemStack> monitor;
        private final Map<WirelessItemEntry, IAEItemStack> changes = new LinkedHashMap<WirelessItemEntry, IAEItemStack>();
        private final Queue<WirelessItemEntry> pending = new ArrayDeque<WirelessItemEntry>();
        private boolean listening;
        private boolean fullUpdate;
        private boolean resetNext;
        private boolean visible;
        private boolean loading;

        private Connection(Session session, int slot, ItemStack terminal, IWirelessTermHandler handler, String key) {
            this.session = session;
            this.playerSlot = slot;
            this.tabId = WirelessOverlay.TAB_BASE + slot;
            this.generation = ++nextGeneration;
            this.terminal = terminal;
            this.handler = handler;
            this.key = key;
            this.infiniteRange = handler.hasInfinityRange(terminal);
            this.infinitePower = handler.hasInfinityPower(terminal);
            this.host = new WirelessTerminalGuiObject(
                handler,
                terminal,
                session.player,
                session.player.worldObj,
                slot,
                0,
                0);
            this.source = new PlayerSource(session.player, host);
            this.monitor = host.getItemInventory();
        }

        private boolean valid() {
            if (session.player.inventory.mainInventory[playerSlot] != terminal
                || !key.equals(handler.getEncryptionKey(terminal))
                || infiniteRange != handler.hasInfinityRange(terminal)
                || infinitePower != handler.hasInfinityPower(terminal)
                || monitor == null
                || !Config.isModEnabled("appliedenergistics2")) return false;
            if (playerSlot < (Config.overlayScanHotbar ? 0 : 9) || !host.rangeCheck()) return false;
            Object station = AEApi.instance()
                .registries()
                .locatable()
                .getLocatableBy(Long.parseLong(key));
            if (!(station instanceof IGridHost)) return false;
            IGridNode stationNode = ((IGridHost) station).getGridNode(ForgeDirection.UNKNOWN);
            if (stationNode == null || stationNode.getGrid() != host.getGrid()) return false;
            if (!AEApi.instance()
                .registries()
                .wireless()
                .checkRange(terminal, session.player)) return false;
            IGridNode node = host.getActionableNode();
            if (node == null || !node.isActive()
                || ((IStorageGrid) host.getGrid()
                    .getCache(IStorageGrid.class)).getItemInventory() != monitor)
                return false;
            return (infinitePower || handler.hasPower(session.player, 0.5, terminal)) && canAccessStorage(
                host.getGrid()
                    .getCache(ISecurityGrid.class),
                session.player);
        }

        private boolean availableForOverlay() {
            return valid() && !nativeContainerUsesNetwork();
        }

        private boolean nativeContainerUsesNetwork() {
            if (!(session.player.openContainer instanceof AEBaseContainer)) return false;
            AEBaseContainer container = (AEBaseContainer) session.player.openContainer;
            Object target = container.getTarget();
            if (!(target instanceof ITerminalHost) && !(target instanceof IInterfaceTerminal)) return false;
            IGridNode node = container instanceof ContainerMEMonitorable
                ? ((ContainerMEMonitorable) container).getNetworkNode()
                : null;
            return isSameTerminalNetwork(target, node, host.getGrid());
        }

        private boolean nativeContainerUsesTerminal() {
            if (!(session.player.openContainer instanceof AEBaseContainer)) return false;
            Object target = ((AEBaseContainer) session.player.openContainer).getTarget();
            if (!(target instanceof WirelessTerminalGuiObject)) return false;
            WirelessTerminalGuiObject nativeHost = (WirelessTerminalGuiObject) target;
            return nativeHost.getInventorySlot() == playerSlot && nativeHost.getItemStack() == terminal;
        }

        private boolean permitted(SecurityPermissions permission) {
            ISecurityGrid security = host.getGrid()
                .getCache(ISecurityGrid.class);
            return security.hasPermission(session.player, permission);
        }

        private ItemStack insert(ItemStack stack) {
            if (!valid() || !permitted(SecurityPermissions.INJECT)) return stack;
            IAEItemStack remainder = Platform.poweredInsert(
                host,
                monitor,
                AEApi.instance()
                    .storage()
                    .createItemStack(stack),
                source);
            return remainder == null ? null : remainder.getItemStack();
        }

        private ItemStack extract(ItemStack template, int amount) {
            if (amount <= 0 || !valid() || !permitted(SecurityPermissions.EXTRACT)) return null;
            IAEItemStack request = AEApi.instance()
                .storage()
                .createItemStack(template);
            request.setStackSize(Math.min(amount, template.getMaxStackSize()));
            IAEItemStack extracted = Platform.poweredExtraction(host, monitor, request, source);
            return extracted == null ? null : extracted.getItemStack();
        }

        private void metadata() {
            visible = true;
            NetworkHandler.INSTANCE
                .sendTo(new PacketWirelessState(session.id, tabId, generation, terminal), session.player);
        }

        private void listen() {
            monitor.addListener(this, this);
            listening = true;
            fullUpdate = true;
            loading = true;
        }

        private void unlisten() {
            if (listening) monitor.removeListener(this);
            listening = false;
            pending.clear();
            changes.clear();
        }

        private void flush() {
            if (!availableForOverlay()) {
                invalidate(this);
                return;
            }
            if (fullUpdate) {
                pending.clear();
                changes.clear();
                for (IAEItemStack item : monitor.getStorageList()) {
                    if (item.getStackSize() > 0)
                        pending.add(new WirelessItemEntry(item.getItemStack(), item.getStackSize()));
                }
                fullUpdate = false;
                resetNext = true;
                loading = true;
            } else if (pending.isEmpty() && !changes.isEmpty()) {
                IItemList<IAEItemStack> storage = monitor.getStorageList();
                for (IAEItemStack changed : changes.values()) {
                    IAEItemStack current = storage.findPrecise(changed);
                    pending.add(
                        new WirelessItemEntry(changed.getItemStack(), current == null ? 0 : current.getStackSize()));
                }
                changes.clear();
            }
            if (pending.isEmpty() && !resetNext) return;
            PacketWirelessState packet = new PacketWirelessState(session.id, tabId, generation, null);
            packet.reset = resetNext;
            resetNext = false;
            ByteBuf sizing = Unpooled.buffer();
            try {
                while (!pending.isEmpty() && packet.entries.size() < 256) {
                    int before = sizing.writerIndex();
                    PacketWirelessState.writeEntry(sizing, pending.peek());
                    if (sizing.writerIndex() > 24000 && !packet.entries.isEmpty()) break;
                    packet.entries.add(pending.remove());
                    if (sizing.writerIndex() - before > 24000) break;
                }
            } finally {
                sizing.release();
            }
            packet.complete = pending.isEmpty();
            if (packet.complete) loading = false;
            NetworkHandler.INSTANCE.sendTo(packet, session.player);
        }

        @Override
        public boolean isValid(Object token) {
            return listening && token == this;
        }

        @Override
        public void postChange(IBaseMonitor<IAEItemStack> inventory, Iterable<IAEItemStack> changed,
            BaseActionSource action) {
            for (IAEItemStack item : changed) changes.put(new WirelessItemEntry(item.getItemStack(), 0), item.copy());
        }

        @Override
        public void onListUpdate() {
            fullUpdate = true;
        }
    }
}
