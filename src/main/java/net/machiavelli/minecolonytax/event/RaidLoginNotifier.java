package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.ColonyPlayer;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RaidLoginNotifier {

    // Keep history of completed (including aborted) raids
    private static final List<ActiveRaidData> completedRaids = Collections.synchronizedList(new ArrayList<>());

    // For each player, track which raids they've already seen
    private static final Map<UUID, Set<UUID>> notifiedRaidsByPlayer = new HashMap<>();

    /** Call this from your raid‐ending logic in WarCommands (endRaid/stopRaid) */
    public static void recordCompletedRaid(ActiveRaidData raid) {
        completedRaids.add(raid);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID playerUUID = player.getUUID();

        // Ensure a set exists
        notifiedRaidsByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
        Set<UUID> notified = notifiedRaidsByPlayer.get(playerUUID);

        // First check active raids with higher priority - this is critical for officers logging in during a raid
        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
            // Force notification for active raids to ensure officers get the message
            if (isPlayerOfficerOrOwner(player, raid.getColony().getPermissions())) {
                notifyPlayer(player, raid, true);
                // Add to notified to prevent duplicate notifications
                UUID identifier = UUID.nameUUIDFromBytes((raid.getColony().getID() + ":" + raid.getRaider()).getBytes());
                notified.add(identifier);
            } else {
                notifyIfRelevant(player, raid, notified, true);
            }
        }

        // Now notify for all past (completed) raids
        synchronized (completedRaids) {
            Iterator<ActiveRaidData> it = completedRaids.iterator();
            while (it.hasNext()) {
                ActiveRaidData raid = it.next();
                boolean wasRelevant = notifyIfRelevant(player, raid, notified, false);
                // Once the owner has been notified, we can drop this history entry
                if (wasRelevant && raid.getColony().getPermissions().getOwner().equals(playerUUID)) {
                    it.remove();
                }
            }
        }
    }

    /**
     * @param ongoing true for active raids, false for completed
     * @return whether this player was in the notify‐set and got a notification
     */
    /**
     * Checks if a player is an officer or owner of a colony
     */
    private static boolean isPlayerOfficerOrOwner(ServerPlayer player, IPermissions perms) {
        UUID playerUUID = player.getUUID();
        boolean isOwner = perms.getOwner().equals(playerUUID);
        boolean isOfficer = perms.getPlayersByRank(perms.getRankOfficer())
                .stream()
                .anyMatch(cp -> cp.getID().equals(playerUUID));
        
        return isOwner || isOfficer;
    }
    
    /**
     * Directly notify a player about a raid without checking if they've been notified
     */
    private static void notifyPlayer(ServerPlayer player, ActiveRaidData raid, boolean ongoing) {
        String raiderName = getRaiderName(raid.getRaider());
        String colonyName = raid.getColony().getName();
        
        // Send chat message
        if (ongoing) {
            // "You are being Raided! <colony> by <player>"
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You are being Raided! " + colonyName + " by " + raiderName)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        } else {
            // Raid completed notification (defense rewards are now integrated into main tax balance)
            String baseMessage = "You have been Raided " + colonyName + " by " + raiderName + " - Check your tax balance with /wnt checktax for any defense rewards!";
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(baseMessage)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        
        // Send title and subtitle for increased visibility
        String headerJson = "{\"text\":\"RAID!\",\"color\":\"red\",\"bold\":true}";
        String subtitleJson = "{\"text\":\""
                + colonyName
                + " by "
                + raiderName
                + "\",\"color\":\"yellow\"}";

        if (player.getServer() != null) {
            String playerName = player.getName().getString();
            player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " title " + headerJson
            );
            player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " subtitle " + subtitleJson
            );
        }
    }
    
    private static boolean notifyIfRelevant(ServerPlayer player, ActiveRaidData raid, Set<UUID> notified, boolean ongoing) {
        IPermissions perms = raid.getColony().getPermissions();

        // Officers + owner
        Set<UUID> notifySet = perms.getPlayersByRank(perms.getRankOfficer())
                .stream().map(ColonyPlayer::getID)
                .collect(Collectors.toSet());
        notifySet.add(perms.getOwner());

        // Unique identifier for this raid instance
        UUID identifier = UUID.nameUUIDFromBytes((raid.getColony().getID() + ":" + raid.getRaider()).getBytes());

        if (notifySet.contains(player.getUUID()) && !notified.contains(identifier)) {
            notifyPlayer(player, raid, ongoing);
            notified.add(identifier);
            return true;
        }
        return false;
    }

    private static String getRaiderName(UUID raiderUUID) {
        ServerPlayer raider = ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayer(raiderUUID);
        return (raider != null) ? raider.getName().getString() : "an unknown raider";
    }
}
