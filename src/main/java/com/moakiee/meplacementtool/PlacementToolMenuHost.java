package com.moakiee.meplacementtool;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.menu.ISubMenu;
import appeng.util.ConfigManager;

/**
 * Menu host for placement tools that supports autocrafting.
 * This is a custom implementation that does not depend on WirelessTerminalItem,
 * so other mods won't recognize this as a wireless terminal.
 */
public class PlacementToolMenuHost extends ItemMenuHost implements IPortableTerminal, IActionHost, ISubMenuHost {

    private final BasePlacementToolItem tool;
    private final BiConsumer<Player, ISubMenu> returnToMainMenu;
    private final IGrid targetGrid;
    private IStorageService sg;
    @Nullable
    private IWirelessAccessPoint myWap;
    /**
     * The distance to the currently connected access point in blocks.
     */
    private double currentDistanceFromGrid = Double.MAX_VALUE;

    public PlacementToolMenuHost(Player player, @Nullable Integer slot, ItemStack itemStack,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, slot, itemStack);
        if (!(itemStack.getItem() instanceof BasePlacementToolItem placementTool)) {
            throw new IllegalArgumentException("Can only use this class with subclasses of BasePlacementToolItem");
        }
        this.tool = placementTool;
        this.returnToMainMenu = returnToMainMenu;

        this.targetGrid = placementTool.getLinkedGrid(itemStack, player.level(), player);
        if (this.targetGrid != null) {
            this.sg = this.targetGrid.getStorageService();
        }
    }

    @Override
    public MEStorage getInventory() {
        return this.sg != null ? this.sg.getInventory() : null;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        if (this.tool != null) {
            final double extracted = Math.min(amt, this.tool.getAECurrentPower(getItemStack()));

            if (mode == Actionable.SIMULATE) {
                return extracted;
            }

            return this.tool.usePower(getPlayer(), extracted, getItemStack()) ? extracted : 0;
        }
        return 0.0;
    }

    @Override
    public IConfigManager getConfigManager() {
        // Create a simple config manager for the placement tool
        var configManager = new ConfigManager((manager, settingName) -> {
            manager.writeToNBT(getItemStack().getOrCreateTag());
        });
        configManager.readFromNBT(getItemStack().getOrCreateTag().copy());
        return configManager;
    }

    @Override
    public IGridNode getActionableNode() {
        this.rangeCheck();
        if (this.myWap != null) {
            return this.myWap.getActionableNode();
        }
        return null;
    }

    public boolean rangeCheck() {
        this.currentDistanceFromGrid = Double.MAX_VALUE;

        if (this.targetGrid != null) {
            @Nullable
            IWirelessAccessPoint bestWap = null;
            double bestSqDistance = Double.MAX_VALUE;

            // Find closest WAP
            for (var wap : this.targetGrid.getMachines(WirelessAccessPointBlockEntity.class)) {
                double sqDistance = getWapSqDistance(wap);

                // If the WAP is not suitable then MAX_VALUE will be returned and the check will fail
                if (sqDistance < bestSqDistance) {
                    bestSqDistance = sqDistance;
                    bestWap = wap;
                }
            }

            // If no WAP is found this will work too
            this.myWap = bestWap;
            this.currentDistanceFromGrid = Math.sqrt(bestSqDistance);
            return this.myWap != null;
        }

        return false;
    }

    /**
     * @return square distance to WAP if the WAP can be used, or {@link Double#MAX_VALUE} if it cannot be used.
     * Note: For placement tools, we ignore range and dimension limits - only check if WAP is active.
     */
    protected double getWapSqDistance(IWirelessAccessPoint wap) {
        // For placement tools, allow cross-dimension access - only check if WAP is active
        if (wap.isActive()) {
            var dc = wap.getLocation();
            if (dc.getLevel() == this.getPlayer().level()) {
                // Same dimension - return actual distance
                var offX = dc.getPos().getX() - this.getPlayer().getX();
                var offY = dc.getPos().getY() - this.getPlayer().getY();
                var offZ = dc.getPos().getZ() - this.getPlayer().getZ();
                return offX * offX + offY * offY + offZ * offZ;
            } else {
                // Different dimension - return a large but valid distance
                return 1.0;
            }
        }
        return Double.MAX_VALUE;
    }

    @Override
    public boolean onBroadcastChanges(AbstractContainerMenu menu) {
        // For placement tools, we do not enforce wireless range checks or continuous
        // power drain. The tool only consumes power when actually placing blocks.
        // This allows crafting menus to stay open regardless of WAP range.
        return ensureItemStillInSlot();
    }

    /**
     * Check wireless range for the placement tool.
     */
    private boolean checkWirelessRange(AbstractContainerMenu menu) {
        if (!rangeCheck()) {
            if (!isClientSide()) {
                getPlayer().displayClientMessage(PlayerMessages.OutOfRange.text(), true);
            }
            return false;
        }

        setPowerDrainPerTick(AEConfig.instance().wireless_getDrainRate(currentDistanceFromGrid));
        return true;
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        returnToMainMenu.accept(player, subMenu);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return getItemStack();
    }

    public String getCloseHotkey() {
        return null; // No hotkey for placement tools
    }
}
