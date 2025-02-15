package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClaimTaxCommand {

    private static final Logger LOGGER = LogManager.getLogger(ClaimTaxCommand.class);

    // Suggestion provider for colony names (with quotes if needed)
    private static final SuggestionProvider<CommandSourceStack> COLONY_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        List<String> colonyNames = colonyManager.getAllColonies().stream()
                .filter(colony -> colony.getPermissions().getRank(player.getUUID()).isColonyManager())
                .map(IColony::getName)
                .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                .collect(Collectors.toList());

        return SharedSuggestionProvider.suggest(colonyNames, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("claimtax")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.argument("colony", StringArgumentType.string())
                                .suggests(COLONY_SUGGESTIONS)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            String colonyName = StringArgumentType.getString(context, "colony").replace("\"", "");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            return execute(context, colonyName, amount);
                                        })
                                )
                                .executes(context -> {
                                    String colonyName = StringArgumentType.getString(context, "colony").replace("\"", "");
                                    return execute(context, colonyName, -1);
                                })
                        )
                        .executes(context -> execute(context))
        );
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ClaimTaxCommand.register(event.getDispatcher());
    }

    // Overloaded execute methods
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return execute(context, null, -1);
    }

    private static int execute(CommandContext<CommandSourceStack> context, @Nullable String colonyName, int amount) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        Level world = source.getLevel();
        MinecraftServer server = source.getServer();

        List<IColony> colonies;

        if (colonyName != null) {
            // Filter colonies in the player's world by name and permissions
            colonies = colonyManager.getColonies(world).stream()
                    .filter(c -> c.getName().equalsIgnoreCase(colonyName.trim()))
                    .filter(c -> c.getPermissions().getRank(player.getUUID()).isColonyManager())
                    .collect(Collectors.toList());

            if (colonies.isEmpty()) {
                source.sendFailure(Component.translatable("command.claimtax.invalid_colony", colonyName));
                return 0;
            }
        } else {
            // Get all colonies in the player's world where they are a manager
            colonies = colonyManager.getColonies(world).stream()
                    .filter(c -> c.getPermissions().getRank(player.getUUID()).isColonyManager())
                    .collect(Collectors.toList());
        }

        boolean foundColonies = false;
        for (IColony colony : colonies) {
            foundColonies = true;
            int claimedAmount = (amount > 0) ? TaxManager.claimTax(colony, amount) : TaxManager.claimTax(colony);

            if (claimedAmount > 0) {
                player.sendSystemMessage(Component.translatable("command.claimtax.success", claimedAmount, colony.getName()));
                // Execute SDMShop command
                String commandString = String.format("sdmshop add %s %d", player.getName().getString(), claimedAmount);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), commandString);
                LOGGER.info("Tax claimed: {} for colony {}", claimedAmount, colony.getName());
            } else {
                player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
            }
        }

        if (!foundColonies) {
            source.sendFailure(Component.translatable(colonyName == null
                    ? "command.claimtax.no_colonies"
                    : "command.claimtax.no_permission"));
        }

        return 1;
    }
}