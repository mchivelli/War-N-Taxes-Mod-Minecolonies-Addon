package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.occupation.OccupationManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.ColonyPlayer;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RaidLoginNotifier {

    // Keep history of completed (including aborted) raids
    private static final List<ActiveRaidData> completedRaids = Collections.synchronizedList(new ArrayList<>());

    // For each player, track which raids/wars/besieges/occupations they've already been notified about
    private static final Map<UUID, Set<UUID>> notifiedRaidsByPlayer = new HashMap<>();
    private static final Map<UUID, Set<Integer>> notifiedWarsByPlayer = new HashMap<>();
    private static final Map<UUID, Set<Integer>> notifiedBesiegesByPlayer = new HashMap<>();
    private static final Map<UUID, Set<Integer>> notifiedOccupationsByPlayer = new HashMap<>();

    /** Call this from raid-ending logic to record completed raids for offline notification. */
    public static void recordCompletedRaid(ActiveRaidData raid) {
        completedRaids.add(raid);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID playerUUID = player.getUUID();

        // --- Raid notifications ---
        notifiedRaidsByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
        Set<UUID> notifiedRaids = notifiedRaidsByPlayer.get(playerUUID);

        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
            if (isPlayerOfficerOrOwner(player, raid.getColony().getPermissions())) {
                notifyRaid(player, raid, true);
                UUID identifier = UUID.nameUUIDFromBytes((raid.getColony().getID() + ":" + raid.getRaider()).getBytes());
                notifiedRaids.add(identifier);
            } else {
                notifyRaidIfRelevant(player, raid, notifiedRaids, true);
            }
        }

        synchronized (completedRaids) {
            Iterator<ActiveRaidData> it = completedRaids.iterator();
            while (it.hasNext()) {
                ActiveRaidData raid = it.next();
                boolean wasRelevant = notifyRaidIfRelevant(player, raid, notifiedRaids, false);
                if (wasRelevant && playerUUID.equals(raid.getColony().getPermissions().getOwner())) {
                    it.remove();
                }
            }
        }

        // --- War notifications ---
        notifiedWarsByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
        Set<Integer> notifiedWars = notifiedWarsByPlayer.get(playerUUID);

        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            try {
                IColony defColony = war.getColony();
                IColony atkColony = war.getAttackerColony();

                boolean playerIsDefender = defColony != null
                        && isPlayerOfficerOrOwner(player, defColony.getPermissions());
                boolean playerIsAttacker = atkColony != null
                        && isPlayerOfficerOrOwner(player, atkColony.getPermissions());

                if (!playerIsDefender && !playerIsAttacker) continue;

                int warKey = playerIsDefender
                        ? (defColony != null ? defColony.getID() : -1)
                        : (atkColony != null ? atkColony.getID() : -1);

                if (warKey < 0 || notifiedWars.contains(warKey)) continue;

                String defName = defColony != null ? defColony.getName() : "unknown";
                String atkName = atkColony != null ? atkColony.getName() : "unknown";
                String msg = playerIsDefender
                        ? "Your colony " + defName + " is at war — attacker: " + atkName + "!"
                        : "Your colony " + atkName + " is waging war against " + defName + "!";

                player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                sendTitleNotification(player, "AT WAR!", defName + " vs " + atkName, "red");
                notifiedWars.add(warKey);
            } catch (Exception ignored) {}
        }

        // --- Besiege notifications ---
        if (TaxConfig.isBesiegeSystemEnabled()) {
            notifiedBesiegesByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
            Set<Integer> notifiedBesieges = notifiedBesiegesByPlayer.get(playerUUID);

            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
                try {
                    int colonyId = entry.getKey();
                    BesiegeManager.BesiegeRaidData raid = entry.getValue();

                    IColony besiegedColony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(colonyId);
                    if (besiegedColony == null) continue;

                    if (!isPlayerOfficerOrOwner(player, besiegedColony.getPermissions())) continue;
                    if (notifiedBesieges.contains(colonyId)) continue;

                    String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
                    player.sendSystemMessage(Component.literal(
                            "Your colony " + besiegedColony.getName() + " is being besieged by " + besiegerName + "!")
                            .withStyle(ChatFormatting.DARK_RED));
                    sendTitleNotification(player, "BESIEGED!", besiegedColony.getName(), "dark_red");
                    notifiedBesieges.add(colonyId);
                } catch (Exception ignored) {}
            }
        }

        // --- Occupation notifications ---
        if (TaxConfig.isOccupationSystemEnabled()) {
            notifiedOccupationsByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
            Set<Integer> notifiedOccupations = notifiedOccupationsByPlayer.get(playerUUID);

            for (Map.Entry<Integer, OccupationManager.OccupationData> entry
                    : OccupationManager.getActiveOccupations().entrySet()) {
                try {
                    OccupationManager.OccupationData occ = entry.getValue();
                    if (occ.isExpired()) continue;

                    boolean isOriginalOwner = occ.getOriginalOwnerUUID().equals(playerUUID);
                    boolean isOccupier = occ.getOccupierUUID().equals(playerUUID);
                    if (!isOriginalOwner && !isOccupier) continue;
                    if (notifiedOccupations.contains(occ.colonyId)) continue;

                    if (isOriginalOwner) {
                        player.sendSystemMessage(Component.literal(
                                "Your colony " + occ.colonyName + " is under occupation! Wage a reclamation war to take it back.")
                                .withStyle(ChatFormatting.RED));
                        sendTitleNotification(player, "OCCUPIED!", occ.colonyName, "red");
                    } else {
                        player.sendSystemMessage(Component.literal(
                                "You are occupying colony " + occ.colonyName + ".")
                                .withStyle(ChatFormatting.GOLD));
                    }
                    notifiedOccupations.add(occ.colonyId);
                } catch (Exception ignored) {}
            }
        }
    }

    private static boolean isPlayerOfficerOrOwner(ServerPlayer player, IPermissions perms) {
        UUID playerUUID = player.getUUID();
        boolean isOwner = playerUUID.equals(perms.getOwner());
        boolean isOfficer = perms.getPlayersByRank(perms.getRankOfficer())
                .stream()
                .anyMatch(cp -> cp.getID().equals(playerUUID));
        return isOwner || isOfficer;
    }

    private static void notifyRaid(ServerPlayer player, ActiveRaidData raid, boolean ongoing) {
        String raiderName = getRaiderName(raid.getRaider());
        String colonyName = raid.getColony().getName();

        if (ongoing) {
            player.sendSystemMessage(Component.literal("You are being Raided! " + colonyName + " by " + raiderName)
                    .withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(Component.literal(
                    "You have been Raided: " + colonyName + " by " + raiderName
                    + " - Check your tax balance with /wnt checktax for any defense rewards!")
                    .withStyle(ChatFormatting.GOLD));
        }

        sendTitleNotification(player, "RAID!", colonyName + " by " + raiderName, "red");
    }

    private static boolean notifyRaidIfRelevant(ServerPlayer player, ActiveRaidData raid, Set<UUID> notified, boolean ongoing) {
        IPermissions perms = raid.getColony().getPermissions();

        Set<UUID> notifySet = perms.getPlayersByRank(perms.getRankOfficer())
                .stream().map(ColonyPlayer::getID)
                .collect(Collectors.toSet());
        notifySet.add(perms.getOwner());

        UUID identifier = UUID.nameUUIDFromBytes((raid.getColony().getID() + ":" + raid.getRaider()).getBytes());

        if (notifySet.contains(player.getUUID()) && !notified.contains(identifier)) {
            notifyRaid(player, raid, ongoing);
            notified.add(identifier);
            return true;
        }
        return false;
    }

    private static void sendTitleNotification(ServerPlayer player, String title, String subtitle, String color) {
        if (player.getServer() == null) return;
        String playerName = player.getName().getString();
        String titleJson = "{\"text\":\"" + title + "\",\"color\":\"" + color + "\",\"bold\":true}";
        String subtitleJson = "{\"text\":\"" + escapeJson(subtitle) + "\",\"color\":\"yellow\"}";
        player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " title " + titleJson);
        player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                "title " + playerName + " subtitle " + subtitleJson);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String getRaiderName(UUID raiderUUID) {
        return getPlayerName(raiderUUID);
    }

    private static String getPlayerName(UUID uuid) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return "unknown";
        ServerPlayer p = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : "an offline player";
    }
}
