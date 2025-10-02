package com.pokefactory.legends.server.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.pokefactory.legends.PokeFactoryLegends;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServerDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDataManager.class);
    private static ServerDataManager instance;
    private final Map<String, CaptureData> pendingCaptures = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private File dataFile;
    
    private ServerDataManager() {}
    
    public static ServerDataManager getInstance() {
        if (instance == null) {
            instance = new ServerDataManager();
        }
        return instance;
    }
    
    public void initialize() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            dataFile = new File(worldDir, "pokefactory_captures.json");
            loadPendingData();
            startSyncScheduler();
            PokeFactoryLegends.LOGGER.info("ServerDataManager initialized with data file: {}", dataFile.getAbsolutePath());
        }
    }
    
    public void recordCapture(UUID playerUuid, int nationalDexNumber) {
        String uniqueKey = playerUuid.toString() + ":" + nationalDexNumber;
        
        // Only record if this player hasn't caught this Pokemon before (in pending data)
        if (!pendingCaptures.containsKey(uniqueKey)) {
            CaptureData capture = new CaptureData(playerUuid, nationalDexNumber, System.currentTimeMillis());
            pendingCaptures.put(uniqueKey, capture);
            savePendingData();
            
            PokeFactoryLegends.LOGGER.info("Recorded new capture: Player {} caught Pokemon #{}", 
                playerUuid, nationalDexNumber);
        }
    }
    
    private void startSyncScheduler() {
        // Sync every 30 seconds
        scheduler.scheduleAtFixedRate(this::syncWithAPI, 30, 30, TimeUnit.SECONDS);
        PokeFactoryLegends.LOGGER.info("API sync scheduler started (30 second intervals)");
    }
    
    private void syncWithAPI() {
        if (pendingCaptures.isEmpty()) {
            return;
        }
        
        List<CaptureData> toSync = new ArrayList<>(pendingCaptures.values());
        PokeFactoryLegends.LOGGER.info("Syncing {} captures with API", toSync.size());
        
        // Process in batches of 10
        for (int i = 0; i < toSync.size(); i += 10) {
            List<CaptureData> batch = toSync.subList(i, Math.min(i + 10, toSync.size()));
            processBatch(batch);
        }
    }
    
    private void processBatch(List<CaptureData> batch) {
        // Group captures by player and track which captures to remove
        Map<UUID, List<JsonObject>> capturesByPlayer = new HashMap<>();
        Map<UUID, List<String>> keysToRemoveByPlayer = new HashMap<>();
        
        for (CaptureData capture : batch) {
            UUID playerUuid = capture.playerUuid();
            capturesByPlayer.computeIfAbsent(playerUuid, k -> new ArrayList<>());
            keysToRemoveByPlayer.computeIfAbsent(playerUuid, k -> new ArrayList<>());
            
            JsonObject captureObj = new JsonObject();
            captureObj.addProperty("national_id", capture.nationalDexNumber());
            captureObj.addProperty("action", "catch");
            capturesByPlayer.get(playerUuid).add(captureObj);
            keysToRemoveByPlayer.get(playerUuid).add(capture.getUniqueKey());
        }
        
        // Process each player's captures
        for (Map.Entry<UUID, List<JsonObject>> entry : capturesByPlayer.entrySet()) {
            UUID playerUuid = entry.getKey();
            JsonObject[] updates = entry.getValue().toArray(new JsonObject[0]);
            List<String> keysToRemove = keysToRemoveByPlayer.get(playerUuid);
            
            PokeFactoryLegends.getApiClient().sendBatchUpdate(playerUuid, updates)
                .thenAccept(success -> {
                    if (success) {
                        // Remove successfully synced captures using pre-collected keys
                        keysToRemove.forEach(pendingCaptures::remove);
                        savePendingData();
                        PokeFactoryLegends.LOGGER.info("Successfully synced {} captures for player {}", 
                            updates.length, playerUuid);
                    } else {
                        PokeFactoryLegends.LOGGER.warn("Failed to sync {} captures for player {}, will retry", 
                            updates.length, playerUuid);
                    }
                });
        }
    }
    
    private void savePendingData() {
        if (dataFile == null) return;
        
        try (FileWriter writer = new FileWriter(dataFile)) {
            JsonObject data = new JsonObject();
            JsonArray capturesArray = new JsonArray();
            
            for (CaptureData capture : pendingCaptures.values()) {
                JsonObject captureObj = new JsonObject();
                captureObj.addProperty("player_uuid", capture.playerUuid().toString());
                captureObj.addProperty("national_dex_number", capture.nationalDexNumber());
                captureObj.addProperty("timestamp", capture.timestamp());
                capturesArray.add(captureObj);
            }
            
            data.add("pending_captures", capturesArray);
            gson.toJson(data, writer);
            
        } catch (IOException e) {
            PokeFactoryLegends.LOGGER.error("Failed to save pending capture data", e);
        }
    }
    
    private void loadPendingData() {
        if (dataFile == null || !dataFile.exists()) return;
        
        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject data = gson.fromJson(reader, JsonObject.class);
            if (data != null && data.has("pending_captures")) {
                JsonArray capturesArray = data.getAsJsonArray("pending_captures");
                
                for (int i = 0; i < capturesArray.size(); i++) {
                    JsonObject captureObj = capturesArray.get(i).getAsJsonObject();
                    UUID playerUuid = UUID.fromString(captureObj.get("player_uuid").getAsString());
                    int nationalDexNumber = captureObj.get("national_dex_number").getAsInt();
                    long timestamp = captureObj.get("timestamp").getAsLong();
                    
                    CaptureData capture = new CaptureData(playerUuid, nationalDexNumber, timestamp);
                    pendingCaptures.put(capture.getUniqueKey(), capture);
                }
                
                PokeFactoryLegends.LOGGER.info("Loaded {} pending captures from disk", pendingCaptures.size());
            }
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.error("Failed to load pending capture data", e);
        }
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            // Final sync before shutdown
            syncWithAPI();
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    // Interrupt if still running
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        LOGGER.warn("Scheduler did not terminate cleanly");
                    }
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        }
        savePendingData();
        // Clear collections to help GC
        pendingCaptures.clear();
        LOGGER.info("ServerDataManager shutdown complete");
    }
    
    public int getPendingCaptureCount() {
        return pendingCaptures.size();
    }
    
    public void forceSyncNow() {
        syncWithAPI();
    }
    
    public void clearPendingCaptures() {
        pendingCaptures.clear();
        savePendingData();
        LOGGER.info("Cleared all pending captures");
    }
}