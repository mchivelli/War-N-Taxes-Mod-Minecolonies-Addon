package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WarEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        System.out.println("[DEBUG] WarEventHandler.onPlayerDeath fired for " + player.getName().getString());
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) return;

        // Debug logging to confirm event trigger.
        System.out.println("[DEBUG] WarEventHandler.onPlayerDeath triggered confirmed for " + player.getName().getString());

        Map<UUID, Integer> lives = WarSystem.getLivesForPlayer(war, player);
        int remaining = lives.compute(player.getUUID(), (k, v) -> (v == null ? 0 : Math.max(0, v - 1)));
        System.out.println("[DEBUG] " + player.getName().getString() + " now has " + remaining + " lives.");

        if (remaining <= 0) {
            // Save inventory if not already saved.
            if (!WarSystem.WarInventoryHandler.hasSavedInventory(player)) {
                WarSystem.WarInventoryHandler.saveAndClearInventory(player);
            }
            war.getSpectators().add(player.getUUID());
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You have no lives remaining! You are now a spectator.")
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
            WarSystem.checkForVictory(war);
        }


        player.playSound(net.minecraft.sounds.SoundEvents.GHAST_DEATH, 1.0F, 1.0F);
        player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);

        WarSystem.updateBossBar(war);


    }

    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if this player is in an active war
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            return; // not in a war, do nothing
        }

        // If the war is still ongoing, re-apply the glow effect
        if (war.getStatus() == WarData.WarStatus.INWAR) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    999999, 0,
                    false, false
            ));
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
                // Announce for defenders.
                sendColonyMessage(citizenColony, Component.literal("Your guard has been killed!")
                        .withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)));
            } else if (citizenColony.equals(war.getAttackerColony())) {
                war.remainingAttackerGuards = Math.max(0, war.remainingAttackerGuards - 1);
                // Announce for attackers.
                sendColonyMessage(citizenColony, Component.literal("An enemy guard has been killed!")
                        .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)));
            } else {
                // Default announcement if we cannot determine affiliation.
                sendColonyMessage(citizenColony, Component.literal("A guard has been killed!")
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




    // Helper method to broadcast a message to all players in a colony:
    private static void sendColonyMessage(IColony colony, Component message) {
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
            if (p != null) {
                p.sendSystemMessage(message);
            }
        });
    }}