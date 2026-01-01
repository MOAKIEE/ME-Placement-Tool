package com.moakiee.meplacementtool;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Key of Spectrum - A decorative/crafting item with colored tooltip
 */
public class ItemKeyOfSpectrum extends Item {

    public ItemKeyOfSpectrum(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.meplacementtool.key_of_spectrum"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
