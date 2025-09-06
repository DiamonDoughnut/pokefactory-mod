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
    private String serverToken;
    private volatile boolean shutdown = false;

    public ApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ModConfig.CONNECTION_TIMEOUT.get()))
                .build();
        this.gson = new Gson();
        this.serverToken = ModConfig.SERVER_TOKEN.get();
    }

    public CompletableFuture<Boolean> authenticateServer() {
        JsonObject authData = new JsonObject();
        authData.addProperty("server_id", "pokefactory_server_1");
        
        return sendPostRequest("/server/auth", authData)
                .thenApply(response -> {
                    if (response != null && response.has("token")) {
                        serverToken = response.get("token").getAsString();
                        PokeFactoryLegends.LOGGER.info("Server authenticated successfully");
                        return true;
                    }
                    PokeFactoryLegends.LOGGER.error("Authentication failed - no token in response: {}", response);
                    return false;
                });
    }

    public CompletableFuture<JsonObject> createPlayer(UUID playerUuid, String playerName) {
        JsonObject playerData = new JsonObject();
        playerData.addProperty("player_uuid", playerUuid.toString());
        playerData.addProperty("player_name", playerName);
        
        return sendPostRequest("/server/player/create", playerData);
    }

    public CompletableFuture<JsonObject> getPlayer(UUID playerUuid) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        
        return sendPostRequest("/server/player/profile", requestData);
    }

    public CompletableFuture<JsonObject> updatePlayerStats(UUID playerUuid, String statName, int value) {
        JsonObject statsData = new JsonObject();
        statsData.addProperty("player_uuid", playerUuid.toString());
        statsData.addProperty("stat_name", statName);
        statsData.addProperty("value", value);
        
        return sendPostRequest("/server/player/stats", statsData);
    }

    public CompletableFuture<JsonObject> updatePokedex(UUID playerUuid, int nationalDexNumber, boolean caught) {
        JsonObject pokedexData = new JsonObject();
        pokedexData.addProperty("player_uuid", playerUuid.toString());
        pokedexData.addProperty("national_id", nationalDexNumber);
        pokedexData.addProperty("action", caught ? "catch" : "release");
        
        return sendPostRequest("/pokedex/update", pokedexData);
    }

    public CompletableFuture<Boolean> sendBatchUpdate(JsonObject batchData) {
        return sendPostRequest("/pokedex/batch", batchData)
                .thenApply(response -> response != null);
    }

    public CompletableFuture<JsonObject> getAllPlayerPokedexData(UUID playerUuid) {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("player_uuid", playerUuid.toString());
        
        return sendPostRequest("/pokedex/get-all", requestData);
    }

    public CompletableFuture<JsonObject> getServerPokedexSnapshot() {
        JsonObject requestData = new JsonObject();
        requestData.addProperty("snapshot_type", "full");
        
        return sendPostRequest("/pokedex/snapshot", requestData);
    }

    public CompletableFuture<Boolean> overwriteServerPokedexData(JsonObject snapshotData) {
        return sendPostRequest("/pokedex/overwrite", snapshotData)
                .thenApply(response -> response != null);
    }

    private CompletableFuture<JsonObject> sendPostRequest(String endpoint, JsonObject data) {
        if (shutdown) {
            return CompletableFuture.completedFuture(null);
        }
        
        String url = ModConfig.API_BASE_URL.get() + endpoint;
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ModConfig.CONNECTION_TIMEOUT.get()))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(data)));
                
        if (serverToken != null && !serverToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + serverToken);
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
                    if (!shutdown) {
                        PokeFactoryLegends.LOGGER.error("API request failed", throwable);
                    }
                    return null;
                });
    }
    
    public void shutdown() {
        shutdown = true;
        // HttpClient doesn't need explicit shutdown in Java 11+
        // but we mark as shutdown to prevent new requests
    }
}