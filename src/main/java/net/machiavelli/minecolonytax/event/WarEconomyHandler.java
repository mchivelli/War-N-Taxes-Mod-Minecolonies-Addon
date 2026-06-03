package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.compat.FtbTeamsCompat;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class WarEconomyHandler {

    private static final Logger LOGGER = LogManager.getLogger(WarEconomyHandler.class);

    public static long deductTeamBalanceWithReport(UUID teamID, double fraction) {
        long totalDeducted = 0L;
        List<UUID> losingPlayers = new ArrayList<>();
        if (FtbTeamsCompat.isInstalled()) {
            FtbTeamsCompat.TeamHandle losingTeam = teamID == null ? null
                    : FtbTeamsCompat.getTeamById(teamID).orElse(null);
            if (losingTeam != null) {
                losingPlayers.addAll(FtbTeamsCompat.getTeamMembers(losingTeam));
            } else if (teamID != null) {
                losingPlayers.add(teamID);
            }
        } else {
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

    public static long transferTeamBalanceToSinglePlayer(UUID losingTeamID,
            UUID winnerUUID,
            double fraction) {
        long totalTransferred = 0L;
        ServerPlayer winnerPlayer = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(winnerUUID);
        if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
            FtbTeamsCompat.TeamHandle losingTeam = losingTeamID == null ? null
                    : FtbTeamsCompat.getTeamById(losingTeamID).orElse(null);
            if (losingTeam == null) return 0L;
            for (UUID loserUUID : FtbTeamsCompat.getTeamMembers(losingTeam)) {
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
            FtbTeamsCompat.TeamHandle losingTeam = losingTeamID == null ? null
                    : FtbTeamsCompat.getTeamById(losingTeamID).orElse(null);
            if (losingTeam == null) return 0L;
            for (UUID loserUUID : FtbTeamsCompat.getTeamMembers(losingTeam)) {
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
                ItemStack coinStack = new ItemStack(
                        ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())),
                        (int) totalTransferred);
                boolean added = winnerPlayer.getInventory().add(coinStack);
                if (!added) {
                    winnerPlayer.drop(coinStack, false);
                }
                winnerPlayer.sendSystemMessage(
                        Component.literal("You received " + totalTransferred + " coins in war reparations!")
                                .withStyle(ChatFormatting.GREEN));
            }
        }
        return totalTransferred;
    }

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
                        return amount;
                    } else {
                        remaining -= available;
                        stack.setCount(0);
                    }
                }
            }
        }
        return amount - remaining;
    }

    public static long getTeamTotalBalance(UUID teamID) {
        long sum = 0;
        FtbTeamsCompat.TeamHandle team = teamID == null ? null
                : FtbTeamsCompat.getTeamById(teamID).orElse(null);
        if (team == null) return 0L;
        Collection<UUID> members = FtbTeamsCompat.getTeamMembers(team);
        if (TaxConfig.isSDMShopConversionEnabled()) {
            for (UUID member : members) {
                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    sum += SDMShopIntegration.getMoney(player);
                }
            }
        } else {
            for (UUID member : members) {
                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (player != null) {
                    sum += getInventoryCurrencyBalance(player);
                }
            }
        }
        return sum;
    }

    public static boolean payReparationsProportionally(UUID losingTeamID, UUID winnerUUID, long demandedAmount) {
        if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
            FtbTeamsCompat.TeamHandle losingTeam = losingTeamID == null ? null
                    : FtbTeamsCompat.getTeamById(losingTeamID).orElse(null);
            if (losingTeam == null) return false;
            Collection<UUID> losingMembers = FtbTeamsCompat.getTeamMembers(losingTeam);
            long totalTransferred = 0L;
            StringBuilder contributionReport = new StringBuilder("Reparations breakdown:\n");
            ServerPlayer winner = ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(winnerUUID);
            for (UUID member : losingMembers) {
                ServerPlayer loser = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(member);
                if (loser != null) {
                    long balance = SDMShopIntegration.getMoney(loser);
                    long take = (long) (balance * ((double) demandedAmount / getTeamTotalBalance(losingTeamID)));
                    SDMShopIntegration.setMoney(loser, balance - take);
                    totalTransferred += take;
                    contributionReport.append("  ").append(loser.getName().getString()).append(": ").append(take).append(" coins\n");
                    loser.sendSystemMessage(Component.literal("You lost " + take + " coins in reparations!")
                            .withStyle(ChatFormatting.RED));
                }
            }
            contributionReport.append("Total: ").append(totalTransferred).append(" coins paid");
            for (UUID member : losingMembers) {
                ServerPlayer p = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                if (p != null) {
                    p.sendSystemMessage(Component.literal(contributionReport.toString()).withStyle(ChatFormatting.YELLOW));
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
            StringBuilder contributionReport = new StringBuilder("Reparations breakdown:\n");
            if (claimedFromColony > 0) {
                contributionReport.append("  Colony Tax: ").append(claimedFromColony).append(" coins\n");
            }

            if (remaining > 0 && losingColony != null) {
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
                    if (actually > 0) {
                        contributionReport.append("  ").append(p.getName().getString()).append(": ").append(actually).append(" coins\n");
                    }
                    p.sendSystemMessage(Component.literal("You lost " + actually + " coins in reparations!")
                            .withStyle(ChatFormatting.RED));
                    if (toTake <= 0)
                        break;
                }
                
                contributionReport.append("Total: ").append(totalTaken).append(" coins paid");
                for (ServerPlayer p : members) {
                    p.sendSystemMessage(Component.literal(contributionReport.toString()).withStyle(ChatFormatting.YELLOW));
                }
            }

            if (totalTaken > 0) {
                ServerPlayer winner = ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayer(winnerUUID);
                if (winner != null) {
                    ItemStack stack = new ItemStack(
                            ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())),
                            (int) totalTaken);
                    if (!winner.getInventory().add(stack)) {
                        winner.drop(stack, false);
                    }
                    winner.sendSystemMessage(Component.literal("You received " + totalTaken + " coins in reparations!")
                            .withStyle(ChatFormatting.GREEN));
                }
            }

            return true;
        }
    }

    public static double transferBalanceToPlayer(UUID loserUUID, UUID winnerUUID, double percentage) {
        double transferredAmount = 0;

        ServerPlayer loserPlayer = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(loserUUID);
        ServerPlayer winnerPlayer = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(winnerUUID);

        if (loserPlayer != null && winnerPlayer != null) {
            if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
                long loserBalance = SDMShopIntegration.getMoney(loserPlayer);
                long transferAmount = (long) (loserBalance * percentage);

                if (transferAmount > 0) {
                    SDMShopIntegration.setMoney(loserPlayer, loserBalance - transferAmount);
                    long winnerBalance = SDMShopIntegration.getMoney(winnerPlayer);
                    SDMShopIntegration.setMoney(winnerPlayer, winnerBalance + transferAmount);
                    transferredAmount = transferAmount;
                    loserPlayer.sendSystemMessage(
                            Component.literal("You lost " + transferAmount + " as war reparations to " +
                                    winnerPlayer.getName().getString() + "!")
                                    .withStyle(ChatFormatting.RED));
                }
            } else {
                long loserInvBalance = getInventoryCurrencyBalance(loserPlayer);
                long transferAmount = (long) (loserInvBalance * percentage);

                if (transferAmount > 0) {
                    long actuallyDeducted = deductCurrencyFromInventory(loserPlayer, transferAmount);
                    if (actuallyDeducted > 0) {
                        ItemStack coinStack = new ItemStack(
                                ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName())),
                                (int) actuallyDeducted);
                        boolean added = winnerPlayer.getInventory().add(coinStack);
                        if (!added) {
                            winnerPlayer.drop(coinStack, false);
                        }
                        transferredAmount = actuallyDeducted;
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
