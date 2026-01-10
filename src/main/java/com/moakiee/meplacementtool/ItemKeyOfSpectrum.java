package com.moakiee.meplacementtool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.items.materials.UpgradeCardItem;

import java.util.List;

/**
 * Key of Spectrum - An upgrade card that allows cables to be dyed in any color
 */
public class ItemKeyOfSpectrum extends UpgradeCardItem {

    public ItemKeyOfSpectrum(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.meplacementtool.key_of_spectrum"));
        
        // Show supported devices (similar to AE2 upgrade cards)
        tooltipComponents.add(Component.translatable("tooltip.meplacementtool.supported_by").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.literal("  ").append(
            Component.translatable("item.meplacementtool.me_cable_placement_tool")).withStyle(ChatFormatting.GRAY));
        
        // Don't call super to avoid duplicate tooltip from UpgradeCardItem
    }
}
