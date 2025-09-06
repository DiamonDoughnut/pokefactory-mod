package com.pokefactory.legends.util;

import com.pokefactory.legends.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public class DevEnvironment {
    
    public static boolean isDevMode() {
        return ModConfig.DEV_MODE.get();
    }
    
    public static boolean shouldSimulateMultiplayer() {
        return ModConfig.SIMULATE_MULTIPLAYER.get();
    }
    
    public static boolean isIntegratedServer() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Minecraft mc = Minecraft.getInstance();
            return mc.hasSingleplayerServer();
        }
        return false;
    }
    
    public static boolean shouldRunServerLogic(Level level) {
        // In dev mode, run server logic even in single-player
        if (isDevMode()) {
            return !level.isClientSide();
        }
        
        // Normal mode: only run on dedicated servers
        return !level.isClientSide() && !isIntegratedServer();
    }
    
    public static boolean shouldInitializeDataManager() {
        // In dev mode with multiplayer simulation, always initialize
        if (isDevMode() && shouldSimulateMultiplayer()) {
            return true;
        }
        
        // Normal mode: only on dedicated servers
        return !isIntegratedServer();
    }
    
    public static String getDevInfo() {
        return String.format("Dev Mode: %s, Simulate MP: %s, Integrated: %s", 
            isDevMode(), shouldSimulateMultiplayer(), isIntegratedServer());
    }
}