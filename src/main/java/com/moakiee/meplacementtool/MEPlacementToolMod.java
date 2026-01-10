package com.moakiee.meplacementtool;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import appeng.api.features.GridLinkables;
import com.moakiee.meplacementtool.client.ModKeyBindings;
import com.moakiee.meplacementtool.client.MEPartPreviewRenderer;
import com.moakiee.meplacementtool.client.MultiblockPreviewRenderer;
import com.moakiee.meplacementtool.client.RadialMenuKeyHandler;
import com.moakiee.meplacementtool.client.ToolInfoHudRenderer;
import com.moakiee.meplacementtool.client.UndoKeyHandler;
import com.moakiee.meplacementtool.network.ModNetwork;

/**
 * ME Placement Tool - Main mod class for NeoForge 1.21.1
 * A tool for placing items directly from your AE2 network.
 */
@Mod(MEPlacementToolMod.MODID)
public class MEPlacementToolMod {
    public static final String MODID = "meplacementtool";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Deferred Registers
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Items
    public static final DeferredHolder<Item, ItemMEPlacementTool> ME_PLACEMENT_TOOL = 
            ITEMS.register("me_placement_tool", () -> new ItemMEPlacementTool(new Item.Properties().stacksTo(1)));
    
    public static final DeferredHolder<Item, ItemMultiblockPlacementTool> MULTIBLOCK_PLACEMENT_TOOL = 
            ITEMS.register("multiblock_placement_tool", () -> new ItemMultiblockPlacementTool(new Item.Properties().stacksTo(1)));
    
    public static final DeferredHolder<Item, ItemKeyOfSpectrum> KEY_OF_SPECTRUM = 
            ITEMS.register("key_of_spectrum", () -> new ItemKeyOfSpectrum(new Item.Properties().stacksTo(64)));
    
    public static final DeferredHolder<Item, ItemPrismCore> PRISM_CORE = 
            ITEMS.register("prism_core", () -> new ItemPrismCore(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, ItemMECablePlacementTool> ME_CABLE_PLACEMENT_TOOL = 
            ITEMS.register("me_cable_placement_tool", () -> new ItemMECablePlacementTool(new Item.Properties().stacksTo(1)));

    // Creative Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ME_PLACEMENT_TOOL_TAB = 
            CREATIVE_MODE_TABS.register("me_placement_tool_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.meplacementtool"))
                    .icon(() -> {
                        var iconStack = new ItemStack(ME_PLACEMENT_TOOL.get());
                        // Try to show a charged icon
                        if (iconStack.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage powerStorage) {
                            powerStorage.injectAEPower(iconStack, powerStorage.getAEMaxPower(iconStack), 
                                    appeng.api.config.Actionable.MODULATE);
                        }
                        return iconStack;
                    })
                    .displayItems((parameters, output) -> {
                        output.accept(ME_PLACEMENT_TOOL.get());
                        output.accept(MULTIBLOCK_PLACEMENT_TOOL.get());
                        output.accept(ME_CABLE_PLACEMENT_TOOL.get());
                        output.accept(KEY_OF_SPECTRUM.get());
                        output.accept(PRISM_CORE.get());

                        // Charged versions
                        var chargedMETool = new ItemStack(ME_PLACEMENT_TOOL.get(), 1);
                        var chargedMultiblockTool = new ItemStack(MULTIBLOCK_PLACEMENT_TOOL.get(), 1);
                        var chargedCableTool = new ItemStack(ME_CABLE_PLACEMENT_TOOL.get(), 1);

                        if (chargedMETool.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage mePowerStorage) {
                            mePowerStorage.injectAEPower(chargedMETool, mePowerStorage.getAEMaxPower(chargedMETool), 
                                    appeng.api.config.Actionable.MODULATE);
                        }
                        if (chargedMultiblockTool.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage multiPowerStorage) {
                            multiPowerStorage.injectAEPower(chargedMultiblockTool, multiPowerStorage.getAEMaxPower(chargedMultiblockTool), 
                                    appeng.api.config.Actionable.MODULATE);
                        }
                        if (chargedCableTool.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage cablePowerStorage) {
                            cablePowerStorage.injectAEPower(chargedCableTool, cablePowerStorage.getAEMaxPower(chargedCableTool), 
                                    appeng.api.config.Actionable.MODULATE);
                        }

                        output.accept(chargedMETool);
                        output.accept(chargedMultiblockTool);
                        output.accept(chargedCableTool);
                    })
                    .build());

