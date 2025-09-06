package com.pokefactory.legends.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pokefactory.legends.PokeFactoryLegends;
import com.pokefactory.legends.server.data.ServerDataManager;
import com.pokefactory.legends.util.DevEnvironment;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

@EventBusSubscriber(modid = PokeFactoryLegends.MOD_ID)
public class ApiTestCommand {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(Commands.literal("pftest")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("auth")
                .executes(context -> {
                    testServerAuth(context.getSource());
                    return 1;
                }))
            .then(Commands.literal("player")
                .then(Commands.argument("action", StringArgumentType.string())
                    .executes(context -> {
                        String action = StringArgumentType.getString(context, "action");
                        testPlayerAction(context.getSource(), action);
                        return 1;
                    })))
            .then(Commands.literal("pokedex")
                .then(Commands.argument("dexNumber", IntegerArgumentType.integer(1, 1025))
                    .executes(context -> {
                        int dexNumber = IntegerArgumentType.getInteger(context, "dexNumber");
                        testPokedexUpdate(context.getSource(), dexNumber);
                        return 1;
                    })))
            .then(Commands.literal("stats")
                .then(Commands.argument("statName", StringArgumentType.string())
                    .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(context -> {
                            String statName = StringArgumentType.getString(context, "statName");
                            int value = IntegerArgumentType.getInteger(context, "value");
                            testStatsUpdate(context.getSource(), statName, value);
                            return 1;
                        }))))
            .then(Commands.literal("server")
                .then(Commands.literal("status")
                    .executes(context -> {
                        showServerStatus(context.getSource());
                        return 1;
                    }))
                .then(Commands.literal("sync")
                    .executes(context -> {
                        forceSyncNow(context.getSource());
                        return 1;
                    }))
                .then(Commands.literal("simulate")
                    .then(Commands.argument("dexNumber", IntegerArgumentType.integer(1, 1025))
                        .executes(context -> {
                            int dexNumber = IntegerArgumentType.getInteger(context, "dexNumber");
                            simulateCapture(context.getSource(), dexNumber);
                            return 1;
                        }))))
            .then(Commands.literal("dev")
                .then(Commands.literal("info")
                    .executes(context -> {
                        showDevInfo(context.getSource());
                        return 1;
                    })))
        );
    }

    private static void testServerAuth(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Testing server authentication..."), false);
        
        PokeFactoryLegends.getApiClient().authenticateServer()
            .thenAccept(success -> {
                if (success) {
                    source.sendSuccess(() -> Component.literal("✓ Server authentication successful"), false);
                } else {
                    source.sendFailure(Component.literal("✗ Server authentication failed"));
                }
            });
    }

    private static void testPlayerAction(CommandSourceStack source, String action) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return;
        }

        UUID playerUuid = player.getUUID();
        String playerName = player.getName().getString();

        switch (action.toLowerCase()) {
            case "create" -> {
                source.sendSuccess(() -> Component.literal("Testing player creation..."), false);
                PokeFactoryLegends.getApiClient().createPlayer(playerUuid, playerName)
                    .thenAccept(response -> {
                        if (response != null) {
                            source.sendSuccess(() -> Component.literal("✓ Player created: " + response), false);
                        } else {
                            source.sendFailure(Component.literal("✗ Player creation failed"));
                        }
                    });
            }
            case "get" -> {
                source.sendSuccess(() -> Component.literal("Testing player retrieval..."), false);
                PokeFactoryLegends.getApiClient().getPlayer(playerUuid)
                    .thenAccept(response -> {
                        if (response != null) {
                            source.sendSuccess(() -> Component.literal("✓ Player data: " + response), false);
                        } else {
                            source.sendFailure(Component.literal("✗ Player retrieval failed"));
                        }
                    });
            }
            default -> source.sendFailure(Component.literal("Unknown action. Use 'create' or 'get'"));
        }
    }

    private static void testPokedexUpdate(CommandSourceStack source, int dexNumber) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return;
        }

        source.sendSuccess(() -> Component.literal("Testing Pokédex update for #" + dexNumber + "..."), false);
        
        PokeFactoryLegends.getApiClient().updatePokedex(player.getUUID(), dexNumber, true)
            .thenAccept(response -> {
                if (response != null) {
                    source.sendSuccess(() -> Component.literal("✓ Pokédex updated: " + response), false);
                } else {
                    source.sendFailure(Component.literal("✗ Pokédex update failed"));
                }
            });
    }

    private static void testStatsUpdate(CommandSourceStack source, String statName, int value) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return;
        }

        source.sendSuccess(() -> Component.literal("Testing stats update: " + statName + " = " + value), false);
        
        PokeFactoryLegends.getApiClient().updatePlayerStats(player.getUUID(), statName, value)
            .thenAccept(response -> {
                if (response != null) {
                    source.sendSuccess(() -> Component.literal("✓ Stats updated: " + response), false);
                } else {
                    source.sendFailure(Component.literal("✗ Stats update failed"));
                }
            });
    }
    
    private static void showServerStatus(CommandSourceStack source) {
        int pendingCount = ServerDataManager.getInstance().getPendingCaptureCount();
        source.sendSuccess(() -> Component.literal(
            "Server Data Status:\n" +
            "- Pending captures: " + pendingCount + "\n" +
            "- Sync interval: 30 seconds"
        ), false);
    }
    
    private static void forceSyncNow(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Forcing immediate API sync..."), false);
        ServerDataManager.getInstance().forceSyncNow();
        source.sendSuccess(() -> Component.literal("✓ Sync initiated"), false);
    }
    
    private static void simulateCapture(CommandSourceStack source, int dexNumber) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return;
        }
        
        ServerDataManager.getInstance().recordCapture(player.getUUID(), dexNumber);
        source.sendSuccess(() -> Component.literal(
            "✓ Simulated capture of Pokémon #" + dexNumber + " for " + player.getName().getString()
        ), false);
    }
    
    private static void showDevInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Development Environment Info:"), false);
        source.sendSuccess(() -> Component.literal(DevEnvironment.getDevInfo()), false);
        source.sendSuccess(() -> Component.literal("Use config to enable dev_mode and simulate_multiplayer"), false);
    }
}