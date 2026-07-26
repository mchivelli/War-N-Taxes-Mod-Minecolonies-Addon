package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.militia.CitizenMilitiaManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.Objects;
import net.minecraft.world.entity.Entity;

@Mod.EventBusSubscriber
public class RaidKillTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaidKillTracker.class);

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityDeath(LivingDeathEvent event) {
        // The EntityMercenary class lives in the INTERNAL com.minecolonies.core package
        // (not the supported api/* surface). A future MC refactor that moves/removes the
        // class would NoClassDefFoundError on every mob death and propagate out of the
        // event bus. Wrap the whole dispatch in try { } catch (NoClassDefFoundError)
        // so that one missing class can never brick the LivingDeathEvent listener.
        try {
            if (event.getEntity() instanceof AbstractEntityCitizen citizen) {
                handleEntityDeath(citizen, event.getSource());
            } else if (event.getEntity() instanceof com.minecolonies.core.entity.mobs.EntityMercenary mercenary) {
                handleMercenaryDeath(mercenary, event.getSource());
            }
        } catch (LinkageError e) {
            // NoClassDefFoundError is a subclass of LinkageError, so this single catch
            // covers both. The multi-catch with both was illegal per Java spec.
            // MineColonies internal class moved/removed — degrade silently so unrelated
            // mob deaths still fire. Log once at debug to surface for diagnosis.
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.debug("[RaidKillTracker] EntityMercenary not resolvable; skipping mercenary-death branch: {}", e.toString());
            }
        }
    }

    private static void handleEntityDeath(AbstractEntityCitizen citizen, DamageSource damageSource) {
        // Fast path: citizen deaths are extremely frequent across hundreds of colonies.
        // This handler only acts on regular raids, claiming raids, or wars — if none are
        // active anywhere, bail before the colony lookup (audit H5). Behavior-preserving:
        // with no conflict, the flags below stay false and the method returns anyway.
        if (RaidManager.getActiveRaids().isEmpty()
                && net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.isEmpty()
                && !ColonyClaimingRaidManager.hasActiveClaimingRaids()) {
            return;
        }

        ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer player ? player : null;
        IColony colony = IColonyManager.getInstance().getColonyByWorld(citizen.getCitizenColonyHandler().getColonyId(),
                citizen.level());
        if (colony == null) {
            return;
        }

        boolean isColonyUnderRaid = RaidManager.isColonyUnderRaid(colony.getID());
        boolean isRegularRaid = false;
        boolean isClaimingRaid = false;
        boolean isWar = false;

        if (killer != null) {
            ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(killer.getUUID());
            isRegularRaid = raidData != null && raidData.isActive() && raidData.getColony().getID() == colony.getID();
            isClaimingRaid = ColonyClaimingRaidManager.isPlayerInClaimingRaid(killer.getUUID(), colony.getID());

            net.machiavelli.minecolonytax.data.WarData warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS
                    .get(colony.getID());
            isWar = warData != null && isPlayerInWar(killer.getUUID(), warData);
        } else if (isColonyUnderRaid) {
            isRegularRaid = true;
        }

        if (!isRegularRaid && !isClaimingRaid && !isWar) {
            return;
        }

        if (isClaimingRaid) {
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info("CLAIMING RAID DEATH DETECTED: {} died in colony {} (killer: {})",
                        citizen.getCitizenData() != null ? citizen.getCitizenData().getName() : "Unknown",
                        colony.getName(),
                        killer != null ? killer.getName().getString() : "environmental");
            }
            handleClaimingRaidDeath(citizen, colony, killer);
            return;
        }

        String combatType = isClaimingRaid ? "claiming" : (isWar ? "war" : "regular");

        ActiveRaidData raidData = null;
        net.machiavelli.minecolonytax.data.WarData warData = null;

        if (isRegularRaid && killer != null) {
            raidData = RaidManager.getActiveRaidForPlayer(killer.getUUID());
        }
        if (isWar) {
            warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colony.getID());
        }

        ICitizenData citizenData = citizen.getCitizenData();
        if (citizenData == null) {
            return;
        }

        boolean isGuard = false;
        boolean isMilitia = false;

        // Method 1: check if citizen was in the original guard snapshot (most reliable)
        ActiveRaidData activeRaid = RaidManager.getActiveRaidForColony(colony.getID());
        if (activeRaid != null) {
            boolean wasInOriginalSnapshot = activeRaid.isOriginalGuard(citizenData.getId());
            if (wasInOriginalSnapshot) {
                isGuard = true;
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("GUARD DETECTED via original snapshot - {} was an original guard (ID: {})",
                            citizenData.getName(), citizenData.getId());
                }
            }
        }

        com.minecolonies.api.colony.jobs.IJob<?> citizenJob = null;
        if (!isGuard) {
            citizenJob = citizenData.getJob();
            if (citizenJob != null && citizenJob.isGuard()) {
                isGuard = true;
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("GUARD DETECTED via job check - {} is a guard", citizenData.getName());
                }
            }
        }

        if (!isGuard && citizenJob == null && citizenData.getWorkBuilding() != null) {
            var workBuilding = citizenData.getWorkBuilding();
            String buildingName = workBuilding.getBuildingDisplayName().toLowerCase();
            if (buildingName.contains("guard") || buildingName.contains("barracks") ||
                    buildingName.contains("archery") || buildingName.contains("combat")) {
                isGuard = true;
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("GUARD DETECTED via building (job cleared) - {} worked at {}",
                            citizenData.getName(), workBuilding.getBuildingDisplayName());
                }
            }
        }

        if (!isGuard) {
            if (citizenData.getWorkBuilding() != null) {
                var workBuilding = citizenData.getWorkBuilding();
                String buildingName = workBuilding.getBuildingDisplayName().toLowerCase();
                if (buildingName.contains("guard") || buildingName.contains("barracks") ||
                        buildingName.contains("archery") || buildingName.contains("combat")) {
                    isGuard = true;
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.info("GUARD DETECTED via building assignment - {} works at {}",
                                citizenData.getName(), workBuilding.getBuildingDisplayName());
                    }
                }
            }
        }

        if (!isGuard) {
            isMilitia = CitizenMilitiaManager.getInstance().isMilitiaMember(colony.getID(), citizenData.getId());
            if (isMilitia) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("MILITIA DETECTED - {} is a militia member", citizenData.getName());
                }
            }
        }

        if (!isMilitia && !isGuard) {
            LOGGER.debug("Citizen {} detection check - Job: {}, Building: {}, trying count comparison",
                    citizenData.getName(),
                    citizenJob != null ? citizenJob.getJobRegistryEntry().toString() : "null",
                    citizenData.getWorkBuilding() != null ? citizenData.getWorkBuilding().getBuildingDisplayName()
                            : "none");
        }

        if (!isGuard && !isMilitia) {
            int defendersBefore = CitizenMilitiaManager.getInstance().getTotalDefenders(colony.getID());
            long currentGuardsAlive = colony.getCitizenManager().getCitizens().stream()
                    .filter(c -> c.getId() != citizenData.getId())
                    .filter(c -> {
                        var job = c.getJob();
                        if (job != null && job.isGuard())
                            return true;
                        // Also check building assignment for guards without jobs set yet
                        if (c.getWorkBuilding() != null) {
                            String buildingName = c.getWorkBuilding().getBuildingDisplayName().toLowerCase();
                            return buildingName.contains("guard") || buildingName.contains("barracks") ||
                                    buildingName.contains("archery") || buildingName.contains("combat");
                        }
                        return false;
                    })
                    .count();

            int actualMilitia = CitizenMilitiaManager.getInstance().getMilitiaMembers(colony.getID()).size();
            int defendersAfter = (int) currentGuardsAlive + actualMilitia;

            LOGGER.debug("COUNT COMPARISON - {} killed, before: {}, after: {} (guards: {}, militia: {})",
                    citizenData.getName(), defendersBefore, defendersAfter, currentGuardsAlive, actualMilitia);

            if (defendersAfter < defendersBefore) {
                isGuard = true;
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("GUARD DETECTED via count comparison - {} was a guard (count: {} -> {})",
                            citizenData.getName(), defendersBefore, defendersAfter);
                }
            } else if (defendersBefore > 0 && defendersAfter >= defendersBefore) {
                LOGGER.warn("COUNT MISMATCH - Expected defender count to decrease but didn't. Assuming {} was a guard",
                        citizenData.getName());
                isGuard = true;
            }
        }

        if (!isGuard && !isMilitia) {
            LOGGER.debug("KILL TRACKER SKIP - {} was not a defender", citizenData.getName());
            return;
        }

        int defendersBefore = CitizenMilitiaManager.getInstance().getTotalDefenders(colony.getID());
        int defendersAfter = defendersBefore - 1;
        String defenderType = isMilitia ? "militia" : "guard";
        int actualGuards = (int) colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getId() != citizenData.getId())
                .filter(c -> c.getJob() != null && c.getJob().isGuard())
                .count();
        int actualMilitia = CitizenMilitiaManager.getInstance().getMilitiaMembers(colony.getID()).size();
        if (isMilitia) {
            actualMilitia--;
        }

        String killerName = killer != null ? killer.getName().getString() : "environmental damage";
        if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
            LOGGER.info("KILL DETECTED - {} {} killed by {} (defenders: {} -> {})",
                    defenderType, citizenData.getName(), killerName, defendersBefore, defendersAfter);
        }

        CitizenMilitiaManager.getInstance().recordDefenderDeath(colony, isGuard);
        CitizenMilitiaManager.getInstance().setTotalDefenders(colony.getID(), defendersAfter);

        if (activeRaid != null) {
            boolean wasOriginalGuard = activeRaid.markGuardKilled(citizenData.getId());
            if (wasOriginalGuard) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info("ID-TRACK: Marked original guard '{}' (ID {}) as killed. {}/{} killed.",
                            citizenData.getName(), citizenData.getId(), activeRaid.getKilledGuardCount(),
                            activeRaid.getOriginalGuardCount());
                }
            } else if (isGuard) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.info(
                            "ID-TRACK: '{}' (ID {}) was a guard but not in original snapshot (auto-promotion). Ignoring for victory.",
                            citizenData.getName(), citizenData.getId());
                }
            }
        }

        int remainingDefenders = defendersAfter;
        double currentStealPercentage = CitizenMilitiaManager.getInstance().calculateTaxPercentage(colony.getID());
        int defendersKilled = CitizenMilitiaManager.getInstance().getDefendersKilled(colony.getID());

        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            LOGGER.info("KILL PROGRESS - Guards: {}, Militia: {}, Total Remaining: {}, Kills: {}",
                    actualGuards, actualMilitia, remainingDefenders, defendersKilled);
        }

        if (isGuard && net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            LOGGER.info("REMAINING GUARDS in colony after kill:");
            colony.getCitizenManager().getCitizens().stream()
                    .filter(c -> c.getId() != citizenData.getId())
                    .filter(c -> c.getJob() != null && c.getJob().isGuard())
                    .forEach(c -> LOGGER.info("  - Guard: {} (ID: {}, Job: {})",
                            c.getName(), c.getId(), c.getJob().getJobRegistryEntry().toString()));
        }

        if ("regular".equals(combatType) && activeRaid != null) {
            int originalGuardsKilled = activeRaid.getKilledGuardCount();
            int originalGuardCount = activeRaid.getOriginalGuardCount();

            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.info("VICTORY CHECK - Original guards killed: {}/{} (ID-based tracking)", originalGuardsKilled,
                        originalGuardCount);
            }

            if (originalGuardsKilled >= originalGuardCount && originalGuardCount > 0) {
                if (raidData != null) {
                    net.machiavelli.minecolonytax.raid.RaidManager.endActiveRaid(raidData,
                            "All guards eliminated - Raiders victorious!");
                    UUID raidingPlayerId = raidData.getRaider();
                    if (raidingPlayerId != null) {
                        ServerPlayer raidingPlayer = RaidManager.getServerPlayerById(raidingPlayerId);
                        if (raidingPlayer != null) {
                            raidingPlayer.sendSystemMessage(Component.literal("RAID VICTORY!")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                    .append(Component.literal("\nAll guards eliminated! Raid completed successfully!")
                                            .withStyle(ChatFormatting.GREEN)));
                        }
                    }
                    return;
                }
            }
        }

        if ("regular".equals(combatType)) {
            if (raidData != null) {
                net.machiavelli.minecolonytax.raid.RaidManager.updateRaidBossBar(raidData);
            }
        } else if ("war".equals(combatType)) {
            // War guard counting is handled SOLELY by WarEventHandler.onCitizenDeath, which fires on
            // the same LivingDeathEvent and dedups via the war's guardIDs set (each guard counted
            // exactly once, with Math.max(0) and side detection). Calling WarSystem.handleGuardKilled
            // here as well decremented remainingDefenderGuards a SECOND time for the same death, so
            // wars resolved as an attacker victory at ~half the real guard losses. Left as a no-op.
        }

        int guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilledCount(colony.getID());
        int totalGuards = CitizenMilitiaManager.getInstance().getTotalGuardsCount(colony.getID());
        int guardsRemaining = totalGuards - guardsKilled;

        if (killer != null && "regular".equals(combatType) && raidData != null
                && killer.getUUID().equals(raidData.getRaider())) {
            Component killMessage = Component.literal(defenderType.toUpperCase() + " ELIMINATED")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("\n"))
                    .append(Component.literal("Killed: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (" + defenderType + ")").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("Guards Progress: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(guardsKilled + "/" + totalGuards + " guards eliminated")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (" + guardsRemaining + " guards remaining)")
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("Tax steal progress: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.format("%.1f%%", currentStealPercentage * 100))
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

            killer.sendSystemMessage(killMessage);
        }

        String killedByText = killer != null ? " was killed by " + killer.getName().getString()
                : " died during the raid";
        Component defenseMessage = Component.literal("DEFENDER FALLEN")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + defenderType + ")").withStyle(ChatFormatting.RED))
                .append(Component.literal(killedByText).withStyle(ChatFormatting.RED))
                .append(Component.literal("\n").withStyle(ChatFormatting.RED))
                .append(Component.literal("Remaining defenders: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(remainingDefenders)).withStyle(ChatFormatting.YELLOW,
                        ChatFormatting.BOLD));

        IPermissions perms = colony.getPermissions();
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            if (killer == null || !uuid.equals(killer.getUUID())) {
                Rank rank = perms.getRank(uuid);
                if (rank != null && (rank.equals(perms.getRankOwner()) ||
                        rank.equals(perms.getRankOfficer()) ||
                        rank.equals(perms.getRankFriend()))) {
                    ServerPlayer defender = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                    if (defender != null) {
                        defender.sendSystemMessage(defenseMessage);
                    }
                }
            }
        });

        if ("claiming".equals(combatType) && killer != null) {
            int colonyTax = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(colony);
            int originalDefenders = defendersBefore;
            int taxAwarded = 0;
            if (originalDefenders > 0) {
                if (colonyTax > 0) {
                    double taxPerKill = currentStealPercentage / originalDefenders;
                    taxAwarded = Math.max(1, (int) (colonyTax * taxPerKill));
                } else {
                    int debtLimit = net.machiavelli.minecolonytax.TaxConfig.getDebtLimit();
                    if (debtLimit > 0) {
                        int taxStealPerGuard = net.machiavelli.minecolonytax.TaxConfig.getTaxStealPerGuard();
                        taxAwarded = Math.max(10, taxStealPerGuard); // Minimum 10 coins per kill
                    }
                }
            }

            if (taxAwarded > 0) {
                try {
                    net.machiavelli.minecolonytax.TaxManager.payTaxDebt(colony, -taxAwarded);
                    if (net.machiavelli.minecolonytax.TaxConfig.isSDMShopConversionEnabled()) {
                        if (net.machiavelli.minecolonytax.integration.SDMShopIntegration.isAvailable()) {
                            long currentBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration
                                    .getMoney(killer);
                            net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(killer,
                                    currentBalance + taxAwarded);

                            Component taxMessage = Component.literal("TAX STOLEN: ")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                    .append(Component.literal("+" + taxAwarded + " coins")
                                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                    .append(Component.literal(" (added to your account)")
                                            .withStyle(ChatFormatting.GREEN));
                            killer.sendSystemMessage(taxMessage);
                        }
                    } else {
                        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                                .getValue(
                                        new net.minecraft.resources.ResourceLocation(
                                                net.machiavelli.minecolonytax.TaxConfig.getCurrencyItemName()));
                        if (item != null) {
                            net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item,
                                    taxAwarded);
                            boolean added = killer.getInventory().add(itemStack);
                            if (!added) {
                                killer.drop(itemStack, false);
                            }

                            Component taxMessage = Component.literal("TAX STOLEN: ")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                    .append(Component
                                            .literal("+" + taxAwarded + " " + item.getDescription().getString())
                                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                    .append(Component.literal(added ? " (added to inventory)" : " (dropped nearby)")
                                            .withStyle(ChatFormatting.GREEN));
                            killer.sendSystemMessage(taxMessage);
                        }
                    }

                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("CLAIMING RAID TAX: {} stole {} tax from colony {} by killing {}",
                                killer.getName().getString(), taxAwarded, colony.getName(), citizenData.getName());
                    }

                } catch (Exception e) {
                    LOGGER.error("Failed to award tax for claiming raid kill", e);
                }
            } else {
                Component noTaxMessage = Component.literal("No tax to steal from this colony!")
                        .withStyle(ChatFormatting.YELLOW);
                killer.sendSystemMessage(noTaxMessage);
            }
        }
    }

    private static void handleMercenaryDeath(com.minecolonies.core.entity.mobs.EntityMercenary mercenary,
            DamageSource damageSource) {
        ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer player ? player : null;
        String killerName = killer != null ? killer.getName().getString() : "environmental damage";

        for (ColonyClaimingRaidManager.ClaimingRaidData raidData : ColonyClaimingRaidManager.getActiveClaimingRaidIds()
                .stream()
                .map(ColonyClaimingRaidManager::getClaimingRaid)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList())) {

            if (raidData.spawnedMercenaries.contains(mercenary)) {
                raidData.spawnedMercenaries.remove(mercenary);

                IColony colony = getColonyById(raidData.colonyId);
                if (colony != null) {
                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                        LOGGER.info("CLAIMING RAID - Mercenary killed by {} in colony {}", killerName,
                                colony.getName());
                    }

                    if (killer != null && killer.getUUID().equals(raidData.claimingPlayerId)) {
                        Component killMessage = Component.literal("MERCENARY ELIMINATED")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .append(Component.literal("\nKilled: Mercenary").withStyle(ChatFormatting.YELLOW));

                        killer.sendSystemMessage(killMessage);
                    }

                    checkClaimingRaidVictory(raidData, colony, killerName);
                }
                break;
            }
        }
    }

    private static void checkClaimingRaidVictory(ColonyClaimingRaidManager.ClaimingRaidData raidData, IColony colony,
            String killerName) {
        int remainingCitizens = 0;
        int remainingMercenaries = 0;

        for (Integer citizenId : raidData.hostileCitizens) {
            ICitizenData remainingCitizen = colony.getCitizenManager().getCivilian(citizenId);
            if (remainingCitizen != null && remainingCitizen.getEntity().isPresent() &&
                    remainingCitizen.getEntity().get().isAlive()) {
                remainingCitizens++;
            }
        }

        for (Entity mercenary : raidData.spawnedMercenaries) {
            if (mercenary.isAlive()) {
                remainingMercenaries++;
            }
        }

        int totalRemaining = remainingCitizens + remainingMercenaries;

        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            LOGGER.info("CLAIMING RAID PROGRESS - {} defenders remaining ({} citizens, {} mercenaries) in colony {}",
                    totalRemaining, remainingCitizens, remainingMercenaries, colony.getName());
        }

        if (totalRemaining == 0) {
            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                LOGGER.info("CLAIMING RAID VICTORY - All defenders eliminated in colony {} by {}",
                        colony.getName(), killerName);
            }

            ColonyClaimingRaidManager.completeClaimingRaid(raidData, true);
        }
    }

    private static IColony getColonyById(int colonyId) {
        try {
            return com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId,
                    null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void handleClaimingRaidDeath(AbstractEntityCitizen citizen, IColony colony, ServerPlayer killer) {
        ICitizenData citizenData = citizen.getCitizenData();
        if (citizenData == null) {
            LOGGER.warn("Claiming raid death - citizen data is null");
            return;
        }

        ColonyClaimingRaidManager.ClaimingRaidData raidData = ColonyClaimingRaidManager.getClaimingRaid(colony.getID());
        if (raidData == null) {
            LOGGER.warn("Claiming raid death detected but no raid data found for colony {}", colony.getID());
            return;
        }

        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
            LOGGER.info("Processing claiming raid death: {} (ID: {}) in colony {}",
                    citizenData.getName(), citizenData.getId(), colony.getName());
        }

        String killerName = killer != null ? killer.getName().getString() : "environmental damage";

        boolean wasHostileCitizen = raidData.hostileCitizens.contains(citizenData.getId());

        if (wasHostileCitizen) {
            raidData.hostileCitizens.remove(citizenData.getId());

            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                LOGGER.info("CLAIMING RAID - Hostile citizen {} killed by {} in colony {}",
                        citizenData.getName(), killerName, colony.getName());
            }

            if (killer != null && killer.getUUID().equals(raidData.claimingPlayerId)) {
                Component killMessage = Component.literal("DEFENDER ELIMINATED")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nKilled: ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (militia)").withStyle(ChatFormatting.GRAY));

                killer.sendSystemMessage(killMessage);
            }

            checkClaimingRaidVictory(raidData, colony, killerName);
            ColonyClaimingRaidManager.forceCheckVictoryCondition(colony.getID());
        } else {
            LOGGER.debug("CLAIMING RAID - Non-defender citizen {} died in colony {} (not part of hostile militia)",
                    citizenData.getName(), colony.getName());
        }
    }

    private static boolean isPlayerInWar(java.util.UUID playerUUID,
            net.machiavelli.minecolonytax.data.WarData warData) {
        if (playerUUID == null || warData == null) {
            return false;
        }
        if (warData.getAttackerLives().containsKey(playerUUID)) {
            return true;
        }
        if (warData.getDefenderLives().containsKey(playerUUID)) {
            return true;
        }
        return false;
    }

}
