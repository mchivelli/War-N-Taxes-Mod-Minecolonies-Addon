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
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.machiavelli.minecolonytax.integration.SDMShopCompat;

import java.util.List;
import java.util.stream.Collectors;

public class TaxDebtCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("taxdebt")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.literal("pay")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("colony", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    ServerPlayer player;
                                                    try {
                                                        player = context.getSource().getPlayerOrException();
                                                    } catch (Exception e) {
                                                        return builder.buildFuture();
                                                    }
                                                    IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                                                    List<String> colonyNames = colonyManager.getAllColonies().stream()
                                                            .filter(colony -> colony.getPermissions().getRank(player.getUUID()).isColonyManager())
                                                            .map(IColony::getName)
                                                            .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                                                            .collect(Collectors.toList());
                                                    return SharedSuggestionProvider.suggest(colonyNames, builder);
                                                })
                                                .executes(context -> {
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                                    String colonyName = StringArgumentType.getString(context, "colony").replace("\"", "");
                                                    return execute(context, colonyName, amount);
                                                })
                                        )
                                )
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context, String colonyName, int amount) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        boolean foundColony = false;

        for (IColony colony : colonyManager.getAllColonies()) {
            // Only process colonies where the player is a manager
            Rank playerRank = colony.getPermissions().getRank(player.getUUID());
            if (playerRank == null || !playerRank.isColonyManager()) {
                continue;
            }
            // If a colony name is provided, check for a match (ignoring case)
            if (colonyName != null && !colony.getName().equalsIgnoreCase(colonyName)) {
                continue;
            }
            foundColony = true;
            int currentTax = TaxManager.getStoredTaxForColony(colony);
            // Continue even if there's no debt - players can add to their colony's balance

            // Attempt to deduct the specified amount from the player's funds.
            boolean deducted = deductCurrency(player, amount);
            if (!deducted) {
                source.sendFailure(Component.translatable("command.taxdebt.insufficient_funds", amount));
                continue;
            }

            // Allow full payment amount, regardless of current balance
            int paid = TaxManager.payTaxDebt(colony, amount);
            source.sendSuccess(() -> Component.translatable("command.taxdebt.success", paid, colony.getName(), TaxManager.getStoredTaxForColony(colony)), false);
        }

        if (!foundColony) {
            source.sendFailure(Component.translatable("command.taxdebt.colony_not_found", colonyName));
        }
        return 1;
    }

    /**
     * Deducts currency from the player using SDMEconomy if enabled, or from the player's inventory otherwise.
     */
    private static boolean deductCurrency(ServerPlayer player, int amount) {
        if (TaxConfig.isSDMShopConversionEnabled()) {
            long balance = SDMShopCompat.getMoney(player);
            if (balance < amount) {
                return false;
            }
            // Only report success when the debit actually happened — a false return here
            // would otherwise still pay down the colony debt with coins never taken.
            return SDMShopCompat.setMoney(player, balance - amount);
        } else {
            return deductCurrencyFromInventory(player, amount);
        }
    }

    /**
     * Deducts the required currency from the player's inventory.
     * Uses ForgeRegistries to obtain the registry name for modded items.
     */
    private static boolean deductCurrencyFromInventory(ServerPlayer player, int amount) {
        // Sufficiency pass BEFORE mutating any stack. The loop below used to shrink the
        // player's stacks to zero one by one and only then discover the total was short:
        // an underpaying player lost every coin they had and the debt stayed unpaid.
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var probe = player.getInventory().getItem(i);
            if (!probe.isEmpty()) {
                var probeName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(probe.getItem());
                if (probeName != null && probeName.toString().equals(TaxConfig.getCurrencyItemName())) {
                    total += probe.getCount();
                }
            }
        }
        if (total < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (registryName != null && registryName.toString().equals(TaxConfig.getCurrencyItemName())) {
                    int available = stack.getCount();
                    if (available >= remaining) {
                        stack.shrink(remaining);
                        return true;
                    } else {
                        remaining -= available;
                        stack.setCount(0);
                    }
                }
            }
        }
        return remaining <= 0;
    }
}
