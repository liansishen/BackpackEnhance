package com.hepdd.backpackenhance.server.overlay;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackInventoryAccess;
import com.hepdd.backpackenhance.integration.BackpackTab;

public class OverlayClickExecutor {

    private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

    public boolean leftClick(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex) {
        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null) {
            return false;
        }

        ItemStack slotStack = target.inventory.getStackInSlot(slotIndex);
        ItemStack cursorStack = player.inventory.getItemStack();

        if (cursorStack == null) {
            if (slotStack != null) {
                player.inventory.setItemStack(slotStack.copy());
                target.inventory.setInventorySlotContents(slotIndex, null);
            }
        } else if (slotStack == null) {
            if (isItemValid(target, slotIndex, cursorStack)) {
                target.inventory.setInventorySlotContents(slotIndex, cursorStack.copy());
                player.inventory.setItemStack(null);
            }
        } else if (canMerge(slotStack, cursorStack) && isItemValid(target, slotIndex, cursorStack)) {
            int limit = Math.min(target.inventory.getInventoryStackLimit(), slotStack.getMaxStackSize());
            int move = Math.min(cursorStack.stackSize, limit - slotStack.stackSize);
            if (move > 0) {
                slotStack.stackSize += move;
                cursorStack.stackSize -= move;
                if (cursorStack.stackSize <= 0) {
                    player.inventory.setItemStack(null);
                }
                target.inventory.setInventorySlotContents(slotIndex, slotStack);
            }
        } else if (isItemValid(target, slotIndex, cursorStack)) {
            target.inventory.setInventorySlotContents(slotIndex, cursorStack.copy());
            player.inventory.setItemStack(slotStack.copy());
        }

