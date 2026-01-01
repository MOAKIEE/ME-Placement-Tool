package com.moakiee.meplacementtool;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.localization.PlayerMessages;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.ConfigManager;

/**
 * Menu host for placement tools that supports autocrafting.
 * Implements IPortableTerminal to provide network access for crafting menus.
 */
public class PlacementToolMenuHost extends ItemMenuHost<Item> implements IPortableTerminal, IActionHost, ISubMenuHost {

    private final BasePlacementToolItem tool;
    private final BiConsumer<Player, ISubMenu> returnToMainMenu;
    private final IGrid targetGrid;
    private IStorageService sg;
    @Nullable
    private IWirelessAccessPoint myWap;
    private double currentDistanceFromGrid = Double.MAX_VALUE;
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected();

    public PlacementToolMenuHost(Item item, Player player, ItemMenuHostLocator locator, 
            @Nullable BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator);
        
        ItemStack itemStack = getItemStack();
        if (!(itemStack.getItem() instanceof BasePlacementToolItem placementTool)) {
            throw new IllegalArgumentException("Can only use this class with subclasses of BasePlacementToolItem");
        }
        this.tool = placementTool;
        this.returnToMainMenu = returnToMainMenu;

        this.targetGrid = placementTool.getLinkedGrid(itemStack, player.level(), player);
        if (this.targetGrid != null) {
            this.sg = this.targetGrid.getStorageService();
        }
        
        updateLinkStatus();
    }

    @Override
    public MEStorage getInventory() {
        return this.sg != null ? this.sg.getInventory() : null;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return linkStatus;
    }

    protected void updateLinkStatus() {
        if (targetGrid == null) {
            this.linkStatus = ILinkStatus.ofDisconnected(PlayerMessages.DeviceNotLinked.text());
        } else if (myWap != null) {
            this.linkStatus = ILinkStatus.ofConnected();
        } else {
            rangeCheck();
            if (myWap != null) {
                this.linkStatus = ILinkStatus.ofConnected();
            } else {
                this.linkStatus = ILinkStatus.ofDisconnected(PlayerMessages.OutOfRange.text());
            }
        }
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
        return new ConfigManager((manager, settingName) -> {});
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

            for (var wap : this.targetGrid.getMachines(WirelessAccessPointBlockEntity.class)) {
                double sqDistance = getWapSqDistance(wap);
                if (sqDistance < bestSqDistance) {
                    bestSqDistance = sqDistance;
                    bestWap = wap;
                }
            }

            this.myWap = bestWap;
            this.currentDistanceFromGrid = Math.sqrt(bestSqDistance);
            return this.myWap != null;
        }

        return false;
    }

    protected double getWapSqDistance(IWirelessAccessPoint wap) {
        if (wap.isActive()) {
            var dc = wap.getLocation();
            if (dc.getLevel() == this.getPlayer().level()) {
                var offX = dc.getPos().getX() - this.getPlayer().getX();
                var offY = dc.getPos().getY() - this.getPlayer().getY();
                var offZ = dc.getPos().getZ() - this.getPlayer().getZ();
                return offX * offX + offY * offY + offZ * offZ;
            } else {
                return 1.0;
            }
        }
        return Double.MAX_VALUE;
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (returnToMainMenu != null) {
            returnToMainMenu.accept(player, subMenu);
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return getItemStack();
    }
}
