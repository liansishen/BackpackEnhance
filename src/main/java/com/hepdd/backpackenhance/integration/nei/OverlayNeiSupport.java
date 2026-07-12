package com.hepdd.backpackenhance.integration.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.Config;
import com.hepdd.backpackenhance.client.overlay.OverlayClientState;
import com.hepdd.backpackenhance.client.overlay.OverlayController;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.net.packet.PacketOverlayDeposit;
import com.hepdd.backpackenhance.net.packet.PacketOverlayExtract;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler.IngredientDistribution;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Bridges BackpackEnhance overlay inventories into NEI recipe transfer / presence / auto-craft.
 * <p>
 * Only the <strong>currently selected overlay tab</strong> is used. Minimized overlay is inactive.
 * <p>
 * <b>Manual + fill:</b> shortfall is placed into craft-matrix slots (direct extract).<br>
 * <b>Bookmark auto-craft:</b> shortfall is borrowed into the <em>player inventory</em> so NEI's
 * native multi-step craft/clear loop is undisturbed. All borrows are recorded in
 * {@link NeiBorrowLedger} and settled back to the overlay when auto-craft ends (tools and unused
 * consumables).
 */
@SideOnly(Side.CLIENT)
public final class OverlayNeiSupport {

    private static final NeiBorrowLedger LEDGER = new NeiBorrowLedger();
    private static boolean wasAutoCrafting;
    /** True after first borrow/session open for the current processing run. */
    private static boolean sessionActive;

    private OverlayNeiSupport() {}

    public static boolean hasOverlayItems() {
        return getActiveTabForNei() != null;
    }

