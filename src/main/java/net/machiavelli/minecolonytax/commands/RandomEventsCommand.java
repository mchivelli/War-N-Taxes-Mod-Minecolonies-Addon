package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.events.random.ActiveEvent;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.events.random.RandomEventType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commands for managing random events.
 *
 * Usage:
 *   /wnt events <colonyId> - View active events for a colony
 *   /wnt triggerevent <colonyId> <eventType> - (Admin) Manually trigger an event
 */
public class RandomEventsCommand {

    private static final SuggestionProvider<CommandSourceStack> EVENT_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(RandomEventType.values())
                            .map(RandomEventType::name)
                            .collect(Collectors.toList()),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /wnt events <colonyId>
        dispatcher.register(
                Commands.literal("wnt")
                        .then(Commands.literal("events")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                        .executes(RandomEventsCommand::viewActiveEvents))
                        )
        );

        // /wnt triggerevent <colonyId> <eventType>
        dispatcher.register(
                Commands.literal("wnt")
                        .then(Commands.literal("triggerevent")
                                .requires(source -> source.hasPermission(2)) // Requires OP
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("eventType", StringArgumentType.word())
                                                .suggests(EVENT_TYPE_SUGGESTIONS)
                                                .executes(RandomEventsCommand::triggerEvent))
                                )
                        )
        );
    }

    private static int viewActiveEvents(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");

        // Get colony
        IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.serverLevel().dimension());
        if (colony == null) {
            player.sendSystemMessage(Component.literal("§cColony not found: " + colonyId));
            return 0;
        }

        // Check if player is owner or officer
        if (!colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.MANAGE_HUTS)) {
            player.sendSystemMessage(Component.literal("§cYou must be an owner or officer of this colony!"));
            return 0;
        }

        // Get active events
        List<ActiveEvent> activeEvents = RandomEventManager.getActiveEvents(colonyId);

        if (activeEvents.isEmpty()) {
            player.sendSystemMessage(Component.literal("§e=== Active Events for " + colony.getName() + " ==="));
            player.sendSystemMessage(Component.literal("§7No active events."));
            return Command.SINGLE_SUCCESS;
        }

        // Display active events
        player.sendSystemMessage(Component.literal("§6=== Active Events for " + colony.getName() + " ===\n"));

        for (ActiveEvent event : activeEvents) {
            RandomEventType type = event.getType();

            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal(type.getColor() + "§l" + type.getDisplayName()));
            player.sendSystemMessage(Component.literal("  §7Description: §f" + type.getDescription()));
            player.sendSystemMessage(Component.literal("  §7Tax Impact: " + formatModifier(type.getTaxMultiplier())));
            player.sendSystemMessage(Component.literal("  §7Happiness: " + formatHappiness(type.getHappinessModifier())));
            player.sendSystemMessage(Component.literal("  §7Remaining: §e" + event.getRemainingCycles() + " cycles"));

            // Show affected citizens for deep integration events
            if (!event.getAffectedCitizens().isEmpty()) {
                player.sendSystemMessage(Component.literal("  §7Affected Citizens: §e" +
                    event.getAffectedCitizens().size() + " citizens"));
            }
        }

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§7Use §e/wnt triggerevent <colonyId> <eventType>§7 to manually trigger events (admin only)"));

        return Command.SINGLE_SUCCESS;
    }

    private static int triggerEvent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        String eventTypeName = StringArgumentType.getString(ctx, "eventType");

        // Get colony
        IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.serverLevel().dimension());
        if (colony == null) {
            player.sendSystemMessage(Component.literal("§cColony not found: " + colonyId));
            return 0;
        }

        // Parse event type
        RandomEventType eventType;
        try {
            eventType = RandomEventType.valueOf(eventTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("§cInvalid event type: " + eventTypeName));
            player.sendSystemMessage(Component.literal("§7Valid types: " +
                Arrays.stream(RandomEventType.values())
                    .map(RandomEventType::name)
                    .collect(Collectors.joining(", "))));
            return 0;
        }

        // Trigger event (bypasses all checks)
        try {
            RandomEventManager.forceTriggerEvent(colony, eventType);

            player.sendSystemMessage(Component.literal("§aSuccessfully triggered " + eventType.getDisplayName() +
                " for colony " + colony.getName()));

            // Notify colony owner/officers
            colony.getMessagePlayerEntities().forEach(serverPlayer -> {
                if (!serverPlayer.equals(player)) {
                    serverPlayer.sendSystemMessage(Component.literal(
                        eventType.getColor() + "§l[Event] " + eventType.getDisplayName() +
                        "§r§7 has been triggered by an admin!"));
                    serverPlayer.sendSystemMessage(Component.literal("§7" + eventType.getDescription()));
                }
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cFailed to trigger event: " + e.getMessage()));
            return 0;
        }
    }

    private static String formatModifier(double multiplier) {
        double percent = (multiplier - 1.0) * 100;
        if (percent > 0) {
            return "§a+" + String.format("%.0f%%", percent);
        } else if (percent < 0) {
            return "§c" + String.format("%.0f%%", percent);
        } else {
            return "§7No change";
        }
    }

    private static String formatHappiness(double modifier) {
        if (modifier > 0) {
            return "§a+" + String.format("%.1f", modifier);
        } else if (modifier < 0) {
            return "§c" + String.format("%.1f", modifier);
        } else {
            return "§7No change";
        }
    }
}