        saveTarget(player, target);
        return true;
    }

    public boolean rightClick(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex) {
        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null) {
            return false;
        }

        ItemStack slotStack = target.inventory.getStackInSlot(slotIndex);
        ItemStack cursorStack = player.inventory.getItemStack();

        if (cursorStack == null) {
            if (slotStack != null) {
                int take = (slotStack.stackSize + 1) / 2;
                ItemStack taken = slotStack.copy();
                taken.stackSize = take;
                slotStack.stackSize -= take;
                player.inventory.setItemStack(taken);
                target.inventory.setInventorySlotContents(slotIndex, slotStack.stackSize <= 0 ? null : slotStack);
            }
        } else if (slotStack == null) {
            if (isItemValid(target, slotIndex, cursorStack)) {
                ItemStack placed = cursorStack.copy();
                placed.stackSize = 1;
                target.inventory.setInventorySlotContents(slotIndex, placed);
                cursorStack.stackSize--;
                if (cursorStack.stackSize <= 0) {
                    player.inventory.setItemStack(null);
                }
            }
        } else if (canMerge(slotStack, cursorStack) && isItemValid(target, slotIndex, cursorStack)) {
            int limit = Math.min(target.inventory.getInventoryStackLimit(), slotStack.getMaxStackSize());
            if (slotStack.stackSize < limit) {
                slotStack.stackSize++;
                cursorStack.stackSize--;
                if (cursorStack.stackSize <= 0) {
                    player.inventory.setItemStack(null);
                }
                target.inventory.setInventorySlotContents(slotIndex, slotStack);
            }
        }

        saveTarget(player, target);
        return true;
    }

    public boolean shiftClickOut(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex) {
        if (player.inventory.getItemStack() != null) {
            return false;
        }

        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null) {
            return false;
        }

        ItemStack slotStack = target.inventory.getStackInSlot(slotIndex);
        if (slotStack == null) {
            return false;
        }

        if (player.openContainer instanceof ContainerPlayer) {
            ItemStack remaining = moveToEmptyPlayerSlot(player, slotStack.copy());
            if (remaining == null || remaining.stackSize != slotStack.stackSize) {
                target.inventory.setInventorySlotContents(slotIndex, remaining);
                saveTarget(player, target);
                player.openContainer.detectAndSendChanges();
                return true;
            }
            return false;
        }

        ItemStack moving = slotStack.copy();
        int before = moving.stackSize;

        // Prefer borrow + native slotClick first so ModularUI/GT shift priorities apply
        // (input slots before charger/battery/extra slots).
        Slot borrowedSlot = findEmptyPlayerContainerSlot(player, moving);
        if (borrowedSlot != null) {
            target.inventory.setInventorySlotContents(slotIndex, null);
            borrowedSlot.putStack(moving);
            player.openContainer.slotClick(borrowedSlot.slotNumber, 0, 1, player);
            ItemStack remaining = borrowedSlot.getStack();
            borrowedSlot.putStack(null);
            target.inventory.setInventorySlotContents(slotIndex, remaining == null ? null : remaining.copy());
            saveTarget(player, target);
            player.openContainer.detectAndSendChanges();
            return true;
        }

        // Fallback: direct insert into non-player slots sorted by ModularUI shift priority.
        ItemStack remaining = insertIntoNonPlayerSlots(player, moving);
        if (remaining == null || remaining.stackSize < before) {
            target.inventory.setInventorySlotContents(slotIndex, remaining);
            saveTarget(player, target);
            player.openContainer.detectAndSendChanges();
            return true;
        }

        return false;
    }

    /**
     * Insert into open-container slots that are not the player's inventory (machine/chest/etc.).
     * Skips phantom / shift-disabled slots and prefers lower ModularUI shift priorities.
     */
    private ItemStack insertIntoNonPlayerSlots(EntityPlayerMP player, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack remaining = stack.copy();
        List<Slot> targets = collectNonPlayerInsertSlots(player);

        // Merge into existing stacks first.
        for (Slot slot : targets) {
            if (remaining == null || remaining.stackSize <= 0) {
                break;
            }
            if (!slot.isItemValid(remaining)) {
                continue;
            }
            ItemStack existing = slot.getStack();
            if (existing == null || !canMerge(existing, remaining)) {
                continue;
            }
            int limit = Math.min(existing.getMaxStackSize(), getSlotStackLimit(slot, remaining));
            int move = Math.min(remaining.stackSize, limit - existing.stackSize);
            if (move > 0) {
                existing.stackSize += move;
                remaining.stackSize -= move;
                slot.putStack(existing);
                slot.onSlotChanged();
            }
        }

        // Then fill empty slots.
        for (Slot slot : targets) {
            if (remaining == null || remaining.stackSize <= 0) {
                break;
            }
            if (slot.getHasStack() || !slot.isItemValid(remaining)) {
                continue;
            }
            int limit = getSlotStackLimit(slot, remaining);
            int move = Math.min(remaining.stackSize, limit);
            if (move <= 0) {
                continue;
            }
            ItemStack placed = remaining.copy();
            placed.stackSize = move;
            slot.putStack(placed);
            slot.onSlotChanged();
            remaining.stackSize -= move;
        }

        return remaining.stackSize <= 0 ? null : remaining;
    }

    private List<Slot> collectNonPlayerInsertSlots(EntityPlayerMP player) {
        List<Slot> slots = new java.util.ArrayList<Slot>();
        for (Object object : player.openContainer.inventorySlots) {
            Slot slot = (Slot) object;
            if (OverlaySlotUtil.isPlayerInventorySlot(player, slot) || OverlaySlotUtil.isShiftInsertDisabled(slot)) {
                continue;
            }
            slots.add(slot);
        }
        java.util.Collections.sort(slots, new java.util.Comparator<Slot>() {

            @Override
            public int compare(Slot a, Slot b) {
                int pa = OverlaySlotUtil.getShiftClickPriority(a);
                int pb = OverlaySlotUtil.getShiftClickPriority(b);
                if (pa != pb) {
                    return pa < pb ? -1 : 1;
                }
                return a.slotNumber - b.slotNumber;
            }
        });
        return slots;
    }

    private int getSlotStackLimit(Slot slot, ItemStack stack) {
        try {
            // SlotItemHandler / ModularSlot expose getItemStackLimit(ItemStack).
            java.lang.reflect.Method method = slot.getClass()
                .getMethod("getItemStackLimit", ItemStack.class);
            Object result = method.invoke(slot, stack);
            if (result instanceof Integer) {
                return Math.min(stack.getMaxStackSize(), ((Integer) result).intValue());
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        } catch (RuntimeException ignored) {
            // fall through
        }
        return Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
    }

    public boolean hotbarSwap(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex,
        int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex >= 9 || player.inventory.getItemStack() != null) {
            return false;
        }

        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null || target.tab.playerSlot == hotbarIndex) {
            return false;
        }

        ItemStack slotStack = target.inventory.getStackInSlot(slotIndex);
        ItemStack hotbarStack = player.inventory.mainInventory[hotbarIndex];
        if (hotbarStack != null && !isItemValid(target, slotIndex, hotbarStack)) {
            return false;
        }

        target.inventory.setInventorySlotContents(slotIndex, hotbarStack == null ? null : hotbarStack.copy());
        player.inventory.mainInventory[hotbarIndex] = slotStack == null ? null : slotStack.copy();
        saveTarget(player, target);
        player.openContainer.detectAndSendChanges();
        return true;
    }

    /**
     * Double-click gather: pull matching items into the cursor, matching vanilla pass 1 —
     * only from <strong>incomplete</strong> stacks (never split a full stack).
     * Vanilla {@code Container} mode 6 first pass uses
     * {@code stackSize != getMaxStackSize()} before optionally taking from full stacks.
     */
    public boolean doubleClickCollect(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex) {
        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null) {
            return false;
        }

        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack == null || cursorStack.stackSize >= cursorStack.getMaxStackSize()) {
            return false;
        }

        int limit = Math.min(target.inventory.getInventoryStackLimit(), cursorStack.getMaxStackSize());
        boolean changed = false;
        for (int i = 0; i < target.storageSlots && cursorStack.stackSize < limit; i++) {
            ItemStack slotStack = target.inventory.getStackInSlot(i);
            if (slotStack == null || !canMerge(cursorStack, slotStack)) {
                continue;
            }
            // Incomplete stacks only — do not take from an already-full group.
            if (slotStack.stackSize >= slotStack.getMaxStackSize()) {
                continue;
            }

            int move = Math.min(slotStack.stackSize, limit - cursorStack.stackSize);
            if (move <= 0) {
                continue;
            }
            cursorStack.stackSize += move;
            slotStack.stackSize -= move;
            target.inventory.setInventorySlotContents(i, slotStack.stackSize <= 0 ? null : slotStack);
            changed = true;
        }

        if (changed) {
            player.inventory.setItemStack(cursorStack);
            saveTarget(player, target);
        }
        return changed;
    }

    public boolean dragDistribute(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, List<Integer> slotIndexes,
        int button) {
        ItemStack cursorStack = player.inventory.getItemStack();
        if (cursorStack == null || slotIndexes.isEmpty()) {
            return false;
        }

        ClickTarget target = getTarget(
            player,
            tabs,
            tabId,
            slotIndexes.get(0)
                .intValue());
        if (target == null) {
            return false;
        }

        List<Integer> validSlots = new java.util.ArrayList<Integer>();
        for (Integer slotIndex : slotIndexes) {
            int slot = slotIndex.intValue();
            if (slot < 0 || slot >= target.storageSlots || validSlots.contains(slotIndex)) {
                continue;
            }
            ItemStack slotStack = target.inventory.getStackInSlot(slot);
            if ((slotStack == null || canMerge(slotStack, cursorStack)) && isItemValid(target, slot, cursorStack)) {
                validSlots.add(slotIndex);
            }
        }

        if (validSlots.isEmpty()) {
            return false;
        }

        int remainingSlots = validSlots.size();
        for (Integer slotIndex : validSlots) {
            if (cursorStack == null || cursorStack.stackSize <= 0) {
                break;
            }

            int slot = slotIndex.intValue();
            ItemStack slotStack = target.inventory.getStackInSlot(slot);
            int limit = Math.min(target.inventory.getInventoryStackLimit(), cursorStack.getMaxStackSize());
            int space = slotStack == null ? limit : limit - slotStack.stackSize;
            if (space <= 0) {
                remainingSlots--;
                continue;
            }

            int amount = button == 1 ? 1 : Math.max(1, cursorStack.stackSize / remainingSlots);
            amount = Math.min(amount, space);
            amount = Math.min(amount, cursorStack.stackSize);

            if (slotStack == null) {
                ItemStack placed = cursorStack.copy();
                placed.stackSize = amount;
                target.inventory.setInventorySlotContents(slot, placed);
            } else {
                slotStack.stackSize += amount;
                target.inventory.setInventorySlotContents(slot, slotStack);
            }

            cursorStack.stackSize -= amount;
            if (cursorStack.stackSize <= 0) {
                player.inventory.setItemStack(null);
                cursorStack = null;
            }
            remainingSlots--;
        }

        saveTarget(player, target);
        return true;
    }

    private ClickTarget getTarget(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex) {
        BackpackTab tab = findTab(tabs, tabId);
        if (tab == null || slotIndex < 0) {
            return null;
        }

        ItemStack backpackStack = player.inventory.mainInventory[tab.playerSlot];
        if (backpackStack == null) {
            return null;
        }

        IInventory inventory = snapshotFactory.createInventory(player, tab.kind, backpackStack);
        if (inventory == null) {
            return null;
        }

        int storageSlots = BackpackInventoryAccess.storageSlots(tab.kind, inventory, backpackStack);
        if (slotIndex >= storageSlots || slotIndex >= inventory.getSizeInventory()) {
            return null;
        }

        return new ClickTarget(tab, backpackStack, inventory, storageSlots);
    }

    private void saveTarget(EntityPlayerMP player, ClickTarget target) {
        snapshotFactory.saveInventory(target.tab.kind, target.backpackStack, target.inventory);
        player.inventory.markDirty();
    }

    private BackpackTab findTab(List<BackpackTab> tabs, int tabId) {
        for (BackpackTab tab : tabs) {
            if (tab.tabId == tabId) {
                return tab;
            }
        }
        return null;
    }

    private boolean canMerge(ItemStack a, ItemStack b) {
        // Strict merge for backpack storage (keep NBT). Tools max-stack-1 never merge here.
        return a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b) && a.stackSize < a.getMaxStackSize();
    }

    /**
     * Whether a backpack stack may satisfy a recipe permutation (mirrors NEI craft matching for
     * damageable items / GT tools). Used when deciding extract eligibility is not needed for
     * empty dest slots — extract places the real stack as-is.
     */
    private boolean canStackForCraft(ItemStack recipeItem, ItemStack backpackItem) {
        if (recipeItem == null || backpackItem == null) {
            return false;
        }
        if (backpackItem.getItem() != recipeItem.getItem()) {
            return false;
        }
        // Damageable (incl. GT tools): ignore damage / GT.ToolStats differences for placement.
        if (backpackItem.isItemStackDamageable() || recipeItem.isItemStackDamageable()
            || backpackItem.getMaxStackSize() == 1 && recipeItem.getMaxStackSize() == 1) {
            return true;
        }
        return backpackItem.getItemDamage() == recipeItem.getItemDamage()
            && ItemStack.areItemStackTagsEqual(backpackItem, recipeItem);
    }

    private boolean isItemValid(ClickTarget target, int slot, ItemStack stack) {
        return BackpackInventoryAccess.isItemValid(target.tab.kind, target.inventory, slot, stack);
    }

    /**
     * Move up to {@code amount} craft-matching items from the player inventory into the overlay
     * backpack. Used to settle NEI auto-craft borrows (tools + unused consumables).
     * <p>
     * Matching is <strong>craft-lenient</strong> (damageable / GT tools ignore durability NBT)
     * so post-craft GT tools still return. Each taken stack is deposited as-is (real NBT), not
     * the borrow-time template.
     */
    public int depositFromPlayer(EntityPlayerMP player, List<BackpackTab> tabs, int preferredTabId, ItemStack template,
        int amount) {
        if (amount <= 0 || template == null || player.inventory.getItemStack() != null) {
            return 0;
        }

        int remaining = amount;
        int deposited = 0;
        List<BackpackTab> order = orderTabsPreferred(tabs, preferredTabId);

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack existing = player.inventory.mainInventory[i];
            if (existing == null || !canStackForCraft(template, existing)) {
                continue;
            }
            int take = Math.min(remaining, existing.stackSize);
            ItemStack moving = existing.copy();
            moving.stackSize = take;
            existing.stackSize -= take;
            if (existing.stackSize <= 0) {
                player.inventory.mainInventory[i] = null;
            }

            int before = moving.stackSize;
            for (BackpackTab tab : order) {
                if (moving == null || moving.stackSize <= 0) {
                    break;
                }
                ClickTarget target = getTarget(player, tabs, tab.tabId, 0);
                if (target == null) {
                    continue;
                }
                moving = insertIntoBackpack(target, moving);
                saveTarget(player, target);
            }
            int moved = before - (moving == null ? 0 : moving.stackSize);
            deposited += moved;
            remaining -= moved;
            // Put back anything that did not fit in backpacks.
            if (moving != null && moving.stackSize > 0) {
                insertIntoPlayerInventory(player, moving);
            }
        }

        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        return deposited;
    }

    private List<BackpackTab> orderTabsPreferred(List<BackpackTab> tabs, int preferredTabId) {
        List<BackpackTab> order = new java.util.ArrayList<BackpackTab>();
        BackpackTab preferred = findTab(tabs, preferredTabId);
        if (preferred != null) {
            order.add(preferred);
        }
        for (BackpackTab tab : tabs) {
            if (preferred == null || tab.tabId != preferred.tabId) {
                order.add(tab);
            }
        }
        return order;
    }

    private ItemStack insertIntoBackpack(ClickTarget target, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        int slots = target.storageSlots;
        for (int i = 0; i < slots && stack.stackSize > 0; i++) {
            ItemStack existing = target.inventory.getStackInSlot(i);
            if (existing != null && canMerge(existing, stack) && isItemValid(target, i, stack)) {
                int limit = Math.min(target.inventory.getInventoryStackLimit(), existing.getMaxStackSize());
                int move = Math.min(stack.stackSize, limit - existing.stackSize);
                if (move > 0) {
                    existing.stackSize += move;
                    stack.stackSize -= move;
                    target.inventory.setInventorySlotContents(i, existing);
                }
            }
        }
        for (int i = 0; i < slots && stack.stackSize > 0; i++) {
            if (target.inventory.getStackInSlot(i) == null && isItemValid(target, i, stack)) {
                int limit = Math.min(target.inventory.getInventoryStackLimit(), stack.getMaxStackSize());
                int move = Math.min(stack.stackSize, limit);
                ItemStack placed = stack.copy();
                placed.stackSize = move;
                target.inventory.setInventorySlotContents(i, placed);
                stack.stackSize -= move;
            }
        }
        return stack.stackSize <= 0 ? null : stack;
    }

    /**
     * Extract up to {@code amount} items from an overlay backpack slot into an open-container slot
     * (craft matrix / machine input). Used by manual NEI + fill.
     *
     * @return number of items actually moved into the destination slot
     */
    public int extractToContainerSlot(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex,
        int amount, int windowId, int containerSlot) {
        if (amount <= 0 || player.inventory.getItemStack() != null) {
            return 0;
        }
        if (player.openContainer == null || player.openContainer.windowId != windowId) {
            return 0;
        }
        if (containerSlot < 0 || containerSlot >= player.openContainer.inventorySlots.size()) {
            return 0;
        }

        Slot dest = player.openContainer.getSlot(containerSlot);
        if (dest == null) {
            return 0;
        }
        // Refuse dumping into player-inventory slots — NEI destinations are craft/machine matrix only.
        if (OverlaySlotUtil.isPlayerInventorySlot(player, dest)) {
            return 0;
        }

        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null) {
            return 0;
        }

        ItemStack slotStack = target.inventory.getStackInSlot(slotIndex);
        if (slotStack == null || slotStack.stackSize <= 0) {
            return 0;
        }

        int toMove = Math.min(amount, slotStack.stackSize);
        ItemStack moving = slotStack.copy();
        moving.stackSize = toMove;

        if (!dest.isItemValid(moving)) {
            return 0;
        }

        ItemStack existing = dest.getStack();
        int moved;
        if (existing == null) {
            int limit = Math.min(dest.getSlotStackLimit(), moving.getMaxStackSize());
            moved = Math.min(toMove, limit);
            if (moved <= 0) {
                return 0;
            }
            ItemStack placed = moving.copy();
            placed.stackSize = moved;
            dest.putStack(placed);
        } else if (canMerge(existing, moving)) {
            int limit = Math.min(dest.getSlotStackLimit(), existing.getMaxStackSize());
            moved = Math.min(toMove, limit - existing.stackSize);
            if (moved <= 0) {
                return 0;
            }
            existing.stackSize += moved;
            dest.putStack(existing);
        } else {
            return 0;
        }

        slotStack.stackSize -= moved;
        target.inventory.setInventorySlotContents(slotIndex, slotStack.stackSize <= 0 ? null : slotStack);
        saveTarget(player, target);
        // Do NOT detectAndSendChanges here. Early S2F craft-slot sync races with client prefill
        // and can re-show ghost ingredients. Client putStack optimistically; backpack state is
        // pushed via PacketOverlayState from the packet handler.
        player.inventory.markDirty();
        return moved;
    }

    /**
     * Extract up to {@code amount} items from an overlay backpack slot into the player's inventory
     * (NEI bookmark auto-craft borrow path).
     */
    public int extractToPlayer(EntityPlayerMP player, List<BackpackTab> tabs, int tabId, int slotIndex, int amount) {
        if (amount <= 0 || player.inventory.getItemStack() != null) {
            return 0;
        }

        ClickTarget target = getTarget(player, tabs, tabId, slotIndex);
        if (target == null) {
            return 0;
        }

        ItemStack slotStack = target.inventory.getStackInSlot(slotIndex);
        if (slotStack == null) {
            return 0;
        }

        int toMove = Math.min(amount, slotStack.stackSize);
        ItemStack moving = slotStack.copy();
        moving.stackSize = toMove;
        int before = moving.stackSize;
        ItemStack remaining = insertIntoPlayerInventory(player, moving);
        int moved = before - (remaining == null ? 0 : remaining.stackSize);
        if (moved <= 0) {
            return 0;
        }

        slotStack.stackSize -= moved;
        target.inventory.setInventorySlotContents(slotIndex, slotStack.stackSize <= 0 ? null : slotStack);
        saveTarget(player, target);
        player.inventory.markDirty();
        // Do NOT detectAndSendChanges here. Early player-slot sync races with client optimistic
        // insert and NEI window-clicks, re-showing borrowed ingredients as ghosts. Overlay state
        // is pushed via PacketOverlayState; player inv converges on craft clicks / settle deposit.
        return moved;
    }

    private ItemStack insertIntoPlayerInventory(EntityPlayerMP player, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        stack = mergeIntoPlayerRange(player, stack, 0, 9);
        if (stack == null) {
            return null;
        }
        return mergeIntoPlayerRange(player, stack, 9, 36);
    }

    private ItemStack mergeIntoPlayerRange(EntityPlayerMP player, ItemStack stack, int startInclusive,
        int endExclusive) {
        for (int i = startInclusive; i < endExclusive && stack.stackSize > 0; i++) {
            ItemStack existing = player.inventory.mainInventory[i];
            if (existing != null && canMerge(existing, stack)) {
                int move = Math.min(stack.stackSize, existing.getMaxStackSize() - existing.stackSize);
                if (move > 0) {
                    existing.stackSize += move;
                    stack.stackSize -= move;
                }
            }
        }
        for (int i = startInclusive; i < endExclusive && stack.stackSize > 0; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.mainInventory[i] = stack.copy();
                stack.stackSize = 0;
            }
        }
        return stack.stackSize <= 0 ? null : stack;
    }

    private Slot findEmptyPlayerContainerSlot(EntityPlayerMP player, ItemStack stack) {
        for (Object object : player.openContainer.inventorySlots) {
            Slot slot = (Slot) object;
            // ModularUI2 player slots use a dummy inventory — match via item handler.
            if (OverlaySlotUtil.isPlayerInventorySlot(player, slot) && !slot.getHasStack()
                && slot.isItemValid(stack)
                && slot.getSlotIndex() >= 0
                && slot.getSlotIndex() < 36) {
                return slot;
            }
        }
        return null;
    }

    private ItemStack moveToEmptyPlayerSlot(EntityPlayerMP player, ItemStack stack) {
        ItemStack remaining = placeInFirstEmptyRange(player, stack, 0, 9);
        if (remaining == null) {
            return null;
        }
        return placeInFirstEmptyRange(player, remaining, 9, 36);
    }

    private ItemStack placeInFirstEmptyRange(EntityPlayerMP player, ItemStack stack, int startInclusive,
        int endExclusive) {
        for (int i = startInclusive; i < endExclusive; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.mainInventory[i] = stack.copy();
                player.inventory.markDirty();
                return null;
            }
        }
        return stack;
    }

    private static class ClickTarget {

        private final BackpackTab tab;
        private final ItemStack backpackStack;
        private final IInventory inventory;
        private final int storageSlots;

        private ClickTarget(BackpackTab tab, ItemStack backpackStack, IInventory inventory, int storageSlots) {
            this.tab = tab;
            this.backpackStack = backpackStack;
            this.inventory = inventory;
            this.storageSlots = storageSlots;
        }
    }
}
