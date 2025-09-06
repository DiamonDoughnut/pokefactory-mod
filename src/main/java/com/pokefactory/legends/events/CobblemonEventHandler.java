package com.pokefactory.legends.events;

import com.pokefactory.legends.PokeFactoryLegends;
import com.pokefactory.legends.server.data.ServerDataManager;
import com.pokefactory.legends.util.DevEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = PokeFactoryLegends.MOD_ID)
public class CobblemonEventHandler {
    private static boolean cobblemonEventsRegistered = false;

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Register Cobblemon events on first player join
            if (!cobblemonEventsRegistered) {
                registerCobblemonEvents();
                cobblemonEventsRegistered = true;
            }
            
            // Only register players if we're running server logic
            if (DevEnvironment.shouldRunServerLogic(player.level())) {
                PokeFactoryLegends.getApiClient().createPlayer(
                    player.getUUID(), 
                    player.getName().getString()
                ).thenAccept(response -> {
                    if (response != null) {
                        PokeFactoryLegends.LOGGER.info("Player {} registered with backend", player.getName().getString());
                    } else {
                        PokeFactoryLegends.LOGGER.warn("Failed to register player {} with backend", player.getName().getString());
                    }
                });
            }
        }
    }

    public static void registerCobblemonEvents() {
        try {
            // Use reflection to safely register Cobblemon events
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> captureEventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
            
            // Get the POKEMON_CAPTURED event field
            Object pokemonCapturedEvent = eventsClass.getField("POKEMON_CAPTURED").get(null);
            
            // Create event handler using reflection
            java.lang.reflect.Method subscribeMethod = pokemonCapturedEvent.getClass().getMethod("subscribe", 
                Class.forName("com.cobblemon.mod.common.api.Priority"), 
                java.util.function.Function.class);
            
            // Get Priority.NORMAL
            Class<?> priorityClass = Class.forName("com.cobblemon.mod.common.api.Priority");
            Object normalPriority = priorityClass.getField("NORMAL").get(null);
            
            // Subscribe to the event
            subscribeMethod.invoke(pokemonCapturedEvent, normalPriority, (java.util.function.Function<Object, Object>) event -> {
                handlePokemonCapture(event);
                // Return Unit.INSTANCE for Kotlin compatibility
                try {
                    return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
                } catch (Exception e) {
                    return null;
                }
            });
            
            PokeFactoryLegends.LOGGER.info("Successfully registered Cobblemon events");
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.warn("Cobblemon not available, events not registered: {}", e.getMessage());
        }
    }
    
    private static void handlePokemonCapture(Object event) {
        try {
            // Use reflection to extract data from the event
            Class<?> eventClass = event.getClass();
            
            // Get player and pokemon from event
            Object player = eventClass.getMethod("getPlayer").invoke(event);
            Object pokemon = eventClass.getMethod("getPokemon").invoke(event);
            
            if (player == null || pokemon == null) {
                return;
            }
            
            ServerPlayer serverPlayer = (ServerPlayer) player;
            
            // Only process if we should run server logic
            if (!DevEnvironment.shouldRunServerLogic(serverPlayer.level())) {
                return;
            }
            
            // Extract Pokemon data using reflection
            Class<?> pokemonClass = pokemon.getClass();
            Object species = pokemonClass.getMethod("getSpecies").invoke(pokemon);
            
            int nationalDexNumber = (Integer) species.getClass().getMethod("getNationalPokedexNumber").invoke(species);
            String pokemonName = (String) species.getClass().getMethod("getName").invoke(species);
            boolean isShiny = (Boolean) pokemonClass.getMethod("getShiny").invoke(pokemon);
            int level = (Integer) pokemonClass.getMethod("getLevel").invoke(pokemon);
            
            // Record capture in server data manager
            ServerDataManager.getInstance().recordCapture(
                serverPlayer.getUUID(),
                nationalDexNumber
            );
            
            PokeFactoryLegends.LOGGER.info("Player {} caught #{} {} (Level {}){}",
                serverPlayer.getName().getString(), 
                nationalDexNumber, 
                pokemonName,
                level,
                isShiny ? " [SHINY]" : ""
            );
            
        } catch (Exception e) {
            PokeFactoryLegends.LOGGER.error("Error handling Pokemon capture event", e);
        }
    }
}