    public static void addActiveTabToItemStackAmount(ItemStackAmount inventory) {
        if (inventory == null) {
            return;
        }
        BackpackTab tab = getActiveTabForNei();
        if (tab == null) {
            return;
        }
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack stack = tab.getSlotStack(i);
            if (stack != null && stack.stackSize > 0) {
                inventory.add(stack.copy());
            }
        }
    }

    private static int countMatchingInOverlay(ItemStack match) {
        if (match == null) {
            return 0;
        }
        BackpackTab tab = getActiveTabForNei();
        if (tab == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack stack = tab.getSlotStack(i);
            if (stack != null && stacksMatchForCrafting(match, stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    private static void addOverlayStacksToList(List<ItemStack> invStacks) {
        if (invStacks == null) {
            return;
        }
        BackpackTab tab = getActiveTabForNei();
        if (tab == null) {
            return;
        }
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack stack = tab.getSlotStack(i);
            if (stack != null && stack.stackSize > 0) {
                invStacks.add(stack.copy());
            }
        }
    }

    /**
     * Add overlay counts into NEI {@code DistributedIngred.invAmount}.
     * <p>
     * GT tools / container items in the overlay must also set {@code isContainerItem=true},
     * matching NEI's own scan of player inventory. Otherwise {@code calculateRecipeQuantity}
     * treats maxStack=1 tools as a hard cap and Shift+ only fills one craft.
     */
    public static void addOverlayAmountsToDistributed(ItemStack match, Object distributedIngred) {
        if (match == null || distributedIngred == null) {
            return;
        }
        try {
            java.lang.reflect.Field invAmount = distributedIngred.getClass()
                .getField("invAmount");
            int extra = countMatchingInOverlay(match);
            if (extra <= 0) {
                return;
            }
            invAmount.setInt(distributedIngred, invAmount.getInt(distributedIngred) + extra);
            if (overlayMatchIsContainerItem(match)) {
                java.lang.reflect.Field containerField = distributedIngred.getClass()
                    .getField("isContainerItem");
                containerField.setBoolean(distributedIngred, true);
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore NEI shape changes
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List buildPresenceOverlay(GuiContainer gui, List ingredients) {
        List states = new ArrayList();
        if (gui == null || ingredients == null) {
            return states;
        }
        List<ItemStack> invStacks = collectContainerStacks(gui);
        addOverlayStacksToList(invStacks);
        for (Object ingredientObj : ingredients) {
            if (!(ingredientObj instanceof PositionedStack)) {
                continue;
            }
            PositionedStack stack = (PositionedStack) ingredientObj;
            boolean found = false;
            for (ItemStack is : invStacks) {
                if (is != null && is.stackSize > 0 && stack.contains(is)) {
                    is.stackSize--;
                    found = true;
                    break;
                }
            }
            states.add(new ItemOverlayState(stack, found));
        }
        return states;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List enrichPresenceOverlay(GuiContainer gui, List existingStates) {
        if (gui == null || existingStates == null || existingStates.isEmpty() || !hasOverlayItems()) {
            return null;
        }
        List ingredients = new ArrayList();
        for (Object object : existingStates) {
            if (object instanceof ItemOverlayState) {
                ingredients.add(((ItemOverlayState) object).getSlot());
            }
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        return buildPresenceOverlay(gui, ingredients);
    }

    /**
     * Supply overlay shortfall before NEI moves ingredients.
     * Auto-craft → player inventory (native NEI path). Manual fill → craft matrix slots.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void prefillCraftSlotsFromOverlay(GuiContainer gui, List assignedIngredients, int multiplier) {
        if (gui == null || assignedIngredients == null || multiplier <= 0 || !hasOverlayItems()) {
            return;
        }
        if (Minecraft.getMinecraft().thePlayer == null || gui.inventorySlots == null) {
            return;
        }

        boolean autoCraft = isAutoCraftProcessing();
        List<PoolEntry> pool = buildActiveTabPool();
        if (pool.isEmpty()) {
            return;
        }

        if (autoCraft) {
            ensureSessionOpen();
            pullShortfallToPlayer(gui, assignedIngredients, multiplier, pool);
        } else {
            pullShortfallToCraftMatrix(gui, assignedIngredients, multiplier, pool);
        }
    }

    /**
     * Main-thread tick: track auto-craft processing edge and settle the borrow ledger.
     */
    public static void tickAutoCraftSettle() {
        boolean now = isAutoCraftProcessing();
        if (now && !wasAutoCrafting) {
            // Prefill on the NEI worker thread may have already opened the session — do not wipe it.
            ensureSessionOpen();
        }
        if ((wasAutoCrafting || sessionActive) && !now) {
            settleLedger();
            sessionActive = false;
        }
        wasAutoCrafting = now;
    }

    private static void ensureSessionOpen() {
        if (sessionActive) {
            return;
        }
        LEDGER.beginSession();
        sessionActive = true;
    }

    /** Force settle if overlay/GUI closes while a ledger is open. */
    public static void forceSettleIfNeeded() {
        if (!LEDGER.isEmpty() || sessionActive) {
            settleLedger();
        }
        sessionActive = false;
        wasAutoCrafting = false;
    }

    /**
     * Auto-craft shortfall → player inventory.
     * <p>
     * Shaped recipes often need the <em>same</em> item in multiple matrix slots. Needs must be
     * aggregated first; per-slot shortfall against full player count would only borrow once.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void pullShortfallToPlayer(GuiContainer gui, List assignedIngredients, int multiplier,
        List<PoolEntry> pool) {
        List<NeedGroup> needs = aggregateIngredientNeeds(assignedIngredients, multiplier);
        for (NeedGroup group : needs) {
            int inPlayer = countMatchingInPlayer(gui, group.match);
            int shortfall = group.totalNeed - inPlayer;
            if (shortfall <= 0) {
                continue;
            }
            pullFromPoolToPlayer(group.match, shortfall, pool);
        }
    }

    /**
     * Manual + shortfall → craft matrix slots.
     * Player inventory is shared across all matrix slots of the same item; reserve it as we go.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void pullShortfallToCraftMatrix(GuiContainer gui, List assignedIngredients, int multiplier,
        List<PoolEntry> pool) {
        int windowId = gui.inventorySlots.windowId;
        List<PlayerReserve> playerReserves = new ArrayList<PlayerReserve>();

        for (Object object : assignedIngredients) {
            if (!(object instanceof IngredientDistribution)) {
                continue;
            }
            IngredientDistribution distrib = (IngredientDistribution) object;
            if (distrib.permutation == null || distrib.slots == null || distrib.slots.length == 0) {
                continue;
            }
            ItemStack match = distrib.permutation;
            if (match.stackSize <= 0) {
                continue;
            }
            // Tools / container items: one instance serves every craft in a multi-fill.
            int totalNeed = needForIngredient(match, multiplier);
            // Cover as much as possible from player stocks not already reserved by earlier slots.
            int coveredByPlayer = takePlayerReserve(gui, playerReserves, match, totalNeed);
            int shortfall = totalNeed - coveredByPlayer;
            if (shortfall <= 0) {
                continue;
            }
            int remaining = shortfall;
            int destCapPerSlot = totalNeed;
            for (Slot dest : distrib.slots) {
                if (remaining <= 0 || dest == null || dest.inventory instanceof InventoryPlayer) {
                    continue;
                }
                int already = 0;
                if (dest.getHasStack()) {
                    ItemStack existing = dest.getStack();
                    if (!stacksMatchForCrafting(match, existing)) {
                        continue;
                    }
                    already = existing.stackSize;
                }
                int slotLimit = Math.min(dest.getSlotStackLimit(), match.getMaxStackSize());
                int space = Math.min(slotLimit, destCapPerSlot) - already;
                if (space <= 0) {
                    continue;
                }
                int want = Math.min(remaining, space);
                int filled = fillDestFromOverlay(windowId, dest, match, want, pool);
                remaining -= filled;
            }
        }
    }

    /**
     * Sum recipe needs across all ingredient slots that craft-match the same stack. Fixes shaped
     * recipes (e.g. 3 planks in different positions) only borrowing enough for the first slot.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<NeedGroup> aggregateIngredientNeeds(List assignedIngredients, int multiplier) {
        List<NeedGroup> groups = new ArrayList<NeedGroup>();
        if (assignedIngredients == null || multiplier <= 0) {
            return groups;
        }
        for (Object object : assignedIngredients) {
            if (!(object instanceof IngredientDistribution)) {
                continue;
            }
            IngredientDistribution distrib = (IngredientDistribution) object;
            if (distrib.permutation == null || distrib.permutation.stackSize <= 0) {
                continue;
            }
            ItemStack match = distrib.permutation;
            int need = needForIngredient(match, multiplier);
            boolean merged = false;
            for (NeedGroup group : groups) {
                if (stacksMatchForCrafting(group.match, match)) {
                    // Tools only need one total even if listed in multiple slots.
                    if (isContainerLikeTool(match)) {
                        group.totalNeed = Math.max(group.totalNeed, need);
                    } else {
                        group.totalNeed += need;
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                groups.add(new NeedGroup(match.copy(), need));
            }
        }
        return groups;
    }

    /** Consumables scale with multiplier; reusable tools only need one stack per fill. */
    private static int needForIngredient(ItemStack match, int multiplier) {
        int perCraft = Math.max(1, match.stackSize);
        if (isContainerLikeTool(match)) {
            return perCraft;
        }
        return perCraft * Math.max(1, multiplier);
    }

    /** Remaining player inventory credited toward covering recipe slots (manual path). */
    private static int takePlayerReserve(GuiContainer gui, List<PlayerReserve> reserves, ItemStack match, int want) {
        if (want <= 0) {
            return 0;
        }
        PlayerReserve reserve = null;
        for (PlayerReserve existing : reserves) {
            if (stacksMatchForCrafting(existing.match, match)) {
                reserve = existing;
                break;
            }
        }
        if (reserve == null) {
            reserve = new PlayerReserve(match.copy(), countMatchingInPlayer(gui, match));
            reserves.add(reserve);
        }
        int take = Math.min(want, Math.max(0, reserve.remaining));
        reserve.remaining -= take;
        return take;
    }

    private static int pullFromPoolToPlayer(ItemStack match, int amount, List<PoolEntry> pool) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || amount <= 0) {
            return 0;
        }
        int need = amount;
        int filled = 0;
        for (PoolEntry entry : pool) {
            if (need <= 0) {
                break;
            }
            if (entry.stack.stackSize <= 0 || !stacksMatchForCrafting(match, entry.stack)) {
                continue;
            }
            int take = Math.min(need, entry.stack.stackSize);
            ItemStack moving = entry.stack.copy();
            moving.stackSize = take;
            // Capture full NBT before mutating pool entry.
            ItemStack debtTemplate = moving.copy();
            ItemStack leftover = insertLocalPlayerInventory(mc.thePlayer.inventory, moving);
            int moved = take - (leftover == null ? 0 : leftover.stackSize);
            if (moved <= 0) {
                continue;
            }
            entry.stack.stackSize -= moved;
            rewriteTabSlot(entry.tabId, entry.slotIndex, entry.stack.stackSize <= 0 ? null : entry.stack);
            // containerSlot < 0 → extractToPlayer on server
            NetworkHandler.INSTANCE.sendToServer(new PacketOverlayExtract(entry.tabId, entry.slotIndex, moved, 0, -1));

            debtTemplate.stackSize = moved;
            LEDGER.record(entry.tabId, entry.slotIndex, debtTemplate, moved);

            filled += moved;
            need -= moved;
        }
        return filled;
    }

    private static void settleLedger() {
        List<NeiBorrowLedger.Entry> entries = LEDGER.drain();
        if (entries.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        for (NeiBorrowLedger.Entry entry : entries) {
            if (entry == null || entry.template == null || entry.amount <= 0) {
                continue;
            }
            int returned = removeFromPlayerInventory(mc.thePlayer.inventory, entry.template, entry.amount);
            if (returned <= 0) {
                continue;
            }
            // Prefer the tab that was borrowed from; fall back to active tab.
            int tabId = entry.tabId;
            if (findTab(tabId) == null) {
                tabId = OverlayController.getActiveOverlayTabId();
                if (tabId < 0) {
                    BackpackTab tab = getActiveTabForNei();
                    tabId = tab == null ? 0 : tab.tabId;
                }
            }
            applyLocalDepositToOverlay(tabId, entry.template, returned);
            ItemStack packetTemplate = entry.template.copy();
            packetTemplate.stackSize = 1;
            NetworkHandler.INSTANCE.sendToServer(new PacketOverlayDeposit(tabId, packetTemplate, returned));
        }
    }

    private static int removeFromPlayerInventory(InventoryPlayer inv, ItemStack template, int amount) {
        int remaining = amount;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack existing = inv.mainInventory[i];
            if (existing == null || !stacksMatchForCrafting(template, existing)) {
                continue;
            }
            int take = Math.min(remaining, existing.stackSize);
            existing.stackSize -= take;
            remaining -= take;
            if (existing.stackSize <= 0) {
                inv.mainInventory[i] = null;
            }
        }
        return amount - remaining;
    }

    private static void applyLocalDepositToOverlay(int tabId, ItemStack template, int amount) {
        BackpackTab tab = findTab(tabId);
        if (tab == null) {
            tab = getActiveTabForNei();
        }
        if (tab == null && template != null) {
            List<BackpackTab> tabs = OverlayClientState.getTabs();
            if (!tabs.isEmpty()) {
                tab = tabs.get(0);
            }
        }
        if (tab == null || amount <= 0 || template == null) {
            return;
        }
        int left = amount;
        ItemStack[] stacks = new ItemStack[tab.storageSlots];
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack cur = tab.getSlotStack(i);
            stacks[i] = cur == null ? null : cur.copy();
        }
        for (int i = 0; i < stacks.length && left > 0; i++) {
            if (stacks[i] != null && stacksMatchForCrafting(template, stacks[i])
                && stacks[i].stackSize < stacks[i].getMaxStackSize()) {
                int move = Math.min(left, stacks[i].getMaxStackSize() - stacks[i].stackSize);
                stacks[i].stackSize += move;
                left -= move;
            }
        }
        for (int i = 0; i < stacks.length && left > 0; i++) {
            if (stacks[i] == null) {
                ItemStack placed = template.copy();
                placed.stackSize = Math.min(left, template.getMaxStackSize());
                stacks[i] = placed;
                left -= placed.stackSize;
            }
        }
        tab.setSlotStacks(stacks);
    }

    private static int fillDestFromOverlay(int windowId, Slot dest, ItemStack match, int amount, List<PoolEntry> pool) {
        int need = amount;
        int filled = 0;
        for (PoolEntry entry : pool) {
            if (need <= 0) {
                break;
            }
            if (entry.stack.stackSize <= 0 || !stacksMatchForCrafting(match, entry.stack)) {
                continue;
            }
            int take = Math.min(need, entry.stack.stackSize);
            if (!applyLocalDestInsert(dest, entry.stack, take)) {
                continue;
            }
            entry.stack.stackSize -= take;
            rewriteTabSlot(entry.tabId, entry.slotIndex, entry.stack.stackSize <= 0 ? null : entry.stack);
            NetworkHandler.INSTANCE
                .sendToServer(new PacketOverlayExtract(entry.tabId, entry.slotIndex, take, windowId, dest.slotNumber));
            filled += take;
            need -= take;
        }
        return filled;
    }

    private static boolean applyLocalDestInsert(Slot dest, ItemStack source, int amount) {
        if (dest == null || source == null || amount <= 0) {
            return false;
        }
        ItemStack moving = source.copy();
        moving.stackSize = amount;
        if (!dest.isItemValid(moving)) {
            return false;
        }
        ItemStack existing = dest.getStack();
        if (existing == null) {
            dest.putStack(moving.copy());
            return true;
        }
        if (!stacksMatchForCrafting(existing, moving)) {
            return false;
        }
        int limit = Math.min(dest.getSlotStackLimit(), existing.getMaxStackSize());
        int space = limit - existing.stackSize;
        if (space < amount) {
            return false;
        }
        existing.stackSize += amount;
        dest.putStack(existing);
        return true;
    }

    private static ItemStack insertLocalPlayerInventory(InventoryPlayer inventory, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        // Mirror server insertIntoPlayerInventory: hotbar then main, strict merge (item+damage+NBT).
        stack = mergeLocalPlayerRange(inventory, stack, 0, 9);
        if (stack == null) {
            return null;
        }
        return mergeLocalPlayerRange(inventory, stack, 9, 36);
    }

    private static ItemStack mergeLocalPlayerRange(InventoryPlayer inventory, ItemStack stack, int startInclusive,
        int endExclusive) {
        for (int i = startInclusive; i < endExclusive && stack.stackSize > 0; i++) {
            ItemStack existing = inventory.mainInventory[i];
            if (existing != null && existing.getItem() == stack.getItem()
                && existing.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize < existing.getMaxStackSize()) {
                int move = Math.min(stack.stackSize, existing.getMaxStackSize() - existing.stackSize);
                existing.stackSize += move;
                stack.stackSize -= move;
            }
        }
        for (int i = startInclusive; i < endExclusive && stack.stackSize > 0; i++) {
            if (inventory.mainInventory[i] == null) {
                inventory.mainInventory[i] = stack.copy();
                stack.stackSize = 0;
            }
        }
        return stack.stackSize <= 0 ? null : stack;
    }

    private static int countMatchingInPlayer(GuiContainer gui, ItemStack match) {
        if (gui == null || gui.inventorySlots == null || match == null) {
            return 0;
        }
        int total = 0;
        for (Object object : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            if (slot == null || !(slot.inventory instanceof InventoryPlayer) || !slot.getHasStack()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack != null && stacksMatchForCrafting(match, stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    private static List<ItemStack> collectContainerStacks(GuiContainer gui) {
        List<ItemStack> invStacks = new ArrayList<ItemStack>();
        if (gui == null || gui.inventorySlots == null) {
            return invStacks;
        }
        for (Object object : gui.inventorySlots.inventorySlots) {
            if (!(object instanceof Slot)) {
                continue;
            }
            Slot slot = (Slot) object;
            if (slot.getHasStack() && slot.getStack().stackSize > 0
                && slot.isItemValid(slot.getStack())
                && gui.mc != null
                && gui.mc.thePlayer != null
                && slot.canTakeStack(gui.mc.thePlayer)) {
                invStacks.add(
                    slot.getStack()
                        .copy());
            }
        }
        return invStacks;
    }

    private static List<PoolEntry> buildActiveTabPool() {
        List<PoolEntry> pool = new ArrayList<PoolEntry>();
        BackpackTab tab = getActiveTabForNei();
        if (tab == null) {
            return pool;
        }
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack stack = tab.getSlotStack(i);
            if (stack != null && stack.stackSize > 0) {
                pool.add(new PoolEntry(stack.copy(), tab.tabId, i));
            }
        }
        return pool;
    }

    private static void rewriteTabSlot(int tabId, int slotIndex, ItemStack stack) {
        BackpackTab tab = findTab(tabId);
        if (tab == null) {
            return;
        }
        ItemStack[] stacks = new ItemStack[tab.storageSlots];
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack current = tab.getSlotStack(i);
            stacks[i] = current == null ? null : current.copy();
        }
        if (slotIndex >= 0 && slotIndex < stacks.length) {
            stacks[slotIndex] = stack == null ? null : stack.copy();
        }
        tab.setSlotStacks(stacks);
    }

    private static BackpackTab findTab(int tabId) {
        for (BackpackTab tab : OverlayClientState.getTabs()) {
            if (tab.tabId == tabId) {
                return tab;
            }
        }
        BackpackTab active = OverlayController.getActiveOverlayTab();
        if (active != null && active.tabId == tabId) {
            return active;
        }
        return null;
    }

    private static BackpackTab getActiveTabForNei() {
        if (Config.overlayMinimized) {
            return null;
        }
        BackpackTab active = OverlayController.getActiveOverlayTab();
        if (active != null) {
            return active;
        }
        List<BackpackTab> tabs = OverlayClientState.getTabs();
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    private static boolean isAutoCraftProcessing() {
        try {
            return codechicken.nei.recipe.AutoCraftingManager.processing();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * True if overlay holds a matching stack that NEI would treat as a non-consumed tool /
     * container item (so maxStack=1 must not cap craft multiplier).
     */
    private static boolean overlayMatchIsContainerItem(ItemStack match) {
        if (isContainerLikeTool(match)) {
            return true;
        }
        BackpackTab tab = getActiveTabForNei();
        if (tab == null) {
            return false;
        }
        for (int i = 0; i < tab.storageSlots; i++) {
            ItemStack stack = tab.getSlotStack(i);
            if (stack != null && stacksMatchForCrafting(match, stack) && isContainerLikeTool(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Align with NEI {@code DefaultOverlayHandler.findInventoryQuantities} container-item /
     * tool detection (GT.ToolStats, damageable, maxStack 1 tools).
     */
    private static boolean isContainerLikeTool(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (stack.getMaxStackSize() != 1) {
            return false;
        }
        if (stack.isItemStackDamageable()) {
            return true;
        }
        if (stack.hasTagCompound() && stack.stackTagCompound.hasKey("GT.ToolStats")) {
            return true;
        }
        try {
            if (NEIServerUtils.isItemTool(stack)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            return stack.getItem()
                .hasContainerItem(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean stacksMatchForCrafting(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return NEIServerUtils.areStacksSameTypeCraftingWithNBT(a, b);
        } catch (Throwable ignored) {
            if (a.getItem() != b.getItem()) {
                return false;
            }
            if (a.isItemStackDamageable() || b.isItemStackDamageable()) {
                return true;
            }
            if (a.getMaxStackSize() == 1 && b.getMaxStackSize() == 1) {
                return true;
            }
            return a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
        }
    }

    private static final class PoolEntry {

        final ItemStack stack;
        final int tabId;
        final int slotIndex;

        PoolEntry(ItemStack stack, int tabId, int slotIndex) {
            this.stack = stack;
            this.tabId = tabId;
            this.slotIndex = slotIndex;
        }
    }

    /** Aggregated recipe need for one craft-matching item type. */
    private static final class NeedGroup {

        final ItemStack match;
        int totalNeed;

        NeedGroup(ItemStack match, int totalNeed) {
            this.match = match;
            this.totalNeed = totalNeed;
        }
    }

    /** Player inventory remaining for covering later matrix slots of the same item. */
    private static final class PlayerReserve {

        final ItemStack match;
        int remaining;

        PlayerReserve(ItemStack match, int remaining) {
            this.match = match;
            this.remaining = remaining;
        }
    }
}
