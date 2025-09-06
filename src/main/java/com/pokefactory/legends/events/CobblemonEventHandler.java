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

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
            } else {
                PokeFactoryLegends.LOGGER.info("Skipping player registration in dev/single-player mode");
            }
        }
    }

    // Cobblemon Events - These will be available when Cobblemon is properly loaded
    // Based on Cobblemon API documentation and common patterns:
    
    /*
     * Primary Pokemon Capture Event
     * Available data from PokemonCapturedEvent:
     * - getPlayer() -> ServerPlayer (the player who caught the Pokemon)
     * - getPokemon() -> Pokemon (the caught Pokemon instance)
     * 
     * From Pokemon object you can access:
     * - getSpecies() -> Species (Pokemon species data)
     * - getSpecies().getNationalPokedexNumber() -> int (National Dex #)
     * - getSpecies().getName() -> String (Pokemon name)
     * - getLevel() -> int (Pokemon level)
     * - getShiny() -> boolean (is shiny)
     * - getNature() -> Nature (Pokemon nature)
     * - getAbility() -> Ability (Pokemon ability)
     * - getIVs() -> IVs (Individual Values)
     * - getEVs() -> EVs (Effort Values)
     * - getForm() -> FormData (Pokemon form/variant)
     * - getGender() -> Gender (Pokemon gender)
     * - getBall() -> PokeBall (ball used to catch)
     * - getOriginalTrainer() -> String (OT name)
     * - getOriginalTrainerUUID() -> UUID (OT UUID)
     * - getCaughtBall() -> PokeBall (ball it was caught in)
     * - getTeraType() -> ElementalType (Tera type if applicable)
     */
    
    // This method will be registered when Cobblemon is available
    public static void registerCobblemonEvents() {
        try {
            // Using reflection to avoid compile-time dependency issues
            Class<?> cobblemonEventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> pokemonCapturedEventClass = Class.forName("com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent");
            
            // This would be the actual event registration when Cobblemon is available:
            /*
            CobblemonEvents.POKEMON_CAPTURED.subscribe(event -> {
                PokemonCapturedEvent captureEvent = (PokemonCapturedEvent) event;
                ServerPlayer player = captureEvent.getPlayer();
                Pokemon pokemon = captureEvent.getPokemon();
                
                // Extract essential data
                int nationalDexNumber = pokemon.getSpecies().getNationalPokedexNumber();
                String pokemonName = pokemon.getSpecies().getName();
                boolean isShiny = pokemon.getShiny();
                
                // Record capture in server data manager (handles deduplication)
                ServerDataManager.getInstance().recordCapture(
                    player.getUUID(),
                    nationalDexNumber
                );
                
                PokeFactoryLegends.LOGGER.info("Player {} caught #{} {} {}", 
                    player.getName().getString(), 
                    nationalDexNumber, 
                    pokemonName,
                    isShiny ? "(Shiny)" : "");
            });
            */
            
            PokeFactoryLegends.LOGGER.info("Cobblemon events would be registered here when available");
            
        } catch (ClassNotFoundException e) {
            PokeFactoryLegends.LOGGER.warn("Cobblemon not available, skipping event registration");
        }
    }
    
    /*
     * Other potentially useful Cobblemon events:
     * 
     * - PokemonReleasedEvent: When a Pokemon is released
     * - PokemonEvolvedEvent: When a Pokemon evolves
     * - PokemonFaintedEvent: When a Pokemon faints in battle
     * - PokemonHealedEvent: When a Pokemon is healed
     * - PokemonLevelUpEvent: When a Pokemon levels up
     * - PokemonSentOutEvent: When a Pokemon is sent out for battle
     * - PokemonRecalledEvent: When a Pokemon is recalled
     * - BattleStartedEvent: When a battle begins
     * - BattleEndedEvent: When a battle ends
     * - PokemonTradeEvent: When Pokemon are traded
     * 
     * Each event provides relevant data about the Pokemon, player, and context
     */
}