package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import net.machiavelli.minecolonytax.militia.CitizenMilitiaManager;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
import net.machiavelli.minecolonytax.economy.RaidPenaltyManager;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.event.RaidLoginNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.machiavelli.minecolonytax.util.TickScheduler;

public class RaidManager {

    private static final Logger LOGGER = LogManager.getLogger(RaidManager.class);
    // Fix #8: cross-thread maps must be ConcurrentHashMap.
    private static final Map<UUID, ActiveRaidData> activeRaids = new ConcurrentHashMap<>();

    /** Per-raider attacker grace (prevents spamming raids from same player). */
    private static final Map<UUID, Long> RAID_GRACE_PERIODS = new ConcurrentHashMap<>();

    /**
     * Global defender grace keyed by colony ID.
     * A colony that was just raided cannot be raided by ANYONE for the grace
     * period,
     * preventing coordinated pile-on raids from multiple allies.
     */
    private static final Map<Integer, Long> DEFENSE_GRACE_PERIODS = new ConcurrentHashMap<>();

    private static final Map<Integer, Integer> lastLoggedGuardCounts = new ConcurrentHashMap<>();

    public int handleRaid(CommandContext<CommandSourceStack> context) {

        try {
            ServerPlayer raider = context.getSource().getPlayerOrException();
            UUID raiderUUID = raider.getUUID();

            // Collect all colonies this player owns, FCT-primary colony first
            int primaryColonyId = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColony(raiderUUID);
            List<IColony> ownedColonies = IColonyManager.getInstance().getColonies(raider.level()).stream()
                    .filter(c -> c.getPermissions().getOwner().equals(raiderUUID))
                    .sorted((a, b) -> {
                        if (a.getID() == primaryColonyId) return -1;
                        if (b.getID() == primaryColonyId) return 1;
                        return 0;
                    })
                    .collect(java.util.stream.Collectors.toList());

            if (ownedColonies.isEmpty()) {
                context.getSource().sendFailure(Component.literal("You must own a colony to initiate a raid."));
                return 0;
            }

            boolean buildingRequirementsEnabled = TaxConfig.isRaidBuildingRequirementsEnabled();
            String requirementsConfig = TaxConfig.getRaidBuildingRequirements();

            // Pick the first owned colony that satisfies requirements.
            // This handles multi-colony players where only one colony qualifies.
            IColony raiderColony = null;
            net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult lastFailResult = null;

            if (buildingRequirementsEnabled) {
                for (IColony candidate : ownedColonies) {
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult result =
                            net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.checkRaidRequirements(candidate);
                    if (result.meetsRequirements) {
                        raiderColony = candidate;
                        break;
                    }
                    lastFailResult = result;
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.info("Colony {} (id={}) does not meet raid requirements: {}",
                                candidate.getName(), candidate.getID(), result.message);
                    }
                }
                if (raiderColony == null) {
                    String failMsg = lastFailResult != null ? lastFailResult.message : "unknown";
                    LOGGER.warn("Raid blocked — no owned colony meets requirements: {}", failMsg);
                    context.getSource()
                            .sendFailure(Component.literal("Cannot initiate raid: " + failMsg)
                                    .withStyle(ChatFormatting.RED));
                    return 0;
                }
            } else {
                // Building requirements disabled — use guard count, prefer FCT primary.
                for (IColony candidate : ownedColonies) {
                    int guardCount = WarSystem.countGuards(candidate);
                    if (guardCount >= TaxConfig.getMinGuardsToRaid()) {
                        raiderColony = candidate;
                        break;
                    }
                }
                if (raiderColony == null) {
                    int minGuards = TaxConfig.getMinGuardsToRaid();
                    LOGGER.warn("Raid blocked — no owned colony has >= {} guards", minGuards);
                    context.getSource()
                            .sendFailure(Component.literal("Your colony must have at least " + minGuards +
                                    " guards to initiate a raid."));
                    return 0;
                }
            }

            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info("Raid requirements met: using colony {} (id={}), buildingEnabled={}, config='{}'",
                        raiderColony.getName(), raiderColony.getID(), buildingRequirementsEnabled, requirementsConfig);
            }

            String colonyName = StringArgumentType.getString(context, "colony");
            Level level = context.getSource().getLevel();
            IColony colony = findColonyByName(colonyName, level);
            if (colony == null) {
                context.getSource().sendFailure(Component.literal("Colony not found!"));
                return 0;
            }

            // Per-colony war check: only block if THIS colony is at war
            if (WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
                context.getSource()
                        .sendFailure(Component.literal("This colony is currently at war! You cannot raid it.")
                                .withStyle(ChatFormatting.RED));
                return 0;
            }

            boolean colonyAlreadyRaided = activeRaids.values().stream()
                    .anyMatch(rd -> rd.getColony().getID() == colony.getID());
            if (colonyAlreadyRaided) {
                context.getSource().sendFailure(Component.literal("This colony is already under a raid!")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            if (!TaxConfig.ALLOW_OFFLINE_RAIDS.get()) {
                ServerPlayer owner = Objects.requireNonNull(colony.getWorld().getServer()).getPlayerList()
                        .getPlayer(colony.getPermissions().getOwner());
                if (owner == null) {
                    context.getSource().sendFailure(Component.literal("Colony owner is offline!"));
                    return 0;
                }
            }

            if (!isRaiderInColony(raider, colony)) {
                context.getSource().sendFailure(Component.literal("You must be inside the colony to start a raid."));
                return 0;
            }

            if (net.machiavelli.minecolonytax.faction.FactionManager.areAllies(raiderColony.getID(), colony.getID())) {
                context.getSource().sendFailure(
                        Component.literal("You cannot raid an allied faction!").withStyle(ChatFormatting.RED));
                return 0;
            }

            // Check RaidGuardProtection
            int targetGuards = WarSystem.countGuards(colony);
            if (TaxConfig.isRaidGuardProtectionEnabled()) {
                int targetGuardTowers = WarSystem.countGuardTowers(colony);
                int minGuardsRequired = TaxConfig.getMinGuardsToBeRaided();
                int minGuardTowersRequired = TaxConfig.getMinGuardTowersToBeRaided();

                if (targetGuards < minGuardsRequired) {
                    context.getSource().sendFailure(Component.translatable("raid.protection.guards",
                            minGuardsRequired, targetGuards).withStyle(ChatFormatting.RED));
                    return 0;
                }

                if (targetGuardTowers < minGuardTowersRequired) {
                    context.getSource().sendFailure(Component.translatable("raid.protection.guard_towers",
                            minGuardTowersRequired, targetGuardTowers).withStyle(ChatFormatting.RED));
                    return 0;
                }
            }

            // Check if colony is in debt and debt system is disabled
            int colonyBalance = TaxManager.getStoredTaxForColony(colony);
            int debtLimit = TaxConfig.getDebtLimit();
            if (colonyBalance < 0 && debtLimit == 0) {
                context.getSource().sendFailure(Component.literal(
                        "Cannot raid colony in debt: Debt system is disabled on this server. Colony has no tax to steal!")
                        .withStyle(ChatFormatting.RED));
                if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                    LOGGER.info("RAID BLOCKED: Colony {} is in debt ({}) and debt system is disabled", colony.getName(),
                            colonyBalance);
                }
                return 0;
            }

            // Fix #10 (Codex MED-3): treasury cost must be deducted AFTER all rejection
            // checks (attacker cooldown, defender grace, self-raid). Compute the cost and
            // verify funds first, but only call deductFromTreasury once all gates pass.
            int pendingRaidCost = 0;
            if (TaxConfig.isRaidTreasuryEnabled()) {
                int targetTaxBalance = TaxManager.getStoredTaxForColony(colony);
                double costPercent = TaxConfig.getRaidTreasuryCostPercent();
                pendingRaidCost = Math.max((int) Math.ceil(targetTaxBalance * costPercent), 50);

                int treasuryBalance = TreasuryManager.getTreasuryBalance(raiderColony.getID());

                if (treasuryBalance < pendingRaidCost) {
                    context.getSource().sendFailure(Component.literal(
                            String.format("Insufficient treasury funds to raid! Need: %d, Have: %d", pendingRaidCost,
                                    treasuryBalance))
                            .withStyle(ChatFormatting.RED));
                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("Raid blocked — colony {} has insufficient treasury ({}) for raid cost ({})",
                                raiderColony.getName(), treasuryBalance, pendingRaidCost);
                    }
                    return 0;
                }
            }

            // Check attacker's own cooldown (same raider can't raid twice in grace window)
            Long graceEnd = RAID_GRACE_PERIODS.get(raiderUUID);
            if (graceEnd != null && System.currentTimeMillis() < graceEnd) {
                long remaining = graceEnd - System.currentTimeMillis();
                String timeLeft = String.format("%d minutes %d seconds",
                        TimeUnit.MILLISECONDS.toMinutes(remaining),
                        TimeUnit.MILLISECONDS.toSeconds(remaining) % 60);
                context.getSource()
                        .sendFailure(Component.literal("You must wait " + timeLeft + " before raiding again!"));
                return 0;
            }

            // Check global DEFENDER grace period — prevents pile-on raids from multiple
            // allies
            Long defenseGraceEnd = DEFENSE_GRACE_PERIODS.get(colony.getID());
            if (defenseGraceEnd != null && System.currentTimeMillis() < defenseGraceEnd) {
                long remaining = defenseGraceEnd - System.currentTimeMillis();
                String timeLeft = String.format("%d minutes %d seconds",
                        TimeUnit.MILLISECONDS.toMinutes(remaining),
                        TimeUnit.MILLISECONDS.toSeconds(remaining) % 60);
                context.getSource()
                        .sendFailure(Component.literal(
                                colony.getName() + " was recently raided and is under protection for " + timeLeft + "!")
                                .withStyle(ChatFormatting.YELLOW));
                return 0;
            }

