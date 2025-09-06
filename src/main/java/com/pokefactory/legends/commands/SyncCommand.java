package com.pokefactory.legends.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.pokefactory.legends.PokeFactoryLegends;
import com.pokefactory.legends.server.data.SyncManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = PokeFactoryLegends.MOD_ID)
public class SyncCommand {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(Commands.literal("pfsync")
            .requires(source -> source.hasPermission(3))
            .then(Commands.literal("from-db")
                .executes(context -> {
                    syncFromDatabase(context.getSource());
                    return 1;
                }))
            .then(Commands.literal("to-db")
                .executes(context -> {
                    syncToDatabase(context.getSource());
                    return 1;
                }))
            .then(Commands.literal("player")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.literal("from-db")
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            syncPlayerFromDatabase(context.getSource(), target);
                            return 1;
                        }))))
            .then(Commands.literal("status")
                .executes(context -> {
                    showSyncStatus(context.getSource());
                    return 1;
                }))
        );
    }

    private static void syncFromDatabase(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6[SYNC] Starting sync FROM database..."), true);
        source.sendSuccess(() -> Component.literal("§c[WARNING] This will OVERWRITE all local Pokédex data!"), true);
        
        SyncManager.getInstance().syncFromDatabase()
            .thenAccept(result -> {
                if (result.contains("Successfully")) {
                    source.sendSuccess(() -> Component.literal("§a[SYNC] " + result), true);
                } else {
                    source.sendFailure(Component.literal("§c[SYNC] " + result));
                }
            });
    }

    private static void syncToDatabase(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6[SYNC] Starting sync TO database..."), true);
        source.sendSuccess(() -> Component.literal("§c[WARNING] This will OVERWRITE all database Pokédex data!"), true);
        
        SyncManager.getInstance().syncToDatabase()
            .thenAccept(result -> {
                if (result.contains("Successfully")) {
                    source.sendSuccess(() -> Component.literal("§a[SYNC] " + result), true);
                } else {
                    source.sendFailure(Component.literal("§c[SYNC] " + result));
                }
            });
    }

    private static void syncPlayerFromDatabase(CommandSourceStack source, ServerPlayer target) {
        source.sendSuccess(() -> Component.literal("§6[SYNC] Syncing player " + target.getName().getString() + " FROM database..."), true);
        
        SyncManager.getInstance().syncPlayerFromDatabase(target.getUUID())
            .thenAccept(result -> {
                if (result.contains("Applied")) {
                    source.sendSuccess(() -> Component.literal("§a[SYNC] " + result), true);
                    target.sendSystemMessage(Component.literal("§a[SYNC] Your Pokédex has been synced from the database"));
                } else {
                    source.sendFailure(Component.literal("§c[SYNC] " + result));
                }
            });
    }

    private static void showSyncStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§b=== PokéFactory Sync Status ==="), false);
        source.sendSuccess(() -> Component.literal("§e/pfsync from-db §7- Sync FROM database (overwrites local)"), false);
        source.sendSuccess(() -> Component.literal("§e/pfsync to-db §7- Sync TO database (overwrites database)"), false);
        source.sendSuccess(() -> Component.literal("§e/pfsync player <name> from-db §7- Sync specific player"), false);
        source.sendSuccess(() -> Component.literal("§c§lWARNING: §cSync operations are destructive!"), false);
    }
}