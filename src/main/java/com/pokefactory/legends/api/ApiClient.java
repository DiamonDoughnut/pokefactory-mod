package com.pokefactory.legends.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pokefactory.legends.PokeFactoryLegends;
import com.pokefactory.legends.config.ModConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private final HttpClient httpClient;
    private final Gson gson;
    private volatile String serverToken;
    private volatile boolean shutdown = false;

    public ApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ModConfig.CONNECTION_TIMEOUT.get()))
                .build();
        this.gson = new Gson();
        this.serverToken = ModConfig.SERVER_TOKEN.get();
    }

    public CompletableFuture<Boolean> authenticateServer() {
        PokeFactoryLegends.LOGGER.debug("authenticateServer method called");
        JsonObject authData = new JsonObject();
        authData.addProperty("server_id", "pokefactory_server_1");
        authData.addProperty("server_key", ModConfig.SERVER_SECRET.get());
        
        return sendPostRequest("/server/auth", authData)
                .thenApply(response -> {
                    PokeFactoryLegends.LOGGER.debug("Received HTTP response: {}", response);    
                    if (response != null && response.has("token")) {
                        serverToken = response.get("token").getAsString();
                        PokeFactoryLegends.LOGGER.info("Server authenticated successfully");
                        return true;
                    }
                    PokeFactoryLegends.LOGGER.error("Authentication failed - no token in response: {}", response);
                    return false;
                });
    }

    public CompletableFuture<JsonObject> createPlayer(UUID playerUuid, String username) {
        JsonObject playerData = new JsonObject();
        playerData.addProperty("player_uuid", playerUuid.toString());
        playerData.addProperty("username", username);
        
        return sendPostRequest("/server/player/create", playerData);
    }

    public CompletableFuture<JsonObject> getPlayer(UUID playerUuid) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        
        return sendPostRequest("/server/player/get", requestData);
    }

    public CompletableFuture<JsonObject> updatePlayerStats(UUID playerUuid, JsonObject stats) {
        JsonObject statsData = new JsonObject();
        statsData.addProperty("player_uuid", playerUuid.toString());
        statsData.add("stats", stats);
        
        return sendPostRequest("/server/player/stats/update", statsData);
    }

    public CompletableFuture<JsonObject> updatePokedex(UUID playerUuid, int nationalDexNumber, boolean caught) {
        JsonObject pokedexData = new JsonObject();
        pokedexData.addProperty("player_uuid", playerUuid.toString());
        pokedexData.addProperty("national_id", nationalDexNumber);
        pokedexData.addProperty("action", caught ? "catch" : "see");
        
        return sendPostRequest("/server/pokedex/update", pokedexData);
    }

    // Batch update by iterating individual updates since no batch endpoint exists
    public CompletableFuture<Boolean> sendBatchUpdate(UUID playerUuid, JsonObject[] pokedexUpdates) {
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(true);
        
        for (JsonObject update : pokedexUpdates) {
            int nationalId = update.get("national_id").getAsInt();
            String action = update.get("action").getAsString();
            boolean caught = "catch".equals(action);
            
            result = result.thenCompose(success -> {
                if (!success) return CompletableFuture.completedFuture(false);
                return updatePokedex(playerUuid, nationalId, caught)
                        .thenApply(response -> response != null);
            });
        }
        
        return result;
    }

    // Get player's Pokedex summary using available endpoint
    public CompletableFuture<JsonObject> getPlayerPokedexSummary(UUID playerUuid) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        
        return sendPostRequest("/server/pokedex/summary", requestData);
    }

    // Get leaderboard data as closest equivalent to server snapshot
    public CompletableFuture<JsonObject> getPokedexLeaderboard() {
        return sendPostRequest("/server/pokedex/leaderboard", new JsonObject());
    }

    // Get player's regional Pokedex data
    public CompletableFuture<JsonObject> getPlayerRegionalPokedex(UUID playerUuid, String region) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        requestData.addProperty("region", region);
        
        return sendPostRequest("/server/pokedex/region", requestData);
    }
    
    // Get player stats using server endpoint
    public CompletableFuture<JsonObject> getPlayerStats(UUID playerUuid) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        
        return sendPostRequest("/server/player/stats/get", requestData);
    }
    
    // Set player data using server endpoint
    public CompletableFuture<JsonObject> setPlayerData(UUID playerUuid, String key, String value) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        requestData.addProperty("key", key);
        requestData.addProperty("value", value);
        
        return sendPostRequest("/server/player/data/set", requestData);
    }
    
    // Get player data using server endpoint
    public CompletableFuture<JsonObject> getPlayerData(UUID playerUuid, String key) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        requestData.addProperty("key", key);
        
        return sendPostRequest("/server/player/data/get", requestData);
    }

    private CompletableFuture<JsonObject> sendPostRequest(String endpoint, JsonObject data) {
        PokeFactoryLegends.LOGGER.debug("Calling sendPostRequest with endpoint: {}", endpoint);
        PokeFactoryLegends.LOGGER.debug("Preparing to send POST request to: {}", endpoint);
        PokeFactoryLegends.LOGGER.debug("Request body: {}", gson.toJson(data));
        if (shutdown) {
            return CompletableFuture.completedFuture(null);
        }
        
        String url = ModConfig.API_BASE_URL.get() + endpoint;
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ModConfig.CONNECTION_TIMEOUT.get()))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(data)));
                
        String token = serverToken; // Local copy to avoid race conditions
        if (token != null && !token.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        
        HttpRequest request = requestBuilder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(ModConfig.CONNECTION_TIMEOUT.get() + 5, TimeUnit.SECONDS)
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            return gson.fromJson(response.body(), JsonObject.class);
                        } catch (Exception e) {
                            PokeFactoryLegends.LOGGER.error("Failed to parse API response", e);
                            return null;
                        }
                    } else {
                        PokeFactoryLegends.LOGGER.warn("API request failed: {} - {}", response.statusCode(), response.body());
                        return null;
                    }
                })
                .exceptionally(throwable -> {
                    return null;
                });
    }
    
    public void shutdown() {
        shutdown = true;
        // HttpClient doesn't need explicit shutdown in Java 11+
        // but we mark as shutdown to prevent new requests
    }
}