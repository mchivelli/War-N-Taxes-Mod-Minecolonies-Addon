package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.militia.CitizenMilitiaManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks kills of guards and militia during raids for proper tax calculation.
 */
@Mod.EventBusSubscriber
public class RaidKillTracker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidKillTracker.class);
    
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        // Only process citizen deaths
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) {
            return;
        }
        
        // Only process if killed by a player
        DamageSource damageSource = event.getSource();
        if (!(damageSource.getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        
        // Get the citizen's colony
        IColony colony = IColonyManager.getInstance().getColonyByWorld(citizen.getCitizenColonyHandler().getColonyId(), citizen.level());
        if (colony == null) {
            return;
        }
        
        // Check if there's an active raid on this colony by this player
        ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(killer.getUUID());
        if (raidData == null || !raidData.isActive() || raidData.getColony().getID() != colony.getID()) {
            LOGGER.debug("No active raid found for player {} in colony {}", killer.getName().getString(), colony.getName());
            return;
        }
        
        // Get citizen data
        ICitizenData citizenData = citizen.getCitizenData();
        if (citizenData == null) {
            return;
        }
        
        boolean isGuardOrMilitia = false;
        String defenderType = "citizen";
        
        // Check if this is a guard
        IJob<?> job = citizenData.getJob();
        if (job != null && job.isGuard()) {
            isGuardOrMilitia = true;
            defenderType = "guard";
            LOGGER.debug("Guard {} killed by raider {} in colony {}", 
                citizenData.getName(), killer.getName().getString(), colony.getName());
        }
        // Check if this is militia
        else if (CitizenMilitiaManager.getInstance().isMilitiaMember(colony.getID(), citizenData.getId())) {
            isGuardOrMilitia = true;
            defenderType = "militia";
            LOGGER.debug("Militia {} killed by raider {} in colony {}", 
                citizenData.getName(), killer.getName().getString(), colony.getName());
        }
        
        if (isGuardOrMilitia) {
            // Record the kill
            CitizenMilitiaManager.getInstance().recordGuardDeath(colony);
            
            // Calculate current steal percentage
            double currentStealPercentage = CitizenMilitiaManager.getInstance().calculateTaxPercentage(colony.getID());
            int guardsKilled = CitizenMilitiaManager.getInstance().getGuardsKilled(colony.getID());
            int totalDefenders = CitizenMilitiaManager.getInstance().getTotalDefenders(colony.getID());
            
            // Notify raider of progress
            Component killMessage = Component.literal("⚔ " + defenderType.toUpperCase() + " ELIMINATED ⚔")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(Component.literal("Killed: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + defenderType + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Progress: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(guardsKilled + "/" + totalDefenders).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" defenders eliminated").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Tax steal progress: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format("%.1f%%", currentStealPercentage * 100)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            
            killer.sendSystemMessage(killMessage);
            
            // Notify colony defenders
            Component defenseMessage = Component.literal("🛡 DEFENDER FALLEN 🛡")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(Component.literal(citizenData.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + defenderType + ") was killed by ").withStyle(ChatFormatting.RED))
                .append(Component.literal(killer.getName().getString()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal("\n").withStyle(ChatFormatting.RED))
                .append(Component.literal("Remaining defenders: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.valueOf(totalDefenders - guardsKilled)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            
            // Send to colony members
            colony.getPermissions().getPlayers().forEach((uuid, data) -> {
                if (!uuid.equals(killer.getUUID())) { // Don't send to the raider
                    ServerPlayer defender = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                    if (defender != null) {
                        defender.sendSystemMessage(defenseMessage);
                    }
                }
            });
            
            LOGGER.info("Raid progress - Colony: {}, Raider: {}, Killed: {} ({}), Progress: {}/{} defenders ({}%)", 
                colony.getName(), killer.getName().getString(), citizenData.getName(), defenderType,
                guardsKilled, totalDefenders, String.format("%.1f", currentStealPercentage * 100));
        }
    }
}
