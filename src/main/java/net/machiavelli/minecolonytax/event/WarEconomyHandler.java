package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import dev.ftb.mods.ftbteams.api.Team;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.machiavelli.minecolonytax.WarSystem.FTB_TEAMS_INSTALLED;
import static net.machiavelli.minecolonytax.WarSystem.FTB_TEAM_MANAGER;

/**
 * Handles transferring or deducting money from entire teams (attacker or
 * defender).
 * In this revised version, if SDMShop conversion is enabled the code uses the
 * SDMShop API.
 * Otherwise, it uses the item specified in the config—deducting coins from the
 * player's inventory.
 */
public class WarEconomyHandler {

    private static final Logger LOGGER = LogManager.getLogger(WarEconomyHandler.class);

    /**
     * Deducts a percentage from each member of a team and reports the total
     * deducted.
     * If SDMShop conversion is enabled, player funds are modified via SDMShopR.
     * Otherwise, the coin item specified in the config is deducted from the
     * player's inventory.
     *
     * @param teamID   The team (or player UUID if no team exists).
     * @param fraction The fraction (e.g. 0.25 for 25% deduction).
     * @return The total amount deducted.
     */
    public static long deductTeamBalanceWithReport(UUID teamID, double fraction) {
        long totalDeducted = 0L;
        List<UUID> losingPlayers = new ArrayList<>();
        if (FTB_TEAMS_INSTALLED) {
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(teamID).orElse(null);
            if (losingTeam != null) {
                losingPlayers.addAll(losingTeam.getMembers());
            } else {
                losingPlayers.add(teamID);
            }
        } else {
            // Fallback: search active wars for matching team IDs.
            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                if (teamID != null && teamID.equals(war.getAttackerTeamID())) {
                    losingPlayers.addAll(war.getAttackerLives().keySet());
                } else if (teamID != null && teamID.equals(war.getDefenderTeamID())) {
                    losingPlayers.addAll(war.getDefenderLives().keySet());
                }
            }
        }
        for (UUID loserUUID : losingPlayers) {
            ServerPlayer loserPlayer = ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(loserUUID);
            if (loserPlayer != null) {
                long deducted;
                if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
                    long balance = SDMShopIntegration.getMoney(loserPlayer);
                    deducted = (long) (balance * fraction);
                    SDMShopIntegration.setMoney(loserPlayer, balance - deducted);
                } else {
                    long invBalance = getInventoryCurrencyBalance(loserPlayer);
                    deducted = (long) (invBalance * fraction);
                    // Deduct coins from the player's inventory.
                    deducted = deductCurrencyFromInventory(loserPlayer, deducted);
                }
                totalDeducted += deducted;
                loserPlayer.sendSystemMessage(
                        Component.literal("You lost " + deducted + " coins due to war reparations!")
                                .withStyle(ChatFormatting.RED));
            }
        }
        return totalDeducted;
    }

    /**
     * Transfers a fraction of each player’s balance on the losing team to a single
     * winner.
     *
     * @param losingTeamID The team (or single player UUID) from which funds are
     *                     deducted.
     * @param winnerUUID   The player receiving the funds.
     * @param fraction     The fraction to be transferred.
     * @return The total transferred amount.
     */
    public static long transferTeamBalanceToSinglePlayer(UUID losingTeamID,
            UUID winnerUUID,
            double fraction) {
        long totalTransferred = 0L;
        ServerPlayer winnerPlayer = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(winnerUUID);
        if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(losingTeamID).orElseThrow();
            for (UUID loserUUID : losingTeam.getMembers()) {
                ServerPlayer loserPlayer = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(loserUUID);
                if (loserPlayer != null) {
                    long balance = SDMShopIntegration.getMoney(loserPlayer);
                    long lostAmount = (long) (balance * fraction);
                    SDMShopIntegration.setMoney(loserPlayer, balance - lostAmount);
                    totalTransferred += lostAmount;
                    loserPlayer.sendSystemMessage(
                            Component.literal("You lost " + lostAmount + " coins in reparations to " +
                                    ((winnerPlayer != null) ? winnerPlayer.getName().getString() : "your enemy") + "!")
                                    .withStyle(ChatFormatting.RED));
                }
            }
            if (winnerPlayer != null && totalTransferred > 0) {
                long winnerBalance = SDMShopIntegration.getMoney(winnerPlayer);
                SDMShopIntegration.setMoney(winnerPlayer, winnerBalance + totalTransferred);
                winnerPlayer.sendSystemMessage(
                        Component.literal("You received " + totalTransferred + " coins in war reparations!")
                                .withStyle(ChatFormatting.GREEN));
            }
        } else {
            // Fallback: Use inventory-based deduction.
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(losingTeamID).orElseThrow();
            for (UUID loserUUID : losingTeam.getMembers()) {
                ServerPlayer loserPlayer = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(loserUUID);
                if (loserPlayer != null) {
                    long invBalance = getInventoryCurrencyBalance(loserPlayer);
                    long lostAmount = (long) (invBalance * fraction);
                    long actuallyDeducted = deductCurrencyFromInventory(loserPlayer, lostAmount);
                    totalTransferred += actuallyDeducted;
                    loserPlayer.sendSystemMessage(
                            Component.literal("You lost " + actuallyDeducted + " coins in reparations to " +
                                    ((winnerPlayer != null) ? winnerPlayer.getName().getString() : "your enemy") + "!")
                                    .withStyle(ChatFormatting.RED));
                }
            }
            if (winnerPlayer != null && totalTransferred > 0) {
                // Try to add coins to the winner's inventory directly.
                ItemStack coinStack = new ItemStack(
                        ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())),
                        (int) totalTransferred);
                boolean added = winnerPlayer.getInventory().add(coinStack);
                if (!added) {
                    // If inventory is full, drop items near winner
                    winnerPlayer.drop(coinStack, false);
                    LOGGER.debug("Winner's inventory was full, dropped {} items near them", totalTransferred);
                }
                winnerPlayer.sendSystemMessage(
                        Component.literal("You received " + totalTransferred + " coins in war reparations!")
                                .withStyle(ChatFormatting.GREEN));
            }
        }
        return totalTransferred;
    }

    /**
     * Returns the total coin balance in the inventory of a player.
     * Assumes 1 coin = 1 item of the type specified by
     * TaxConfig.getCurrencyItemName().
     */
    public static long getInventoryCurrencyBalance(ServerPlayer player) {
        long total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (registryName != null && registryName.toString().equals(TaxConfig.getCurrencyItemName())) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Deducts up to the specified amount of currency items from the player's
     * inventory.
     * Returns the total amount that was actually deducted.
     */
    public static long deductCurrencyFromInventory(ServerPlayer player, long amount) {
        long remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (registryName != null && registryName.toString().equals(TaxConfig.getCurrencyItemName())) {
                    int available = stack.getCount();
                    if (available >= remaining) {
                        stack.shrink((int) remaining);
                        return amount; // Full deduction achieved.
                    } else {
                        remaining -= available;
                        stack.setCount(0);
                    }
                }
            }
        }
        return amount - remaining; // Total deducted.
    }

    /**
     * Sums up the total currency balance for all members of the team.
     */
    public static long getTeamTotalBalance(UUID teamID) {
        long sum = 0;
        if (TaxConfig.isSDMShopConversionEnabled()) {
            Team team = FTB_TEAM_MANAGER.getTeamByID(teamID).orElseThrow();
            for (UUID member : team.getMembers()) {
                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    sum += SDMShopIntegration.getMoney(player);
                }
            }
        } else {
            Team team = FTB_TEAM_MANAGER.getTeamByID(teamID).orElseThrow();
            for (UUID member : team.getMembers()) {
                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    sum += getInventoryCurrencyBalance(player);
                }
            }
        }
        return sum;
    }

    /**
     * Deducts a demanded amount proportionally from each member of the losing team.
     * If the team's total funds (via SDMShop or inventory) are less than the
     * demanded amount, returns false.
     * Otherwise, transfers the deducted total to the winner.
     */
    public static boolean payReparationsProportionally(UUID losingTeamID, UUID winnerUUID, long demandedAmount) {
        if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
            // --- SDMShop-only path (unchanged) ---
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(losingTeamID).orElseThrow();
            long totalTransferred = 0L;
            ServerPlayer winner = ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(winnerUUID);
            for (UUID member : losingTeam.getMembers()) {
                ServerPlayer loser = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(member);
                if (loser != null) {
                    long balance = SDMShopIntegration.getMoney(loser);
                    long take = (long) (balance * ((double) demandedAmount / getTeamTotalBalance(losingTeamID)));
                    SDMShopIntegration.setMoney(loser, balance - take);
                    totalTransferred += take;
                    loser.sendSystemMessage(Component.literal("You lost " + take + " coins in reparations!")
                            .withStyle(ChatFormatting.RED));
                }
            }
            if (winner != null && totalTransferred > 0) {
                long wb = SDMShopIntegration.getMoney(winner);
                SDMShopIntegration.setMoney(winner, wb + totalTransferred);
                winner.sendSystemMessage(
                        Component.literal("You received " + totalTransferred + " coins in reparations!")
                                .withStyle(ChatFormatting.GREEN));
            }
            return true;
        } else {
            // --- Colony tax first ---
            IColony losingColony = null;
            for (WarData w : WarSystem.ACTIVE_WARS.values()) {
                if (losingTeamID.equals(w.getAttackerTeamID()))
                    losingColony = w.getAttackerColony();
                else if (losingTeamID.equals(w.getDefenderTeamID()))
                    losingColony = w.getColony();
            }

            long claimedFromColony = 0;
            if (losingColony != null) {
                claimedFromColony = TaxManager.claimTax(losingColony, (int) demandedAmount);
            }

            long remaining = demandedAmount - claimedFromColony;
            long totalTaken = claimedFromColony;

            if (remaining > 0 && losingColony != null) {
                // collect all alive participants on that side
                WarData war = WarSystem.ACTIVE_WARS.get(losingColony.getID());
                List<ServerPlayer> members = new ArrayList<>();
                if (losingTeamID.equals(war.getAttackerTeamID())) {
                    war.getAttackerLives().keySet().forEach(uuid -> {
                        ServerPlayer p = ServerLifecycleHooks.getCurrentServer()
                                .getPlayerList().getPlayer(uuid);
                        if (p != null)
                            members.add(p);
                    });
                } else {
                    war.getDefenderLives().keySet().forEach(uuid -> {
                        ServerPlayer p = ServerLifecycleHooks.getCurrentServer()
                                .getPlayerList().getPlayer(uuid);
                        if (p != null)
                            members.add(p);
                    });
                }

                long sumInv = members.stream()
                        .mapToLong(WarEconomyHandler::getInventoryCurrencyBalance)
                        .sum();

                long toTake = remaining;
                for (ServerPlayer p : members) {
                    long bal = getInventoryCurrencyBalance(p);
                    long take = Math.round((double) bal / sumInv * remaining);
                    if (take > toTake)
                        take = toTake;
                    long actually = deductCurrencyFromInventory(p, take);
                    totalTaken += actually;
                    toTake -= actually;
                    p.sendSystemMessage(Component.literal("You lost " + actually + " coins in reparations!")
                            .withStyle(ChatFormatting.RED));
                    if (toTake <= 0)
                        break;
                }
            }

            // hand over to winner
            if (totalTaken > 0) {
                ServerPlayer winner = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(winnerUUID);
                if (winner != null) {
                    ItemStack stack = new ItemStack(
                            ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())),
                            (int) totalTaken);
                    if (!winner.getInventory().add(stack)) {
                        // If inventory is full, drop items near winner
                        winner.drop(stack, false);
                        LOGGER.debug("Winner's inventory was full, dropped {} items near them", totalTaken);
                    }
                    winner.sendSystemMessage(Component.literal("You received " + totalTaken + " coins in reparations!")
                            .withStyle(ChatFormatting.GREEN));
                }
            }

            return true;
        }
    }

    /**
     * Transfers a percentage of a losing player's balance directly to a winning
     * player.
     * 
     * @param loserUUID  UUID of the player to deduct from
     * @param winnerUUID UUID of the player to receive funds
     * @param percentage Percentage of loser's balance to transfer
     * @return The amount actually transferred
     */
    public static double transferBalanceToPlayer(UUID loserUUID, UUID winnerUUID, double percentage) {
        double transferredAmount = 0;

        ServerPlayer loserPlayer = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(loserUUID);
        ServerPlayer winnerPlayer = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(winnerUUID);

        if (loserPlayer != null && winnerPlayer != null) {
            if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
                // Use SDMShop economy
                long loserBalance = SDMShopIntegration.getMoney(loserPlayer);
                long transferAmount = (long) (loserBalance * percentage);

                if (transferAmount > 0) {
                    // Remove from loser
                    SDMShopIntegration.setMoney(loserPlayer, loserBalance - transferAmount);

                    // Add to winner
                    long winnerBalance = SDMShopIntegration.getMoney(winnerPlayer);
                    SDMShopIntegration.setMoney(winnerPlayer, winnerBalance + transferAmount);

                    transferredAmount = transferAmount;

                    // Notify loser
                    loserPlayer.sendSystemMessage(
                            Component.literal("You lost " + transferAmount + " as war reparations to " +
                                    winnerPlayer.getName().getString() + "!")
                                    .withStyle(ChatFormatting.RED));
                }
            } else {
                // Use inventory-based economy
                long loserInvBalance = getInventoryCurrencyBalance(loserPlayer);
                long transferAmount = (long) (loserInvBalance * percentage);

                if (transferAmount > 0) {
                    // Deduct from loser's inventory
                    long actuallyDeducted = deductCurrencyFromInventory(loserPlayer, transferAmount);

                    if (actuallyDeducted > 0) {
                        // Add to winner's inventory
                        ItemStack coinStack = new ItemStack(
                                ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())),
                                (int) actuallyDeducted);

                        boolean added = winnerPlayer.getInventory().add(coinStack);
                        if (!added) {
                            // If inventory is full, drop items near winner
                            winnerPlayer.drop(coinStack, false);
                            LOGGER.debug("Winner's inventory was full, dropped {} items near them", actuallyDeducted);
                        }

                        transferredAmount = actuallyDeducted;

                        // Notify loser
                        loserPlayer.sendSystemMessage(
                                Component.literal("You lost " + actuallyDeducted + " as war reparations to " +
                                        winnerPlayer.getName().getString() + "!")
                                        .withStyle(ChatFormatting.RED));
                    }
                }
            }
        }

        return transferredAmount;
    }
}
