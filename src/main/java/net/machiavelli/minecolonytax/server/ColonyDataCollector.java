package net.machiavelli.minecolonytax.server;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.gui.data.ColonySummary;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ColonyDataCollector {
    private static final Logger LOGGER = LogManager.getLogger(ColonyDataCollector.class);

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
     * Collects the colonies a player can target with a spy: every colony the player does NOT
     * manage. Only id + name are populated (the espionage UI only needs those to resolve a
     * target). No relationship gate — spying is open to any rival, matching the deploy handler
     * which already accepts any target colony id. Colonies the player owns/manages are excluded
     * (you don't spy on yourself), which is the fix that makes rival colonies — rather than only
     * the player's own — appear as spy targets.
     */
    public static List<ColonySummary> collectSpyTargetColonies(ServerPlayer player) {
        List<ColonySummary> targets = new ArrayList<>();
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();

        for (IColony colony : colonyManager.getAllColonies()) {
            if (colony == null || isPlayerManagerOfColony(player, colony)) {
                continue; // skip colonies the player owns/manages — you don't spy on yourself
            }
            targets.add(new ColonySummary(colony.getID(), colony.getName()));
        }
        targets.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return targets;
    }

    public static List<VassalIncomeData> collectVassalIncomeData(ServerPlayer player) {
        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
        UUID playerId = player.getUUID();

        try {
            List<VassalIncomeData> vassalData = net.machiavelli.minecolonytax.vassalization.VassalManager
                    .getVassalIncomeForPlayer(playerId);

            for (VassalIncomeData data : vassalData) {
                boolean canClaim = true;
                try {
                    IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                    IColony vassalColony = colonyManager.getColonyByWorld(data.getVassalColonyId(),
                            player.serverLevel());
                    if (vassalColony != null) {
                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
                        boolean isBeingRaided = RaidManager.getActiveRaidForColony(data.getVassalColonyId()) != null;
                        boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(data.getVassalColonyId());
                        boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(data.getVassalColonyId()) != null;
                        canClaim = (war == null) && !isBeingRaided && !isBesieged && !isOccupied
                                && !TaxManager.isGenerationDisabled(data.getVassalColonyId());
                    }
                } catch (Exception e) {
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.debug("Error checking claim status for vassal colony {}: {}", data.getVassalColonyName(),
                                e.getMessage());
                    }
                    canClaim = false;
                }

                vassalIncomes.add(new VassalIncomeData(
                        data.getVassalColonyId(),
                        data.getVassalColonyName(),
                        data.getTributeRate(),
                        data.getTributeOwed(),
                        data.getLastTribute(),
                        data.getLastPayment(),
                        canClaim,
                        VassalIncomeData.VassalKind.VASSAL));
            }

        } catch (Exception e) {
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.debug("Error collecting vassal income data for player {}: {}", player.getName().getString(),
                        e.getMessage());
            }
        }

        // Step 9: surface tax-occupied (Primary) and provisionally-claimed (Secondary)
        // colonies in the Vassals tab where this player is the occupier.
        //
        // Dedup against existing vassal rows so a colony that is both a vassal AND
        // tax-occupied (rare edge case) doesn't double-list. Build the seen-set
        // from the rows we just added above.
        Set<Integer> alreadyListedColonyIds = new HashSet<>();
        for (VassalIncomeData existing : vassalIncomes) {
            alreadyListedColonyIds.add(existing.getVassalColonyId());
        }
        try {
            for (net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData occ
                    : net.machiavelli.minecolonytax.occupation.OccupationManager.getActiveOccupations().values()) {
                if (occ == null || occ.isExpired()) continue;
                if (!playerId.equals(occ.getOccupierUUID())) continue;
                if (!alreadyListedColonyIds.add(occ.colonyId)) continue; // already listed as a vassal

                // Map mode → badge kind.
                VassalIncomeData.VassalKind kind =
                        occ.getMode() == net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationMode.TAX_ONLY
                        ? VassalIncomeData.VassalKind.TAX_OCCUPIED
                        : VassalIncomeData.VassalKind.PROVISIONAL;

                // Use the configured occupation tax percentage as the displayed "rate"
                // so the row is honest about what's being collected.
                int tributeRate = (int) Math.round(
                        net.machiavelli.minecolonytax.TaxConfig.getOccupationTaxPercentage() * 100);

                vassalIncomes.add(new VassalIncomeData(
                        occ.colonyId,
                        occ.colonyName,
                        tributeRate,
                        0,                              // no pending one-shot tribute model here
                        0,                              // last-tribute not tracked separately for occupations
                        occ.lastTaxCollectionTime,      // re-use for "last payment" label
                        true,                           // can collect via /wnt collectoccupation
                        kind));
            }
        } catch (Exception e) {
            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                LOGGER.debug("Error collecting occupation rows for vassal feed: {}", e.getMessage());
            }
        }

        return vassalIncomes;
    }

    private static boolean isPlayerManagerOfColony(ServerPlayer player, IColony colony) {
        var rank = colony.getPermissions().getRank(player.getUUID());
        return rank != null && rank.isColonyManager();
    }

    private static ColonyTaxData collectSingleColonyData(IColony colony, UUID playerId) {
        try {
            int colonyId = colony.getID();
            String colonyName = colony.getName();

            int taxBalance = TaxManager.getStoredTaxForColony(colony);
            int maxTaxRevenue = TaxConfig.getMaxTaxRevenue();

            int buildingCount = 0;
            int guardCount = 0;
            int guardTowerCount = 0;

            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                    buildingCount++;

                    String displayName = building.getBuildingDisplayName();
                    String className = building.getClass().getName().toLowerCase();
                    String toString = building.toString().toLowerCase();

                    if ((displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) ||
                            className.contains("guardtower") ||
                            toString.contains("guardtower") ||
                            toString.contains("guard_tower")) {
                        guardTowerCount++;
                    }

                    try {
                        if (building.getAllAssignedCitizen() != null) {
                            guardCount += building.getAllAssignedCitizen().size();
                        }
                    } catch (Exception ignored) {
                        // not all building types support getAllAssignedCitizen
                    }
                }
            }

            boolean isAtWar = false;
            boolean isBeingRaided = false;

            WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
            if (war == null) {
                // Also show at-war if this colony is the attacker
                for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
                    if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
                        war = wd;
                        break;
                    }
                }
            }
            isAtWar = (war != null);
            isBeingRaided = RaidManager.getActiveRaidForColony(colonyId) != null;
            boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId);
            boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(colonyId) != null;
            boolean canClaimTax = !isAtWar && !isBeingRaided && !isBesieged && !isOccupied && !TaxManager.isGenerationDisabled(colonyId);

            boolean isVassal = false;
            int vassalTributeRate = 0;
            boolean hasVassals = false;
            int vassalCount = 0;

            try {
                isVassal = net.machiavelli.minecolonytax.vassalization.VassalManager.isColonyVassal(colonyId);
                if (isVassal) {
                    vassalTributeRate = net.machiavelli.minecolonytax.vassalization.VassalManager
                            .getVassalTributeRate(colonyId);
                }

                vassalCount = net.machiavelli.minecolonytax.vassalization.VassalManager.countVassalsForPlayer(playerId);
                hasVassals = vassalCount > 0;

                if (isVassal || hasVassals) {
                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                        LOGGER.debug("Colony {} (Player: {}) - isVassal: {}, vassalRate: {}, hasVassals: {}, vassalCount: {}",
                                colonyId, playerId, isVassal, vassalTributeRate, hasVassals, vassalCount);
                    }
                }

            } catch (Exception e) {
                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
                    LOGGER.debug("Error checking vassal status for colony {}: {}", colonyName, e.getMessage());
                }
            }

            long lastTaxGeneration = System.currentTimeMillis();
            int debtAmount = taxBalance < 0 ? Math.abs(taxBalance) : 0;

            double colonyHappiness = TaxManager.calculateColonyAverageHappiness(colony);
            double happinessMultiplier = TaxConfig.calculateHappinessTaxMultiplier(colonyHappiness);
            int approximateRevenue = calculateApproximateRevenue(buildingCount, guardTowerCount, happinessMultiplier);

            boolean isOwner = colony.getPermissions().getOwner().equals(playerId);
            String taxPolicy = TaxPolicyManager.getPolicy(colonyId).name();

            return new ColonyTaxData(
                    colonyId, colonyName, taxBalance, maxTaxRevenue,
                    buildingCount, guardCount, guardTowerCount,
                    canClaimTax, isAtWar, isBeingRaided,
                    isVassal, vassalTributeRate, hasVassals, vassalCount,
                    lastTaxGeneration, debtAmount, approximateRevenue, isOwner,
                    taxPolicy, colonyHappiness, happinessMultiplier,
                    isBesieged, isOccupied);

        } catch (Exception e) {
            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
                LOGGER.error("Error collecting data for colony {}: {}", colony.getName(), e.getMessage());
            }
            return null;
        }
    }

    /**
     * Estimates tax revenue per interval.
     * Uses average building-level assumptions; not a substitute for TaxManager's real calculation.
     */
    private static int calculateApproximateRevenue(int buildingCount, int guardTowerCount, double happinessMultiplier) {
        if (buildingCount == 0) {
            return 0;
        }

        // Assumes average building level 3; this is a display estimate, not the real TaxManager calculation
        double rawTaxPerBuilding = 2.0 + (1.0 * 3);
        double approximateRevenue = buildingCount * rawTaxPerBuilding * happinessMultiplier;

        int requiredGuardTowers = TaxConfig.getRequiredGuardTowersForBoost();
        if (guardTowerCount >= requiredGuardTowers) {
            double boostPercentage = TaxConfig.getGuardTowerTaxBoostPercentage();
            double boostAmount = approximateRevenue * boostPercentage;
            approximateRevenue += boostAmount;
        }

        int maxRevenue = TaxConfig.getMaxTaxRevenue();
        if (maxRevenue > 0) {
            approximateRevenue = Math.min(approximateRevenue, maxRevenue);
        }

        return (int) Math.round(approximateRevenue);
    }
}
