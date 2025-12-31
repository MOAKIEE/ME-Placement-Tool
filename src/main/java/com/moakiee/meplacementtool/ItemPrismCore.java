package com.moakiee.meplacementtool;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 棱镜原体 - 一个承载着色彩可能性的中间合成物品
 */
public class ItemPrismCore extends Item {

    public ItemPrismCore(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                @Nullable Level world,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        // 显示带有彩色"光谱的钥匙"的tooltip（颜色已在语言文件中定义）
        tooltip.add(Component.translatable("tooltip.meplacementtool.prism_core.prefix"));
        super.appendHoverText(stack, world, tooltip, flag);
    }
}
