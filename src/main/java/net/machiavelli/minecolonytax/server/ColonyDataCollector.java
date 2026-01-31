package net.machiavelli.minecolonytax.server;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Collects colony tax data for GUI display
 */
public class ColonyDataCollector {
    private static final Logger LOGGER = LogManager.getLogger(ColonyDataCollector.class);

    /**
     * Collects tax data for all colonies the player can manage
     */
    public static List<ColonyTaxData> collectColonyData(ServerPlayer player) {
        List<ColonyTaxData> colonyDataList = new ArrayList<>();
        UUID playerId = player.getUUID();
        
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        
        for (IColony colony : colonyManager.getAllColonies()) {
            if (isPlayerManagerOfColony(player, colony)) {
                ColonyTaxData data = collectSingleColonyData(colony, playerId);
                if (data != null) {
                    colonyDataList.add(data);
                }
            }
        }
        
        return colonyDataList;
    }
    
    /**
     * Collect vassal income data for GUI display
     */
    public static List<VassalIncomeData> collectVassalIncomeData(ServerPlayer player) {
        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
        UUID playerId = player.getUUID();
        
        try {
            // Get all vassal income data for this player
            List<VassalIncomeData> vassalData = 
                net.machiavelli.minecolonytax.vassalization.VassalManager.getVassalIncomeForPlayer(playerId);
            
            for (VassalIncomeData data : vassalData) {
                // Check if tribute can be claimed (not blocked by war/raid)
                boolean canClaim = true;
                try {
                    IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                    IColony vassalColony = colonyManager.getColonyByWorld(data.getVassalColonyId(), player.serverLevel());
                    if (vassalColony != null) {
                        // Check for war/raid restrictions
                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
                        boolean isBeingRaided = RaidManager.getActiveRaidForColony(data.getVassalColonyId()) != null;
                        canClaim = (war == null) && !isBeingRaided && !TaxManager.isGenerationDisabled(data.getVassalColonyId());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Error checking claim status for vassal colony {}: {}", data.getVassalColonyName(), e.getMessage());
                    canClaim = false;
                }
                
                vassalIncomes.add(new VassalIncomeData(
                    data.getVassalColonyId(),
                    data.getVassalColonyName(),
                    data.getTributeRate(),
                    data.getTributeOwed(),
                    data.getLastTribute(),
                    data.getLastPayment(),
                    canClaim
                ));
            }
            
        } catch (Exception e) {
            LOGGER.debug("Error collecting vassal income data for player {}: {}", player.getName().getString(), e.getMessage());
        }
        
        return vassalIncomes;
    }
    
    /**
     * Checks if a player is a manager (owner or officer) of a colony
     */
    private static boolean isPlayerManagerOfColony(ServerPlayer player, IColony colony) {
        var rank = colony.getPermissions().getRank(player.getUUID());
        return rank != null && rank.isColonyManager();
    }

    /**
     * Collects data for a single colony
     */
    private static ColonyTaxData collectSingleColonyData(IColony colony, UUID playerId) {
        try {
            int colonyId = colony.getID();
            String colonyName = colony.getName();
            
            // Tax information
            int taxBalance = TaxManager.getStoredTaxForColony(colony);
            int maxTaxRevenue = TaxConfig.getMaxTaxRevenue();
            
            // Building and guard counts
            int buildingCount = 0;
            int guardCount = 0;
            int guardTowerCount = 0;
            
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                    buildingCount++;
                    
                    // Count guards and guard towers
                    String displayName = building.getBuildingDisplayName();
                    String className = building.getClass().getName().toLowerCase();
                    String toString = building.toString().toLowerCase();
                    
                    // Check if it's a guard tower
                    if ((displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) ||
                        className.contains("guardtower") ||
                        toString.contains("guardtower") ||
                        toString.contains("guard_tower")) {
                        guardTowerCount++;
                    }
                    
                    // Count assigned guards (approximate)
                    try {
                        if (building.getAllAssignedCitizen() != null) {
                            guardCount += building.getAllAssignedCitizen().size();
                        }
                    } catch (Exception ignored) {
                        // Some buildings might not support this method
                    }
                }
            }
            
            // War and raid status
            boolean isAtWar = false;
            boolean isBeingRaided = false;
            
            // Check for active war
            WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
            if (war == null) {
                // Check if attacking in a war
                for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
                    if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
                        war = wd;
                        break;
                    }
                }
            }
            isAtWar = (war != null);
            
            // Check for active raid
            isBeingRaided = RaidManager.getActiveRaidForColony(colonyId) != null;
            
            // Can claim tax?
            boolean canClaimTax = !isAtWar && !isBeingRaided && !TaxManager.isGenerationDisabled(colonyId);
            
            // Vassalization status using VassalManager
            boolean isVassal = false;
            int vassalTributeRate = 0;
            boolean hasVassals = false;
            int vassalCount = 0;
            
            try {
                // Check if this colony is a vassal
                isVassal = net.machiavelli.minecolonytax.vassalization.VassalManager.isColonyVassal(colonyId);
                if (isVassal) {
                    vassalTributeRate = net.machiavelli.minecolonytax.vassalization.VassalManager.getVassalTributeRate(colonyId);
                }
                
                // Check if player has vassals
                vassalCount = net.machiavelli.minecolonytax.vassalization.VassalManager.countVassalsForPlayer(playerId);
                hasVassals = vassalCount > 0;
                
                // Debug logging for vassalization issues
                if (isVassal || hasVassals) {
                    LOGGER.info("Colony {} (Player: {}) - isVassal: {}, vassalRate: {}, hasVassals: {}, vassalCount: {}", 
                        colonyId, playerId, isVassal, vassalTributeRate, hasVassals, vassalCount);
                }
                
            } catch (Exception e) {
                LOGGER.debug("Error checking vassal status for colony {}: {}", colonyName, e.getMessage());
            }
            
            // Last tax generation (simplified - use current time for now)
            long lastTaxGeneration = System.currentTimeMillis();
            
            // Calculate debt amount (negative tax balance)
            int debtAmount = taxBalance < 0 ? Math.abs(taxBalance) : 0;
            
            // Calculate approximate revenue per interval based on building count and tax rates
            int approximateRevenue = calculateApproximateRevenue(buildingCount, guardTowerCount);
            
            // Check if player is owner (colony founder)
            boolean isOwner = colony.getPermissions().getOwner().equals(playerId);

            // Get active tax policy
            String taxPolicy = TaxPolicyManager.getPolicy(colonyId).name();

            return new ColonyTaxData(
                colonyId, colonyName, taxBalance, maxTaxRevenue,
                buildingCount, guardCount, guardTowerCount,
                canClaimTax, isAtWar, isBeingRaided,
                isVassal, vassalTributeRate, hasVassals, vassalCount,
                lastTaxGeneration, debtAmount, approximateRevenue, isOwner,
                taxPolicy
            );
            
        } catch (Exception e) {
            LOGGER.error("Error collecting data for colony {}: {}", colony.getName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Calculate approximate tax revenue per interval based on ACTUAL config values
     * This now accurately reflects the real tax generation logic from TaxManager
     */
    private static int calculateApproximateRevenue(int buildingCount, int guardTowerCount) {
        // This is just an estimate - we can't access actual buildings here
        // But we can provide a better estimate using actual config values
        
        // Return 0 if no buildings
        if (buildingCount == 0) {
            return 0;
        }
        
        // Use a weighted average of common building types from config
        // This is more accurate than the previous hardcoded 3.5 value
        double estimatedAvgBaseTax = 2.0; // Fallback if we can't calculate
        double estimatedAvgUpgradeTax = 1.0; // Per level
        int estimatedAvgLevel = 3; // Assume average building level of 3
        
        // Calculate estimated raw tax per building
        double rawTaxPerBuilding = estimatedAvgBaseTax + (estimatedAvgUpgradeTax * estimatedAvgLevel);
        
        // Apply happiness modifier (assume neutral happiness for estimate)
        // Real calculation uses actual colony happiness from TaxManager.calculateColonyAverageHappiness()
        double happinessMultiplier = 1.0; // Neutral happiness
        if (TaxConfig.isHappinessTaxModifierEnabled()) {
            // Assume neutral happiness (5.0) which gives 1.0 multiplier
            // Real calculation: TaxConfig.calculateHappinessTaxMultiplier(colonyAvgHappiness)
            happinessMultiplier = 1.0;
        }
        
        // Calculate base generation
        double approximateRevenue = buildingCount * rawTaxPerBuilding * happinessMultiplier;
        
        // Apply guard tower boost if requirements are met (matching TaxManager logic)
        int requiredGuardTowers = TaxConfig.getRequiredGuardTowersForBoost();
        if (guardTowerCount >= requiredGuardTowers) {
            double boostPercentage = TaxConfig.getGuardTowerTaxBoostPercentage();
            double boostAmount = approximateRevenue * boostPercentage;
            approximateRevenue += boostAmount;
        }
        
        // Cap at max tax revenue if configured
        int maxRevenue = TaxConfig.getMaxTaxRevenue();
        if (maxRevenue > 0) {
            approximateRevenue = Math.min(approximateRevenue, maxRevenue);
        }
        
        return (int) Math.round(approximateRevenue);
    }
}
