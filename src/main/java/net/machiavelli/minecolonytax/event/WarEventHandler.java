package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.RaidData;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.commands.WarCommands;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WarEventHandler {

    // Store player inventories temporarily when keeping inventory on death
    private static final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    
    // Track players who have disconnected during active wars/raids for reconnection handling
    private static final Map<UUID, Integer> disconnectedWarParticipants = new HashMap<>();
    
    /**
     * Get the map of disconnected war participants.
     * Map values: 1 = attacker, 2 = defender, 3 = raider
     * 
     * @return Map of UUID to integer indicating player role
     */
    public static Map<UUID, Integer> getDisconnectedWarParticipants() {
        return disconnectedWarParticipants;
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Handle raider death during active raid
        ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(player.getUUID());
        if (raidData != null) {
            Entity src = event.getSource().getEntity();
            if (src instanceof ServerPlayer killer) {
                RaidManager.handleRaiderKilled(raidData, killer);
            } else {
                RaidManager.endActiveRaid(raidData, "Raider killed");
            }
            return;
        }
        System.out.println("[DEBUG] WarEventHandler.onPlayerDeath fired for " + player.getName().getString());
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) return;

        // Debug logging to confirm event trigger.
        System.out.println("[DEBUG] WarEventHandler.onPlayerDeath triggered confirmed for " + player.getName().getString());
        
        // Check if we should keep inventory on last life
        Map<UUID, Integer> lives = WarSystem.getLivesForPlayer(war, player);
        int currentLives = lives.getOrDefault(player.getUUID(), 0);
        
        // Notify player if they're on their last life
        if (currentLives == 1) {
            // Send the info message about being on last life
            String keepInventoryMessage = TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get() 
                ? "§e§lWarning: §r§eYou are on your last life! If you die, you will keep your inventory and become a spectator."
                : "§e§lWarning: §r§eYou are on your last life! If you die, you will lose your inventory and become a spectator.";
            
            player.sendSystemMessage(Component.literal(keepInventoryMessage));
            // Play an alert sound to make sure they notice
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 0.5F);
        }
        
        // If player is on their last life and keepInventoryOnLastLife is enabled, preserve inventory
        if (currentLives == 1 && TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
            System.out.println("[DEBUG] Player " + player.getName().getString() + " is on last life with keepInventoryOnLastLife enabled");
            
            // Save the player's inventory before they die
            ItemStack[] inventoryCopy = new ItemStack[player.getInventory().getContainerSize()];
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                inventoryCopy[i] = player.getInventory().getItem(i).copy();
            }
            savedInventories.put(player.getUUID(), inventoryCopy);
            
            // Add player to the list for tracking
            war.getLastLifeInventoryPreservation().add(player.getUUID());
            
            // We still want to reduce lives, so we'll handle that manually
            lives.compute(player.getUUID(), (k, v) -> (v == null ? 0 : Math.max(0, v - 1)));
            
            // Send message about inventory preservation
            player.sendSystemMessage(Component.literal("You have died on your last life! Your inventory will be restored when you respawn.")
                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)));
            
            // Set player to spectator mode
            war.getSpectators().add(player.getUUID());
            
            // Check for victory
            WarSystem.checkForVictory(war);
        }

        // Check if killed by another player and track it
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            // Call the new method to track player kills in war
            WarSystem.onPlayerKilledInWar(killer, player, war);
            
            // Also handle if this is a raider being killed during a raid
            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
                // Check if the dead player is an active raider
                if (entry.getKey().equals(player.getUUID())) {
                    // Call the raid kill handler
                    System.out.println("[DEBUG] Raider killed during raid: " + player.getName().getString());
                    RaidManager.handleRaiderKilled(entry.getValue(), killer);
                    break;
                }
            }
        }

        // IMPORTANT: We need to actually decrement the lives counter for normal deaths
        // This was fixed - lives weren't being reduced properly
        int remaining = lives.compute(player.getUUID(), (k, v) -> (v == null ? 0 : Math.max(0, v - 1)));
        System.out.println("[DEBUG] " + player.getName().getString() + " had life decremented and now has " + remaining + " lives.");

        if (remaining <= 0) {
            // We've already handled the last life case above, so this is for other cases
            // where the player has run out of lives
            
            // Save inventory if not already saved and keepInventoryOnLastLife is disabled
            if (!net.machiavelli.minecolonytax.TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
                if (!WarSystem.WarInventoryHandler.hasSavedInventory(player)) {
                    WarSystem.WarInventoryHandler.saveAndClearInventory(player);
                }
            }
            
            war.getSpectators().add(player.getUUID());
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You are now a spectator until the war ends.")
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
            WarSystem.checkForVictory(war);
        } else if (remaining == 1) {
            // This is their last life - notify them!
            player.sendSystemMessage(Component.literal("⚠ WARNING: This is your LAST LIFE! ⚠")
                    .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)));
            
            // If colony transfer is enabled, remind them they can keep fighting
            if (net.machiavelli.minecolonytax.TaxConfig.ENABLE_COLONY_TRANSFER.get() && 
                net.machiavelli.minecolonytax.TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
                player.sendSystemMessage(Component.literal("If you die, you will keep your inventory and can continue fighting as a spectator.")
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD)));
            }
        }


        player.playSound(net.minecraft.sounds.SoundEvents.GHAST_DEATH, 1.0F, 1.0F);
        player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);

        WarSystem.updateBossBar(war);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if this player is in an active war
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            return; // not in a war, do nothing
        }
        
        // Check if this player died on their last life and we need to restore their inventory
        UUID playerUUID = player.getUUID();
        if (war.getLastLifeInventoryPreservation().contains(playerUUID) && savedInventories.containsKey(playerUUID)) {
            // Restore the player's inventory
            ItemStack[] savedItems = savedInventories.get(playerUUID);
            player.getInventory().clearContent(); // Clear any default respawn items
            
            for (int i = 0; i < savedItems.length && i < player.getInventory().getContainerSize(); i++) {
                if (savedItems[i] != null) {
                    player.getInventory().setItem(i, savedItems[i]);
                }
            }
            
            // Send confirmation message
            player.sendSystemMessage(Component.literal("Your inventory has been restored as configured.")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)));
            
            // Clean up the saved inventory
            savedInventories.remove(playerUUID);
            
            // Set the player to spectator mode
            player.setGameMode(GameType.SPECTATOR);
            
            // Confirm to console
            System.out.println("[INFO] Restored inventory for player " + player.getName().getString() + " after last life death");
        }

        // If the war is still ongoing, re-apply the glow effect
        if (war.getStatus() == WarData.WarStatus.INWAR) {
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    999999, 0,
                    false, false
            ));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        UUID playerUUID = player.getUUID();
        
        // Check if player is in an active war
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war != null) {
            // Store player in our tracking map to restore boss bar on reconnect
            // This also indicates they're part of an active war
            if (war.getAttackerLives().containsKey(playerUUID)) {
                disconnectedWarParticipants.put(playerUUID, 1); // 1 indicates attacker side
            } else if (war.getDefenderLives().containsKey(playerUUID)) {
                disconnectedWarParticipants.put(playerUUID, 2); // 2 indicates defender side
            }
            
            // We don't need to do anything with boss bars here, as the ServerBossEvent 
            // automatically removes disconnected players
            
            System.out.println("[DEBUG] Player " + player.getName().getString() + " disconnected during active war");
        }
        
        // Check if player is in an active raid
        ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(playerUUID);
        if (raidData != null) {
            // Mark this player as participating in a raid when disconnected
            // 3 indicates active raid participation
            disconnectedWarParticipants.put(playerUUID, 3);
            
            // Do not end the raid when the raider disconnects
            // The raid will continue and the player can reconnect to it
            
            System.out.println("[DEBUG] Player " + player.getName().getString() + " disconnected during active raid");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        UUID playerUUID = player.getUUID();
        
        // Check if this player was in an active war when they disconnected
        if (disconnectedWarParticipants.containsKey(playerUUID)) {
            int warStatus = disconnectedWarParticipants.get(playerUUID);
            
            // Handle war reconnection
            if (warStatus == 1 || warStatus == 2) { // War attacker or defender
                // Find the war they were participating in
                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                    boolean isAttacker = war.getAttackerLives().containsKey(playerUUID);
                    boolean isDefender = war.getDefenderLives().containsKey(playerUUID);
                    
                    if (isAttacker || isDefender) {
                        // Re-add player to the appropriate boss bar
                        if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                            war.alliesBossEvent.addPlayer(player);
                        } else if (war.bossEvent != null) {
                            war.bossEvent.addPlayer(player);
                        }
                        
                        player.sendSystemMessage(Component.literal("You have reconnected to an active war.")
                                .withStyle(ChatFormatting.GOLD));
                        
                        // If they're spectating due to no lives left, ensure they're in spectator mode
                        if (war.getSpectators().contains(playerUUID)) {
                            player.setGameMode(GameType.SPECTATOR);
                        }
                    }
                }
            } 
            // Handle raid reconnection
            else if (warStatus == 3) { // Raid participant
                ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(playerUUID);
                if (raidData != null && raidData.getBossEvent() != null) {
                    // Re-add player to the raid boss bar
                    raidData.getBossEvent().addPlayer(player);
                    
                    player.sendSystemMessage(Component.literal("You have reconnected to an active raid.")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            
            // Remove from our tracking now that they've reconnected
            disconnectedWarParticipants.remove(playerUUID);
        }
        
        // Always check if player belongs to a colony that's being raided (defender logic)
        // This catches cases where a defender wasn't properly tracked or was offline when raid started
        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
            if (raidData.isActive() && raidData.getBossEvent() != null && raidData.getColony() != null) {
                // Check if player is a member of the colony being raided
                if (raidData.getColony().getPermissions().getPlayers().containsKey(playerUUID)) {
                    // Add them to the boss bar
                    raidData.getBossEvent().addPlayer(player);
                    player.sendSystemMessage(Component.literal("Your colony is currently being raided!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCitizenDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;
        var data = citizen.getCitizenData();
        if (data == null) return;
        IColony citizenColony = data.getColony();
        if (citizenColony == null) return;
        WarData war = WarSystem.ACTIVE_WARS.get(citizenColony.getID());
        if (war == null) return;

        // Only proceed if this citizen was registered as a guard.
        if (war.getGuardIDs().remove(data.getId())) {
            // Determine which side this guard is on.
            // We assume that if the guard's colony equals the attacked colony, then it's defender;
            // if it equals the attacker's colony (stored in WarData), then it's attacker.
            if (citizenColony.equals(war.getColony())) {
                war.remainingDefenderGuards = Math.max(0, war.remainingDefenderGuards - 1);
                // Announce only to defenders
                for (UUID uuid : war.getDefenderLives().keySet()) {
                    if (war.getColony().getWorld().getServer() != null) {
                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) {
                        p.sendSystemMessage(Component.literal("Your guard has been killed!")
                                .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)));
                        }
                    }
                }
            } else if (citizenColony.equals(war.getAttackerColony())) {
                war.remainingAttackerGuards = Math.max(0, war.remainingAttackerGuards - 1);
                // Announce only to attackers
                for (UUID uuid : war.getAttackerLives().keySet()) {
                    if (war.getColony().getWorld().getServer() != null) {
                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) {
                        p.sendSystemMessage(Component.literal("Your guard has been killed!")
                                .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)));
                        }
                    }
                }
            } else {
                // Default announcement if we cannot determine affiliation - only to war participants
                sendWarParticipantsMessage(war, Component.literal("A guard has been killed!")
                        .withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(true)));
            }
            // Remove glowing effect from the citizen.
            citizen.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            WarSystem.updateBossBar(war);
            WarSystem.checkForVictory(war);
            System.out.println("[DEBUG] Attacker Guards: " + war.getRemainingAttackerGuards() +
                    " | Defender Guards: " + war.getRemainingDefenderGuards());
        }
    }

    // Helper method to broadcast a message only to players participating in a war
    private static void sendWarParticipantsMessage(WarData war, Component message) {
        // Send to attackers
        for (UUID uuid : war.getAttackerLives().keySet()) {
            if (war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.sendSystemMessage(message);
                }
            }
        }
        
        // Send to defenders
        for (UUID uuid : war.getDefenderLives().keySet()) {
            if (war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.sendSystemMessage(message);
                }
            }
        }
    }

    // Helper method to broadcast a message to all players in a colony:
    private static void sendColonyMessage(IColony colony, Component message) {
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
            if (p != null) {
                p.sendSystemMessage(message);
            }
        });
    }
}