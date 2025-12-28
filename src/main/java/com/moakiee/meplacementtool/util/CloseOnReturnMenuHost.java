package com.moakiee.meplacementtool.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;

/**
 * A wrapper around ISubMenuHost that closes the menu instead of returning to main menu.
 * Used for placement tools' auto-crafting feature where we don't want to show 
 * the wireless terminal after crafting is submitted.
 */
public class CloseOnReturnMenuHost implements ISubMenuHost, IActionHost {
    
    private final ISubMenuHost delegate;
    
    public CloseOnReturnMenuHost(ISubMenuHost delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        // Close the menu instead of returning to terminal
        player.closeContainer();
    }
    
    @Override
    public ItemStack getMainMenuIcon() {
        return delegate.getMainMenuIcon();
    }
    
    @Override
    @Nullable
    public IGridNode getActionableNode() {
        if (delegate instanceof IActionHost actionHost) {
            return actionHost.getActionableNode();
        }
        return null;
    }
}
