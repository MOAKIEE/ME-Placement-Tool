package com.moakiee.meplacementtool;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu type registration for ME Placement Tool
 */
public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = 
            DeferredRegister.create(Registries.MENU, MEPlacementToolMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WandMenu>> WAND_MENU = 
            MENUS.register("wand_menu", () -> 
                    IMenuTypeExtension.create((id, inv, buf) -> new WandMenu(id, inv, buf))
            );

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
