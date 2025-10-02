package com.pokefactory.legends.server.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pokefactory.legends.PokeFactoryLegends;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SyncManager {
    private static volatile SyncManager instance;
    
    private SyncManager() {}
    
    public static SyncManager getInstance() {
        if (instance == null) {
            synchronized (SyncManager.class) {
                if (instance == null) {
                    instance = new SyncManager();
                }
            }
        }
        return instance;
    }
    
    public CompletableFuture<String> syncFromDatabase() {
        PokeFactoryLegends.LOGGER.info("Starting sync FROM database (overwriting local data)");
        
        return PokeFactoryLegends.getApiClient().getPokedexLeaderboard()
            .thenCompose(leaderboard -> {
                if (leaderboard == null) {
                    return CompletableFuture.completedFuture("Failed to retrieve database leaderboard");
                }
                
                return applyDatabaseLeaderboard(leaderboard);
            });
    }
    
    public CompletableFuture<String> syncToDatabase() {
        PokeFactoryLegends.LOGGER.info("Starting sync TO database (batch updating individual players)");
        
        return generateLocalPlayerUpdates()
            .thenCompose(playerUpdates -> {
                if (playerUpdates.isEmpty()) {
                    return CompletableFuture.completedFuture("No local data to sync");
                }
                
                ServerDataManager dataManager = ServerDataManager.getInstance();
                return syncAllPlayersToDatabase(playerUpdates)
                    .thenApply(success -> {
                        if (success) {
                            // Clear pending captures since we just synced everything
                            dataManager.clearPendingCaptures();
                            return "Successfully synced " + playerUpdates.size() + " players to database";
                        } else {
                            return "Failed to sync some players to database";
                        }
                    });
            });
    }
    
    public CompletableFuture<String> syncPlayerFromDatabase(UUID playerUuid) {
        PokeFactoryLegends.LOGGER.info("Syncing player {} FROM database", playerUuid);
        
        var apiClient = PokeFactoryLegends.getApiClient();
        return apiClient.getPlayerPokedexSummary(playerUuid)
            .thenApply(playerData -> {
                if (playerData == null) {
                    return "Failed to retrieve player data from database";
                }
                
                return applyPlayerDatabaseSummary(playerUuid, playerData);
            });
    }
    
    private CompletableFuture<String> applyDatabaseLeaderboard(JsonObject leaderboard) {
        try {
            // Note: Leaderboard doesn't contain detailed pokedex data, only summaries
            // This is a limitation of the available API endpoints
            PokeFactoryLegends.LOGGER.warn("Leaderboard sync only provides summary data, not detailed pokedex entries");
            
            return CompletableFuture.completedFuture(
                "Leaderboard retrieved but detailed sync not possible with current API endpoints"
            );
                        
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.error("Failed to apply database leaderboard", e);
            return CompletableFuture.completedFuture("Error applying leaderboard: " + e.getMessage());
        }
    }
    
    private CompletableFuture<Map<UUID, JsonObject[]>> generateLocalPlayerUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<UUID, JsonObject[]> playerUpdates = new HashMap<>();
                
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return playerUpdates;
                }
                
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    JsonArray pokedexData = getCobblemonPokedexData(player.getUUID());
                    List<JsonObject> updates = new ArrayList<>();
                    
                    for (JsonElement element : pokedexData) {
                        JsonObject entry = element.getAsJsonObject();
                        JsonObject update = new JsonObject();
                        update.addProperty("national_id", entry.get("national_dex_number").getAsInt());
                        update.addProperty("action", "catch");
                        updates.add(update);
                    }
                    
                    if (!updates.isEmpty()) {
                        playerUpdates.put(player.getUUID(), updates.toArray(new JsonObject[0]));
                    }
                }
                
                return playerUpdates;
                
            } catch (Exception e) {
                PokeFactoryLegends.LOGGER.error("Failed to generate local player updates", e);
                return new HashMap<UUID, JsonObject[]>();
            }
        }).exceptionally(throwable -> {
            PokeFactoryLegends.LOGGER.error("Async player update generation failed", throwable);
            return new HashMap<UUID, JsonObject[]>();
        });
    }
    
    private String applyPlayerDatabaseSummary(UUID playerUuid, JsonObject playerData) {
        try {
            // Summary only contains totals, not individual entries
            if (playerData.has("total_caught")) {
                int totalCaught = playerData.get("total_caught").getAsInt();
                return "Player has " + totalCaught + " Pokemon caught (summary only, detailed sync not available)";
            }
            
            return "Retrieved player summary but no detailed sync possible with current API endpoints";
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.error("Failed to apply player database data", e);
            String sanitizedMessage = e.getMessage() != null ? 
                e.getMessage().replaceAll("[<>&\"']", "") : "Unknown error";
            return "Error applying player data: " + sanitizedMessage;
        }
    }
    
    private void applyCobblemonPokedexEntry(UUID playerUuid, int nationalDexNumber) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) return;
            
            // Use reflection to access Cobblemon's pokedex system
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object cobblemonInstance = cobblemonClass.getField("INSTANCE").get(null);
            
            // Get player data
            Object storage = cobblemonClass.getMethod("getStorage").invoke(cobblemonInstance);
            Object playerData = storage.getClass().getMethod("getParty", java.util.UUID.class).invoke(storage, playerUuid);
            
            // Get pokedex from player data
            Object pokedex = playerData.getClass().getMethod("getPokedex").invoke(playerData);
            
            // Get species by national dex number
            Class<?> speciesClass = Class.forName("com.cobblemon.mod.common.pokemon.Species");
            Object species = speciesClass.getMethod("getByNationalPokedexNumber", int.class).invoke(null, nationalDexNumber);
            
            if (species != null) {
                // Set as caught in pokedex
                pokedex.getClass().getMethod("setHasCaught", speciesClass, boolean.class).invoke(pokedex, species, true);
                PokeFactoryLegends.LOGGER.info("Applied pokedex entry #{} for player {}", nationalDexNumber, playerUuid);
            }
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.warn("Could not apply pokedex entry #{} for player {}: {}", 
                nationalDexNumber, playerUuid, e.getMessage());
        }
    }
    
    private JsonArray getCobblemonPokedexData(UUID playerUuid) {
        JsonArray pokedexData = new JsonArray();
        
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return pokedexData;
            
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) return pokedexData;
            
            // Use reflection to access Cobblemon's pokedex system
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object cobblemonInstance = cobblemonClass.getField("INSTANCE").get(null);
            
            // Get player data
            Object storage = cobblemonClass.getMethod("getStorage").invoke(cobblemonInstance);
            Object playerData = storage.getClass().getMethod("getParty", java.util.UUID.class).invoke(storage, playerUuid);
            
            // Get pokedex from player data
            Object pokedex = playerData.getClass().getMethod("getPokedex").invoke(playerData);
            
            // Get all species and check which are caught
            Class<?> speciesClass = Class.forName("com.cobblemon.mod.common.pokemon.Species");
            Object allSpecies = speciesClass.getMethod("getAllSpecies").invoke(null);
            
            if (allSpecies instanceof Iterable) {
                for (Object species : (Iterable<?>) allSpecies) {
                    boolean hasCaught = (Boolean) pokedex.getClass().getMethod("hasCaught", speciesClass).invoke(pokedex, species);
                    
                    if (hasCaught) {
                        int nationalDexNumber = (Integer) species.getClass().getMethod("getNationalPokedexNumber").invoke(species);
                        
                        JsonObject entry = new JsonObject();
                        entry.addProperty("national_dex_number", nationalDexNumber);
                        entry.addProperty("caught", true);
                        pokedexData.add(entry);
                    }
                }
            }
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.warn("Could not retrieve pokedex data for player {}: {}", playerUuid, e.getMessage());
        }
        
        return pokedexData;
    }
    
    private CompletableFuture<Boolean> syncAllPlayersToDatabase(Map<UUID, JsonObject[]> playerUpdates) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (Map.Entry<UUID, JsonObject[]> entry : playerUpdates.entrySet()) {
            UUID playerUuid = entry.getKey();
            JsonObject[] updates = entry.getValue();
            
            CompletableFuture<Boolean> future = PokeFactoryLegends.getApiClient()
                .sendBatchUpdate(playerUuid, updates);
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
    }
}