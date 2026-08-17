package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class PvPKillEconomyHandler {

    private static final Logger LOGGER = LogManager.getLogger(PvPKillEconomyHandler.class);

    // Per-(killer-victim) reward cooldown. Key format: "killerUuid:victimUuid".
    // No TaxConfig key exists for this today, so hardcoded to 300 seconds (5 minutes).
    private static final long KILL_COOLDOWN_SECONDS = 300L;
    private static final java.util.Map<String, Long> LAST_REWARD_AT_MS = new ConcurrentHashMap<>();

    // Per-killer 24-hour cap. Beyond MAX_REWARDS_PER_DAY kills, reward = 0.
    private static final int MAX_REWARDS_PER_DAY = 20;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final java.util.Map<UUID, KillCount> KILLER_DAY_COUNT = new ConcurrentHashMap<>();

    private static final class KillCount {
        int count;
        long resetAtMs;
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!TaxConfig.ENABLE_PVP_KILL_ECONOMY.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        DamageSource damageSource = event.getSource();
        if (damageSource == null || !(damageSource.getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (victim.getUUID().equals(killer.getUUID())) {
            return;
        }

        // Fix #5a: per-(killer,victim) cooldown to prevent alt-kill farming.
        final String pairKey = killer.getUUID() + ":" + victim.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_REWARD_AT_MS.get(pairKey);
        if (last != null && (now - last) < (KILL_COOLDOWN_SECONDS * 1000L)) {
            long remainSec = ((KILL_COOLDOWN_SECONDS * 1000L) - (now - last)) / 1000L;
            if (TaxConfig.isDebugLogging()) {
                LOGGER.debug("PvP kill reward suppressed (cooldown {}s remaining) for {} -> {}",
                        remainSec, killer.getName().getString(), victim.getName().getString());
            }
            return;
        }

        // Fix #5b: per-killer 24h cap to prevent grind-style farming across many victims.
        KillCount kc = KILLER_DAY_COUNT.computeIfAbsent(killer.getUUID(), k -> {
            KillCount fresh = new KillCount();
            fresh.count = 0;
            fresh.resetAtMs = System.currentTimeMillis() + DAY_MS;
            return fresh;
        });
        if (now >= kc.resetAtMs) {
            kc.count = 0;
            kc.resetAtMs = now + DAY_MS;
        }
        if (kc.count >= MAX_REWARDS_PER_DAY) {
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("PvP kill reward suppressed (24h cap of {} hit) for killer {}",
                        MAX_REWARDS_PER_DAY, killer.getName().getString());
            }
            return;
        }
        // Record the attempt up-front so an exception in the reward path doesn't
        // hand the killer infinite "free attempts". Increment cooldown/count now.
        LAST_REWARD_AT_MS.put(pairKey, now);
        kc.count++;

        boolean isRaidRelated = checkIfRaidRelated(victim, killer);
        double rewardPercentage = TaxConfig.PVP_KILL_REWARD_PERCENTAGE.get();

        if (isRaidRelated) {
            double raidPenaltyMultiplier = TaxConfig.RAID_PENALTY_PERCENTAGE.get();
            if (raidPenaltyMultiplier > 0) {
                rewardPercentage = Math.max(rewardPercentage, raidPenaltyMultiplier);
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Applying enhanced raid death penalty: {}% (base: {}%, raid penalty: {}%)",
                            rewardPercentage * 100, TaxConfig.PVP_KILL_REWARD_PERCENTAGE.get() * 100,
                            raidPenaltyMultiplier * 100);
                }
            }
        }

        if (rewardPercentage <= 0) {
            return;
        }
        if (TaxConfig.isSDMShopConversionEnabled()) {
            handleSDMShopTransfer(victim, killer, rewardPercentage, isRaidRelated);
        } else {
            handleItemTransfer(victim, killer, rewardPercentage, isRaidRelated);
        }
    }

    private static boolean checkIfRaidRelated(ServerPlayer victim, ServerPlayer killer) {
        if (RaidManager.getActiveRaidForPlayer(victim.getUUID()) != null) {
            return true;
        }
        for (int colonyId : ColonyClaimingRaidManager.getActiveClaimingRaidIds()) {
            if (ColonyClaimingRaidManager.isPlayerInClaimingRaid(victim.getUUID(), colonyId)) {
                return true;
            }
        }
        if (RaidManager.getActiveRaidForPlayer(killer.getUUID()) != null) {
            return true;
        }
        return false;
    }

    private static void handleSDMShopTransfer(ServerPlayer victim, ServerPlayer killer, double percentage,
            boolean isRaidRelated) {
        if (!SDMShopIntegration.isAvailable()) {
            LOGGER.warn("SDMShop integration enabled but SDMShop mod not available for PvP kill reward");
            return;
        }

        try {
            long victimBalance = SDMShopIntegration.getMoney(victim);
            if (victimBalance <= 0) {
                killer.sendSystemMessage(
                        Component.literal("PvP Kill: " + victim.getName().getString() + " had no money to transfer.")
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }

            long transferAmount = Math.max(1, (long) (victimBalance * percentage));
            long killerBalance = SDMShopIntegration.getMoney(killer);

            // Debit and credit as a checked pair: if the killer credit fails AFTER the
            // victim debit succeeded, roll the debit back — otherwise the coins are
            // destroyed and neither player has them.
            if (!SDMShopIntegration.setMoney(victim, victimBalance - transferAmount)) {
                LOGGER.error("Failed to debit ${} from {} via SDMShop API - transfer aborted",
                        transferAmount, victim.getName().getString());
                return;
            }
            if (!SDMShopIntegration.setMoney(killer, killerBalance + transferAmount)) {
                SDMShopIntegration.setMoney(victim, victimBalance); // roll back the debit
                LOGGER.error("Failed to credit ${} to {} via SDMShop API - victim refunded",
                        transferAmount, killer.getName().getString());
                return;
            }
            String deathType = isRaidRelated ? "Raid Death" : "PvP Death";
            String killType = isRaidRelated ? "Raid Defense" : "PvP Kill";

            victim.sendSystemMessage(Component
                    .literal(deathType + ": Lost $" + transferAmount + " to " + killer.getName().getString())
                    .withStyle(net.minecraft.ChatFormatting.RED));
            killer.sendSystemMessage(Component
                    .literal(killType + ": Earned $" + transferAmount + " from " + victim.getName().getString())
                    .withStyle(net.minecraft.ChatFormatting.GREEN));

            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("{} economy: {} transferred ${} from {} to {}",
                        isRaidRelated ? "Raid" : "PvP", percentage * 100 + "%", transferAmount,
                        victim.getName().getString(), killer.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error in SDMShop PvP kill transfer: {}", e.getMessage());
        }
    }

    private static void handleItemTransfer(ServerPlayer victim, ServerPlayer killer, double percentage,
            boolean isRaidRelated) {
        try {
            String currencyItemName = TaxConfig.getCurrencyItemName();
            Item currencyItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(currencyItemName));

            if (currencyItem == null) {
                LOGGER.warn("Currency item '{}' not found for PvP kill reward", currencyItemName);
                return;
            }

            int victimCurrencyCount = 0;
            for (ItemStack stack : victim.getInventory().items) {
                if (stack.getItem() == currencyItem) {
                    victimCurrencyCount += stack.getCount();
                }
            }

            if (victimCurrencyCount <= 0) {
                String killType = isRaidRelated ? "Raid Defense" : "PvP Kill";
                killer.sendSystemMessage(Component.literal(killType + ": " + victim.getName().getString() + " had no "
                        + currencyItemName + " to transfer.").withStyle(net.minecraft.ChatFormatting.GRAY));
                return;
            }

            int transferAmount = Math.max(1, (int) (victimCurrencyCount * percentage));
            int remainingToRemove = transferAmount;
            for (int i = 0; i < victim.getInventory().items.size() && remainingToRemove > 0; i++) {
                ItemStack stack = victim.getInventory().items.get(i);
                if (stack.getItem() == currencyItem) {
                    int toRemove = Math.min(remainingToRemove, stack.getCount());
                    stack.shrink(toRemove);
                    remainingToRemove -= toRemove;
                }
            }

            ItemStack rewardStack = new ItemStack(currencyItem, transferAmount);
            boolean added = killer.getInventory().add(rewardStack);
            if (!added) {
                killer.drop(rewardStack, false);
            }

            String deathType = isRaidRelated ? "Raid Death" : "PvP Death";
            String killType = isRaidRelated ? "Raid Defense" : "PvP Kill";

            victim.sendSystemMessage(Component.literal(deathType + ": Lost " + transferAmount + " " + currencyItemName
                    + " to " + killer.getName().getString()).withStyle(net.minecraft.ChatFormatting.RED));
            killer.sendSystemMessage(Component.literal(killType + ": Earned " + transferAmount + " " + currencyItemName
                    + " from " + victim.getName().getString()).withStyle(net.minecraft.ChatFormatting.GREEN));

            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("{} economy: {} transferred {} {} from {} to {}",
                        isRaidRelated ? "Raid" : "PvP", percentage * 100 + "%", transferAmount, currencyItemName,
                        victim.getName().getString(), killer.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error in item-based PvP kill transfer: {}", e.getMessage());
        }
    }
}