            if (colony.getPermissions().getOwner().equals(raiderUUID)) {
                context.getSource().sendFailure(Component.literal("You cannot raid your own colony!"));
                return 0;
            }

            // All rejection checks have now passed — actually deduct the treasury cost
            // and send the comprehensive raid instructions.
            if (TaxConfig.isRaidTreasuryEnabled() && pendingRaidCost > 0) {
                TreasuryManager.deductFromTreasury(raiderColony.getID(), pendingRaidCost);
                final int finalRaidCost = pendingRaidCost;
                context.getSource().sendSuccess(() -> Component.literal(
                        String.format("Paid %d from treasury to start raid.", finalRaidCost))
                        .withStyle(ChatFormatting.GOLD), false);
                if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                    LOGGER.info("Raider colony {} paid {} from treasury for raid", raiderColony.getName(),
                            pendingRaidCost);
                }
            }

            // Send comprehensive raid instructions to the raider
            sendRaidInstructions(raider, colony, targetGuards);

            colony.getPermissions().setPlayerRank(raiderUUID, colony.getPermissions().getRankHostile(), level);
            ActiveRaidData raidData = new ActiveRaidData(raiderUUID, colony);

            // Initialize guard count for revenue calculation
            raidData.initializeGuardCount(targetGuards);
            // Snapshot original guard IDs for robust kill tracking
            raidData.snapshotOriginalGuardIds();

            raidData.setRaiderColony(raiderColony); // Store the raider's colony for later cleanup
            activeRaids.put(raiderUUID, raidData);

            // Apply GLOW effect to the raider for visibility to defenders
            applyGlowEffectToRaider(raider);

            // Enable raid interactions on the TARGET colony only.
            // The raider's own colony does not need Hostile rank permissions modified —
            // defenders do not go to the raider's colony during a raid. Previously this
            // also modified the raider colony, which needlessly polluted its Hostile rank.
            RaidManager.setRaidInteractionPermissions(colony, true);

            // Apply resistance effects to defending guards
            GuardResistanceHandler.applyResistanceToGuardsForRaid(colony);

            // Apply bribe debuff if espionage bribe was successful against this colony
            applyBribeEffectToGuards(colony);

            // Initialize militia system for kill tracking (even if militia is disabled)
            CitizenMilitiaManager.getInstance().initializeColonyMilitia(colony.getID());

            // Activate militia system if enabled
            if (TaxConfig.ENABLE_CITIZEN_MILITIA.get()) {
                int militiaActivated = CitizenMilitiaManager.getInstance().activateMilitia(colony);
                sendColonyMessage(colony, Component
                        .literal("⚔ " + militiaActivated + " citizens have joined the militia to defend the colony!")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                    LOGGER.info("Activated {} militia members for colony {} during raid", militiaActivated,
                            colony.getName());
                }
            } else {
                int existingGuards = (int) colony.getCitizenManager().getCitizens().stream()
                        .filter(c -> c.getJob() != null && c.getJob().isGuard())
                        .count();
                CitizenMilitiaManager.getInstance().setTotalDefenders(colony.getID(), existingGuards);
                CitizenMilitiaManager.getInstance().setTotalGuardsCount(colony.getID(), existingGuards);
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("Militia disabled - Set total defenders for colony {} to {} guards only",
                            colony.getName(),
                            existingGuards);
                }
            }

            // Send progress tracking confirmation to raider
            int finalTotalDefenders = CitizenMilitiaManager.getInstance().getTotalDefenders(colony.getID());
            raider.sendSystemMessage(Component.literal("🎯 RAID PROGRESS TRACKING INITIALIZED")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal("\nTotal Defenders: " + finalTotalDefenders)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (Progress will update as you eliminate defenders)")
                            .withStyle(ChatFormatting.GRAY)));

            startRaidCountdown(raidData);
            // Send styled raid alert to all colony members
            sendColonyMessage(colony,
                    Component.translatable("raid.alert.colony", colony.getName(), raider.getName().getString())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

            // Grant advancement
            try {
                net.minecraft.advancements.Advancement adv = raider.getServer().getAdvancements()
                        .getAdvancement(new net.minecraft.resources.ResourceLocation("minecolonytax:codex/start_raid"));
                if (adv != null) {
                    raider.getAdvancements().award(adv, "check");
                }
            } catch (Exception e) {
            }

            return 1;

        } catch (Exception e) {
            LOGGER.error("Raid command failed", e);
            context.getSource().sendFailure(Component.literal("Raid failed: " + e.getMessage()));
        }
        return 0;
    }

    public int stopRaidCommand(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            // Stops the raid initiated by the executing player, or falls back to any active
            // raid if the executor is an admin who is not themselves raiding.
            ActiveRaidData raidData = activeRaids.get(player.getUUID());
            if (raidData == null) {
                if (!ctx.getSource().hasPermission(2)) {
                    ctx.getSource().sendFailure(Component.literal("You have no active raid to stop."));
                    return 0;
                }
                Optional<ActiveRaidData> anyRaid = activeRaids.values().stream().findFirst();
                if (anyRaid.isPresent()) {
                    raidData = anyRaid.get();
                } else {
                    ctx.getSource().sendFailure(Component.literal("No active raid to stop."));
                    return 0;
                }
            }
            endRaid(raidData, "Stopped by operator");
            ctx.getSource().sendSuccess(() -> Component.literal("Raid stopped."), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Stop raid command failed", e);
            ctx.getSource().sendFailure(Component.literal("Failed to stop raid: " + e.getMessage()));
            return 0;
        }
    }

    public static ActiveRaidData getActiveRaidForPlayer(UUID playerId) {
        return activeRaids.get(playerId);
    }

    // New: lookup by colony ID to support ID-based kill tracking when killer is
    // null or differs
    public static ActiveRaidData getActiveRaidForColony(int colonyId) {
        for (ActiveRaidData data : activeRaids.values()) {
            if (data != null && data.getColony() != null && data.getColony().getID() == colonyId && data.isActive()) {
                return data;
            }
        }
        return null;
    }

    public static void endActiveRaid(ActiveRaidData raidData, String reason) {
        if (!raidData.isActive())
            return;
        raidData.setActive(false);

        // Remove GLOW effect from raider when raid ends
        if (raidData.getColony() != null && raidData.getColony().getWorld() != null
                && raidData.getColony().getWorld().getServer() != null) {
            ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList()
                    .getPlayer(raidData.getRaider());
            if (raiderPlayer != null) {
                removeGlowEffectFromRaider(raiderPlayer);
            }
        }
        if (raidData.getBossEvent() != null) {
            raidData.getBossEvent().removeAllPlayers();
            raidData.getBossEvent().setVisible(false);
        }

        // Remove resistance effects from defending guards
        GuardResistanceHandler.removeResistanceFromGuardsForRaid(raidData.getColony());

        // Deactivate militia system if enabled
        if (TaxConfig.ENABLE_CITIZEN_MILITIA.get()) {
            CitizenMilitiaManager.getInstance().deactivateMilitia(raidData.getColony());
            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) LOGGER.info("Deactivated militia for colony {} after raid ended", raidData.getColony().getName());
        }

        // Disable raid interactions for both colonies involved
        RaidManager.setRaidInteractionPermissions(raidData.getColony(), false);
        if (raidData.getRaiderColony() != null) {
            RaidManager.setRaidInteractionPermissions(raidData.getRaiderColony(), false);
        }

        // Demote the raider from the Hostile rank on the target colony.
        // The raider was assigned hostile rank at raid start; restore them to Neutral.
        IColony targetColony = raidData.getColony();
        if (targetColony != null && targetColony.getWorld() != null) {
            try {
                com.minecolonies.api.colony.permissions.IPermissions perms = targetColony.getPermissions();
                UUID raiderUUID = raidData.getRaider();
                com.minecolonies.api.colony.permissions.Rank current = perms.getRank(raiderUUID);
                if (current != null && current.isHostile()) {
                    perms.setPlayerRank(raiderUUID, perms.getRankNeutral(), targetColony.getWorld());
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging())
                        LOGGER.debug("Demoted raider {} from Hostile to Neutral on colony {} after raid end.", raiderUUID, targetColony.getName());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to demote raider from hostile rank on colony {} after raid end", targetColony.getName(), e);
            }
        }

        // CRITICAL FIX: Transfer tax revenue for successful raids
        ServerPlayer raiderPlayer = null;
        if (raidData.getColony() != null && raidData.getColony().getWorld() != null
                && raidData.getColony().getWorld().getServer() != null) {
            raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raidData.getRaider());
        }

        if (raiderPlayer != null) {
            // Only transfer tax revenue if raid completed successfully AND raider is
            // eligible for rewards
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info("TAX TRANSFER CHECK - Reason: '{}', Eligible for rewards: {}", reason,
                        raidData.isEligibleForRewards());
                LOGGER.info(
                        "TAX TRANSFER CHECK - hasLeftBoundaries: {}, guardsKilled (ActiveRaidData): {}, hasKilledAnyGuards: {}",
                        raidData.hasLeftBoundaries(), raidData.getGuardsKilled(), raidData.hasKilledAnyGuards());
                LOGGER.info("TAX TRANSFER CHECK - Reason match: {}",
                        reason.equals("Raid completed successfully") || reason.contains("All guards eliminated")
                                || reason.contains("All defenders eliminated"));
            }

            if (reason.equals("Raid completed successfully") || reason.contains("All guards eliminated")
                    || reason.contains("All defenders eliminated")) {
                if (raidData.isEligibleForRewards()) {
                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("Tax transfer approved - Raider: {}, Colony: {}",
                                raiderPlayer.getName().getString(),
                                raidData.getColony().getName());
                    }
                    transferTaxRevenue(raidData);

                    // Apply raid penalty to the raided colony
                    if (TaxConfig.getRaidPenaltyTaxReductionPercent() > 0) {
                        RaidPenaltyManager.applyRaidPenalty(raidData.getColony().getID());
                        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                            LOGGER.info("Applied raid penalty to colony {} - tax reduced for {} hours",
                                    raidData.getColony().getName(), TaxConfig.getRaidPenaltyDurationHours());
                        }

                        // Notify colony owner about the penalty
                        sendColonyMessage(raidData.getColony(), Component.literal(
                                String.format(
                                        "⚠ Your colony has been damaged! Tax generation reduced by %.0f%% for %d hours. Use /wnt repair to restore.",
                                        TaxConfig.getRaidPenaltyTaxReductionPercent() * 100,
                                        TaxConfig.getRaidPenaltyDurationHours()))
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    }
                } else {
                    // Raider left boundaries or didn't kill any guards - no rewards
                    String denialReason = raidData.hasLeftBoundaries() ? "left colony boundaries"
                            : "failed to kill any guards or militia";

                    Component denialMessage = Component.literal("🚫 RAID FAILED! 🚫")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal("\n"))
                            .append(Component.literal("No rewards earned - you ").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(denialReason).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                            .append(Component.literal("!").withStyle(ChatFormatting.YELLOW));

                    raiderPlayer.sendSystemMessage(denialMessage);
                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("❌ TAX TRANSFER DENIED - Raider {} ineligible: {}",
                                raiderPlayer.getName().getString(),
                                denialReason);
                    }

                    // LOG FAILED RAID TO HISTORY (structured)
                    int _failedBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(raidData.getColony());
                    net.machiavelli.minecolonytax.data.HistoryManager.getColonyHistory(raidData.getColony().getID())
                            .addRaidEntry(
                                    raiderPlayer.getUUID(),
                                    raiderPlayer.getName().getString(),
                                    0, // No amount stolen
                                    false, // Not successful
                                    denialReason,
                                    _failedBal, _failedBal);
                    net.machiavelli.minecolonytax.data.HistoryManager.saveHistory();
                }
            } else {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("❌ TAX TRANSFER SKIPPED - Raid ended without victory: '{}'", reason);
                }

                // LOG RAID ENDED EARLY TO HISTORY (if player is online)
                if (raiderPlayer != null) {
                    int _earlyBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(raidData.getColony());
                    net.machiavelli.minecolonytax.data.HistoryManager.getColonyHistory(raidData.getColony().getID())
                            .addRaidEntry(
                                    raiderPlayer.getUUID(),
                                    raiderPlayer.getName().getString(),
                                    0, // No amount stolen
                                    false, // Not successful
                                    reason,
                                    _earlyBal, _earlyBal);
                    net.machiavelli.minecolonytax.data.HistoryManager.saveHistory();
                }
            }
        } else {
            LOGGER.warn("❌ TAX TRANSFER FAILED - Raider player not found online");
        }

        RAID_GRACE_PERIODS.put(raidData.getRaider(), System.currentTimeMillis() + getRaidGraceDurationMs());

        // Global defender protection — any raider is blocked for the full grace window
        if (raidData.getColony() != null) {
            DEFENSE_GRACE_PERIODS.put(raidData.getColony().getID(),
                    System.currentTimeMillis() + getRaidGraceDurationMs());
        }

        // Purge expired grace period entries to prevent unbounded map growth
        long now = System.currentTimeMillis();
        RAID_GRACE_PERIODS.entrySet().removeIf(e -> e.getValue() <= now);
        DEFENSE_GRACE_PERIODS.entrySet().removeIf(e -> e.getValue() <= now);

        RaidLoginNotifier.recordCompletedRaid(raidData);
        activeRaids.remove(raidData.getRaider());

        // Timer abbrechen wie in endRaid (idempotent: schadet nicht, falls der Task bereits weg/-1 ist).
        // endActiveRaid wird auch direkt aufgerufen (z. B. handleRaiderKilled), ohne dass der Countdown-Task
        // zuvor gecancelt wurde — sonst tickt er bis zum naechsten isActive()-Check weiter.
        TickScheduler.cancel(raidData.getCountdownTaskId());
        raidData.setCountdownTaskId(-1);

        // Restore Hostile rank to pre-raid state now that the raid is no longer active
        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(raidData.getColony());

        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
            LOGGER.info("Raid ended: {}", reason);
        }
    }

    public static Map<UUID, ActiveRaidData> getActiveRaids() {
        // Fix #8: return an unmodifiable view rather than the live map so callers
        // cannot mutate the canonical state and CME risk is reduced.
        return Collections.unmodifiableMap(activeRaids);
    }

    public static void handleRaiderKilled(ActiveRaidData raidData, ServerPlayer killer) {
        LOGGER.debug("handleRaiderKilled called for raider {}", raidData.getRaider());
        // Guard the colony→world→server chain (the colony's dimension may be unloaded, or the colony
        // resolved/disbanded). endActiveRaid/endRaid null-check this; this handler, invoked straight
        // from LivingDeathEvent, did not and would NPE on the event thread.
        if (raidData.getColony() == null || raidData.getColony().getWorld() == null
                || raidData.getColony().getWorld().getServer() == null) {
            LOGGER.debug("Colony/world unloaded during raider death — ending raid without payout");
            RaidManager.endActiveRaid(raidData, "Raider killed while colony unloaded");
            return;
        }
        ServerPlayer raider = raidData.getColony().getWorld().getServer().getPlayerList()
                .getPlayer(raidData.getRaider());
        if (raider == null) {
            LOGGER.debug("Raider is offline, ending raid");
            RaidManager.endActiveRaid(raidData, "Raider killed while offline");
            return;
        }

        LOGGER.debug("Processing raid kill. Raider: {}, Killer: {}", raider.getName().getString(),
                killer.getName().getString());

        // Remove GLOW effect from raider immediately when killed
        removeGlowEffectFromRaider(raider);

        // NOTE: no "stolen amount transferred back to the colony" bonus here. Player raids no longer
        // steal tax over time (the actual theft in transferTaxRevenue happens only on a WON/expired
        // raid), so on a raider DEATH nothing was ever taken from the colony. Crediting it
        // balance * stealPercentage via incrementTaxRevenue therefore minted currency out of nothing,
        // proportional to the colony's own balance, on every repelled raid — a farmable inflation
        // exploit. The colony's real reward for a successful defense is the separate raid penalty and
        // defense-reward transfers below, which move currency from the raider's side.

        double penaltyPercentage = TaxConfig.RAID_PENALTY_PERCENTAGE.get();
        double defenseRewardPercentage = TaxConfig.RAID_DEFENSE_REWARD_PERCENTAGE.get();
        LOGGER.debug("Raid penalty percentage from config: {}", penaltyPercentage);
        LOGGER.debug("Raid defense reward percentage from config: {}", defenseRewardPercentage);
        // Currency name for messages
        final String currencyName;
        if (TaxConfig.isSDMShopConversionEnabled()) {
            currencyName = "coins";
        } else {
            String tempCurrencyName = TaxConfig.getCurrencyItemName();
            if (tempCurrencyName.contains(":")) {
                tempCurrencyName = tempCurrencyName.substring(tempCurrencyName.lastIndexOf(":") + 1);
            }
            currencyName = tempCurrencyName;
        }

        int raidPenalty = 0;

        if (TaxConfig.isSDMShopConversionEnabled()) {
            if (SDMShopIntegration.isAvailable()) {
                long raiderBalance = SDMShopIntegration.getMoney(raider);
                LOGGER.debug("Raider SDMShop balance: {}", raiderBalance);
                int computedPenalty = (int) (raiderBalance * penaltyPercentage);
                raidPenalty = Math.max(1, computedPenalty);
                LOGGER.debug("Computed raid penalty (with minimum 1): {}", raidPenalty);
            } else {
                LOGGER.warn("SDMShop integration is enabled in config but SDMShop mod is not available");
                raidPenalty = 1; // Use minimum penalty
                LOGGER.debug("Using fallback raid penalty: {}", raidPenalty);
            }

            raidData.addToTotalTransferred(raidPenalty);

            if (raidPenalty > 0) {
                // Use the /sdmshop pay command to transfer funds directly from raider to killer
                // The command format is: /sdmshop pay <recipient> <amount>
                String payCmd = String.format("sdmshop pay %s %d", killer.getName().getString(), raidPenalty);
                LOGGER.debug("Executing command on behalf of raider: {}", payCmd);
                try {
                    // Execute the pay command using the raider's command source stack
                    // This ensures the command is processed as if the raider executed it
                    CommandSourceStack raiderSource = raider.createCommandSourceStack();
                    raider.getServer().getCommands().performPrefixedCommand(raiderSource, payCmd);

                    LOGGER.debug("Successfully transferred {} from {} to {}",
                            raidPenalty, raider.getName().getString(), killer.getName().getString());
                } catch (Exception e) {
                    LOGGER.error("Failed to transfer funds from raider to killer", e);
                    // Fallback to the old method if the pay command fails
                    try {
                        String removeCmd = String.format("sdmshop remove %s %d", raider.getName().getString(),
                                raidPenalty);
                        String addCmd = String.format("sdmshop add %s %d", killer.getName().getString(), raidPenalty);

                        LOGGER.debug("Falling back to remove/add method. Executing: {}", removeCmd);
                        raidData.getColony().getWorld().getServer().getCommands()
                                .performPrefixedCommand(
                                        raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                                        removeCmd);

                        LOGGER.debug("Executing: {}", addCmd);
                        raidData.getColony().getWorld().getServer().getCommands()
                                .performPrefixedCommand(
                                        raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                                        addCmd);
                    } catch (Exception ex) {
                        LOGGER.error("Fallback method also failed", ex);
                    }
                }
            }
        } else {
            // Penalty scales with the raider's treasury balance; falls back to a
            // town-hall-based floor when the treasury is empty.
            IColony raiderColonyForPenalty = raidData.getRaiderColony();
            int treasuryBalance = raiderColonyForPenalty != null
                    ? net.machiavelli.minecolonytax.economy.TreasuryManager
                            .getTreasuryBalance(raiderColonyForPenalty.getID())
                    : 0;

            if (treasuryBalance > 0) {
                raidPenalty = (int) (treasuryBalance * penaltyPercentage);
                LOGGER.debug("Using treasury-scaled penalty. Treasury: {}, Penalty: {}", treasuryBalance, raidPenalty);
            } else {
                // Fallback: base on town hall tax (unchanged legacy behaviour)
                int baseTaxAmount = 250;
                if (TaxConfig.BUILDING_TAXES.containsKey("townhall")) {
                    baseTaxAmount = (int) (TaxConfig.BUILDING_TAXES.get("townhall").get() * 5.0);
                }
                raidPenalty = (int) (baseTaxAmount * penaltyPercentage);
                LOGGER.debug("Using fallback base-tax penalty. Base amount: {}, Penalty: {}", baseTaxAmount,
                        raidPenalty);
            }
            raidPenalty = Math.max(100, raidPenalty);
            LOGGER.debug("Final non-SDM raid penalty: {}", raidPenalty);

            // Deduct from raider's colony tax balance, respecting the debt limit so repeated raider
            // deaths can't drive the colony arbitrarily negative. The killer's bounty is paid from what
            // was ACTUALLY taken (currency-conserving — no minting at the debt floor). Common case
            // (colony solvent) equals the full penalty.
            IColony raiderColony = raidData.getRaiderColony();
            if (raiderColony != null) {
                raidPenalty = TaxManager.deductRespectingDebtLimit(raiderColony, raidPenalty);
                LOGGER.debug("Deducted {} from raider's colony tax balance (debt-limit aware)", raidPenalty);
            } else {
                LOGGER.error("Could not deduct raid penalty: raider's colony is null");
            }
            // Record the ACTUAL transferred amount (after the clamp) so the history stat matches items.
            raidData.addToTotalTransferred(raidPenalty);

            // Give items to the killer (only what was actually deducted above)
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(new net.minecraft.resources.ResourceLocation(TaxConfig.getCurrencyItemName()));
            if (item != null && raidPenalty > 0) {
                net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item,
                        raidPenalty);
                boolean added = killer.getInventory().add(itemStack);
                if (!added) {
                    // If inventory is full, drop items near killer
                    killer.drop(itemStack, false);
                    LOGGER.debug("Killer's inventory was full, dropped {} items near them", raidPenalty);
                } else {
                    LOGGER.debug("Successfully gave {} items to killer", raidPenalty);
                }
            } else if (item == null && raidPenalty > 0) {
                // Fallback to give command if item not found in registry
                String giveCmd = String.format("give %s %s %d", killer.getName().getString(),
                        TaxConfig.getCurrencyItemName(), raidPenalty);
                LOGGER.debug("Executing command: {}", giveCmd);
                try {
                    raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(
                            raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                            giveCmd);
                } catch (Exception e) {
                    LOGGER.error("Failed to execute give command for raid penalty", e);
                }
            }
        }

        // ========== NEW: RAID DEFENSE REWARD SYSTEM ==========
        // ===================================================
        // DEFENSE REWARD CALCULATION
        // ===================================================
        int calculatedDefenseReward = 0;
        // Tatsaechlich gutgeschriebener Betrag (nach Debt-Limit-Clamp) — fuer Anzeige == Gutschrift
        int creditedDefenseReward = 0;
        if (defenseRewardPercentage > 0) {
            if (TaxConfig.isSDMShopConversionEnabled()) {
                if (SDMShopIntegration.isAvailable()) {
                    long raiderBalance = SDMShopIntegration.getMoney(raider);
                    calculatedDefenseReward = (int) (raiderBalance * defenseRewardPercentage);
                    LOGGER.debug("Calculated defense reward from SDMShop balance {}: {}", raiderBalance,
                            calculatedDefenseReward);

                    if (calculatedDefenseReward > 0) {
                        // Remove money from raider and add to defending colony's main tax balance
                        if (SDMShopIntegration.setMoney(raider, raiderBalance - calculatedDefenseReward)) {
                            TaxManager.incrementTaxRevenue(raidData.getColony(), calculatedDefenseReward);
                            creditedDefenseReward = calculatedDefenseReward; // SDMShop: gutgeschrieben == berechnet
                            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                                LOGGER.info("Transferred {} defense reward from {} to colony {} main tax balance",
                                        calculatedDefenseReward, raider.getName().getString(),
                                        raidData.getColony().getName());
                            }
                        } else {
                            LOGGER.error("Failed to deduct defense reward from raider's balance");
                            calculatedDefenseReward = 0; // Reset if transfer failed
                        }
                    }
                } else {
                    LOGGER.warn("SDMShop integration enabled but not available for defense reward calculation");
                }
            } else {
                // For item-based currency, calculate reward based on raider's colony tax
                // balance
                IColony raiderColony = raidData.getRaiderColony();
                if (raiderColony != null) {
                    int raiderColonyBalance = TaxManager.getStoredTaxForColony(raiderColony);
                    if (raiderColonyBalance > 0) {
                        calculatedDefenseReward = (int) (raiderColonyBalance * defenseRewardPercentage);
                        calculatedDefenseReward = Math.max(50, calculatedDefenseReward); // Minimum reward

                        // Deduct from raider's colony (debt-limit aware) and credit the defending colony
                        // ONLY with what was actually taken — a currency-conserving transfer that can't
                        // push the raider colony past -DebtLimit.
                        int taken = TaxManager.deductRespectingDebtLimit(raiderColony, calculatedDefenseReward);
                        TaxManager.incrementTaxRevenue(raidData.getColony(), taken);
                        creditedDefenseReward = taken; // nur der geclampte, tatsaechlich transferierte Betrag

                        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                            LOGGER.info(
                                    "Transferred {} defense reward from raider's colony {} to defending colony {} main tax balance",
                                    taken, raiderColony.getName(), raidData.getColony().getName());
                        }
                    } else {
                        // If no positive balance, create a base reward from raider colony tax debt
                        calculatedDefenseReward = (int) (250 * defenseRewardPercentage); // Base amount
                        calculatedDefenseReward = Math.max(25, calculatedDefenseReward); // Minimum reward

                        // Add debt to raider's colony (bounded by DebtLimit) and reward the defending
                        // colony with what was actually taken. Debt disabled (limit 0) or already at the
                        // floor yields 0 — no unbounded debt, no minting.
                        int taken = TaxManager.deductRespectingDebtLimit(raiderColony, calculatedDefenseReward);
                        TaxManager.incrementTaxRevenue(raidData.getColony(), taken);
                        creditedDefenseReward = taken; // nur der geclampte, tatsaechlich transferierte Betrag

                        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                            LOGGER.info(
                                    "Created {} defense reward debt for raider's colony {} and credited defending colony {} main tax balance",
                                    taken, raiderColony.getName(), raidData.getColony().getName());
                        }
                    }
                } else {
                    LOGGER.warn("Could not calculate defense reward: raider's colony is null");
                }
            }
        }

        // Final values for use in lambda expressions.
        // Anzeige (Broadcast + Title) nutzt den tatsaechlich gutgeschriebenen Betrag, nicht den Pre-Clamp-Wert.
        final int defenseReward = creditedDefenseReward;
        // ===================================================

        // Update war statistics for the killer
        PlayerWarDataManager.incrementPlayersKilledInWar(killer);
        PlayerWarDataManager.addAmountRaided(killer, raidPenalty);
        net.machiavelli.minecolonytax.db.WarStatsDB.recordWarKill(
                killer.getUUID(), killer.getName().getString(),
                raider.getUUID(), raider.getName().getString());

        MutableComponent message = Component.literal("⚔ RAID DEFENDER VICTORY! ⚔")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true))
                .append(Component.literal("\n"))
                .append(Component.literal("Raider ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(raider.getName().getString()).withStyle(ChatFormatting.RED))
                .append(Component.literal(" was killed by ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(killer.getName().getString())
                        .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)))
                .append(Component.literal("!\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Raid ended with ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(raidPenalty + " " + currencyName)
                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)))
                .append(Component.literal(" transferred to the killer").withStyle(ChatFormatting.GOLD));

        // (Removed the "stolen tax recovered to colony" line: player raids no longer mint that amount
        // on a raider death — see the note in the deduction logic above.)

        // Add defense reward information to the message if there was a reward
        if (defenseReward > 0) {
            message = message
                    .append(Component.literal(" and ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(defenseReward + " " + currencyName)
                            .withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(true)))
                    .append(Component.literal(" claimable reward added to colony balance")
                            .withStyle(ChatFormatting.GOLD));
        }

        message = message.append(Component.literal(".").withStyle(ChatFormatting.GOLD));

        LOGGER.debug("Broadcasting raid kill message to server");
        raidData.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(message, false);

        IPermissions raidEndPerms = raidData.getColony().getPermissions();
        raidData.getColony().getPermissions().getPlayers().forEach((uuid, data) -> {
            // Only send to colony allies: Owner, Officers, and Friends
            // Excludes: Hostile and Neutral players
            Rank rank = raidEndPerms.getRank(uuid);
            if (rank != null && (rank.equals(raidEndPerms.getRankOwner()) ||
                    rank.equals(raidEndPerms.getRankOfficer()) ||
                    rank.equals(raidEndPerms.getRankFriend()))) {
                ServerPlayer colonyMember = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (colonyMember != null) {
                    String titleCmd = String.format(
                            "title %s title {\"text\":\"Raid Ended!\",\"color\":\"green\",\"bold\":true}",
                            colonyMember.getName().getString());
                    String subtitleCmd;
                    if (defenseReward > 0) {
                        subtitleCmd = String.format(
                                "title %s subtitle {\"text\":\"Raider killed! +%d %s reward available\",\"color\":\"gold\"}",
                                colonyMember.getName().getString(), defenseReward, currencyName);
                    } else {
                        subtitleCmd = String.format(
                                "title %s subtitle {\"text\":\"Raider killed by %s\",\"color\":\"gold\"}",
                                colonyMember.getName().getString(), killer.getName().getString());
                    }
                    try {
                        raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(
                                raidData.getColony().getWorld().getServer().createCommandSourceStack(), titleCmd);
                        raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(
                                raidData.getColony().getWorld().getServer().createCommandSourceStack(), subtitleCmd);
                    } catch (Exception e) {
                        LOGGER.error("Failed to send title to colony member", e);
                    }
                }
            }
        });

        LOGGER.debug("Ending raid due to raider being killed");
        RaidManager.endActiveRaid(raidData,
                "Raider killed by " + killer.getName().getString() + ". Penalty: " + raidPenalty + " " + currencyName);
    }

    private void startRaidCountdown(ActiveRaidData raidData) {
        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
            if (raidData.getColony().getWorld() == null || raidData.getColony().getWorld().getServer() == null) {
                LOGGER.warn("Raid countdown: Colony world or server is null, cancelling task for raid on colony {}",
                        raidData.getColony().getID());
                TickScheduler.cancel(raidData.getCountdownTaskId());
                raidData.setCountdownTaskId(-1);
                endRaid(raidData, "Colony world/server became unavailable");
                return;
            }

            // Ensure we stop ticking if raid became inactive
            if (!raidData.isActive()) {
                TickScheduler.cancel(raidData.getCountdownTaskId());
                raidData.setCountdownTaskId(-1);
                return;
            }

            ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList()
                    .getPlayer(raidData.getRaider());
            if (raiderPlayer == null) {
                sendColonyMessage(raidData.getColony(),
                        Component.literal("Raid stopped! Raider disconnected.").withStyle(ChatFormatting.RED));
                endRaid(raidData, "Raider disconnected");
                TickScheduler.cancel(raidData.getCountdownTaskId());
                raidData.setCountdownTaskId(-1);
                return;
            }

            // Check if raider is still in colony boundaries
            boolean isInColony = isRaiderInColony(raiderPlayer, raidData.getColony());
            if (!isInColony && !raidData.hasLeftBoundaries()) {
                // First time leaving boundaries - mark it and notify
                raidData.markLeftBoundaries();

                // Calculate what they would have earned up to this point
                double currentStealPercentage = CitizenMilitiaManager.getInstance()
                        .calculateTaxPercentage(raidData.getColony().getID());
                int colonyBalance = TaxManager.getStoredTaxForColony(raidData.getColony());
                int potentialStolen = (int) (colonyBalance * currentStealPercentage);
                raidData.setPotentialStolenAmount(potentialStolen);

                // Notify raider they've lost their rewards
                Component penaltyMessage = Component.literal("⚠ RAID DISQUALIFIED! ⚠")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("You left the colony boundaries!")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("Potential earnings forfeited: ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(String.valueOf(potentialStolen)).withStyle(ChatFormatting.RED,
                                ChatFormatting.BOLD))
                        .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("RAID ENDING - You gain nothing!").withStyle(ChatFormatting.RED,
                                ChatFormatting.BOLD));

                raiderPlayer.sendSystemMessage(penaltyMessage);

                // Notify colony defenders
                Component defenseMessage = Component.literal("🎉 RAIDER FLED! 🎉")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("The raider ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(raiderPlayer.getName().getString())
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" left the colony boundaries and was disqualified!")
                                .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("Raid ended - Colony successfully defended!")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

                sendColonyMessage(raidData.getColony(), defenseMessage);

                LOGGER.info(
                        "Raider {} left colony {} boundaries. Raid ended immediately. Potential earnings of {} forfeited.",
                        raiderPlayer.getName().getString(), raidData.getColony().getName(), potentialStolen);

                // End the raid immediately - raider gains nothing
                endRaid(raidData, "Raider left colony boundaries and was disqualified");
                TickScheduler.cancel(raidData.getCountdownTaskId());
                raidData.setCountdownTaskId(-1);
                return;
            }

            // Increment elapsed time FIRST before checking duration
            raidData.setElapsedSeconds(raidData.getElapsedSeconds() + 1);
            RaidManager.updateRaidBossBar(raidData);

            // Check if raid time has expired AFTER incrementing (use > not >= to allow full
            // duration)
            if (raidData.getElapsedSeconds() > RaidManager.getMaxRaidDurationSeconds()) {
                endRaid(raidData, "Raid completed successfully");
                TickScheduler.cancel(raidData.getCountdownTaskId());
                raidData.setCountdownTaskId(-1);
                return;
            }

            if (!raidData.isWarningSent() && isRaiderInColony(raiderPlayer, raidData.getColony())) {
                IPermissions warningPerms = raidData.getColony().getPermissions();
                raidData.getColony().getPermissions().getPlayers().forEach((uuid, data) -> {
                    if (!uuid.equals(raidData.getRaider())) {
                        // Only send to colony allies: Owner, Officers, and Friends
                        // Excludes: Hostile and Neutral players
                        Rank rank = warningPerms.getRank(uuid);
                        if (rank != null && (rank.equals(warningPerms.getRankOwner()) ||
                                rank.equals(warningPerms.getRankOfficer()) ||
                                rank.equals(warningPerms.getRankFriend()))) {
                            ServerPlayer p = (ServerPlayer) raidData.getColony().getWorld().getPlayerByUUID(uuid);
                            if (p != null) {
                                p.sendSystemMessage(Component
                                        .literal("Warning: Hostile player " + raiderPlayer.getName().getString()
                                                + " has entered the colony!")
                                        .withStyle(ChatFormatting.RED));
                            }
                        }
                    }
                });
                raidData.setWarningSent(true);
            }

            // Maintain GLOW effect on raider when inside colony, remove when outside
            if (raiderPlayer != null) {
                if (isRaiderInColony(raiderPlayer, raidData.getColony())) {
                    applyGlowEffectToRaider(raiderPlayer);
                } else {
                    // Remove GLOW effect if raider is outside colony boundaries
                    removeGlowEffectFromRaider(raiderPlayer);
                }
            }

            // Tax revenue transfer removed from timer - will only happen after successful
            // raid completion
        }, 1000, 1000));
    }

    public static void updateRaidBossBar(ActiveRaidData raidData) {
        if (raidData.getColony().getWorld() == null || raidData.getColony().getWorld().getServer() == null) {
            return;
        }
        raidData.getColony().getWorld().getServer().execute(() -> {
            if (!raidData.isActive())
                return;
            ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList()
                    .getPlayer(raidData.getRaider());

            // Determine raider status
            String status;
            if (raiderPlayer == null) {
                status = "OFFLINE";
            } else if (raidData.hasLeftBoundaries()) {
                status = "DISQUALIFIED";
            } else if (!isRaiderInColony(raiderPlayer, raidData.getColony())) {
                status = "LEAVING!";
            } else {
                status = "ACTIVE";
            }

            float progress = Math.min((float) raidData.getElapsedSeconds() / getMaxRaidDurationSeconds(), 1.0f);
            int remainingSeconds = Math.max(getMaxRaidDurationSeconds() - raidData.getElapsedSeconds(), 0);

            // Get kill progress - Prefer ID-based snapshot tracking
            int guardsKilled = raidData.getKilledGuardCount();
            int originalGuardCount = raidData.getOriginalGuardCount();
            if (originalGuardCount <= 0) {
                // Fallback to previous mechanism if snapshot empty
                guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilledCount(raidData.getColony().getID());
                originalGuardCount = CitizenMilitiaManager.getInstance()
                        .getTotalGuardsCount(raidData.getColony().getID());
            }
            double stealPercentage = CitizenMilitiaManager.getInstance()
                    .calculateTaxPercentage(raidData.getColony().getID());

            // DISABLED: Guard reconciliation system was causing false positives
            // Guards were being marked as dead when they were actually alive but just not
            // spawned/loaded
            // Reconciliation is now handled by proper death event tracking in
            // CitizenMilitiaManager
            // If guards die without event detection, that's less harmful than falsely
            // marking all guards as dead

            // Check for victory based on actual tracked kills (no reconciliation)
            if (originalGuardCount > 0 && guardsKilled >= originalGuardCount) {
                if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                    LOGGER.info("RAID VICTORY! All {} guards eliminated", originalGuardCount);
                }
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info(
                            "TAX TRANSFER DEBUG: Ending raid with reason 'All guards eliminated - Raiders victorious!'");
                }
                RaidManager.endActiveRaid(raidData, "All guards eliminated - Raiders victorious!");
                return; // Exit early - raid is over
            }

            // Victory progress: How many of the original guards have been killed
            double victoryProgress = originalGuardCount > 0 ? (double) guardsKilled / originalGuardCount : 0.0;
            Component name = Component.literal(
                    String.format("Raid: %s | Guards Killed: %d/%d | Victory: %.1f%% | Tax: %.1f%% | Time: %02d:%02d",
                            status, guardsKilled, originalGuardCount, victoryProgress * 100, stealPercentage * 100,
                            remainingSeconds / 60, remainingSeconds % 60));

            // Log raid progress every 30 seconds OR when guards are killed
            int colonyId = raidData.getColony().getID();
            int lastLogged = lastLoggedGuardCounts.getOrDefault(colonyId, 0);
            boolean shouldLog = (remainingSeconds % 30 == 0) || (guardsKilled != lastLogged);

            if (shouldLog) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    // Log4j2 kennt nur {} — Prozentwerte vorformatieren, sonst blieben {:.1f} literal und die Argumente verrutschten
                    LOGGER.info("RAID PROGRESS: Guards {}/{}, Victory {}%, Tax {}%, Time {}:{}",
                            guardsKilled, originalGuardCount,
                            String.format("%.1f", victoryProgress * 100), String.format("%.1f", stealPercentage * 100),
                            remainingSeconds / 60, String.format("%02d", remainingSeconds % 60));
                }
                lastLoggedGuardCounts.put(colonyId, guardsKilled);
            }

            raidData.getBossEvent().setName(name);
            raidData.getBossEvent().setProgress(progress);

            // Change boss bar color based on status
            if (raidData.hasLeftBoundaries()) {
                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.RED);
            } else if (guardsKilled > 0) {
                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.YELLOW);
            } else {
                raidData.getBossEvent().setColor(net.minecraft.world.BossEvent.BossBarColor.WHITE);
            }
        });
    }

    private static boolean isRaiderInColony(ServerPlayer raider, IColony colony) {
        if (raider == null || colony == null || colony.getWorld() == null)
            return false;
        BlockPos raiderPos = raider.blockPosition();
        return colony.isCoordInColony(colony.getWorld(), raiderPos);
    }

    /**
     * Fix #9: this used to be a divergent copy of {@link #endActiveRaid(ActiveRaidData, String)}
     * that applied different penalties, wrote different history entries, and used a different
     * reward-eligibility rule. To avoid two paths drifting further, this is now a thin
     * delegate that:
     *   (a) sends the colony-wide "raid ended" message that was specific to this code path
     *   (b) cancels the countdown task that this code path used to manage
     *   (c) calls endActiveRaid for the canonical cleanup (penalty + structured history).
     */
    private void endRaid(ActiveRaidData raidData, String reason) {
        if (!raidData.isActive())
            return;

        // Preserve colony-wide raid-end message that was unique to this code path.
        if (raidData.getColony() != null) {
            MutableComponent raidEndMsgToColony = Component.translatable("raid.end.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("\n----------------------------------------")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n").append(
                            Component.translatable("raid.end.colony.body", reason).withStyle(ChatFormatting.YELLOW)))
                    .append(Component.literal("\n----------------------------------------")
                            .withStyle(ChatFormatting.DARK_GRAY));
            sendColonyMessageExcluding(raidData.getColony(), raidEndMsgToColony, raidData.getRaider());
        }

        // Cancel the countdown task (only this path created one).
        if (raidData.getCountdownTaskId() >= 0) {
            TickScheduler.cancel(raidData.getCountdownTaskId());
            raidData.setCountdownTaskId(-1);
        }

        // Delegate to the canonical end-of-raid path (writes structured history + applies penalty).
        RaidManager.endActiveRaid(raidData, reason);
    }

    // Utility methods that might be shared or moved to a more central utility class
    private IColony findColonyByName(String name, Level level) {
        return IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /**
     * Enable or disable raid interaction permissions for a colony.
     * Uses the configured raid actions from TaxConfig to ensure consistency.
     * 
     * @param colony  The colony to modify permissions for
     * @param allowed Whether to allow (true) or deny (false) the raid actions
     */
    public static void setRaidInteractionPermissions(IColony colony, boolean allowed) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
            return;

        if (allowed) {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
        }
        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        // Get raid actions from config to ensure they match the config file
        for (Action a : TaxConfig.getRaidActions()) {
            perms.setPermission(hostile, a, allowed);
        }
    }

    /**
     * Apply bribe debuff (weakness + slowness) to random guards if espionage bribe
     * was used.
     * Called at raid start on the main server thread.
     */
    private static void applyBribeEffectToGuards(IColony targetColony) {
        if (!TaxConfig.isSpySystemEnabled())
            return;

        int guardsToDebuff = net.machiavelli.minecolonytax.espionage.SpyManager
                .consumeBribedGuards(targetColony.getID());
        if (guardsToDebuff <= 0)
            return;

        // Collect all loaded guard entities
        List<com.minecolonies.api.entity.citizen.AbstractEntityCitizen> guards = new ArrayList<>();
        for (com.minecolonies.api.colony.ICitizenData citizen : targetColony.getCitizenManager().getCitizens()) {
            if (citizen.getJob() != null && citizen.getJob().isGuard() && citizen.getEntity().isPresent()) {
                guards.add(citizen.getEntity().get());
            }
        }

        // Shuffle and pick N random guards
        Collections.shuffle(guards);
        int count = Math.min(guardsToDebuff, guards.size());

        // Duration matches max raid time so effects last the full raid
        int durationTicks = 20 * RaidManager.getMaxRaidDurationSeconds();

        for (int i = 0; i < count; i++) {
            com.minecolonies.api.entity.citizen.AbstractEntityCitizen guard = guards.get(i);
            guard.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 1, false, false, true));
            guard.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 1, false, false, true));
        }

        LOGGER.info("Bribe effect applied: {} of {} guards debuffed in colony {} for raid",
                count, guards.size(), targetColony.getName());

        // Notify defending colony that something is wrong with their guards
        sendColonyMessage(targetColony, Component.literal(
                count + " of your guards seem sluggish and unable to fight properly...")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
    }

    private static void sendColonyMessage(IColony colony, Component message) {
        if (colony == null || colony.getWorld() == null)
            return;
        IPermissions perms = colony.getPermissions();
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            // Only send to colony allies: Owner, Officers, and Friends
            // Excludes: Hostile and Neutral players
            Rank rank = perms.getRank(uuid);
            if (rank != null && (rank.equals(perms.getRankOwner()) ||
                    rank.equals(perms.getRankOfficer()) ||
                    rank.equals(perms.getRankFriend()))) {
                ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                if (p != null)
                    p.sendSystemMessage(message);
            }
        });
    }

    private void sendColonyMessageExcluding(IColony colony, Component message, UUID excludePlayer) {
        if (colony == null || colony.getWorld() == null)
            return;
        IPermissions perms = colony.getPermissions();
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            if (!uuid.equals(excludePlayer)) { // Exclude specific player
                // Only send to colony allies: Owner, Officers, and Friends
                // Excludes: Hostile and Neutral players
                Rank rank = perms.getRank(uuid);
                if (rank != null && (rank.equals(perms.getRankOwner()) ||
                        rank.equals(perms.getRankOfficer()) ||
                        rank.equals(perms.getRankFriend()))) {
                    ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                    if (p != null)
                        p.sendSystemMessage(message);
                }
            }
        });
    }

    private static int getMaxRaidDurationSeconds() {
        return TaxConfig.MAX_RAID_DURATION_MINUTES.get() * 60;
    }

    private int getTaxInterval() {
        return TaxConfig.RAID_TAX_INTERVAL_SECONDS.get();
    }

    private double[] getTaxPercentages() {
        return TaxConfig.RAID_TAX_PERCENTAGES.get().stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static long getRaidGraceDurationMs() {
        return TimeUnit.MINUTES.toMillis(TaxConfig.RAID_GRACE_PERIOD_MINUTES.get());
    }

    /**
     * Get a ServerPlayer by their UUID from the server
     * 
     * @param playerId The player's UUID
     * @return The ServerPlayer, or null if not found/online
     */
    public static ServerPlayer getServerPlayerById(UUID playerId) {
        if (playerId == null)
            return null;

        try {
            // Get the server instance
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.getPlayerList().getPlayer(playerId);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get player by UUID {}: {}", playerId, e.getMessage());
        }
        return null;
    }

    /**
     * Check if a colony is currently under any kind of raid (player or entity)
     */
    public static boolean isColonyUnderRaid(int colonyId) {
        // Check for active player raids
        for (ActiveRaidData raidData : activeRaids.values()) {
            if (raidData.getColony().getID() == colonyId && raidData.isActive()) {
                return true;
            }
        }
        return false;
    }

    private static void transferTaxRevenue(ActiveRaidData raidData) {
        ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList()
                .getPlayer(raidData.getRaider());
        if (raiderPlayer == null) {
            LOGGER.warn("Raider is offline, skipping revenue transfer for raid on {}", raidData.getColony().getName());
            return;
        }

        LOGGER.info("Starting tax revenue transfer for raider {} on colony {}",
                raiderPlayer.getName().getString(), raidData.getColony().getName());

        double finalPercentage;

        // Check if we're using the new guard-kill-based tax stealing system
        LOGGER.info("Tax steal config: TAX_STEAL_PER_GUARD_KILLED = {}", TaxConfig.TAX_STEAL_PER_GUARD_KILLED.get());
        if (TaxConfig.TAX_STEAL_PER_GUARD_KILLED.get()) {
            // New system: Tax stolen based on guards/militia killed, distributed from max
            // percentage
            finalPercentage = CitizenMilitiaManager.getInstance().calculateTaxPercentage(raidData.getColony().getID());
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info("Calculated tax percentage based on kills: {}%", finalPercentage * 100);
            }

            if (finalPercentage == 0.0) {
                LOGGER.warn("No guards/militia killed during raid, no tax stolen for raid on {}",
                        raidData.getColony().getName());
                return;
            }

            int guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilled(raidData.getColony().getID());
            int totalDefenders = CitizenMilitiaManager.getInstance().getTotalDefenders(raidData.getColony().getID());
            double maxTaxPercentage = TaxConfig.MAX_RAID_TAX_PERCENTAGE.get();

            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info(
                        "Balanced tax calculation - Guards/militia killed: {}/{} defenders, Max tax: {}%, Final rate: {}%",
                        guardsKilled, totalDefenders, maxTaxPercentage * 100, finalPercentage * 100);
            }
        } else {
            // Old system: Time-based tax stealing
            // Check if any guards were killed during the raid (for the old system)
            if (raidData.getGuardsKilled() == 0) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.debug("No guards killed during raid, skipping revenue transfer for raid on {}",
                            raidData.getColony().getName());
                }
                return;
            }

            // Calculate final tax percentage based on raid duration and guard kills
            int raidDuration = raidData.getElapsedSeconds();
            int taxInterval = TaxConfig.RAID_TAX_INTERVAL_SECONDS.get();
            double[] taxPercentages = TaxConfig.RAID_TAX_PERCENTAGES.get().stream().mapToDouble(Double::doubleValue)
                    .toArray();
            int intervalsPassed = Math.min(raidDuration / taxInterval, taxPercentages.length);
            double totalBasePercentage = 0;

            // Sum up all the percentages that would have been applied during the raid
            for (int i = 0; i < intervalsPassed; i++) {
                totalBasePercentage += taxPercentages[Math.min(i, taxPercentages.length - 1)];
            }

            // Scale revenue based on percentage of guards killed
            double guardKillPercentage = raidData.getGuardKillPercentage();
            finalPercentage = totalBasePercentage * guardKillPercentage;

            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info(
                        "Time-based tax calculation - Guards killed: {}/{} ({}%), Total base rate: {}%, Final rate: {}%",
                        raidData.getGuardsKilled(), raidData.getTotalGuards(),
                        guardKillPercentage * 100, totalBasePercentage * 100, finalPercentage * 100);
            }
        }

        // Calculate colony balance to take based on their stored tax
        int colonyBalance = TaxManager.getStoredTaxForColony(raidData.getColony());

        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            // Log4j2 kennt nur {} — Prozentwert vorformatieren (sonst blieb {:.1f} literal und das Argument ungenutzt)
            LOGGER.info("💰 TAX CALCULATION DEBUG: Colony balance={}, Percentage={}%",
                    colonyBalance, String.format("%.1f", finalPercentage * 100));
        }

        int amountToDeduct = 0;

        // Can only steal from colonies with positive balance!
        if (colonyBalance > 0) {
            amountToDeduct = (int) (colonyBalance * finalPercentage);
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info("💰 POSITIVE BALANCE: Stealing {} from colony {} ({}% of {})",
                        amountToDeduct, raidData.getColony().getName(), finalPercentage * 100, colonyBalance);
            }
        } else if (colonyBalance <= 0) {
            // Colony is in debt - use TaxStealPerGuard system if debt is enabled
            int debtLimit = TaxConfig.getDebtLimit();
            if (debtLimit > 0) {
                // NEW SYSTEM: Fixed amount per guard killed when raiding debt colonies
                int guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilled(raidData.getColony().getID());
                int taxStealPerGuard = TaxConfig.getTaxStealPerGuard();
                int baseAmount = guardsKilled * taxStealPerGuard;

                // Check if we can add more debt
                int currentDebt = Math.abs(colonyBalance); // Current debt (positive value)
                if (currentDebt < debtLimit) {
                    // How much more debt can be added
                    int availableDebt = debtLimit - currentDebt;
                    // Take the smaller of calculated amount or available debt room
                    amountToDeduct = Math.min(baseAmount, availableDebt);

                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.info(
                                "💰 DEBT COLONY: Guards killed={}, Per-guard steal={}, Base amount={}, Current debt={}, Debt limit={}, Final debt to add={}",
                                guardsKilled, taxStealPerGuard, baseAmount, currentDebt, debtLimit, amountToDeduct);
                    }
                } else {
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.info("💰 DEBT LIMIT REACHED: Colony {} already at debt limit (debt: {}, limit: {})",
                                raidData.getColony().getName(), currentDebt, debtLimit);
                    }
                }
            } else {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("💰 NO LOOT: Colony {} is in debt ({}) and debt creation is disabled",
                            raidData.getColony().getName(), colonyBalance);
                }
            }
        }

        // Apply RAID_FORCE investment multiplier from the attacker's colony.
        // Only scales the positive-balance steal, and is capped at the colony's balance: a boosted
        // percentage can never steal more than the colony actually holds. The debt-colony branch
        // (colonyBalance <= 0) is deliberately left unscaled — there is nothing extra to extract from
        // an empty treasury, and scaling it would let RAID_FORCE push debt past the configured limit.
        if (net.machiavelli.minecolonytax.TaxConfig.isUpgradesEnabled()
                && amountToDeduct > 0 && colonyBalance > 0 && raidData.getRaiderColony() != null) {
            double raidForceMultiplier = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                    .getRaidForceMultiplier(raidData.getRaiderColony().getID());
            if (raidForceMultiplier > 1.0) {
                amountToDeduct = (int) (amountToDeduct * raidForceMultiplier);
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging())
                    LOGGER.debug("RAID_FORCE multiplier {} applied; adjusted steal amount to {}",
                            raidForceMultiplier, amountToDeduct);
            }
        }

        // Never steal more than a solvent colony actually holds. Guards against BOTH the RAID_FORCE
        // multiplier above AND a misconfigured RaidTaxPercentages whose tiers sum to > 1 (which would
        // otherwise let the base percentage alone over-draw the balance into debt). Only the
        // positive-balance case is capped — the debt branch has its own availableDebt limit and is
        // deliberately left unscaled.
        if (colonyBalance > 0 && amountToDeduct > colonyBalance) {
            amountToDeduct = colonyBalance;
        }

        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            LOGGER.info("💰 FINAL CALCULATION: Colony balance={}, Final amount to transfer={}",
                    colonyBalance, amountToDeduct);
        }

        // Process the transfer if there's anything to transfer
        if (amountToDeduct > 0) {
            // Apply the deduction to the colony (either from positive balance or creating
            // more debt)
            TaxManager.payTaxDebt(raidData.getColony(), -amountToDeduct);

            if (colonyBalance > 0) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("💰 STOLEN: Deducted {} from colony {} positive balance", amountToDeduct,
                            raidData.getColony().getName());
                }
            } else {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("💰 DEBT CREATED: Added {} debt to colony {}", amountToDeduct,
                            raidData.getColony().getName());
                }
            }
        } else {
            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                LOGGER.info("💰 NO TRANSFER: No tax to steal from colony {} (balance: {})",
                        raidData.getColony().getName(), colonyBalance);
            }

            // Inform the raider why they got no loot (raiderPlayer is already available in
            // this scope)
            if (colonyBalance <= 0 && TaxConfig.getDebtLimit() == 0) {
                raiderPlayer.sendSystemMessage(Component
                        .literal("⚠️ No loot obtained: Colony is in debt and debt system is disabled on this server!")
                        .withStyle(ChatFormatting.YELLOW));
            } else if (colonyBalance <= 0) {
                int guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilled(raidData.getColony().getID());
                if (guardsKilled == 0) {
                    raiderPlayer.sendSystemMessage(Component
                            .literal("⚠️ No loot obtained: Colony is in debt - kill guards to earn "
                                    + TaxConfig.getTaxStealPerGuard() + " per guard!")
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    raiderPlayer.sendSystemMessage(
                            Component.literal("⚠️ No loot obtained: Colony has reached its debt limit!")
                                    .withStyle(ChatFormatting.YELLOW));
                }
            } else {
                raiderPlayer.sendSystemMessage(Component.literal("⚠️ No loot obtained: No tax available to steal!")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        // Only proceed with transfer if we have an amount to transfer
        if (amountToDeduct > 0) {
            // Transfer to the raider's account
            try {
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    if (SDMShopIntegration.isAvailable()) {
                        // Use SDMShop integration to add currency to player
                        long currentBalance = SDMShopIntegration.getMoney(raiderPlayer);
                        if (SDMShopIntegration.setMoney(raiderPlayer, currentBalance + amountToDeduct)) {
                            LOGGER.info(
                                    "Raid completion: Added {} currency to player {} via SDMShop API (new balance: {})",
                                    amountToDeduct, raiderPlayer.getName().getString(),
                                    currentBalance + amountToDeduct);
                        } else {
                            LOGGER.error("Failed to transfer {} currency to player {} via SDMShop API",
                                    amountToDeduct, raiderPlayer.getName().getString());
                        }
                    } else {
                        LOGGER.warn(
                                "SDMShop integration is enabled but SDMShop mod is not available. Currency transfer skipped for player: {}",
                                raiderPlayer.getName().getString());
                    }
                } else {
                    // Give items using denomination-aware utility
                    net.machiavelli.minecolonytax.util.ItemUtils.giveCurrencyToPlayer(raiderPlayer, amountToDeduct);
                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("Raid completion: Gave {} currency to player {}", amountToDeduct,
                                raiderPlayer.getName().getString());
                    }
                }

                // Update total transferred amount for statistics
                raidData.addToTotalTransferred(amountToDeduct);

                // TRACK RAID STATISTICS FOR PLAYER
                PlayerWarDataManager.incrementRaidedColonies(raiderPlayer);
                PlayerWarDataManager.addAmountRaided(raiderPlayer, amountToDeduct);
                net.machiavelli.minecolonytax.db.WarStatsDB.recordRaid(
                        raiderPlayer.getUUID(), raiderPlayer.getName().getString(),
                        raidData.getColony().getID(), amountToDeduct);
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("Updated raid statistics for player {} - Raided amount: {}",
                            raiderPlayer.getName().getString(), amountToDeduct);
                }

                // LOG SUCCESSFUL RAID TO HISTORY (structured)
                net.machiavelli.minecolonytax.data.HistoryManager.getColonyHistory(raidData.getColony().getID())
                        .addRaidEntry(
                                raiderPlayer.getUUID(),
                                raiderPlayer.getName().getString(),
                                amountToDeduct,
                                true, // Successful
                                null, // No failure reason
                                colonyBalance,
                                colonyBalance - amountToDeduct
                        );
                net.machiavelli.minecolonytax.data.HistoryManager.saveHistory();

                // Get proper currency name
                String currencyName;
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    currencyName = "Coins";
                } else {
                    currencyName = TaxConfig.getCurrencyItemName();
                    if (currencyName.contains(":")) {
                        currencyName = currencyName.substring(currencyName.indexOf(":") + 1);
                    }
                }

                // Create message to send
                Component taxMessage = Component.literal("⚔ RAID COMPLETED! ⚔\n" + raidData.getColony().getName()
                        + " lost " + amountToDeduct
                        + " " + currencyName + " to raider "
                        + raiderPlayer.getName().getString()
                        + "!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

                // Send message to all relevant players without duplication
                Set<ServerPlayer> playersToNotify = new HashSet<>();

                // Add target colony members (only allies: Owner, Officers, Friends)
                // Excludes: Hostile and Neutral players
                IPermissions targetPerms = raidData.getColony().getPermissions();
                raidData.getColony().getPermissions().getPlayers().forEach((uuid, data) -> {
                    Rank rank = targetPerms.getRank(uuid);
                    if (rank != null && (rank.equals(targetPerms.getRankOwner()) ||
                            rank.equals(targetPerms.getRankOfficer()) ||
                            rank.equals(targetPerms.getRankFriend()))) {
                        ServerPlayer p = (ServerPlayer) raidData.getColony().getWorld().getPlayerByUUID(uuid);
                        if (p != null)
                            playersToNotify.add(p);
                    }
                });

                // Add raider's colony members (if different colony, only allies: Owner,
                // Officers, Friends)
                // Excludes: Hostile and Neutral players
                if (raidData.getRaiderColony() != null && !raidData.getRaiderColony().equals(raidData.getColony())) {
                    IPermissions raiderPerms = raidData.getRaiderColony().getPermissions();
                    raidData.getRaiderColony().getPermissions().getPlayers().forEach((uuid, data) -> {
                        Rank rank = raiderPerms.getRank(uuid);
                        if (rank != null && (rank.equals(raiderPerms.getRankOwner()) ||
                                rank.equals(raiderPerms.getRankOfficer()) ||
                                rank.equals(raiderPerms.getRankFriend()))) {
                            ServerPlayer p = (ServerPlayer) raidData.getRaiderColony().getWorld().getPlayerByUUID(uuid);
                            if (p != null)
                                playersToNotify.add(p);
                        }
                    });
                }

                // Send message to all collected players
                for (ServerPlayer player : playersToNotify) {
                    player.sendSystemMessage(taxMessage);
                }

                // Comprehensive logging of the transaction
                if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                    LOGGER.info("Raid completion tax transfer: {} {} from {} to {}",
                            amountToDeduct, currencyName, raidData.getColony().getName(),
                            raiderPlayer.getName().getString());
                }
            } catch (Exception e) {
                LOGGER.error("Error processing raid completion tax transfer: ", e);
            }
        }
    }

    /**
     * Apply GLOW effect to raider so defenders can easily locate them
     */
    private static void applyGlowEffectToRaider(ServerPlayer raider) {
        if (raider != null) {
            // Only apply and log if raider doesn't already have the glow effect
            if (!raider.hasEffect(MobEffects.GLOWING)) {
                // Apply GLOW effect with a duration of 70 seconds (longer than tax interval to
                // ensure continuity)
                // Amplifier 0 = level 1 effect, hideParticles = false, showIcon = true
                MobEffectInstance glowEffect = new MobEffectInstance(MobEffects.GLOWING, 70 * 20, 0, false, true, true);
                raider.addEffect(glowEffect);
                LOGGER.debug("Applied GLOW effect to raider: {}", raider.getName().getString());
            }
        }
    }

    /**
     * Send comprehensive raid instructions to the raider when starting a raid
     */
    private void sendRaidInstructions(ServerPlayer raider, IColony colony, int totalDefenders) {
        String currencyName = getCurrencyName();
        int colonyBalance = TaxManager.getStoredTaxForColony(colony);
        int maxRaidDuration = RaidManager.getMaxRaidDurationSeconds();
        double maxTaxPercentage = TaxConfig.MAX_RAID_TAX_PERCENTAGE.get();

        // Get militia count if enabled
        int militiaCount = 0;
        if (TaxConfig.ENABLE_CITIZEN_MILITIA.get()) {
            militiaCount = CitizenMilitiaManager.getInstance().getMilitiaCount(colony.getID());
        }

        MutableComponent instructions = Component.literal("⚔ RAID INITIATED ⚔")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(Component.literal("═══════════════════════════════════════").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.literal("TARGET: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(colony.getName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal("OBJECTIVE: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal("Kill guards/militia to steal tax revenue").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal("TIME LIMIT: ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(String.format("%d:%02d minutes", maxRaidDuration / 60, maxRaidDuration % 60))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n\n"))
                .append(Component.literal("⚡ COLONY STATUS ⚡").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal("• Guards: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(totalDefenders - militiaCount))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal("• Militia: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(militiaCount)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal("• Tax Balance: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(colonyBalance + " " + currencyName).withStyle(ChatFormatting.YELLOW,
                        ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal("• Max Steal: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format("%.1f%% (%d %s)", maxTaxPercentage * 100,
                        (int) (colonyBalance * maxTaxPercentage), currencyName))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("\n\n"))
                .append(Component.literal("⚠ CRITICAL RULES ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal("• STAY IN BOUNDARIES: ").withStyle(ChatFormatting.YELLOW,
                        ChatFormatting.BOLD))
                .append(Component.literal("Leaving colony = INSTANT DISQUALIFICATION").withStyle(ChatFormatting.RED))
                .append(Component.literal("\n"))
                .append(Component.literal("• DEATH PENALTY: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("Death = Colony gets your potential earnings").withStyle(ChatFormatting.RED))
                .append(Component.literal("\n"))
                .append(Component.literal("• KILL REQUIREMENT: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("No kills = No reward").withStyle(ChatFormatting.RED))
                .append(Component.literal("\n"))
                .append(Component.literal("═══════════════════════════════════════").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.literal("Good luck, raider! Watch the boss bar for your progress.")
                        .withStyle(ChatFormatting.GREEN));

        raider.sendSystemMessage(instructions);

        // Send title and subtitle for emphasis
        try {
            String titleCmd = String.format(
                    "title %s title {\"text\":\"RAID STARTED\",\"color\":\"red\",\"bold\":true}",
                    raider.getName().getString());
            String subtitleCmd = String.format(
                    "title %s subtitle {\"text\":\"Kill %d defenders • Stay in boundaries!\",\"color\":\"yellow\"}",
                    raider.getName().getString(), totalDefenders);

            raider.getServer().getCommands().performPrefixedCommand(raider.getServer().createCommandSourceStack(),
                    titleCmd);
            raider.getServer().getCommands().performPrefixedCommand(raider.getServer().createCommandSourceStack(),
                    subtitleCmd);
        } catch (Exception e) {
            LOGGER.error("Failed to send raid start title to raider", e);
        }

        LOGGER.info("Sent comprehensive raid instructions to raider {} for colony {}",
                raider.getName().getString(), colony.getName());
    }

    /**
     * Gets the appropriate currency name based on config settings
     */
    private static String getCurrencyName() {
        if (TaxConfig.isSDMShopConversionEnabled()) {
            return "$";
        } else {
            return "coins";
        }
    }

    /**
     * Remove GLOW effect from raider when raid ends
     */
    public static void removeGlowEffectFromRaider(ServerPlayer raider) {
        if (raider != null) {
            raider.removeEffect(MobEffects.GLOWING);
            LOGGER.debug("Removed GLOW effect from raider: {}", raider.getName().getString());
        }
    }

    public static ActiveRaidData getActiveRaidByColony(int colonyId) {
        return activeRaids.values().stream()
                .filter(raid -> raid.getColony().getID() == colonyId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a player is currently raiding the specified colony.
     * 
     * @param playerUUID the player's UUID
     * @param colony     the colony being checked
     * @return true if the player is currently raiding this colony
     */
    public static boolean isPlayerCurrentlyRaiding(UUID playerUUID, com.minecolonies.api.colony.IColony colony) {
        if (playerUUID == null || colony == null) {
            return false;
        }

        ActiveRaidData raidData = activeRaids.get(playerUUID);
        return raidData != null &&
                raidData.isActive() &&
                raidData.getColony().getID() == colony.getID();
    }

}
