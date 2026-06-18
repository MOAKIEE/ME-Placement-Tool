package com.moakiee.meplacementtool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Prism Core - A decorative/crafting item with rainbow colored tooltip
 */
public class ItemPrismCore extends Item {

    public ItemPrismCore(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.meplacementtool.prism_core.prefix"));
        // Rainbow text for "光谱的钥匙" / "Key of Spectrum"
        MutableComponent rainbow = Component.empty();
        String text = Component.translatable("tooltip.meplacementtool.prism_core.rainbow").getString();
        ChatFormatting[] colors = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE};
        for (int i = 0; i < text.length(); i++) {
            rainbow.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(colors[i % colors.length]));
        }
        tooltipComponents.accept(rainbow);
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, tooltipFlag);
    }
}
