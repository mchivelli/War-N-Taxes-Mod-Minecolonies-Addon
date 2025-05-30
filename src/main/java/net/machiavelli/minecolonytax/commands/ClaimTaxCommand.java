package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.sixik.sdmshoprework.SDMShopR; // Import the SDMShop API

import java.util.List;
import java.util.stream.Collectors;

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
                .map(name -> name.contains(" ") ? "\"" + name + "\"" : name) // Add quotes for names with spaces
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
                        .executes(context -> execute(context, null, -1))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context, String colonyName, int amount) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        List<IColony> colonies = colonyManager.getAllColonies();

        boolean foundColonies = false;

        for (IColony colony : colonies) {
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());

            // Skip if the colony name doesn't match
            if (colonyName != null && !colony.getName().equalsIgnoreCase(colonyName)) {
                continue;
            }

            if (playerRank != null && playerRank.isColonyManager()) {
                foundColonies = true;

                int claimedAmount = TaxManager.claimTax(colony, amount);
                if (claimedAmount > 0) {
                    player.sendSystemMessage(Component.translatable("command.claimtax.success", claimedAmount, colony.getName()));

                    // Update player's funds using SDMShop API if enabled
                    if (TaxConfig.isSDMShopConversionEnabled()) {
                        long currentBalance = SDMShopR.getMoney(player);
                        SDMShopR.setMoney(player, currentBalance + claimedAmount);
                    } else {
                        String itemName = TaxConfig.getCurrencyItemName();
                        String giveCommand = String.format("give %s %s %d", player.getName().getString(), itemName, claimedAmount);
                        source.getServer().getCommands().performPrefixedCommand(source.getServer().createCommandSourceStack(), giveCommand);
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
                }
            }
        }

        if (!foundColonies) {
            if (colonyName != null) {
                source.sendFailure(Component.translatable("command.claimtax.colony_not_found", colonyName));
            } else {
                source.sendFailure(Component.translatable("command.claimtax.no_colonies"));
            }
        }

        return 1;
    }
}
