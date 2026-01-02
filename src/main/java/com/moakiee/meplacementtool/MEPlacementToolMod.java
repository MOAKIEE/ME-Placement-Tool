package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.slf4j.Logger;
import appeng.api.features.GridLinkables;
import com.moakiee.meplacementtool.client.MEPartPreviewRenderer;
import com.moakiee.meplacementtool.client.ModKeyBindings;
import com.moakiee.meplacementtool.client.MultiblockPreviewRenderer;
import com.moakiee.meplacementtool.client.RadialMenuKeyHandler;
import com.moakiee.meplacementtool.client.UndoKeyHandler;

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
    public static final RegistryObject<Item> KEY_OF_SPECTRUM = ITEMS.register("key_of_spectrum",
            () -> new ItemKeyOfSpectrum(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> PRISM_CORE = ITEMS.register("prism_core",
            () -> new ItemPrismCore(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ME_CABLE_PLACEMENT_TOOL = ITEMS.register("me_cable_placement_tool",
            () -> new ItemMECablePlacementTool(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> ME_PLACEMENT_TOOL_TAB = CREATIVE_MODE_TABS.register("me_placement_tool_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.meplacementtool"))
                    .icon(() -> {
                        var iconStack = new ItemStack(ME_PLACEMENT_TOOL.get());
                        if (iconStack.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage powerStorage) {
                            powerStorage.injectAEPower(iconStack, powerStorage.getAEMaxPower(iconStack), appeng.api.config.Actionable.MODULATE);
                        }
                        return iconStack;
                    })
                    .displayItems((parameters, output) -> {
                        output.accept(ME_PLACEMENT_TOOL.get());
                        output.accept(MULTIBLOCK_PLACEMENT_TOOL.get());
                        output.accept(KEY_OF_SPECTRUM.get());
                        output.accept(PRISM_CORE.get());
                        output.accept(ME_CABLE_PLACEMENT_TOOL.get());
                        
                        var chargedMETool = new ItemStack(ME_PLACEMENT_TOOL.get(), 1);
                        var chargedMultiblockTool = new ItemStack(MULTIBLOCK_PLACEMENT_TOOL.get(), 1);
                        
                        if (chargedMETool.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage mePowerStorage) {
                            mePowerStorage.injectAEPower(chargedMETool, mePowerStorage.getAEMaxPower(chargedMETool), appeng.api.config.Actionable.MODULATE);
                        }
                        if (chargedMultiblockTool.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage multiPowerStorage) {
                            multiPowerStorage.injectAEPower(chargedMultiblockTool, multiPowerStorage.getAEMaxPower(chargedMultiblockTool), appeng.api.config.Actionable.MODULATE);
                        }
                        
                        var chargedCableTool = new ItemStack(ME_CABLE_PLACEMENT_TOOL.get(), 1);
                        if (chargedCableTool.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage cablePowerStorage) {
                            cablePowerStorage.injectAEPower(chargedCableTool, cablePowerStorage.getAEMaxPower(chargedCableTool), appeng.api.config.Actionable.MODULATE);
                        }

                        output.accept(chargedMETool);
                        output.accept(chargedMultiblockTool);
                        output.accept(chargedCableTool);
                    })
                    .build());

    public static MEPlacementToolMod instance;
    public MultiblockPreviewRenderer multiblockPreviewRenderer;
    public UndoHistory undoHistory;

    public MEPlacementToolMod()
    {
        instance = this;
        undoHistory = new UndoHistory();
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



        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Register AE2 grid linkable handler for our custom placement tool items
        try {
            GridLinkables.register(ME_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
            GridLinkables.register(MULTIBLOCK_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
            GridLinkables.register(ME_CABLE_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
        } catch (Exception e) {
            LOGGER.error("Failed to register GridLinkable handler: {}", e.getMessage());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // register screen for our wand menu
            event.enqueueWork(() -> MenuScreens.register(ModMenus.WAND_MENU.get(), WandScreen::new));
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.OPEN_RADIAL_MENU);
            event.register(ModKeyBindings.UNDO_MODIFIER);
        }

        @SubscribeEvent
        public static void onClientSetupComplete(FMLClientSetupEvent event) {
            MEPlacementToolMod.instance.multiblockPreviewRenderer = new MultiblockPreviewRenderer();
            MinecraftForge.EVENT_BUS.register(MEPlacementToolMod.instance.multiblockPreviewRenderer);
            MinecraftForge.EVENT_BUS.register(new UndoKeyHandler());
            MinecraftForge.EVENT_BUS.register(new RadialMenuKeyHandler());
            // Install ME Part preview renderer (for cables, panels, quartz fiber, etc.)
            MEPartPreviewRenderer.install();
            // Install Cable Placement Tool preview renderer
            com.moakiee.meplacementtool.client.CablePreviewRenderer.install();
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        public static String lastSelectedText = null;
        public static long lastSelectedTime = 0L;
        public static String lastCountText = null;
        public static long lastCountTime = 0L;
        
        // HUD renderer for tool information
        private static final com.moakiee.meplacementtool.client.ToolInfoHudRenderer toolInfoHudRenderer = new com.moakiee.meplacementtool.client.ToolInfoHudRenderer();
        
        static {
            // Register the HUD renderer
            MinecraftForge.EVENT_BUS.register(toolInfoHudRenderer);
        }

        @SubscribeEvent
        public static void onRenderCrosshair(RenderGuiOverlayEvent.Pre event) {
            // Hide crosshair when radial menu is open
            if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
                var screen = Minecraft.getInstance().screen;
                if (screen instanceof com.moakiee.meplacementtool.client.RadialMenuScreen || 
                    screen instanceof com.moakiee.meplacementtool.client.DualLayerRadialMenuScreen) {
                    event.setCanceled(true);
                }
            }
        }

        public static void showCountOverlay(String text) {
            lastCountText = text;
            lastCountTime = System.currentTimeMillis();
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
