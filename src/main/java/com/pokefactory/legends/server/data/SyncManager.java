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
    private static SyncManager instance;
    
    private SyncManager() {}
    
    public static SyncManager getInstance() {
        if (instance == null) {
            instance = new SyncManager();
        }
        return instance;
    }
    
    public CompletableFuture<String> syncFromDatabase() {
        PokeFactoryLegends.LOGGER.info("Starting sync FROM database (overwriting local data)");
        
        return PokeFactoryLegends.getApiClient().getServerPokedexSnapshot()
            .thenCompose(snapshot -> {
                if (snapshot == null) {
                    return CompletableFuture.completedFuture("Failed to retrieve database snapshot");
                }
                
                return applyDatabaseSnapshot(snapshot);
            });
    }
    
    public CompletableFuture<String> syncToDatabase() {
        PokeFactoryLegends.LOGGER.info("Starting sync TO database (overwriting database with local data)");
        
        return generateLocalSnapshot()
            .thenCompose(localSnapshot -> {
                if (localSnapshot == null) {
                    return CompletableFuture.completedFuture("Failed to generate local snapshot");
                }
                
                return PokeFactoryLegends.getApiClient().overwriteServerPokedexData(localSnapshot)
                    .thenApply(success -> {
                        if (success) {
                            // Clear pending captures since we just overwrote the database
                            ServerDataManager.getInstance().clearPendingCaptures();
                            return "Successfully synced local data to database";
                        } else {
                            return "Failed to sync local data to database";
                        }
                    });
            });
    }
    
    public CompletableFuture<String> syncPlayerFromDatabase(UUID playerUuid) {
        PokeFactoryLegends.LOGGER.info("Syncing player {} FROM database", playerUuid);
        
        return PokeFactoryLegends.getApiClient().getAllPlayerPokedexData(playerUuid)
            .thenApply(playerData -> {
                if (playerData == null) {
                    return "Failed to retrieve player data from database";
                }
                
                return applyPlayerDatabaseData(playerUuid, playerData);
            });
    }
    
    private CompletableFuture<String> applyDatabaseSnapshot(JsonObject snapshot) {
        try {
            // Clear all pending captures first
            ServerDataManager.getInstance().clearPendingCaptures();
            
            if (!snapshot.has("players")) {
                return CompletableFuture.completedFuture("Invalid snapshot format");
            }
            
            JsonArray players = snapshot.getAsJsonArray("players");
            int totalUpdated = 0;
            
            for (JsonElement playerElement : players) {
                JsonObject playerData = playerElement.getAsJsonObject();
                UUID playerUuid = UUID.fromString(playerData.get("player_uuid").getAsString());
                
                if (playerData.has("pokedex_data")) {
                    JsonArray pokedexData = playerData.getAsJsonArray("pokedex_data");
                    for (JsonElement entryElement : pokedexData) {
                        JsonObject entry = entryElement.getAsJsonObject();
                        int nationalDexNumber = entry.get("national_dex_number").getAsInt();
                        
                        // Apply to Cobblemon player data (this would need Cobblemon integration)
                        applyCobblemonPokedexEntry(playerUuid, nationalDexNumber);
                        totalUpdated++;
                    }
                }
            }
            
            return CompletableFuture.completedFuture(
                "Successfully applied database snapshot: " + totalUpdated + " entries updated"
            );
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.error("Failed to apply database snapshot", e);
            return CompletableFuture.completedFuture("Error applying snapshot: " + e.getMessage());
        }
    }
    
    private CompletableFuture<JsonObject> generateLocalSnapshot() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject snapshot = new JsonObject();
                JsonArray playersArray = new JsonArray();
                
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return null;
                }
                
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    JsonObject playerData = new JsonObject();
                    playerData.addProperty("player_uuid", player.getUUID().toString());
                    playerData.addProperty("player_name", player.getName().getString());
                    
                    // Get Cobblemon pokedex data for this player
                    JsonArray pokedexData = getCobblemonPokedexData(player.getUUID());
                    playerData.add("pokedex_data", pokedexData);
                    
                    playersArray.add(playerData);
                }
                
                snapshot.add("players", playersArray);
                snapshot.addProperty("timestamp", System.currentTimeMillis());
                snapshot.addProperty("server_id", "pokefactory_server_1");
                
                return snapshot;
                
            } catch (Exception e) {
                PokeFactoryLegends.LOGGER.error("Failed to generate local snapshot", e);
                return null;
            }
        }).exceptionally(throwable -> {
            PokeFactoryLegends.LOGGER.error("Async snapshot generation failed", throwable);
            return null;
        });
    }
    
    private String applyPlayerDatabaseData(UUID playerUuid, JsonObject playerData) {
        try {
            if (!playerData.has("pokedex_data")) {
                return "No pokedex data found for player";
            }
            
            JsonArray pokedexData = playerData.getAsJsonArray("pokedex_data");
            int entriesApplied = 0;
            
            for (JsonElement entryElement : pokedexData) {
                JsonObject entry = entryElement.getAsJsonObject();
                int nationalDexNumber = entry.get("national_dex_number").getAsInt();
                
                applyCobblemonPokedexEntry(playerUuid, nationalDexNumber);
                entriesApplied++;
            }
            
            return "Applied " + entriesApplied + " pokedex entries for player";
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.error("Failed to apply player database data", e);
            return "Error applying player data: " + e.getMessage();
        }
    }
    
    private void applyCobblemonPokedexEntry(UUID playerUuid, int nationalDexNumber) {
        // This would integrate with Cobblemon's pokedex system
        // For now, just log the action
        PokeFactoryLegends.LOGGER.info("Would apply pokedex entry #{} for player {}", 
            nationalDexNumber, playerUuid);
        
        /*
         * Actual Cobblemon integration would look like:
         * 
         * ServerPlayer player = getPlayerByUuid(playerUuid);
         * if (player != null) {
         *     CobblemonPokedex pokedex = Cobblemon.playerData.get(player).getPokedex();
         *     Species species = PokemonSpecies.getByNationalPokedexNumber(nationalDexNumber);
         *     pokedex.setHasCaught(species, true);
         * }
         */
    }
    
    private JsonArray getCobblemonPokedexData(UUID playerUuid) {
        JsonArray pokedexData = new JsonArray();
        
        // This would integrate with Cobblemon's pokedex system
        // For now, return empty array
        
        /*
         * Actual Cobblemon integration would look like:
         * 
         * ServerPlayer player = getPlayerByUuid(playerUuid);
         * if (player != null) {
         *     CobblemonPokedex pokedex = Cobblemon.playerData.get(player).getPokedex();
         *     for (Species species : PokemonSpecies.getAllSpecies()) {
         *         if (pokedex.hasCaught(species)) {
         *             JsonObject entry = new JsonObject();
         *             entry.addProperty("national_dex_number", species.getNationalPokedexNumber());
         *             entry.addProperty("caught", true);
         *             pokedexData.add(entry);
         *         }
         *     }
         * }
         */
        
        return pokedexData;
    }
}