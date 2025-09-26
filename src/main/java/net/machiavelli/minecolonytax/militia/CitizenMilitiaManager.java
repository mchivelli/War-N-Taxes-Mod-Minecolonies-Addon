package net.machiavelli.minecolonytax.militia;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the citizen militia system during raids.
 * Simplified version that only gives weapons to avoid crashes.
 */
public class CitizenMilitiaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CitizenMilitiaManager.class);
    
    // Track militia members per colony (simplified - no attribute tracking)
    private final Map<Integer, Set<Integer>> colonyMilitiaMembers = new ConcurrentHashMap<>();
    
    // Track guards killed during raids for tax stealing
    private final Map<Integer, Integer> guardsKilledPerColony = new ConcurrentHashMap<>();
    
    // Track militia killed during raids (separate from guards)
    private final Map<Integer, Integer> militiaKilledPerColony = new ConcurrentHashMap<>();
    
    // Track total defenders (guards + militia) per colony for tax calculation
    private final Map<Integer, Integer> totalDefendersPerColony = new ConcurrentHashMap<>();
    
    // Track total guards only (for raid victory condition)
    private final Map<Integer, Integer> totalGuardsPerColony = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static CitizenMilitiaManager instance;
    
    private CitizenMilitiaManager() {}
    
    public static CitizenMilitiaManager getInstance() {
        if (instance == null) {
            instance = new CitizenMilitiaManager();
        }
        return instance;
    }
    
    /**
     * Activates the militia for a colony during a raid.
     * Simplified version that only gives weapons to avoid crashes.
     * @param colony The colony under raid
     * @return Number of citizens converted to militia
     */
    public int activateMilitia(IColony colony) {
        if (!TaxConfig.ENABLE_CITIZEN_MILITIA.get()) {
            LOGGER.debug("Militia system disabled in config for colony {}", colony.getID());
            return 0;
        }
        
        int colonyId = colony.getID();
        
        // Clean up any existing militia data
        deactivateMilitia(colony);
        
        Set<Integer> militiaMembers = new HashSet<>();
        
        // Get eligible citizens
        List<ICitizenData> eligibleCitizens = getEligibleCitizens(colony);
        
        // Calculate how many to convert
        int targetMilitiaCount = (int) Math.ceil(eligibleCitizens.size() * TaxConfig.MILITIA_CONVERSION_PERCENTAGE.get());
        
        // Randomly select citizens to convert
        Collections.shuffle(eligibleCitizens);
        int converted = 0;
        
        for (ICitizenData citizen : eligibleCitizens) {
            if (converted >= targetMilitiaCount) {
                break;
            }
            
            // Apply militia equipment and combat AI
            applyMilitiaEquipment(citizen);
            enableMilitiaCombatAI(citizen);
            militiaMembers.add(citizen.getId());
            converted++;
            LOGGER.debug("Applied militia equipment and combat AI to citizen {} in colony {}", 
                citizen.getName(), colonyId);
        }
        
        // Count existing guards in the colony
        int existingGuards = countExistingGuards(colony);
        int totalDefenders = existingGuards + converted;
        
        // Store tracking data
        colonyMilitiaMembers.put(colonyId, militiaMembers);
        guardsKilledPerColony.put(colonyId, 0);
        militiaKilledPerColony.put(colonyId, 0);
        totalDefendersPerColony.put(colonyId, totalDefenders);
        totalGuardsPerColony.put(colonyId, existingGuards); // ONLY GUARDS for victory condition
        
        LOGGER.info("Activated militia for colony {}: {} citizens equipped with weapons", 
            colonyId, converted);
        LOGGER.info("DEFENDER COUNT DEBUG - Colony {}: {} existing guards + {} militia = {} total defenders", 
            colonyId, existingGuards, converted, totalDefenders);
        
        return converted;
    }
    
    /**
     * Deactivates the militia after a raid ends.
     * @param colony The colony whose raid ended
     */
    public void deactivateMilitia(IColony colony) {
        int colonyId = colony.getID();
        LOGGER.info("Deactivating militia for colony {}", colonyId);
        
        // Get current militia members
        Set<Integer> militiaMembers = colonyMilitiaMembers.getOrDefault(colonyId, new HashSet<>());
        
        // Remove equipment and combat AI from each militia member
        for (Integer citizenId : militiaMembers) {
            ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
            if (citizen != null) {
                // Remove wooden sword and combat AI
                removeMilitiaEquipment(citizen);
                disableMilitiaCombatAI(citizen);
                
                LOGGER.debug("Removed militia equipment and combat AI from citizen {} in colony {}", 
                    citizen.getName(), colonyId);
            }
        }
        
        // Clean up tracking data
        colonyMilitiaMembers.remove(colonyId);
        
        LOGGER.info("Deactivated militia for colony {}: {} members restored", 
            colonyId, militiaMembers.size());
    }
    
    /**
     * Counts existing guards in the colony.
     * @param colony The colony to check
     * @return Number of existing guards
     */
    private int countExistingGuards(IColony colony) {
        return (int) colony.getCitizenManager().getCitizens().stream()
            .filter(citizen -> {
                IJob<?> job = citizen.getJob();
                return job != null && job.isGuard();
            })
            .count();
    }
    
    /**
     * Gets eligible citizens for militia conversion.
     * @param colony The colony to check
     * @return List of eligible citizens
     */
    private List<ICitizenData> getEligibleCitizens(IColony colony) {
        List<ICitizenData> allCitizens = colony.getCitizenManager().getCitizens();
        LOGGER.debug("Total citizens in colony {}: {}", colony.getID(), allCitizens.size());
        
        return allCitizens.stream()
            .filter(citizen -> {
                // Check if citizen is eligible
                if (citizen.isChild()) {
                    LOGGER.debug("Citizen {} is a child - skipping", citizen.getName());
                    return false;
                }
                
                if (citizen.getEntity().isEmpty()) {
                    LOGGER.debug("Citizen {} has no entity - skipping", citizen.getName());
                    return false;
                }
                
                // Check level requirement (simplified check - citizen must not be a new citizen)
                if (citizen.getCitizenSkillHandler() == null) {
                    LOGGER.debug("Citizen {} has no skill handler - skipping", citizen.getName());
                    return false;
                }
                
                // Don't convert existing guards
                IJob<?> job = citizen.getJob();
                if (job != null && job.isGuard()) {
                    LOGGER.debug("Citizen {} is already a guard - skipping", citizen.getName());
                    return false;
                }
                
                // Only skip truly critical workers during raids
                if (job != null) {
                    String jobName = job.getJobRegistryEntry().getKey().getPath();
                    // Only skip deliverymen as they're critical for resource distribution during raids
                    if (jobName.equals("deliveryman")) {
                        LOGGER.debug("Citizen {} is critical worker ({}) - skipping", citizen.getName(), jobName);
                        return false;
                    }
                }
                
                LOGGER.debug("Citizen {} is eligible for militia conversion", citizen.getName());
                return true;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Apply militia equipment to eligible citizens.
     * @param citizen The citizen to equip
     */
    private void applyMilitiaEquipment(ICitizenData citizen) {
        if (citizen.getEntity().isEmpty()) {
            return;
        }
        
        try {
            AbstractEntityCitizen entity = citizen.getEntity().get();
            
            // Give wooden sword for combat
            ItemStack woodenSword = new ItemStack(Items.WOODEN_SWORD);
            entity.setItemSlot(EquipmentSlot.MAINHAND, woodenSword);
            
            LOGGER.info("Applied militia equipment to {} - wooden sword", citizen.getName());
            
        } catch (Exception e) {
            LOGGER.error("Failed to apply militia equipment to citizen {}", citizen.getName(), e);
        }
    }
    
    /**
     * Remove militia equipment (wooden sword) from citizen.
     * @param citizen the citizen to remove equipment from
     */
    private void removeMilitiaEquipment(ICitizenData citizen) {
        if (citizen.getEntity().isEmpty()) {
            return;
        }
        
        try {
            AbstractEntityCitizen entity = citizen.getEntity().get();
            ItemStack mainHand = entity.getMainHandItem();
            
            // Only remove wooden swords that we gave them
            if (mainHand.getItem() == Items.WOODEN_SWORD) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                LOGGER.debug("Removed wooden sword from former militia member {}", citizen.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to remove equipment from former militia member {}", citizen.getName(), e);
        }
    }
    
    /**
     * Enable combat AI for militia members to attack raiding players.
     * @param citizen The citizen to enable combat AI for
     */
    private void enableMilitiaCombatAI(ICitizenData citizen) {
        if (citizen.getEntity().isEmpty()) {
            return;
        }
        
        try {
            AbstractEntityCitizen entity = citizen.getEntity().get();
            
            // Clear existing AI goals that might interfere with combat
            entity.goalSelector.removeAllGoals((goal) -> true);
            entity.targetSelector.removeAllGoals((goal) -> true);
            
            // Add custom militia attack goal that doesn't require ATTACK_DAMAGE attribute
            entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
            
            // Add retaliation AI (highest priority - attack when attacked)
            entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
            
            // Add AI goal to attack players who are raiding the colony
            entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, ServerPlayer.class, 16, true, false, (target) -> {
                if (target instanceof ServerPlayer serverPlayer) {
                    boolean isRaiding = RaidManager.isPlayerCurrentlyRaiding(serverPlayer.getUUID(), citizen.getColony());
                    if (isRaiding) {
                        LOGGER.info("MILITIA TARGETING: {} is targeting raiding player: {}", citizen.getName(), serverPlayer.getName().getString());
                    }
                    return isRaiding;
                }
                return false;
            }));
            
            LOGGER.info("Enabled custom combat AI for militia member {} (bypasses ATTACK_DAMAGE requirement)", citizen.getName());
            
        } catch (Exception e) {
            LOGGER.error("Failed to enable combat AI for militia member {}", citizen.getName(), e);
        }
    }
    
    /**
     * Disable combat AI for militia members when reverting to civilians.
     * @param citizen The citizen to disable combat AI for
     */
    private void disableMilitiaCombatAI(ICitizenData citizen) {
        if (citizen.getEntity().isEmpty()) {
            return;
        }
        
        try {
            AbstractEntityCitizen entity = citizen.getEntity().get();
            
            // Clear all AI goals and restore original citizen AI
            entity.goalSelector.removeAllGoals((goal) -> true);
            entity.targetSelector.removeAllGoals((goal) -> true);
            
            // Restore original job AI
            if (citizen.getJob() != null) {
                entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
            }
            
            LOGGER.debug("Disabled combat AI and restored original AI for former militia member {}", citizen.getName());
            
        } catch (Exception e) {
            LOGGER.error("Failed to disable combat AI for former militia member {}", citizen.getName(), e);
        }
    }
    
    /**
     * Records a defender death during a raid (guard or militia).
     * @param colony The colony where the defender died
     * @param isGuard true if guard, false if militia
     */
    public void recordDefenderDeath(IColony colony, boolean isGuard) {
        int colonyId = colony.getID();
        String defenderType = isGuard ? "guard" : "militia";
        
        if (isGuard) {
            guardsKilledPerColony.compute(colonyId, (k, v) -> v == null ? 1 : v + 1);
            LOGGER.info("GUARD ELIMINATED - {} killed in colony {}, total guards eliminated: {}", 
                defenderType.toUpperCase(), colonyId, guardsKilledPerColony.get(colonyId));
        } else {
            militiaKilledPerColony.compute(colonyId, (k, v) -> v == null ? 1 : v + 1);
            LOGGER.info("MILITIA ELIMINATED - {} killed in colony {}, total militia eliminated: {}", 
                defenderType.toUpperCase(), colonyId, militiaKilledPerColony.get(colonyId));
        }
    }
    
    /**
     * Records a guard death during a raid.
     * @param colony The colony where the guard died
     * @deprecated Use recordDefenderDeath(colony, true) instead
     */
    @Deprecated
    public void recordGuardDeath(IColony colony) {
        recordDefenderDeath(colony, true);
    }
    
    /**
     * Gets the number of defenders killed for a colony (guards + militia).
     * @param colonyId The colony ID
     * @return Number of defenders killed
     */
    public int getDefendersKilled(int colonyId) {
        int guardsKilled = guardsKilledPerColony.getOrDefault(colonyId, 0);
        int militiaKilled = militiaKilledPerColony.getOrDefault(colonyId, 0);
        return guardsKilled + militiaKilled;
    }
    
    /**
     * Gets the number of GUARDS ONLY killed for a colony (for raid victory condition).
     * @param colonyId The colony ID
     * @return Number of guards killed (militia not included)
     */
    public int getGuardsKilledCount(int colonyId) {
        return guardsKilledPerColony.getOrDefault(colonyId, 0);
    }
    
    /**
     * Gets the total number of GUARDS ONLY for a colony (for raid victory condition).
     * @param colonyId The colony ID
     * @return Total number of guards (militia not included)
     */
    public int getTotalGuardsCount(int colonyId) {
        return totalGuardsPerColony.getOrDefault(colonyId, 0);
    }
    
    /**
     * Gets the number of guards killed for a colony.
     * @param colonyId The colony ID
     * @return Number of guards killed
     * @deprecated Use getGuardsKilledCount(colonyId) instead for guard-only count
     */
    @Deprecated
    public int getGuardsKilled(int colonyId) {
        return getDefendersKilled(colonyId);
    }
    
    /**
     * Gets the total number of defenders (guards + militia) for a colony.
     * @param colonyId The colony ID
     * @return Total number of defenders
     */
    public int getTotalDefenders(int colonyId) {
        return totalDefendersPerColony.getOrDefault(colonyId, 0);
    }
    
    /**
     * Calculates the tax percentage that should be applied based on defenders killed.
     * This distributes the max tax percentage across all defenders (guards + militia).
     * @param colonyId The colony ID
     * @return Tax percentage (0.0 - 1.0)
     */
    public double calculateTaxPercentage(int colonyId) {
        int defendersKilled = getDefendersKilled(colonyId);
        int totalDefenders = getTotalDefenders(colonyId);
        
        // FIX: Don't return 0 if all defenders are killed - this should return maximum percentage
        if (totalDefenders == 0) {
            // If no defenders were ever set, return 0
            return 0.0;
        }
        
        if (defendersKilled == 0) {
            return 0.0;
        }
        
        double maxTaxPercentage = TaxConfig.MAX_RAID_TAX_PERCENTAGE.get();
        double percentagePerDefender = maxTaxPercentage / totalDefenders;
        double finalPercentage = defendersKilled * percentagePerDefender;
        
        // Ensure we don't exceed the maximum
        return Math.min(finalPercentage, maxTaxPercentage);
    }
    
    /**
     * Calculates tax to steal based on defenders killed (guards + militia).
     * @param colony The colony being raided
     * @param currentTax The colony's current tax
     * @return Amount of tax to steal
     */
    public int calculateTaxToSteal(IColony colony, int currentTax) {
        if (!TaxConfig.TAX_STEAL_PER_GUARD_KILLED.get()) {
            // Use old system
            return 0;
        }
        
        int defendersKilled = getDefendersKilled(colony.getID());
        double stealPercentage = TaxConfig.TAX_STEAL_PERCENTAGE_PER_GUARD.get();
        
        int taxToSteal = (int) (currentTax * defendersKilled * stealPercentage);
        
        LOGGER.debug("Colony {} - Defenders killed: {}, Tax to steal: {} ({}% per defender)", 
            colony.getID(), defendersKilled, taxToSteal, stealPercentage * 100);
        
        return Math.min(taxToSteal, currentTax);
    }
    
    /**
     * Checks if a citizen is currently a militia member.
     * @param colonyId The colony ID
     * @param citizenId The citizen ID
     * @return true if the citizen is militia
     */
    public boolean isMilitiaMember(int colonyId, int citizenId) {
        Set<Integer> militia = colonyMilitiaMembers.get(colonyId);
        return militia != null && militia.contains(citizenId);
    }
    
    /**
     * Gets the number of active militia members in a colony.
     * @param colonyId The colony ID
     * @return Number of militia members
     */
    public int getMilitiaCount(int colonyId) {
        Set<Integer> militia = colonyMilitiaMembers.get(colonyId);
        return militia != null ? militia.size() : 0;
    }
    
    /**
     * Initialize militia system for a colony.
     * @param colonyId The colony ID
     */
    public void initializeColonyMilitia(int colonyId) {
        colonyMilitiaMembers.putIfAbsent(colonyId, ConcurrentHashMap.newKeySet());
        guardsKilledPerColony.putIfAbsent(colonyId, 0);
        totalDefendersPerColony.putIfAbsent(colonyId, 0);
        LOGGER.debug("Initialized militia system for colony {}", colonyId);
    }
    
    /**
     * Add a citizen as a militia member.
     * @param colonyId The colony ID
     * @param citizenId The citizen ID
     */
    public void addMilitiaMember(int colonyId, int citizenId) {
        colonyMilitiaMembers.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(citizenId);
        LOGGER.debug("Added citizen {} as militia member in colony {}", citizenId, colonyId);
    }
    
    /**
     * Set the total defender count for a colony.
     * @param colonyId The colony ID
     * @param count The total defender count
     */
    public void setTotalDefenders(int colonyId, int count) {
        totalDefendersPerColony.put(colonyId, count);
        LOGGER.debug("Set total defenders for colony {} to {}", colonyId, count);
    }
    
    /**
     * Set the total guard count for a colony (for raid victory condition).
     * @param colonyId The colony ID
     * @param count The total guard count
     */
    public void setTotalGuardsCount(int colonyId, int count) {
        totalGuardsPerColony.put(colonyId, count);
        LOGGER.debug("Set total guards for colony {} to {}", colonyId, count);
    }
    
    /**
     * Clear all militia data for a colony.
     * @param colonyId The colony ID
     */
    public void clearColonyMilitia(int colonyId) {
        colonyMilitiaMembers.remove(colonyId);
        guardsKilledPerColony.remove(colonyId);
        militiaKilledPerColony.remove(colonyId);
        totalDefendersPerColony.remove(colonyId);
        totalGuardsPerColony.remove(colonyId);
        LOGGER.debug("Cleared militia data for colony {}", colonyId);
    }
    
    /**
     * Get the militia members for a colony (for kill tracking).
     * @param colonyId The colony ID
     * @return Set of militia member citizen IDs
     */
    public Set<Integer> getMilitiaMembers(int colonyId) {
        return colonyMilitiaMembers.getOrDefault(colonyId, new HashSet<>());
    }
}
