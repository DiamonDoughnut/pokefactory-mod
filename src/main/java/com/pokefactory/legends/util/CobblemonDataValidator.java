package com.pokefactory.legends.util;

import com.pokefactory.legends.PokeFactoryLegends;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CobblemonDataValidator {
    
    public static CompletableFuture<ValidationResult> validatePlayer(ServerPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Set<Integer> cobblemonCaught = getCobblemonPokedexData(player);
                Set<Integer> backendCaught = getBackendPokedexData(player);
                
                Set<Integer> missingInBackend = new HashSet<>(cobblemonCaught);
                missingInBackend.removeAll(backendCaught);
                
                Set<Integer> extraInBackend = new HashSet<>(backendCaught);
                extraInBackend.removeAll(cobblemonCaught);
                
                return new ValidationResult(player.getUUID(), cobblemonCaught.size(), 
                    backendCaught.size(), missingInBackend, extraInBackend);
                    
            } catch (Exception e) {
                PokeFactoryLegends.LOGGER.error("Failed to validate player {}", player.getName().getString(), e);
                return ValidationResult.error(player.getUUID(), e.getMessage());
            }
        });
    }
    
    private static Set<Integer> getCobblemonPokedexData(ServerPlayer player) {
        Set<Integer> caught = new HashSet<>();
        try {
            // Use reflection to access Cobblemon's Pokedex data
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object storage = cobblemonClass.getMethod("getStorage").invoke(null);
            
            Class<?> storageClass = storage.getClass();
            Object playerData = storageClass.getMethod("getPlayerData", UUID.class)
                .invoke(storage, player.getUUID());
            
            if (playerData != null) {
                Object pokedexData = playerData.getClass().getMethod("getPokedexData").invoke(playerData);
                
                // Get all caught species
                Object caughtSpecies = pokedexData.getClass().getMethod("getCaughtSpecies").invoke(pokedexData);
                
                if (caughtSpecies instanceof Collection) {
                    for (Object species : (Collection<?>) caughtSpecies) {
                        Integer nationalId = (Integer) species.getClass().getMethod("getNationalPokedexNumber").invoke(species);
                        caught.add(nationalId);
                    }
                }
            }
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.warn("Could not access Cobblemon pokedex data for {}: {}", 
                player.getName().getString(), e.getMessage());
        }
        return caught;
    }
    
    private static Set<Integer> getBackendPokedexData(ServerPlayer player) {
        Set<Integer> caught = new HashSet<>();
        try {
            // Fetch from backend API synchronously (since we're already in async context)
            var response = PokeFactoryLegends.getApiClient().getPlayerPokedexSummary(player.getUUID()).join();
            
            if (response != null && response.has("caught_pokemon")) {
                var caughtArray = response.getAsJsonArray("caught_pokemon");
                for (var element : caughtArray) {
                    caught.add(element.getAsInt());
                }
            }
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.warn("Could not fetch backend pokedex data for {}: {}", 
                player.getName().getString(), e.getMessage());
        }
        return caught;
    }
    
    public static class ValidationResult {
        public final UUID playerId;
        public final int cobblemonCount;
        public final int backendCount;
        public final Set<Integer> missingInBackend;
        public final Set<Integer> extraInBackend;
        public final boolean hasError;
        public final String errorMessage;
        
        public ValidationResult(UUID playerId, int cobblemonCount, int backendCount, 
                              Set<Integer> missingInBackend, Set<Integer> extraInBackend) {
            this.playerId = playerId;
            this.cobblemonCount = cobblemonCount;
            this.backendCount = backendCount;
            this.missingInBackend = missingInBackend;
            this.extraInBackend = extraInBackend;
            this.hasError = false;
            this.errorMessage = null;
        }
        
        public static ValidationResult error(UUID playerId, String error) {
            return new ValidationResult(playerId, 0, 0, new HashSet<>(), new HashSet<>()) {
                private final boolean hasError = true;
                private final String errorMessage = error;
                
                @Override
                public boolean hasError() { return hasError; }
                @Override
                public String getErrorMessage() { return errorMessage; }
            };
        }
        
        public boolean hasError() { return hasError; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isInSync() { return missingInBackend.isEmpty() && extraInBackend.isEmpty(); }
    }
}