package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.RaidData;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WarEventHandler {

    private static final Logger LOGGER = LogManager.getLogger(WarEventHandler.class);

    // Stores player inventories temporarily when keepInventoryOnLastLife is active.
    private static final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    // 1 = attacker, 2 = defender, 3 = raider
    private static final Map<UUID, Integer> disconnectedWarParticipants = new HashMap<>();

    public static Map<UUID, Integer> getDisconnectedWarParticipants() {
        return disconnectedWarParticipants;
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
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
        
        if (TaxConfig.isDebugLogging()) LOGGER.debug("WarEventHandler.onPlayerDeath fired for {}", player.getName().getString());
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) return;

        if (war.isJoinPhaseActive()) {
            if (TaxConfig.isDebugLogging()) LOGGER.debug("War still in join phase, not deducting lives for {}", player.getName().getString());
            return;
        }

        Map<UUID, Integer> lives = WarSystem.getLivesForPlayer(war, player);
        int currentLives = lives.getOrDefault(player.getUUID(), 0);

        if (currentLives == 1 && TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} is on last life — saving inventory for post-death restoration", player.getName().getString());

            ItemStack[] inventoryCopy = new ItemStack[player.getInventory().getContainerSize()];
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                inventoryCopy[i] = player.getInventory().getItem(i).copy();
            }
            savedInventories.put(player.getUUID(), inventoryCopy);
            war.getLastLifeInventoryPreservation().add(player.getUUID());
            event.getEntity().getTags().add("war_keep_inventory");
        }

        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            WarSystem.onPlayerKilledInWar(killer, player, war);

            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
                if (entry.getKey().equals(player.getUUID())) {
                    if (TaxConfig.isDebugLogging()) LOGGER.debug("Raider {} killed during raid", player.getName().getString());
                    RaidManager.handleRaiderKilled(entry.getValue(), killer);
                    break;
                }
            }
        }

        UUID playerUUID = player.getUUID();
        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("Player {} died during war. Lives before: {}", player.getName().getString(), lives.get(playerUUID));
        }

        if (lives.isEmpty()) {
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} not in war lives map, skipping lives processing", player.getName().getString());
            return;
        }

        int defaultLives = TaxConfig.PLAYER_LIVES_IN_WAR.get();
        if (!lives.containsKey(playerUUID)) {
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} missing from lives map, initializing with {}", player.getName().getString(), defaultLives);
            lives.put(playerUUID, defaultLives);
        }

        int remaining = lives.compute(playerUUID, (k, v) -> {
            if (v == null || v <= 0) {
                // Lives entry is invalid; reset to default minus one death.
                return defaultLives - 1;
            }
            return Math.max(0, v - 1);
        });
        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("{} now has {} lives after death", player.getName().getString(), remaining);
        }

        player.playSound(net.minecraft.sounds.SoundEvents.GHAST_DEATH, 1.0F, 1.0F);
        player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            return;
        }
        
        UUID playerUUID = player.getUUID();
        Map<UUID, Integer> lives = WarSystem.getLivesForPlayer(war, player);
        
        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("RESPAWN - Player {} respawning", player.getName().getString());
            LOGGER.debug("RESPAWN - Lives map: {}", lives);
            LOGGER.debug("RESPAWN - Player UUID: {}", playerUUID);
            LOGGER.debug("RESPAWN - Contains player key: {}", lives.containsKey(playerUUID));
        }

        if (lives.isEmpty()) {
            if (TaxConfig.isDebugLogging()) LOGGER.debug("RESPAWN - Player {} is not participating in any war, skipping respawn processing", player.getName().getString());
            return;
        }

        int currentLives = lives.getOrDefault(playerUUID, 0);

        if (TaxConfig.isDebugLogging()) LOGGER.debug("RESPAWN - Player {} respawned with {} lives remaining", player.getName().getString(), currentLives);
        
        if (war.getLastLifeInventoryPreservation().contains(playerUUID) && savedInventories.containsKey(playerUUID)) {
            ItemStack[] savedItems = savedInventories.get(playerUUID);
            player.getInventory().clearContent();
            for (int i = 0; i < savedItems.length && i < player.getInventory().getContainerSize(); i++) {
                if (savedItems[i] != null) {
                    player.getInventory().setItem(i, savedItems[i]);
                }
            }
            
            player.sendSystemMessage(Component.literal("Your inventory has been restored as configured.")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)));
            savedInventories.remove(playerUUID);
            war.getSpectators().add(playerUUID);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You have died on your last life! You are now a spectator until the war ends.")
                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)));
            
            if (TaxConfig.isNormalLogging()) LOGGER.info("Restored inventory for player {} after last life death", player.getName().getString());
            WarSystem.checkForVictory(war);
            WarSystem.updateBossBar(war);
        } else if (currentLives <= 0) {
            if (!TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
                if (!WarSystem.WarInventoryHandler.hasSavedInventory(player)) {
                    WarSystem.WarInventoryHandler.saveAndClearInventory(player);
                }
            }
            
            war.getSpectators().add(playerUUID);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You are now a spectator until the war ends.")
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
            
            WarSystem.checkForVictory(war);
            WarSystem.updateBossBar(war);
        } else if (currentLives == 1) {
            player.sendSystemMessage(Component.literal("WARNING: This is your LAST LIFE!")
                    .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)));
            
            // Send the info message about being on last life
            String keepInventoryMessage = TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get() 
                ? "§e§lWarning: §r§eYou are on your last life! If you die, you will keep your inventory and become a spectator."
                : "§e§lWarning: §r§eYou are on your last life! If you die, you will lose your inventory and become a spectator.";
            
            player.sendSystemMessage(Component.literal(keepInventoryMessage));
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 0.5F);
            if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
                player.sendSystemMessage(Component.literal("If you die, you will keep your inventory and can continue fighting as a spectator.")
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD)));
            }
            
            WarSystem.updateBossBar(war);
        } else {
            WarSystem.updateBossBar(war);
        }

        if (war.getStatus() == WarData.WarStatus.INWAR) {
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    999999, 0,
                    false, false
            ));
        }
        player.getTags().remove("war_keep_inventory");
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        UUID playerUUID = player.getUUID();
        
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war != null) {
            if (war.getAttackerLives().containsKey(playerUUID)) {
                disconnectedWarParticipants.put(playerUUID, 1);
            } else if (war.getDefenderLives().containsKey(playerUUID)) {
                disconnectedWarParticipants.put(playerUUID, 2);
            }
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} disconnected during active war", player.getName().getString());
        }

        ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(playerUUID);
        if (raidData != null) {
            disconnectedWarParticipants.put(playerUUID, 3);

            if (TaxConfig.isDebugLogging()) LOGGER.debug("Player {} disconnected during active raid", player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        net.machiavelli.minecolonytax.db.WarStatsDB.upsertPlayerLogin(
                player.getUUID(), player.getName().getString());

        // Deliver any spy mission rewards (intel book / map) that couldn't be handed over while
        // the player was offline at mission completion. Safe to call repeatedly — drains the queue.
        net.machiavelli.minecolonytax.espionage.SpyManager.deliverPendingRewards(player);

        UUID playerUUID = player.getUUID();
        
        if (disconnectedWarParticipants.containsKey(playerUUID)) {
            int warStatus = disconnectedWarParticipants.get(playerUUID);
            if (warStatus == 1 || warStatus == 2) {
                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                    boolean isAttacker = war.getAttackerLives().containsKey(playerUUID);
                    boolean isDefender = war.getDefenderLives().containsKey(playerUUID);
                    if (isAttacker || isDefender) {
                        if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                            war.alliesBossEvent.addPlayer(player);
                        } else if (war.bossEvent != null) {
                            war.bossEvent.addPlayer(player);
                        }
                        player.sendSystemMessage(Component.literal("You have reconnected to an active war.")
                                .withStyle(ChatFormatting.GOLD));
                        if (war.getSpectators().contains(playerUUID)) {
                            player.setGameMode(GameType.SPECTATOR);
                        }
                    }
                }
            } else if (warStatus == 3) {
                ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(playerUUID);
                if (raidData != null && raidData.getBossEvent() != null) {
                    raidData.getBossEvent().addPlayer(player);
                    player.sendSystemMessage(Component.literal("You have reconnected to an active raid.")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            disconnectedWarParticipants.remove(playerUUID);
        }

        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
            if (raidData.isActive() && raidData.getBossEvent() != null && raidData.getColony() != null) {
                if (raidData.getColony().getPermissions().getPlayers().containsKey(playerUUID)) {
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

            // Separate sets per side prevent collisions when both colonies share the same sequential citizen ID.
        boolean isDefenderGuard = citizenColony.equals(war.getColony())
                && war.getDefenderGuardIDs().remove(data.getId());
        boolean isAttackerGuard = !isDefenderGuard
                && citizenColony.equals(war.getAttackerColony())
                && war.getAttackerGuardIDs().remove(data.getId());

        if (isDefenderGuard || isAttackerGuard) {
            if (isDefenderGuard) {
                war.remainingDefenderGuards = Math.max(0, war.remainingDefenderGuards - 1);
                for (UUID uuid : war.getDefenderLives().keySet()) {
                    if (war.getColony().getWorld().getServer() != null) {
                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) {
                            p.sendSystemMessage(Component.literal("Your guard has been killed!")
                                    .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)));
                        }
                    }
                }
            } else {
                war.remainingAttackerGuards = Math.max(0, war.remainingAttackerGuards - 1);
                for (UUID uuid : war.getAttackerLives().keySet()) {
                    if (war.getColony().getWorld().getServer() != null) {
                        ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) {
                            p.sendSystemMessage(Component.literal("Your guard has been killed!")
                                    .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)));
                        }
                    }
                }
            }
            citizen.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            citizen.level().playSound(null, citizen.position().x(), citizen.position().y(), citizen.position().z(),
                    net.minecraft.sounds.SoundEvents.VILLAGER_HURT,
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.0F,
                    0.8F);

            WarSystem.updateBossBar(war);
            WarSystem.checkForVictory(war);
            if (TaxConfig.isDebugLogging()) LOGGER.debug("Attacker Guards: {} | Defender Guards: {}", war.getRemainingAttackerGuards(), war.getRemainingDefenderGuards());
        }
    }

    /**
     * PRIMARY levitation block: fires synchronously inside LivingEntity.addEffect()
     * via canBeAffected(), before the effect is ever applied.
     * DENY → canBeAffected() returns false → addEffect() returns false immediately.
     */
    /**
     * PRIMARY levitation block: fires synchronously inside LivingEntity.addEffect()
     * via canBeAffected(), before the effect is ever applied.
     *
     * Key: checks for Player (not ServerPlayer) because MineColonies' PlayerInteractEvent
     * handler has no isClientSide() guard — it calls addEffect() on the CLIENT-side
     * LocalPlayer entity as well, which is NOT a ServerPlayer. Without this fix the
     * client-side application bypasses the server-side DENY entirely.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevitationApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() != MobEffects.LEVITATION) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (TaxConfig.isSuppressColonyLevitation()
                || (event.getEntity() instanceof ServerPlayer sp && isActiveConflictParticipant(sp.getUUID()))) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * FALLBACK: fires AFTER addEffect() stores the effect (if Applicable was bypassed).
     * Removes it immediately so the player never actually floats.
     * Also handles any forceAddEffect() path that skips canBeAffected().
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevitationAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance().getEffect() != MobEffects.LEVITATION) return;
        if (!(event.getEntity() instanceof Player player)) return;

        boolean shouldStrip = TaxConfig.isSuppressColonyLevitation()
                || (player instanceof ServerPlayer sp && isActiveConflictParticipant(sp.getUUID()));
        if (shouldStrip) {
            player.removeEffect(MobEffects.LEVITATION);
        }
    }

    /**
     * FINAL fallback: strips levitation at tick end.
     * Runs on both client and server (TickEvent fires on both).
     * O(1) hasEffect check exits immediately when no levitation present.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (!player.hasEffect(MobEffects.LEVITATION)) return;

        boolean shouldStrip = TaxConfig.isSuppressColonyLevitation()
                || (player instanceof ServerPlayer sp && isActiveConflictParticipant(sp.getUUID()));
        if (shouldStrip) {
            player.removeEffect(MobEffects.LEVITATION);
        }
    }

    private static boolean isActiveConflictParticipant(UUID playerId) {
        // O(1) HashMap get — check raids first (most common conflict type)
        if (!RaidManager.getActiveRaids().isEmpty()
                && RaidManager.getActiveRaidForPlayer(playerId) != null) return true;
        // isEmpty guards avoid creating an iterator on empty maps
        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                if ((war.getAttackerLives() != null && war.getAttackerLives().containsKey(playerId))
                        || (war.getDefenderLives() != null && war.getDefenderLives().containsKey(playerId))) {
                    return true;
                }
            }
        }
        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
                if (besiege.besiegingPlayerUUID != null && besiege.besiegingPlayerUUID.equals(playerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void sendWarParticipantsMessage(WarData war, Component message) {
        for (UUID uuid : war.getAttackerLives().keySet()) {
            if (war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.sendSystemMessage(message);
                }
            }
        }
        for (UUID uuid : war.getDefenderLives().keySet()) {
            if (war.getColony().getWorld().getServer() != null) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.sendSystemMessage(message);
                }
            }
        }
    }

    private static void sendColonyMessage(IColony colony, Component message) {
        IPermissions perms = colony.getPermissions();
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            Rank rank = perms.getRank(uuid);
            if (rank != null && (rank.equals(perms.getRankOwner()) || 
                                rank.equals(perms.getRankOfficer()) || 
                                rank.equals(perms.getRankFriend()))) {
                ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
                if (p != null) {
                    p.sendSystemMessage(message);
                }
            }
        });
    }
}
