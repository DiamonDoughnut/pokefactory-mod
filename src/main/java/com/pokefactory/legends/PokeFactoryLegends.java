package com.pokefactory.legends;

import com.pokefactory.legends.api.ApiClient;
import com.pokefactory.legends.config.ModConfig;
import com.pokefactory.legends.events.CobblemonEventHandler;
import com.pokefactory.legends.server.data.ServerDataManager;
import com.pokefactory.legends.util.DevEnvironment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PokeFactoryLegends.MOD_ID)
public class PokeFactoryLegends {
    public static final String MOD_ID = "pokefactory_legends";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static ApiClient apiClient;

    public PokeFactoryLegends(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(Type.COMMON, ModConfig.SPEC);
        
        // Register NeoForge events on the NeoForge event bus
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("PokéFactory Legends initializing...");
        apiClient = new ApiClient();
        LOGGER.info("API client initialized");
        LOGGER.info("Cobblemon events will be registered on first player join");
    }
    
    private void onServerStarted(final ServerStartedEvent event) {
        if (DevEnvironment.shouldInitializeDataManager()) {
            // Authenticate server with backend (same for dev and production)
            PokeFactoryLegends.LOGGER.debug("Connection Timeout: {}", ModConfig.CONNECTION_TIMEOUT.get());
            apiClient.authenticateServer()
                .thenAccept(success -> {
                    
                    if (success) {
                        LOGGER.info("Server authenticated with backend - {}", DevEnvironment.getDevInfo());
                    } else {
                        LOGGER.warn("Failed to authenticate with backend - {}", DevEnvironment.getDevInfo());
                    }
                });
            
            ServerDataManager.getInstance().initialize();
            LOGGER.info("Server-side data manager initialized - {}", DevEnvironment.getDevInfo());
        } else {
            LOGGER.info("Skipping server initialization (single-player mode without dev settings)");
        }
    }
    
    private void onServerStopping(final ServerStoppingEvent event) {
        try {
            ServerDataManager.getInstance().shutdown();
            if (apiClient != null) {
                apiClient.shutdown();
            }
            LOGGER.info("Server-side components shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }
    }
    
    public static ApiClient getApiClient() {
        return apiClient;
    }
}