package com.moakiee.meplacementtool;

import appeng.menu.SlotSemantics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import appeng.api.util.AEColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu for the Cable Placement Tool GUI.
 * Provides cable type, color, and placement mode selection with an upgrade slot for Key of Spectrum.
 */
public class CableToolMenu extends AbstractContainerMenu {

    // Sync fields - these are synced manually via packets
    public int currentMode;
    public int currentCableType;
    public int currentColor;
    public boolean hasUpgrade;

    private final Container upgradeContainer;
    private final ItemStack toolStack;
    private final Player player;
    private final int toolSlot;
    
    // Track slots by semantic for compatibility with AE2-style slot semantics
    private final Map<String, List<Slot>> slotsBySemantic = new HashMap<>();

    public CableToolMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, getToolStackFromSlot(playerInventory, buf.readInt()), buf.readInt());
    }

    private static ItemStack getToolStackFromSlot(Inventory inv, int slot) {
        if (slot == 40) {
            return inv.player.getOffhandItem();
        }
        return inv.getItem(slot);
    }

    public CableToolMenu(int id, Inventory playerInventory, ItemStack toolStack, int slot) {
        super(ModMenus.CABLE_TOOL_MENU.get(), id);

        this.player = playerInventory.player;
        this.toolStack = toolStack;
        this.toolSlot = slot;

        // Initialize upgrade container - a simple 1-slot container
        this.upgradeContainer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                onUpgradeChanged();
            }
            
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.getItem() == MEPlacementToolMod.KEY_OF_SPECTRUM.get();
            }
            
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };

        // Load existing upgrade from tool
        loadUpgradeFromTool();

        // Add upgrade slot - only accepts Key of Spectrum
        // Position is set off-screen as we render it manually in the Screen
        Slot upgradeSlot = new Slot(upgradeContainer, 0, -9999, -9999) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == MEPlacementToolMod.KEY_OF_SPECTRUM.get();
            }
            
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.addSlot(upgradeSlot);
        addSlotToSemantic(upgradeSlot, SlotSemantics.UPGRADE.id());

        // Create player inventory slots with custom positions
        createCustomPlayerInventorySlots(playerInventory);

        // Load current settings from tool
        loadSettings();
    }

    private void addSlotToSemantic(Slot slot, String semantic) {
        slotsBySemantic.computeIfAbsent(semantic, k -> new ArrayList<>()).add(slot);
    }
    
    public List<Slot> getSlots(appeng.menu.SlotSemantic semantic) {
        return slotsBySemantic.getOrDefault(semantic.id(), new ArrayList<>());
    }

    private void loadUpgradeFromTool() {
        if (ItemMECablePlacementTool.hasUpgrade(toolStack)) {
            this.upgradeContainer.setItem(0, new ItemStack(MEPlacementToolMod.KEY_OF_SPECTRUM.get()));
        }
        this.hasUpgrade = !this.upgradeContainer.getItem(0).isEmpty();
    }

    private void loadSettings() {
        this.currentMode = ItemMECablePlacementTool.getMode(toolStack).ordinal();
        this.currentCableType = ItemMECablePlacementTool.getCableType(toolStack).ordinal();
        this.currentColor = ItemMECablePlacementTool.getColor(toolStack).ordinal();
        this.hasUpgrade = ItemMECablePlacementTool.hasUpgrade(toolStack);
    }

    /**
     * Create player inventory slots at custom positions matching the GUI texture.
     * Main inventory: (8,172), 9 columns x 3 rows, slot size 16px, spacing 2px (total 18px per cell)
     * Hotbar: (8,230), 9 slots, same spacing
     */
    private void createCustomPlayerInventorySlots(Inventory playerInventory) {
        final int SLOT_SIZE = 18; // 16px slot + 2px spacing
        final int INV_X = 8;
        final int INV_Y = 172;
        final int HOTBAR_Y = 230;
        
        // Main inventory (slots 9-35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;
                int x = INV_X + col * SLOT_SIZE;
                int y = INV_Y + row * SLOT_SIZE;
                Slot slot = new Slot(playerInventory, index, x, y);
                this.addSlot(slot);
                addSlotToSemantic(slot, SlotSemantics.PLAYER_INVENTORY.id());
            }
        }
        
        // Hotbar (slots 0-8)
        for (int col = 0; col < 9; col++) {
            int x = INV_X + col * SLOT_SIZE;
            Slot slot = new Slot(playerInventory, col, x, HOTBAR_Y);
            this.addSlot(slot);
            addSlotToSemantic(slot, SlotSemantics.PLAYER_HOTBAR.id());
        }
    }

    private void onUpgradeChanged() {
        boolean newHasUpgrade = !this.upgradeContainer.getItem(0).isEmpty();
        if (this.hasUpgrade != newHasUpgrade) {
            this.hasUpgrade = newHasUpgrade;
            // Save to tool data component
            ItemMECablePlacementTool.setUpgrade(toolStack, newHasUpgrade);
        }
    }

    public void setMode(int mode) {
        if (mode >= 0 && mode < ItemMECablePlacementTool.PlacementMode.values().length) {
            this.currentMode = mode;
            ItemMECablePlacementTool.setMode(toolStack, ItemMECablePlacementTool.PlacementMode.values()[mode]);
        }
    }

    public void setCableType(int type) {
        if (type >= 0 && type < ItemMECablePlacementTool.CableType.values().length) {
            this.currentCableType = type;
            ItemMECablePlacementTool.setCableType(toolStack, ItemMECablePlacementTool.CableType.values()[type]);
        }
    }

    public void setColor(int color) {
        if (color >= 0 && color < AEColor.values().length) {
            this.currentColor = color;
            ItemMECablePlacementTool.setColor(toolStack, AEColor.values()[color]);
        }
    }

    public ItemMECablePlacementTool.PlacementMode getPlacementMode() {
        return ItemMECablePlacementTool.PlacementMode.values()[currentMode];
    }

    public ItemMECablePlacementTool.CableType getCableType() {
        return ItemMECablePlacementTool.CableType.values()[currentCableType];
    }

    public AEColor getSelectedColor() {
        return AEColor.values()[currentColor];
    }

    public boolean hasUpgradeInstalled() {
        return this.hasUpgrade;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (!player.level().isClientSide) {
            // Save upgrade state
            ItemStack upgradeStack = this.upgradeContainer.getItem(0);
            ItemMECablePlacementTool.setUpgrade(toolStack, !upgradeStack.isEmpty());
        }
    }
    
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();
            
            // If from upgrade slot, move to player inventory
            if (index == 0) {
                if (!this.moveItemStackTo(stackInSlot, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } 
            // If from player inventory and it's a Key of Spectrum, move to upgrade slot
            else if (stackInSlot.getItem() == MEPlacementToolMod.KEY_OF_SPECTRUM.get()) {
                if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            // Standard player inventory <-> hotbar logic
            else if (index < 28) { // Main inventory
                if (!this.moveItemStackTo(stackInSlot, 28, 37, false)) {
                    return ItemStack.EMPTY;
                }
            } else { // Hotbar
                if (!this.moveItemStackTo(stackInSlot, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem() == this.toolStack || player.getOffhandItem() == this.toolStack;
    }
}
