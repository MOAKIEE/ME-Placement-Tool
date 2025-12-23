package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import appeng.api.features.GridLinkables;
import appeng.items.tools.powered.WirelessTerminalItem;

@Mod(MEPlacementToolMod.MODID)
public class MEPlacementToolMod
{
    public static final String MODID = "meplacementtool";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> ME_PLACEMENT_TOOL = ITEMS.register("me_placement_tool",
            () -> new ItemMEPlacementTool(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MULTIBLOCK_PLACEMENT_TOOL = ITEMS.register("multiblock_placement_tool",
            () -> new ItemMultiblockPlacementTool(new Item.Properties().stacksTo(1)));

    public MEPlacementToolMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // register our menus
        ModMenus.register(modEventBus);

        // register network messages
        com.moakiee.meplacementtool.network.ModNetwork.register();

        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
        // Register AE2 grid linkable handler for our custom wireless terminal item
        try {
            GridLinkables.register(ME_PLACEMENT_TOOL.get(), WirelessTerminalItem.LINKABLE_HANDLER);
            GridLinkables.register(MULTIBLOCK_PLACEMENT_TOOL.get(), WirelessTerminalItem.LINKABLE_HANDLER);
            LOGGER.info("Registered GridLinkable handler for ME Placement Tool and Multiblock Placement Tool");
        } catch (Exception e) {
            LOGGER.error("Failed to register GridLinkable handler: {}", e.getMessage());
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ME_PLACEMENT_TOOL.get());
            event.accept(MULTIBLOCK_PLACEMENT_TOOL.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("HELLO from server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            // register screen for our wand menu
            event.enqueueWork(() -> MenuScreens.register(ModMenus.WAND_MENU.get(), WandScreen::new));
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        public static String lastSelectedText = null;
        public static long lastSelectedTime = 0L;
        public static String lastCountText = null;
        public static long lastCountTime = 0L;

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || !player.isCrouching()) return;
            double scroll = event.getScrollDelta();
            if (scroll == 0) return;

            var main = player.getMainHandItem();
            if (main.isEmpty() || main.getItem() != MULTIBLOCK_PLACEMENT_TOOL.get()) return;

            int nextCount = ItemMultiblockPlacementTool.getNextPlacementCount(main, scroll > 0);
            com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(
                    new com.moakiee.meplacementtool.network.UpdatePlacementCountPacket(nextCount));
            main.getOrCreateTag().putInt("placement_count", nextCount);
            event.setCanceled(true);

            showCountOverlay("Placement Count: " + nextCount);
        }

        public static void showCountOverlay(String text) {
            lastCountText = text;
            lastCountTime = System.currentTimeMillis();
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onLeftClickEmpty(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
            var player = event.getEntity();
            if (player == null || player.level().isClientSide == false) return;
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;
            var main = player.getMainHandItem();
            if (main.isEmpty() || (main.getItem() != ME_PLACEMENT_TOOL.get() && main.getItem() != MULTIBLOCK_PLACEMENT_TOOL.get())) return;

            // previous slot: find previous non-empty configured slot (items or fluids). If none configured, do nothing.
            var tag = main.getOrCreateTag();
            var cfg = tag.contains(WandMenu.TAG_KEY) ? tag.getCompound(WandMenu.TAG_KEY).copy() : new net.minecraft.nbt.CompoundTag();
            int selected = cfg.contains("SelectedSlot") ? cfg.getInt("SelectedSlot") : 0;
            // collect configured indices
            java.util.List<Integer> configured = new java.util.ArrayList<>();
            if (cfg.contains("items")) {
                net.minecraftforge.items.ItemStackHandler h = new net.minecraftforge.items.ItemStackHandler(9);
                h.deserializeNBT(cfg.getCompound("items"));
                for (int i = 0; i < 9; i++) {
                    var s = h.getStackInSlot(i);
                    if (!s.isEmpty()) configured.add(i);
                }
            }
            if (cfg.contains("fluids")) {
                var ftag = cfg.getCompound("fluids");
                for (String k : ftag.getAllKeys()) {
                    try {
                        int idx = Integer.parseInt(k);
                        if (!configured.contains(idx)) configured.add(idx);
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!configured.isEmpty()) {
                // find current index in configured list
                int pos = configured.indexOf(selected);
                if (pos == -1) pos = 0;
                pos = (pos - 1 + configured.size()) % configured.size();
                selected = configured.get(pos);
                cfg.putInt("SelectedSlot", selected);
                tag.put(WandMenu.TAG_KEY, cfg);
            } else {
                // nothing configured, do nothing
            }

                // send to server
                com.moakiee.meplacementtool.network.ModNetwork.CHANNEL.sendToServer(
                        new com.moakiee.meplacementtool.network.UpdateWandConfigPacket(cfg));

                // show overlay text
                String name = "Empty";
                try {
                    // Prefer items (including WrappedGenericStack)
                    if (cfg.contains("items")) {
                        net.minecraftforge.items.ItemStackHandler h = new net.minecraftforge.items.ItemStackHandler(9);
                        h.deserializeNBT(cfg.getCompound("items"));
                        var s = h.getStackInSlot(cfg.contains("SelectedSlot") ? cfg.getInt("SelectedSlot") : 0);
                        if (!s.isEmpty()) {
                            // If this is an AE wrapped generic stack, unwrap and get AE display name
                            try {
                                var gs = appeng.api.stacks.GenericStack.unwrapItemStack(s);
                                if (gs != null) {
                                    name = gs.what().getDisplayName().getString();
                                } else {
                                    name = s.getHoverName().getString();
                                }
                            } catch (Throwable ignored) {
                                name = s.getHoverName().getString();
                            }
                        } else if (cfg.contains("fluids")) {
                            var f = cfg.getCompound("fluids").getString(Integer.toString(cfg.contains("SelectedSlot") ? cfg.getInt("SelectedSlot") : 0));
                            if (f != null && !f.isEmpty()) {
                                try {
                                    var rl = new net.minecraft.resources.ResourceLocation(f);
                                    var fl = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(rl);
                                    if (fl != null) {
                                        name = appeng.api.stacks.AEFluidKey.of(fl).getDisplayName().getString();
                                    } else {
                                        name = f;
                                    }
                                } catch (Throwable ignored) {
                                    name = f;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
                showSelectedOverlay(name);
        }

            public static void showSelectedOverlay(String text) {
                lastSelectedText = text;
                lastSelectedTime = System.currentTimeMillis();
            }

            @SubscribeEvent
            public static void onRenderOverlay(RenderGuiOverlayEvent event) {
                try {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    int sw = mc.getWindow().getGuiScaledWidth();
                    int sh = mc.getWindow().getGuiScaledHeight();
                    var gg = event.getGuiGraphics();
                    var font = mc.font;

                    if (lastSelectedText != null && System.currentTimeMillis() - lastSelectedTime < 2000) {
                        int x = sw / 2;
                        int y = sh - 50;
                        int w = font.width(lastSelectedText);
                        gg.drawString(font, lastSelectedText, x - w / 2, y, 0xFFFFFF, false);
                    }

                    if (lastCountText != null && System.currentTimeMillis() - lastCountTime < 2000) {
                        int x = sw / 2;
                        int y = sh - 70;
                        int w = font.width(lastCountText);
                        gg.drawString(font, lastCountText, x - w / 2, y, 0xFFFF00, false);
                    }
                } catch (Throwable t) {
                    LogUtils.getLogger().warn("Error rendering overlay", t);
                }
            }
    }

    
}
