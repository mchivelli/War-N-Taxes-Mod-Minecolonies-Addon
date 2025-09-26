package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
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

/**
 * Tracks kills of guards and militia during raids for proper tax calculation.
 */
@Mod.EventBusSubscriber
public class RaidKillTracker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidKillTracker.class);
    
    // Event handler for tracking guard/militia kills during raids and wars
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityDeath(LivingDeathEvent event) {
        // Process only citizen deaths for efficiency
        
        // EFFICIENCY: Only process citizen deaths - skip all other entities immediately
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) {
            return;
        }
        
        // Get damage source and potential killer
        DamageSource damageSource = event.getSource();
        ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer player ? player : null;
        
        // Get the citizen's colony
        IColony colony = IColonyManager.getInstance().getColonyByWorld(citizen.getCitizenColonyHandler().getColonyId(), citizen.level());
        if (colony == null) {
            return;
        }
        
        // CRITICAL FIX: Check for ANY active raid on this colony (not just player-specific)
        boolean isColonyUnderRaid = RaidManager.isColonyUnderRaid(colony.getID());
        boolean isRegularRaid = false;
        boolean isClaimingRaid = false;
        boolean isWar = false;
        
        if (killer != null) {
            // Player-caused death - check all combat types
            ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(killer.getUUID());
            isRegularRaid = raidData != null && raidData.isActive() && raidData.getColony().getID() == colony.getID();
            isClaimingRaid = ColonyClaimingRaidManager.isPlayerInClaimingRaid(killer.getUUID(), colony.getID());
            
            net.machiavelli.minecolonytax.data.WarData warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colony.getID());
            isWar = warData != null && isPlayerInWar(killer.getUUID(), warData);
        } else if (isColonyUnderRaid) {
            // Environmental death during raid - treat as regular raid casualty
            isRegularRaid = true;
        }
        
        // EFFICIENCY: Skip processing if no active combat in this colony
        if (!isRegularRaid && !isClaimingRaid && !isWar) {
            return;
        }
        
        
        String combatType = isClaimingRaid ? "claiming" : (isWar ? "war" : "regular");
        
        // Get raid and war data for later use
        ActiveRaidData raidData = null;
        net.machiavelli.minecolonytax.data.WarData warData = null;
        
        if (isRegularRaid && killer != null) {
            raidData = RaidManager.getActiveRaidForPlayer(killer.getUUID());
        }
        if (isWar) {
            warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colony.getID());
        }
        
        // Get citizen data
        ICitizenData citizenData = citizen.getCitizenData();
        if (citizenData == null) {
            return;
        }
        
        // CRITICAL FIX: GUARDS HAVE PRIORITY OVER MILITIA for victory condition
        // Always check for guard status FIRST before militia
        boolean isGuard = false;
        boolean isMilitia = false;
        
        // Method 1: MOST RELIABLE - Check if citizen was in the original guard snapshot
        ActiveRaidData activeRaid = RaidManager.getActiveRaidForColony(colony.getID());
        if (activeRaid != null) {
            boolean wasInOriginalSnapshot = activeRaid.isOriginalGuard(citizenData.getId());
            if (wasInOriginalSnapshot) {
                isGuard = true;
                LOGGER.info("GUARD DETECTED via original snapshot - {} was an original guard (ID: {})", 
                    citizenData.getName(), citizenData.getId());
            }
        }
        
        // Method 2: Check if citizen still has guard job (works if job hasn't been cleared yet)
        com.minecolonies.api.colony.jobs.IJob<?> citizenJob = null;
        if (!isGuard) {
            citizenJob = citizenData.getJob();
            if (citizenJob != null && citizenJob.isGuard()) {
                isGuard = true;
                LOGGER.info("GUARD DETECTED via job check - {} is a guard", citizenData.getName());
            }
        }
        
        // Method 3: Check previous job if current job is null (job might have been cleared)
        if (!isGuard && citizenJob == null && citizenData.getWorkBuilding() != null) {
            // If job is null but they have a work building, check the building type
            var workBuilding = citizenData.getWorkBuilding();
            String buildingName = workBuilding.getBuildingDisplayName().toLowerCase();
            if (buildingName.contains("guard") || buildingName.contains("barracks") || 
                buildingName.contains("archery") || buildingName.contains("combat")) {
                isGuard = true;
                LOGGER.info("GUARD DETECTED via building (job cleared) - {} worked at {}", 
                    citizenData.getName(), workBuilding.getBuildingDisplayName());
            }
        }
        
        // Method 4: Check building assignment - auto-promoted guards might not have job set immediately
        if (!isGuard) {
            // Check if citizen is assigned to a guard building (more reliable for auto-promoted guards)
            if (citizenData.getWorkBuilding() != null) {
                var workBuilding = citizenData.getWorkBuilding();
                // Check if the building type indicates this is a guard position
                String buildingName = workBuilding.getBuildingDisplayName().toLowerCase();
                if (buildingName.contains("guard") || buildingName.contains("barracks") || 
                    buildingName.contains("archery") || buildingName.contains("combat")) {
                    isGuard = true;
                    LOGGER.info("GUARD DETECTED via building assignment - {} works at {}", 
                        citizenData.getName(), workBuilding.getBuildingDisplayName());
                }
            }
        }
        
        // Method 5: Check militia status ONLY if not already a guard
        if (!isGuard) {
            isMilitia = CitizenMilitiaManager.getInstance().isMilitiaMember(colony.getID(), citizenData.getId());
            if (isMilitia) {
                LOGGER.info("MILITIA DETECTED - {} is a militia member", citizenData.getName());
            }
        }
        
        // Method 6: For debugging, log detection status
        if (!isMilitia && !isGuard) {
            LOGGER.debug("Citizen {} detection check - Job: {}, Building: {}, trying count comparison", 
                citizenData.getName(), 
                citizenJob != null ? citizenJob.getJobRegistryEntry().toString() : "null",
                citizenData.getWorkBuilding() != null ? citizenData.getWorkBuilding().getBuildingDisplayName() : "none");
        }
        
        
        // Method 7: Count comparison fallback (LAST RESORT - improved)
        if (!isGuard && !isMilitia) {
            int defendersBefore = CitizenMilitiaManager.getInstance().getTotalDefenders(colony.getID());
            
            // Count guards more carefully - include the current citizen in "before" count
            long currentGuardsAlive = colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getId() != citizenData.getId()) // Exclude the one that just died
                .filter(c -> {
                    var job = c.getJob();
                    if (job != null && job.isGuard()) return true;
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
            
            // More aggressive detection: if total defenders went down, assume it was a guard
            if (defendersAfter < defendersBefore) {
                isGuard = true;
                LOGGER.info("GUARD DETECTED via count comparison - {} was a guard (count: {} -> {})", 
                    citizenData.getName(), defendersBefore, defendersAfter);
            } else if (defendersBefore > 0 && defendersAfter >= defendersBefore) {
                // Special case: defender count didn't decrease but we should have had one
                // This might indicate a timing issue - be more aggressive
                LOGGER.warn("COUNT MISMATCH - Expected defender count to decrease but didn't. Assuming {} was a guard", 
                    citizenData.getName());
                isGuard = true;
            }
        }
        
        // If neither guard nor militia, skip
        if (!isGuard && !isMilitia) {
            LOGGER.debug("KILL TRACKER SKIP - {} was not a defender", citizenData.getName());
            return;
        }
        
        // Get defender counts for tracking
        int defendersBefore = CitizenMilitiaManager.getInstance().getTotalDefenders(colony.getID());
        int defendersAfter = defendersBefore - 1;
        
        String defenderType = isMilitia ? "militia" : "guard";
        
        // Calculate actual remaining counts for logging
        int actualGuards = (int) colony.getCitizenManager().getCitizens().stream()
            .filter(c -> c.getId() != citizenData.getId()) // Exclude the dead citizen
            .filter(c -> c.getJob() != null && c.getJob().isGuard())
            .count();
        int actualMilitia = CitizenMilitiaManager.getInstance().getMilitiaMembers(colony.getID()).size();
        if (isMilitia) {
            actualMilitia--; // Subtract the killed militia member if not already removed
        }
        
        String killerName = killer != null ? killer.getName().getString() : "environmental damage";
        LOGGER.info("KILL DETECTED - {} {} killed by {} (defenders: {} -> {})", 
            defenderType, citizenData.getName(), killerName, defendersBefore, defendersAfter);
        
        // Record the kill and update defender count
        CitizenMilitiaManager.getInstance().recordDefenderDeath(colony, isGuard);
        CitizenMilitiaManager.getInstance().setTotalDefenders(colony.getID(), defendersAfter);
        
        // NEW: ID-based guard kill tracking against original snapshot
        if (activeRaid != null) {
            boolean wasOriginalGuard = activeRaid.markGuardKilled(citizenData.getId());
            if (wasOriginalGuard) {
                LOGGER.info("ID-TRACK: Marked original guard '{}' (ID {}) as killed. {}/{} killed.",
                    citizenData.getName(), citizenData.getId(), activeRaid.getKilledGuardCount(), activeRaid.getOriginalGuardCount());
            } else if (isGuard) {
                LOGGER.info("ID-TRACK: '{}' (ID {}) was a guard but not in original snapshot (auto-promotion). Ignoring for victory.",
                    citizenData.getName(), citizenData.getId());
            }
        }
        
        int remainingDefenders = defendersAfter;
        
        // Calculate current progress
        double currentStealPercentage = CitizenMilitiaManager.getInstance().calculateTaxPercentage(colony.getID());
        int defendersKilled = CitizenMilitiaManager.getInstance().getDefendersKilled(colony.getID());
        
        LOGGER.info("KILL PROGRESS - Guards: {}, Militia: {}, Total Remaining: {}, Kills: {}", 
            actualGuards, actualMilitia, remainingDefenders, defendersKilled);
        
        // ENHANCED DIAGNOSTICS: Log all remaining guards in colony for debugging
        if (isGuard) {
            LOGGER.info("REMAINING GUARDS in colony after kill:");
            colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getId() != citizenData.getId()) // Exclude just killed
                .filter(c -> c.getJob() != null && c.getJob().isGuard())
                .forEach(c -> LOGGER.info("  - Guard: {} (ID: {}, Job: {})", 
                    c.getName(), c.getId(), c.getJob().getJobRegistryEntry().toString()));
        }
        
        // CHECK FOR RAID VICTORY: End raid when all original guards have been killed
        // Use ID-based tracking to ensure we only count the original guards, not auto-promoted ones
        if ("regular".equals(combatType) && activeRaid != null) {
            int originalGuardsKilled = activeRaid.getKilledGuardCount();
            int originalGuardCount = activeRaid.getOriginalGuardCount();
            
            LOGGER.info("VICTORY CHECK - Original guards killed: {}/{} (ID-based tracking)", originalGuardsKilled, originalGuardCount);
            
            if (originalGuardsKilled >= originalGuardCount && originalGuardCount > 0) {
                if (raidData != null) {
                    // End the raid with victory
                    net.machiavelli.minecolonytax.raid.RaidManager.endActiveRaid(raidData, "All guards eliminated - Raiders victorious!");
                    
                    // Notify the raiding player of instant victory (if there is one)
                    UUID raidingPlayerId = raidData.getRaider();
                    if (raidingPlayerId != null) {
                        ServerPlayer raidingPlayer = RaidManager.getServerPlayerById(raidingPlayerId);
                        if (raidingPlayer != null) {
                            raidingPlayer.sendSystemMessage(Component.literal("🏆 RAID VICTORY! 🏆")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                .append(Component.literal("\nAll guards eliminated! Raid completed successfully!")
                                       .withStyle(ChatFormatting.GREEN)));
                        }
                    }
                    
                    return; // Exit early - raid is over
                }
            }
        }
        
        // Update progress tracking immediately
        if ("regular".equals(combatType)) {
            // Update raid boss bar
            if (raidData != null) {
                net.machiavelli.minecolonytax.raid.RaidManager.updateRaidBossBar(raidData);
            }
        } else if ("war".equals(combatType)) {
            // Update war progress
            if (warData != null) {
                boolean isDefenderGuard = warData.getColony().getID() == colony.getID();
                net.machiavelli.minecolonytax.WarSystem.handleGuardKilled(warData, isDefenderGuard);
            }
        }
        
        // Get guard-specific progress for victory condition
        int guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilledCount(colony.getID());
        int totalGuards = CitizenMilitiaManager.getInstance().getTotalGuardsCount(colony.getID());
        int guardsRemaining = totalGuards - guardsKilled;
        
        // Notify raider of progress (only if killed by the raiding player)
        if (killer != null && "regular".equals(combatType) && raidData != null && killer.getUUID().equals(raidData.getRaider())) {
            Component killMessage = Component.literal("⚔ " + defenderType.toUpperCase() + " ELIMINATED ⚔")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(Component.literal("Killed: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + defenderType + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Guards Progress: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(guardsKilled + "/" + totalGuards + " guards eliminated").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + guardsRemaining + " guards remaining)").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Tax steal progress: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format("%.1f%%", currentStealPercentage * 100)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            
            killer.sendSystemMessage(killMessage);
        }
        
        // Notify colony defenders  
        String killedByText = killer != null ? " was killed by " + killer.getName().getString() : " died during the raid";
        Component defenseMessage = Component.literal("🛡 DEFENDER FALLEN 🛡")
            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            .append(Component.literal("\n"))
            .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" (" + defenderType + ")").withStyle(ChatFormatting.RED))
            .append(Component.literal(killedByText).withStyle(ChatFormatting.RED))
            .append(Component.literal("\n").withStyle(ChatFormatting.RED))
            .append(Component.literal("Remaining defenders: ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(String.valueOf(remainingDefenders)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        
        // Send to colony members
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            if (killer == null || !uuid.equals(killer.getUUID())) { // Don't send to the raider (if there is one)
                ServerPlayer defender = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                if (defender != null) {
                    defender.sendSystemMessage(defenseMessage);
                }
            }
        });
        
        // Calculate and award tax for claiming raids (only if killed by a player)
        if ("claiming".equals(combatType) && killer != null) {
            int colonyTax = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(colony);
            int originalDefenders = defendersBefore; // Use the original defender count
            if (colonyTax > 0 && originalDefenders > 0) {
                double taxPerKill = currentStealPercentage / originalDefenders;
                int taxAwarded = Math.max(1, (int) (colonyTax * taxPerKill));
                
                try {
                    net.machiavelli.minecolonytax.TaxManager.payTaxDebt(colony, -taxAwarded);
                    
                    if (net.machiavelli.minecolonytax.TaxConfig.isSDMShopConversionEnabled()) {
                        if (net.machiavelli.minecolonytax.integration.SDMShopIntegration.isAvailable()) {
                            long currentBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getMoney(killer);
                            net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(killer, currentBalance + taxAwarded);
                        }
                    } else {
                        IColony killerColony = com.minecolonies.api.colony.IColonyManager.getInstance()
                                .getColonies(killer.level()).stream()
                                .filter(c -> c.getPermissions().getOwner().equals(killer.getUUID()) || 
                                           c.getPermissions().hasPermission(killer, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS))
                                .findFirst().orElse(null);
                        if (killerColony != null) {
                            net.machiavelli.minecolonytax.TaxManager.incrementTaxRevenue(killerColony, taxAwarded);
                        }
                    }
                    
                    Component taxMessage = Component.literal("💰 TAX REWARD: ")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("+" + taxAwarded + " coins")
                                   .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                    killer.sendSystemMessage(taxMessage);
                    
                } catch (Exception e) {
                    LOGGER.error("Failed to award tax for claiming raid kill", e);
                }
            }
        }
    }
    
    /**
     * Check if a player is participating in a war.
     * @param playerUUID The player's UUID
     * @param warData The war data
     * @return true if the player is in the war, false otherwise
     */
    private static boolean isPlayerInWar(java.util.UUID playerUUID, net.machiavelli.minecolonytax.data.WarData warData) {
        if (playerUUID == null || warData == null) {
            return false;
        }
        
        // Check if player is an attacker
        if (warData.getAttackerLives().containsKey(playerUUID)) {
            return true;
        }
        
        // Check if player is a defender
        if (warData.getDefenderLives().containsKey(playerUUID)) {
            return true;
        }
        
        return false;
    }
}
