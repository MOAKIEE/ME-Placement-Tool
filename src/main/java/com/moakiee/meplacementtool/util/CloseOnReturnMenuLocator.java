package com.moakiee.meplacementtool.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.core.AELog;
import appeng.menu.ISubMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;

/**
 * A MenuLocator that wraps the host to close the menu instead of returning to main menu.
 * This is used for placement tools' auto-crafting feature.
 */
public record CloseOnReturnMenuLocator(int itemIndex, @Nullable BlockPos blockPos) implements MenuLocator {

    @Nullable
    public <T> T locate(Player player, Class<T> hostInterface) {
        ItemStack it = player.getInventory().getItem(itemIndex);

        if (!it.isEmpty() && it.getItem() instanceof IMenuItem guiItem) {
            ItemMenuHost menuHost = guiItem.getMenuHost(player, itemIndex, it, blockPos);
            if (menuHost != null) {
                // Wrap the host to close on return instead of opening terminal
                Object wrappedHost = new WrappedMenuHost(menuHost);
                if (hostInterface.isInstance(wrappedHost)) {
                    return hostInterface.cast(wrappedHost);
                }
            }
        } else {
            AELog.warn("Item in slot %d of %s is not an IMenuItem: %s", itemIndex, player, it);
        }

        return null;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeInt(itemIndex);
        buf.writeBoolean(blockPos != null);
        if (blockPos != null) {
            buf.writeBlockPos(blockPos);
        }
    }

    public static CloseOnReturnMenuLocator readFromPacket(FriendlyByteBuf buf) {
        var itemIndex = buf.readInt();
        BlockPos blockPos = null;
        if (buf.readBoolean()) {
            blockPos = buf.readBlockPos();
        }
        return new CloseOnReturnMenuLocator(itemIndex, blockPos);
    }

    public static MenuLocator forInventorySlot(int inventorySlot) {
        return new CloseOnReturnMenuLocator(inventorySlot, null);
    }

    public static MenuLocator forHand(Player player, InteractionHand hand) {
        ItemStack is = player.getItemInHand(hand);
        if (is.isEmpty()) {
            throw new IllegalArgumentException("Cannot open an item-inventory with empty hands");
        }
        int invSize = player.getInventory().getContainerSize();
        for (int i = 0; i < invSize; i++) {
            if (player.getInventory().getItem(i) == is) {
                return forInventorySlot(i);
            }
        }
        throw new IllegalArgumentException("Could not find item held in hand " + hand + " in player inventory");
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("CloseOnReturnMenuItem");
        result.append('{');
        result.append("slot=").append(itemIndex);
        if (blockPos != null) {
            result.append(',').append("pos=").append(blockPos);
        }
        result.append('}');
        return result.toString();
    }

    /**
     * Wrapper that implements ISubMenuHost to intercept returnToMainMenu.
     */
    private static class WrappedMenuHost implements ISubMenuHost, IActionHost {
        private final ItemMenuHost delegate;

        public WrappedMenuHost(ItemMenuHost delegate) {
            this.delegate = delegate;
        }

        @Override
        public void returnToMainMenu(Player player, ISubMenu subMenu) {
            // Close the menu instead of returning to terminal
            player.closeContainer();
        }

        @Override
        public ItemStack getMainMenuIcon() {
            if (delegate instanceof ISubMenuHost subMenuHost) {
                return subMenuHost.getMainMenuIcon();
            }
            return delegate.getItemStack();
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
}
