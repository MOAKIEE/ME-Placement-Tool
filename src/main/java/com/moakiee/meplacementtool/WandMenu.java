package com.moakiee.meplacementtool;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import com.moakiee.meplacementtool.compat.InternalInventoryAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class WandMenu extends AEBaseMenu {
    public static final String TAG_KEY = "placement_config";

    private final ItemStackHandler handler;
    private final InternalInventoryAdapter adapter;
    private final List<Slot> ghostSlots = new ArrayList<>();
    private final java.util.Map<Integer, String> fluidMap = new java.util.HashMap<>();
    private static final ThreadLocal<CompoundTag> TEMP_TAG = new ThreadLocal<>();

    public WandMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, handlerFromBuf(buf));
        CompoundTag cfg = TEMP_TAG.get();
        if (cfg != null && cfg.contains("fluids")) {
            var ftag = cfg.getCompound("fluids");
            for (String key : ftag.getAllKeys()) {
                try {
                    int idx = Integer.parseInt(key);
                    this.fluidMap.put(idx, ftag.getString(key));
                } catch (NumberFormatException ignored) {}
            }
        }
        TEMP_TAG.remove();
    }

    private static ItemStackHandler handlerFromBuf(FriendlyByteBuf buf) {
        CompoundTag cfg = buf.readNbt();
        TEMP_TAG.set(cfg);
        ItemStackHandler h = new ItemStackHandler(18);
        if (cfg != null) {
            CompoundTag itemsTag = cfg.contains("items") ? cfg.getCompound("items") : cfg;
            // Manually copy items to preserve 18-slot size (old data may have only 9 slots)
            if (itemsTag.contains("Items")) {
                net.minecraft.nbt.ListTag list = itemsTag.getList("Items", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag itemTag = list.getCompound(i);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < 18) {
                        h.setStackInSlot(slot, ItemStack.of(itemTag));
                    }
                }
            }
        }
        return h;
    }

    public WandMenu(int id, Inventory playerInventory, ItemStackHandler handler) {
        super(ModMenus.WAND_MENU.get(), id, playerInventory,
                new ItemMenuHost(playerInventory.player, null, playerInventory.player.getMainHandItem()));

        // Ensure handler is always 18 slots (expand old 9-slot data if needed)
        if (handler == null) {
            this.handler = new ItemStackHandler(18);
        } else if (handler.getSlots() < 18) {
            // Expand old handler to 18 slots
            ItemStackHandler newHandler = new ItemStackHandler(18);
            for (int i = 0; i < handler.getSlots(); i++) {
                newHandler.setStackInSlot(i, handler.getStackInSlot(i));
            }
            this.handler = newHandler;
        } else {
            this.handler = handler;
        }
        this.adapter = new InternalInventoryAdapter(this.handler);

        // register a custom semantic for our ghost grid, then add 3x3 FakeSlot slots under it
        var ghostSemantic = appeng.menu.SlotSemantics.get("ME_WAND_GHOST");
        if (ghostSemantic == null) {
            ghostSemantic = appeng.menu.SlotSemantics.register("ME_WAND_GHOST", false);
        }

        // add 9 paged ghost slots that change their backing index based on current page
        int startX = 62;
        int startY = 17;
        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            int x = startX + col * 18;
            int y = startY + row * 18;
            PagedSlot s = new PagedSlot(this.handler, i, x, y, () -> this.currentPage);
            this.addSlot(s, ghostSemantic);
            this.ghostSlots.add(s);
        }

        // add player inventory slots (so player's inventory is visible)
        int playerInvX = 8;
        int playerInvY = 84;
        // main inventory 3 rows x 9 cols
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = playerInvX + col * 18;
                int y = playerInvY + row * 18;
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x, y));
            }
        }
        // hotbar
        for (int hb = 0; hb < 9; ++hb) {
            int x = playerInvX + hb * 18;
            int y = playerInvY + 58;
            this.addSlot(new Slot(playerInventory, hb, x, y));
        }
    }

    public ItemStackHandler getHandler() {
        return this.handler;
    }

    public List<Slot> getGhostSlots() {
        return this.ghostSlots;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = page;
    }

    private int currentPage = 0;

    private static ItemStackHandler readHandlerFromBuf(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        ItemStackHandler h = new ItemStackHandler(18);
        if (tag != null) {
            CompoundTag itemsTag = tag.contains("Items") ? tag : tag;
            if (itemsTag.contains("Items")) {
                net.minecraft.nbt.ListTag list = itemsTag.getList("Items", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag itemTag = list.getCompound(i);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < 18) {
                        h.setStackInSlot(slot, ItemStack.of(itemTag));
                    }
                }
            }
        }
        return h;
    }

    public void setFluidSlot(int index, String fluidId) {
        if (fluidId == null) this.fluidMap.remove(index);
        else this.fluidMap.put(index, fluidId);
    }

    public String getFluidSlot(int index) {
        return this.fluidMap.get(index);
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        // intercept clicks on our ghost slots: copy cursor item type into slot or clear
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            if (this.ghostSlots.contains(slot)) {
                // Use PagedSlot's actual slot index (accounts for current page)
                int actualIndex;
                if (slot instanceof PagedSlot pagedSlot) {
                    actualIndex = pagedSlot.getActualSlotIndex();
                } else {
                    actualIndex = this.ghostSlots.indexOf(slot);
                }
                ItemStack carried = this.getCarried();
                if (!carried.isEmpty()) {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    this.handler.setStackInSlot(actualIndex, copy);
                } else {
                    this.handler.setStackInSlot(actualIndex, ItemStack.EMPTY);
                }
                // do not propagate default behavior
                return;
            }
        }

        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack main = player.getMainHandItem();
            if (!main.isEmpty()) {
                CompoundTag combined = new CompoundTag();
                combined.put("items", this.handler.serializeNBT());
                CompoundTag ftag = new CompoundTag();
                for (var e : this.fluidMap.entrySet()) {
                    ftag.putString(Integer.toString(e.getKey()), e.getValue());
                }
                combined.put("fluids", ftag);
                main.getOrCreateTag().put(TAG_KEY, combined);
            }
        } else {
            // client: send current handler NBT to server so server persists it
            CompoundTag combined = new CompoundTag();
            combined.put("items", this.handler.serializeNBT());
            CompoundTag ftag = new CompoundTag();
            for (var e : this.fluidMap.entrySet()) {
                ftag.putString(Integer.toString(e.getKey()), e.getValue());
            }
            combined.put("fluids", ftag);
            // Preserve SelectedSlot from the existing item tag if present to avoid unintentionally
            // clearing or changing the selected slot when the GUI is closed.
            try {
                ItemStack main = player.getMainHandItem();
                if (!main.isEmpty()) {
                    CompoundTag existing = main.getOrCreateTag();
                    if (existing.contains(TAG_KEY)) {
                        CompoundTag prev = existing.getCompound(TAG_KEY);
                        if (prev.contains("SelectedSlot")) {
                            combined.putInt("SelectedSlot", prev.getInt("SelectedSlot"));
                        }
                    }
                }
            } catch (Throwable ignored) {}
            com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(
                    new com.moakiee.meplacementtool.network.UpdateWandConfigPacket(combined));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return true;
    }

    /**
     * Override broadcastChanges to skip syncing ghost slots.
     * PagedSlot's dynamic index causes sync issues with vanilla Container logic.
     * Ghost slots don't need server-client sync since they're just configuration.
     */
    @Override
    public void broadcastChanges() {
        // Update the lastSlots tracking for ghost slots to prevent false "change" detection
        // We use reflection because lastSlots is private in AbstractContainerMenu
        try {
            java.lang.reflect.Field lastSlotsField = net.minecraft.world.inventory.AbstractContainerMenu.class.getDeclaredField("lastSlots");
            lastSlotsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            net.minecraft.core.NonNullList<ItemStack> lastSlots = (net.minecraft.core.NonNullList<ItemStack>) lastSlotsField.get(this);
            
            for (int i = 0; i < this.slots.size(); i++) {
                Slot slot = this.slots.get(i);
                if (this.ghostSlots.contains(slot)) {
                    // Set lastSlots to match current to prevent "change" detection
                    if (i < lastSlots.size()) {
                        lastSlots.set(i, slot.getItem().copy());
                    }
                }
            }
        } catch (Exception ignored) {
            // If reflection fails, fall through to normal behavior
        }
        super.broadcastChanges();
    }
}
