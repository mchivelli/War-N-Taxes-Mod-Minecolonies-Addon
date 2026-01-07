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
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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
                                            String colonyName = StringArgumentType.getString(context, "colony")
                                                    .replace("\"", "");
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            return execute(context, colonyName, amount);
                                        }))
                                .executes(context -> {
                                    String colonyName = StringArgumentType.getString(context, "colony").replace("\"",
                                            "");
                                    return execute(context, colonyName, -1);
                                }))
                        .executes(context -> execute(context, null, -1)));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String colonyName, int amount)
            throws CommandSyntaxException {
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

                // Check tax claiming permissions with owner override
                boolean isOwner = playerRank.equals(colony.getPermissions().getRankOwner());
                boolean isOfficer = playerRank.equals(colony.getPermissions().getRankOfficer()) || isOwner;

                if (!TaxPermissionManager.canPlayerClaimTax(colony.getID(), player.getUUID(), isOwner, isOfficer)) {
                    player.sendSystemMessage(
                            Component.literal("You do not have permission to claim taxes for colony " + colony.getName()
                                    + ". Contact a colony owner.").withStyle(net.minecraft.ChatFormatting.RED));
                    continue;
                }

                // Check if colony is currently being raided before attempting to claim
                if (RaidManager.getActiveRaidForColony(colony.getID()) != null) {
                    player.sendSystemMessage(Component
                            .literal("Cannot claim tax for colony " + colony.getName()
                                    + " - colony is currently being raided!")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    continue;
                }

                // Check if colony is currently involved in a war (defender or attacker) before
                // attempting to claim
                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID()); // defender-side lookup
                if (war == null) {
                    for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
                        if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colony.getID()) {
                            war = wd; // attacker-side involvement
                            break;
                        }
                    }
                }
                if (war != null) {
                    String phase = war.isJoinPhaseActive() ? "join phase" : "active war";
                    player.sendSystemMessage(Component
                            .literal("Cannot claim tax for colony " + colony.getName()
                                    + " - colony is currently at war (" + phase + ")!")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    continue;
                }

                // Claim tax revenue (now includes raid rewards in main balance)
                int totalClaimed = TaxManager.claimTax(colony, amount);

                if (totalClaimed > 0) {
                    // Grant advancement
                    try {
                        net.minecraft.advancements.Advancement adv = player.getServer().getAdvancements()
                                .getAdvancement(
                                        new net.minecraft.resources.ResourceLocation("minecolonytax:codex/claim_tax"));
                        if (adv != null) {
                            player.getAdvancements().award(adv, "check");
                        }
                    } catch (Exception e) {
                    }

                    player.sendSystemMessage(Component.translatable("command.claimtax.success",
                            colony.getName(), totalClaimed));

                    // Update player's funds using SDMShop API if enabled
                    if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
                        long currentBalance = SDMShopIntegration.getMoney(player);
                        SDMShopIntegration.setMoney(player, currentBalance + totalClaimed);
                    } else {
                        // Use direct inventory manipulation instead of give command for modded items
                        Item item = ForgeRegistries.ITEMS
                                .getValue(new ResourceLocation(TaxConfig.getCurrencyItemName()));
                        if (item != null) {
                            ItemStack itemStack = new ItemStack(item, totalClaimed);
                            boolean added = player.getInventory().add(itemStack);
                            if (!added) {
                                // If inventory is full, drop items near player
                                player.drop(itemStack, false);
                                player.sendSystemMessage(Component.translatable("taxmanager.inventory_full",
                                        totalClaimed, TaxConfig.getCurrencyItemName()));
                            } else {
                                player.sendSystemMessage(Component.translatable("taxmanager.currency_received",
                                        totalClaimed, TaxConfig.getCurrencyItemName()));
                            }
                        } else {
                            // Fallback to give command if item not found in registry
                            String itemName = TaxConfig.getCurrencyItemName();
                            String giveCommand = String.format("give %s %s %d", player.getName().getString(), itemName,
                                    totalClaimed);
                            source.getServer().getCommands()
                                    .performPrefixedCommand(source.getServer().createCommandSourceStack(), giveCommand);
                        }
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