    // Static instance and undo history
    public static MEPlacementToolMod instance;
    public MultiblockPreviewRenderer multiblockPreviewRenderer;
    public UndoHistory undoHistory;

    public MEPlacementToolMod(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;
        undoHistory = new UndoHistory();

        // Register Deferred Registers
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModMenus.register(modEventBus);
        ModNetwork.register(modEventBus);

        if (net.neoforged.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.register(ClientModEvents.class);
        }

        // Register event listeners
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server events
        NeoForge.EVENT_BUS.register(this);

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ME Placement Tool common setup");

        // Register AE2 grid linkable handler for our placement tools
        event.enqueueWork(() -> {
            try {
                GridLinkables.register(ME_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
                GridLinkables.register(MULTIBLOCK_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
                GridLinkables.register(ME_CABLE_PLACEMENT_TOOL.get(), BasePlacementToolItem.LINKABLE_HANDLER);
                LOGGER.info("Registered GridLinkable handlers for placement tools");
            } catch (Exception e) {
                LOGGER.error("Failed to register GridLinkable handler: {}", e.getMessage());
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("ME Placement Tool server starting");
    }

    /**
     * Client-side event subscribers
     */
    public static class ClientModEvents {
        private static final Logger CLIENT_LOGGER = LogUtils.getLogger();

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            CLIENT_LOGGER.info("ME Placement Tool client setup");
        }

        @SubscribeEvent
        public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(ModMenus.WAND_MENU.get(), WandScreen::new);
            event.register(ModMenus.CABLE_TOOL_MENU.get(), com.moakiee.meplacementtool.client.CableToolScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.OPEN_RADIAL_MENU);
            event.register(ModKeyBindings.UNDO_MODIFIER);
            event.register(ModKeyBindings.OPEN_CABLE_TOOL_GUI);
        }

        @SubscribeEvent
        public static void onClientSetupComplete(FMLClientSetupEvent event) {
            MEPlacementToolMod.instance.multiblockPreviewRenderer = new MultiblockPreviewRenderer();
            NeoForge.EVENT_BUS.register(MEPlacementToolMod.instance.multiblockPreviewRenderer);
            NeoForge.EVENT_BUS.register(new UndoKeyHandler());
            NeoForge.EVENT_BUS.register(new RadialMenuKeyHandler());
            NeoForge.EVENT_BUS.register(ClientForgeEvents.class);
            
            // HUD renderer for tool information display
            NeoForge.EVENT_BUS.register(new ToolInfoHudRenderer());
            
            // Install ME Part preview renderer
            MEPartPreviewRenderer.install();
            
            // Install Cable preview renderer for ME Cable Placement Tool
            com.moakiee.meplacementtool.client.CablePreviewRenderer.install();
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
            event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, 
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "overlay"), 
                (graphics, partialTick) -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player == null) return;
                    
                    long now = System.currentTimeMillis();
                    int width = mc.getWindow().getGuiScaledWidth();
                    int height = mc.getWindow().getGuiScaledHeight();
                    
                    // Position above hotbar (hotbar is ~22 pixels from bottom, text goes above it)
                    int baseY = height - 59; // Above hotbar
                    
                    // Render Selected Item Text
                    if (ClientForgeEvents.lastSelectedText != null &&
                        now - ClientForgeEvents.lastSelectedTime < 2000) {
                        
                        String text = ClientForgeEvents.lastSelectedText;
                        int x = (width - mc.font.width(text)) / 2;
                        int y = baseY - 12; // Selected item text on top
                        
                        graphics.drawString(mc.font, text, x, y, 0xFFFFFF, true);
                    }
                    
                    // Render Placement Count Text
                    if (ClientForgeEvents.lastCountText != null &&
                        now - ClientForgeEvents.lastCountTime < 2000) {
                        
                        String text = ClientForgeEvents.lastCountText;
                        int x = (width - mc.font.width(text)) / 2;
                        int y = baseY; // Count text below selected item text
                        
                        graphics.drawString(mc.font, text, x, y, 0xFFFFFF, true);
                    }
                });
        }
    }


    /**
     * Client-side Forge event subscribers for GUI overlays
     */
    public static class ClientForgeEvents {
        public static String lastSelectedText = null;
        public static long lastSelectedTime = 0L;
        public static String lastCountText = null;
        public static long lastCountTime = 0L;

        @SubscribeEvent
        public static void onRenderCrosshair(RenderGuiLayerEvent.Pre event) {
            // Hide crosshair when radial menu is open
            if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
                var screen = net.minecraft.client.Minecraft.getInstance().screen;
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

        // Note: GUI overlay rendering will be handled in a separate event handler
        // registered through NeoForge's RegisterGuiLayersEvent
    }

    /**
     * Common Forge event subscribers (both client and server)
     */
    @net.neoforged.fml.common.EventBusSubscriber(modid = MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
    public static class CommonForgeEvents {
        @SubscribeEvent
        public static void onLeftClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
            if (handleCableToolLeftClick(event.getEntity(), event.getLevel().isClientSide)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onLeftClickEmpty(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
            // This event only fires on client side, so we need to send a packet to server
            var player = event.getEntity();
            var stack = player.getMainHandItem();
            
            if (stack.getItem() != ME_CABLE_PLACEMENT_TOOL.get()) {
                return;
            }
            
            // Check if any points are set (client-side check)
            var p1 = ItemMECablePlacementTool.getPoint1(stack);
            var p2 = ItemMECablePlacementTool.getPoint2(stack);
            var p3 = ItemMECablePlacementTool.getPoint3(stack);
            
            if (p1 != null || p2 != null || p3 != null) {
                // Clear points locally on client for immediate visual feedback
                ItemMECablePlacementTool.clearAllPoints(stack);
                
                // Send packet to server to clear points on server side
                int slot = player.getInventory().selected;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.moakiee.meplacementtool.network.ClearCableToolPointsPayload(slot)
                );
            }
        }

        /**
         * Handle left click for Cable Placement Tool - clears selected points.
         * @return true if points were cleared and event should be canceled
         */
        private static boolean handleCableToolLeftClick(net.minecraft.world.entity.player.Player player, boolean isClientSide) {
            var stack = player.getMainHandItem();
            
            // Only handle for Cable Placement Tool
            if (stack.getItem() != ME_CABLE_PLACEMENT_TOOL.get()) {
                return false;
            }
            
            // Check if any points are set
            var p1 = ItemMECablePlacementTool.getPoint1(stack);
            var p2 = ItemMECablePlacementTool.getPoint2(stack);
            var p3 = ItemMECablePlacementTool.getPoint3(stack);
            
            if (p1 != null || p2 != null || p3 != null) {
                // Clear all points
                ItemMECablePlacementTool.clearAllPoints(stack);
                
                if (!isClientSide) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.meplacementtool.points_cleared"), true);
                }
                
                return true;
            }
            return false;
        }

        /**
         * Handle item switch - reset cable tool points when switching away from the tool.
         */
        @SubscribeEvent
        public static void onEquipmentChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) {
                return;
            }
            
            // Only care about main hand slot
            if (event.getSlot() != net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
                return;
            }
            
            var oldItem = event.getFrom();
            var newItem = event.getTo();
            
            // If switching AWAY FROM cable placement tool (to a different item)
            if (oldItem.getItem() == ME_CABLE_PLACEMENT_TOOL.get() && 
                newItem.getItem() != ME_CABLE_PLACEMENT_TOOL.get()) {
                // Reset all points on the old tool
                ItemMECablePlacementTool.clearAllPoints(oldItem);
            }
        }
    }
}
