package net.machiavelli.minecolonytax.event;

import dev.ftb.mods.ftbteams.api.Team;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.sixik.sdmshoprework.SDMShopR;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.machiavelli.minecolonytax.WarSystem.FTB_TEAMS_INSTALLED;
import static net.machiavelli.minecolonytax.WarSystem.FTB_TEAM_MANAGER;

/**
 * Handles transferring or deducting money from entire teams (attacker or defender).
 * In this revised version, if SDMShop conversion is enabled the code uses the SDMShop API.
 * Otherwise, it uses the item specified in the config—deducting coins from the player's inventory.
 */
public class WarEconomyHandler {

    private static final Logger LOGGER = LogManager.getLogger(WarEconomyHandler.class);

    /**
     * Deducts a percentage from each member of a team and reports the total deducted.
     * If SDMShop conversion is enabled, player funds are modified via SDMShopR.
     * Otherwise, the coin item specified in the config is deducted from the player's inventory.
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
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    long balance = SDMShopR.getMoney(loserPlayer);
                    deducted = (long) (balance * fraction);
                    SDMShopR.setMoney(loserPlayer, balance - deducted);
                } else {
                    long invBalance = getInventoryCurrencyBalance(loserPlayer);
                    deducted = (long) (invBalance * fraction);
                    // Deduct coins from the player's inventory.
                    deducted = deductCurrencyFromInventory(loserPlayer, deducted);
                }
                totalDeducted += deducted;
                loserPlayer.sendSystemMessage(
                        Component.literal("You lost " + deducted + " coins due to war reparations!")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
        return totalDeducted;
    }

    /**
     * Transfers a fraction of each player’s balance on the losing team to a single winner.
     *
     * @param losingTeamID The team (or single player UUID) from which funds are deducted.
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
        if (TaxConfig.isSDMShopConversionEnabled()) {
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(losingTeamID).orElseThrow();
            for (UUID loserUUID : losingTeam.getMembers()) {
                ServerPlayer loserPlayer = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(loserUUID);
                if (loserPlayer != null) {
                    long balance = SDMShopR.getMoney(loserPlayer);
                    long lostAmount = (long) (balance * fraction);
                    SDMShopR.setMoney(loserPlayer, balance - lostAmount);
                    totalTransferred += lostAmount;
                    loserPlayer.sendSystemMessage(
                            Component.literal("You lost " + lostAmount + " coins in reparations to " +
                                            ((winnerPlayer != null) ? winnerPlayer.getName().getString() : "your enemy") + "!")
                                    .withStyle(ChatFormatting.RED)
                    );
                }
            }
            if (winnerPlayer != null && totalTransferred > 0) {
                long winnerBalance = SDMShopR.getMoney(winnerPlayer);
                SDMShopR.setMoney(winnerPlayer, winnerBalance + totalTransferred);
                winnerPlayer.sendSystemMessage(
                        Component.literal("You received " + totalTransferred + " coins in war reparations!")
                                .withStyle(ChatFormatting.GREEN)
                );
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
                                    .withStyle(ChatFormatting.RED)
                    );
                }
            }
            if (winnerPlayer != null && totalTransferred > 0) {
                // Try to add coins to the winner's inventory directly.
                ItemStack coinStack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())), (int) totalTransferred);
                boolean added = winnerPlayer.getInventory().add(coinStack);
                if (!added) {
                    // If inventory is full, fall back to executing a give command.
                    String command = String.format("give %s %s %d", winnerPlayer.getName().getString(), TaxConfig.getCurrencyItemName(), totalTransferred);
                    winnerPlayer.getServer().getCommands().performPrefixedCommand(winnerPlayer.createCommandSourceStack(), command);
                }
                winnerPlayer.sendSystemMessage(
                        Component.literal("You received " + totalTransferred + " coins in war reparations!")
                                .withStyle(ChatFormatting.GREEN)
                );
            }
        }
        return totalTransferred;
    }

    /**
     * Returns the total coin balance in the inventory of a player.
     * Assumes 1 coin = 1 item of the type specified by TaxConfig.getCurrencyItemName().
     */
    private static long getInventoryCurrencyBalance(ServerPlayer player) {
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
     * Deducts up to the specified amount of currency items from the player's inventory.
     * Returns the total amount that was actually deducted.
     */
    private static long deductCurrencyFromInventory(ServerPlayer player, long amount) {
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
                    sum += SDMShopR.getMoney(player);
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
     * If the team's total funds (via SDMShop or inventory) are less than the demanded amount, returns false.
     * Otherwise, transfers the deducted total to the winner.
     */
    public static boolean payReparationsProportionally(UUID losingTeamID, UUID winnerUUID, long demandedAmount) {
        if (TaxConfig.isSDMShopConversionEnabled()) {
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(losingTeamID).orElseThrow();
            long totalTeamBalance = getTeamTotalBalance(losingTeamID);
            if (totalTeamBalance < demandedAmount) {
                return false; // Not enough funds.
            }
            long remainingToDeduct = demandedAmount;
            long totalExtracted = 0;
            for (UUID member : losingTeam.getMembers()) {
                ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (sp != null) {
                    long balance = SDMShopR.getMoney(sp);
                    double frac = (double) balance / totalTeamBalance;
                    long toRemove = Math.round(demandedAmount * frac);
                    if (toRemove > remainingToDeduct) {
                        toRemove = remainingToDeduct;
                    }
                    SDMShopR.setMoney(sp, balance - toRemove);
                    totalExtracted += toRemove;
                    remainingToDeduct -= toRemove;
                    sp.sendSystemMessage(
                            Component.literal("You lost " + toRemove + " coins due to war reparations!")
                                    .withStyle(ChatFormatting.RED)
                    );
                    if (remainingToDeduct <= 0) {
                        break;
                    }
                }
            }
            if (totalExtracted > 0) {
                ServerPlayer winnerPlayer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(winnerUUID);
                if (winnerPlayer != null) {
                    long winnerBalance = SDMShopR.getMoney(winnerPlayer);
                    SDMShopR.setMoney(winnerPlayer, winnerBalance + totalExtracted);
                    winnerPlayer.sendSystemMessage(
                            Component.literal("You received " + totalExtracted + " coins in reparations!")
                                    .withStyle(ChatFormatting.GREEN)
                    );
                }
            }
            return true;
        } else {
            Team losingTeam = FTB_TEAM_MANAGER.getTeamByID(losingTeamID).orElseThrow();
            long totalTeamBalance = getTeamTotalBalance(losingTeamID);
            if (totalTeamBalance < demandedAmount) {
                return false; // Not enough funds.
            }
            long remainingToDeduct = demandedAmount;
            long totalExtracted = 0;
            List<ServerPlayer> membersOnline = new ArrayList<>();
            for (UUID member : losingTeam.getMembers()) {
                ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (sp != null) {
                    membersOnline.add(sp);
                }
            }
            for (ServerPlayer sp : membersOnline) {
                long balance = getInventoryCurrencyBalance(sp);
                double frac = (double) balance / totalTeamBalance;
                long toRemove = Math.round(demandedAmount * frac);
                if (toRemove > remainingToDeduct) {
                    toRemove = remainingToDeduct;
                }
                long deducted = deductCurrencyFromInventory(sp, toRemove);
                totalExtracted += deducted;
                remainingToDeduct -= deducted;
                sp.sendSystemMessage(
                        Component.literal("You lost " + deducted + " coins due to war reparations!")
                                .withStyle(ChatFormatting.RED)
                );
                if (remainingToDeduct <= 0) {
                    break;
                }
            }
            if (totalExtracted > 0) {
                ServerPlayer winnerPlayer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(winnerUUID);
                if (winnerPlayer != null) {
                    ItemStack coinStack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())), (int) totalExtracted);
                    boolean added = winnerPlayer.getInventory().add(coinStack);
                    if (!added) {
                        String command = String.format("give %s %s %d", winnerPlayer.getName().getString(), TaxConfig.getCurrencyItemName(), totalExtracted);
                        winnerPlayer.getServer().getCommands().performPrefixedCommand(winnerPlayer.createCommandSourceStack(), command);
                    }
                    winnerPlayer.sendSystemMessage(
                            Component.literal("You received " + totalExtracted + " coins in reparations!")
                                    .withStyle(ChatFormatting.GREEN)
                    );
                }
            }
            return true;
        }
    }
}
