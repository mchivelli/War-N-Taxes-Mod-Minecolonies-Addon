package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.raid.EntityRaidManager;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
import net.machiavelli.minecolonytax.event.RaidEndEvent;
import net.machiavelli.minecolonytax.event.RaidLoginNotifier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RaidManager {

    private static final Logger LOGGER = LogManager.getLogger(RaidManager.class);
    private static final Map<UUID, ActiveRaidData> activeRaids = new HashMap<>();
    private static final Map<UUID, Long> RAID_GRACE_PERIODS = new HashMap<>();


    private static final Set<Action> RAID_ACTIONS = EnumSet.of(
            Action.TOSS_ITEM,
            Action.PICKUP_ITEM,
            Action.ATTACK_CITIZEN,
            Action.GUARDS_ATTACK,
            Action.FILL_BUCKET,
            Action.SHOOT_ARROW,
            Action.RIGHTCLICK_BLOCK,
            Action.RIGHTCLICK_ENTITY,
            Action.HURT_CITIZEN,
            Action.ATTACK_ENTITY,
            Action.HURT_VISITOR,
            Action.THROW_POTION

    );


    public int handleRaid(CommandContext<CommandSourceStack> context) {
        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("raid.active.error").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!activeRaids.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("raid.already.active.error").withStyle(ChatFormatting.RED));
            return 0;
        }

        try {
            ServerPlayer raider = context.getSource().getPlayerOrException();
            // Prefer the colony where the player is the OWNER; fall back to any colony where the player is a member.
            IColony raiderColony = IColonyManager.getInstance().getColonies(raider.level()).stream()
                    .filter(c -> c.getPermissions().getOwner().equals(raider.getUUID()))
                    .findFirst()
                    .orElseGet(() -> IColonyManager.getInstance().getColonies(raider.level()).stream()
                            .filter(c -> c.getPermissions().getPlayers().containsKey(raider.getUUID()))
                            .findFirst()
                            .orElse(null));

            if (raiderColony == null) {
                context.getSource().sendFailure(Component.literal("You must belong to a colony to initiate a raid."));
                return 0;
            }

            int raiderGuardCount = WarSystem.countGuards(raiderColony);
            int minGuardsForRaid = TaxConfig.getMinGuardsToRaid();
            if (raiderGuardCount < minGuardsForRaid) {
                context.getSource().sendFailure(Component.literal("Your colony must have at least " + minGuardsForRaid +
                        " guards to initiate a raid. (Found: " + raiderGuardCount + ")"));
                return 0;
            }

            UUID raiderUUID = raider.getUUID();
            String colonyName = StringArgumentType.getString(context, "colony");
            Level level = context.getSource().getLevel();
            IColony colony = findColonyByName(colonyName, level); // Assumed to be made accessible or moved
            if (colony == null) {
                context.getSource().sendFailure(Component.literal("Colony not found!"));
                return 0;
            }

            if (!TaxConfig.ALLOW_OFFLINE_RAIDS.get()) {
                ServerPlayer owner = Objects.requireNonNull(colony.getWorld().getServer()).getPlayerList().getPlayer(colony.getPermissions().getOwner());
                if (owner == null) {
                    context.getSource().sendFailure(Component.literal("Colony owner is offline!"));
                    return 0;
                }
            }

            if (!isRaiderInColony(raider, colony)) {
                context.getSource().sendFailure(Component.literal("You must be inside the colony to start a raid."));
                return 0;
            }

            // Check RaidGuardProtection
            int targetGuards = WarSystem.countGuards(colony);
            if (TaxConfig.isRaidGuardProtectionEnabled()) {
                int targetGuardTowers = WarSystem.countGuardTowers(colony);
                int minGuardsRequired = TaxConfig.getMinGuardsToBeRaided();
                int minGuardTowersRequired = TaxConfig.getMinGuardTowersToBeRaided();

                if (targetGuards < minGuardsRequired) {
                    context.getSource().sendFailure(Component.translatable("raid.protection.guards", 
                            minGuardsRequired, targetGuards).withStyle(ChatFormatting.RED));
                    return 0;
                }

                if (targetGuardTowers < minGuardTowersRequired) {
                    context.getSource().sendFailure(Component.translatable("raid.protection.guard_towers", 
                            minGuardTowersRequired, targetGuardTowers).withStyle(ChatFormatting.RED));
                    return 0;
                }
            }

            Long graceEnd = RAID_GRACE_PERIODS.get(raiderUUID);
            if (graceEnd != null && System.currentTimeMillis() < graceEnd) {
                long remaining = graceEnd - System.currentTimeMillis();
                String timeLeft = String.format("%d minutes %d seconds",
                        TimeUnit.MILLISECONDS.toMinutes(remaining),
                        TimeUnit.MILLISECONDS.toSeconds(remaining) % 60);
                context.getSource().sendFailure(Component.literal("You must wait " + timeLeft + " before raiding again!"));
                return 0;
            }

            // Use the new formatted raid initiation message
            raider.sendSystemMessage(Component.translatable("raid.initiate.message", colony.getName())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            if (colony.getPermissions().getOwner().equals(raiderUUID)) {
                context.getSource().sendFailure(Component.literal("You cannot raid your own colony!"));
                return 0;
            }

            colony.getPermissions().setPlayerRank(raiderUUID, colony.getPermissions().getRankHostile(), level);
            ActiveRaidData raidData = new ActiveRaidData(raiderUUID, colony);
            
            // Initialize guard count for revenue calculation
            raidData.initializeGuardCount(targetGuards);
            
            raidData.setRaiderColony(raiderColony); // Store the raider's colony for later cleanup
            activeRaids.put(raiderUUID, raidData);
            
            // Apply GLOW effect to the raider for visibility to defenders
            applyGlowEffectToRaider(raider);
            
            // Enable raid interactions for both colonies involved
            RaidManager.setRaidInteractionPermissions(colony, true);
            RaidManager.setRaidInteractionPermissions(raiderColony, true);
            
            startRaidCountdown(raidData);
            // Send styled raid alert to all colony members
            sendColonyMessage(colony, Component.translatable("raid.alert.colony", colony.getName(), raider.getName().getString())
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            return 1;

        } catch (Exception e) {
            LOGGER.error("Raid command failed", e);
            context.getSource().sendFailure(Component.literal("Raid failed: " + e.getMessage()));
        }
        return 0;
    }

    public int stopRaidCommand(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            // Attempt to find a raid associated with this player.
            // This might need refinement if any player can stop any raid vs. only their own.
            // For now, let's assume an admin is stopping a raid, and we need a way to target it.
            // The original command didn't take a target, implying it stopped the player's *own* raid if they were a raider,
            // or it was an admin command that stopped *all* raids or a targeted one.
            // The plan says "Method for WarCommands.stopRaid() delegation." - WarCommands.stopRaid was an admin command.
            // It got raidData via activeRaids.get(player.getUUID()) which is problematic if admin is not the raider.
            // Let's assume for now it stops the first active raid if any, for simplicity, or requires a target.
            // This part needs clarification from original WarCommands.stopRaid logic.
            // For now, a simple stop for the *executing player's* raid if they are raiding.

            ActiveRaidData raidData = activeRaids.get(player.getUUID()); // Stops the raid initiated by the command executor
            if (raidData == null) {
                 // If not raiding, perhaps check all active raids if admin?
                 // For now, stick to "no active raid for this player"
                Optional<ActiveRaidData> anyRaid = activeRaids.values().stream().findFirst();
                if(anyRaid.isPresent()){
                    raidData = anyRaid.get();
                } else {
                    ctx.getSource().sendFailure(Component.literal("No active raid to stop."));
                    return 0;
                }
            }
            endRaid(raidData, "Stopped by operator");
            ctx.getSource().sendSuccess(() -> Component.literal("Raid stopped."), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Stop raid command failed", e);
            ctx.getSource().sendFailure(Component.literal("Failed to stop raid: " + e.getMessage()));
            return 0;
        }
    }


    public static ActiveRaidData getActiveRaidForPlayer(UUID playerId) {
        return activeRaids.get(playerId);
    }

    public static void endActiveRaid(ActiveRaidData raidData, String reason) {
        // This static method might be an issue if endRaid becomes non-static.
        // For now, assuming endRaid can be called statically or an instance is passed.
        // Let's make endRaid non-static and call it from here if needed, or this method becomes non-static.
        // For now, directly calling a new non-static endRaid.
        // This method seems redundant if endRaid is public.
        new RaidManager().endRaid(raidData, reason); // Or make endRaid static if it doesn't rely on instance state
    }

    public static Map<UUID, ActiveRaidData> getActiveRaids() {
        return activeRaids;
    }

    public static void handleRaiderKilled(ActiveRaidData raidData, ServerPlayer killer) {
        LOGGER.debug("handleRaiderKilled called for raider {}", raidData.getRaider());
        ServerPlayer raider = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raidData.getRaider());
        if (raider == null) {
            LOGGER.debug("Raider is offline, ending raid");
            RaidManager.endActiveRaid(raidData, "Raider killed while offline");
            return;
        }

        LOGGER.debug("Processing raid kill. Raider: {}, Killer: {}", raider.getName().getString(), killer.getName().getString());

        // Remove GLOW effect from raider immediately when killed
        removeGlowEffectFromRaider(raider);

        double penaltyPercentage = TaxConfig.RAID_PENALTY_PERCENTAGE.get();
        LOGGER.debug("Raid penalty percentage from config: {}", penaltyPercentage);

        String currencyName;
        if (TaxConfig.isSDMShopConversionEnabled()){
          currencyName = "$";
        } else {
            currencyName = TaxConfig.getCurrencyItemName();
            if (currencyName.contains(":")) {
                currencyName = currencyName.substring(currencyName.indexOf(":") + 1);
        }

        }

        int raidPenalty = 0;

        if (TaxConfig.isSDMShopConversionEnabled()) {
            if (SDMShopIntegration.isAvailable()) {
                long raiderBalance = SDMShopIntegration.getMoney(raider);
                LOGGER.debug("Raider SDMShop balance: {}", raiderBalance);
                int computedPenalty = (int) (raiderBalance * penaltyPercentage);
                raidPenalty = Math.max(1, computedPenalty);
                LOGGER.debug("Computed raid penalty (with minimum 1): {}", raidPenalty);
            } else {
                LOGGER.warn("SDMShop integration is enabled in config but SDMShop mod is not available");
                raidPenalty = 1; // Use minimum penalty
                LOGGER.debug("Using fallback raid penalty: {}", raidPenalty);
            }

            raidData.addToTotalTransferred(raidPenalty);

            if (raidPenalty > 0) {
                // Use the /sdmshop pay command to transfer funds directly from raider to killer
                // The command format is: /sdmshop pay <recipient> <amount>
                String payCmd = String.format("sdmshop pay %s %d", killer.getName().getString(), raidPenalty);
                LOGGER.debug("Executing command on behalf of raider: {}", payCmd);
                try {
                    // Execute the pay command using the raider's command source stack
                    // This ensures the command is processed as if the raider executed it
                    CommandSourceStack raiderSource = raider.createCommandSourceStack();
                    raider.getServer().getCommands().performPrefixedCommand(raiderSource, payCmd);
                    
                    LOGGER.debug("Successfully transferred {} from {} to {}", 
                        raidPenalty, raider.getName().getString(), killer.getName().getString());
                } catch (Exception e) {
                    LOGGER.error("Failed to transfer funds from raider to killer", e);
                    // Fallback to the old method if the pay command fails
                    try {
                        String removeCmd = String.format("sdmshop remove %s %d", raider.getName().getString(), raidPenalty);
                        String addCmd = String.format("sdmshop add %s %d", killer.getName().getString(), raidPenalty);
                        
                        LOGGER.debug("Falling back to remove/add method. Executing: {}", removeCmd);
                        raidData.getColony().getWorld().getServer().getCommands()
                                .performPrefixedCommand(
                                        raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                                        removeCmd
                                );
                        
                        LOGGER.debug("Executing: {}", addCmd);
                        raidData.getColony().getWorld().getServer().getCommands()
                                .performPrefixedCommand(
                                        raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                                        addCmd
                                );
                    } catch (Exception ex) {
                        LOGGER.error("Fallback method also failed", ex);
                    }
                }
            }
        } else {
            int baseTaxAmount = 250;
            if (TaxConfig.BUILDING_TAXES.containsKey("townhall")) {
                baseTaxAmount = (int)(TaxConfig.BUILDING_TAXES.get("townhall").get() * 5.0);
            }
            raidPenalty = (int)(baseTaxAmount * penaltyPercentage);
            raidPenalty = Math.max(100, raidPenalty);
            LOGGER.debug("Using direct item. Base amount: {}, Penalty: {}", baseTaxAmount, raidPenalty);
            raidData.addToTotalTransferred(raidPenalty);
            
            // Deduct from raider's colony tax balance
            IColony raiderColony = raidData.getRaiderColony();
            if (raiderColony != null) {
                LOGGER.debug("Deducting {} from raider's colony tax balance", raidPenalty);
                TaxManager.adjustTax(raiderColony, -raidPenalty);
            } else {
                LOGGER.error("Could not deduct raid penalty: raider's colony is null");
            }
            
            // Give items to the killer
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(TaxConfig.getCurrencyItemName()));
            if (item != null) {
                net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item, raidPenalty);
                boolean added = killer.getInventory().add(itemStack);
                if (!added) {
                    // If inventory is full, drop items near killer
                    killer.drop(itemStack, false);
                    LOGGER.debug("Killer's inventory was full, dropped {} items near them", raidPenalty);
                } else {
                    LOGGER.debug("Successfully gave {} items to killer", raidPenalty);
                }
            } else {
                // Fallback to give command if item not found in registry
                String giveCmd = String.format("give %s %s %d", killer.getName().getString(),
                        TaxConfig.getCurrencyItemName(), raidPenalty);
                LOGGER.debug("Executing command: {}", giveCmd);
                try {
                    raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(
                        raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                        giveCmd
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to give items to killer", e);
                }
            }
        }

        // Update war statistics for the killer
        PlayerWarDataManager.incrementPlayersKilledInWar(killer);
        PlayerWarDataManager.addAmountRaided(killer, raidPenalty);
        
        Component message = Component.literal("⚔ RAID DEFENDER VICTORY! ⚔")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true))
                .append(Component.literal("\n"))
                .append(Component.literal("Raider ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(raider.getName().getString()).withStyle(ChatFormatting.RED))
                .append(Component.literal(" was killed by ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(killer.getName().getString()).withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)))
                .append(Component.literal("!\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Raid ended with ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(raidPenalty + " " + currencyName).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)))
                .append(Component.literal(" transferred to the killer.").withStyle(ChatFormatting.GOLD));

        LOGGER.debug("Broadcasting raid kill message to server");
        raidData.getColony().getWorld().getServer().getPlayerList().broadcastSystemMessage(message, false);

        raidData.getColony().getPermissions().getPlayers().forEach((uuid, data) -> {
            ServerPlayer colonyMember = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (colonyMember != null) {
                String titleCmd = String.format("title %s title {\"text\":\"Raid Ended!\",\"color\":\"green\",\"bold\":true}", colonyMember.getName().getString());
                String subtitleCmd = String.format("title %s subtitle {\"text\":\"Raider killed by %s\",\"color\":\"gold\"}", colonyMember.getName().getString(), killer.getName().getString());
                try {
                    raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(raidData.getColony().getWorld().getServer().createCommandSourceStack(), titleCmd);
                    raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(raidData.getColony().getWorld().getServer().createCommandSourceStack(), subtitleCmd);
                } catch (Exception e) {
                    LOGGER.error("Failed to send title to colony member", e);
                }
            }
        });

        PlayerWarDataManager.incrementPlayersKilledInWar(killer);
        LOGGER.debug("Ending raid due to raider being killed");
        RaidManager.endActiveRaid(raidData, "Raider killed by " + killer.getName().getString() + ". Penalty: " + raidPenalty + " " + currencyName);
    }

    private void startRaidCountdown(ActiveRaidData raidData) {
        raidData.setTimerTask(new TimerTask() {
            @Override
            public void run() {
                if (raidData.getColony().getWorld() == null || raidData.getColony().getWorld().getServer() == null) {
                    LOGGER.warn("Raid countdown: Colony world or server is null, cancelling task for raid on colony {}", raidData.getColony().getID());
                    this.cancel();
                    endRaid(raidData, "Colony world/server became unavailable");
                    return;
                }

                // Ensure we stop ticking if raid became inactive
                raidData.getColony().getWorld().getServer().execute(() -> {
                    if (!raidData.isActive()) {
                        this.cancel();
                    }
                });
                if (!raidData.isActive()) {
                    return;
                }

                ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raidData.getRaider());
                if (raiderPlayer == null || !isRaiderInColony(raiderPlayer, raidData.getColony())) {
                    sendColonyMessage(raidData.getColony(), Component.literal("Raid stopped! Raider left the colony.").withStyle(ChatFormatting.RED));
                    endRaid(raidData, "Raider left colony");
                    this.cancel();
                    return;
                }
                if (raidData.getElapsedSeconds() >= getMaxRaidDurationSeconds()) {
                    endRaid(raidData, "Raid completed successfully");
                    this.cancel();
                    return;
                }

                raidData.setElapsedSeconds(raidData.getElapsedSeconds() + 1);
                updateRaidBossBar(raidData);

                if (!raidData.isWarningSent() && isRaiderInColony(raiderPlayer, raidData.getColony())) {
                    raidData.getColony().getPermissions().getPlayers().forEach((uuid, data) -> {
                        if (!uuid.equals(raidData.getRaider())) {
                            ServerPlayer p = (ServerPlayer) raidData.getColony().getWorld().getPlayerByUUID(uuid);
                            if (p != null) {
                                p.sendSystemMessage(Component.literal("Warning: Hostile player " + raiderPlayer.getName().getString() + " has entered the colony!")
                                        .withStyle(ChatFormatting.RED));
                            }
                        }
                    });
                    raidData.setWarningSent(true);
                }

                // Maintain GLOW effect on raider when inside colony, remove when outside
                if (raiderPlayer != null) {
                    if (isRaiderInColony(raiderPlayer, raidData.getColony())) {
                        applyGlowEffectToRaider(raiderPlayer);
                    } else {
                        // Remove GLOW effect if raider is outside colony boundaries
                        removeGlowEffectFromRaider(raiderPlayer);
                    }
                }

                // Tax revenue transfer removed from timer - will only happen after successful raid completion
            }
        });
        new Timer().scheduleAtFixedRate(raidData.getTimerTask(), 1000, 1000);
    }

    private void updateRaidBossBar(ActiveRaidData raidData) {
        if (raidData.getColony().getWorld() == null || raidData.getColony().getWorld().getServer() == null) {
            return;
        }
        raidData.getColony().getWorld().getServer().execute(() -> {
            if (!raidData.isActive()) return;
            ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raidData.getRaider());
            String status = (raiderPlayer == null || !isRaiderInColony(raiderPlayer, raidData.getColony())) ? "Leaving Colony!" : "Active";
            float progress = Math.min((float) raidData.getElapsedSeconds() / getMaxRaidDurationSeconds(), 1.0f);
            int remainingSeconds = Math.max(getMaxRaidDurationSeconds() - raidData.getElapsedSeconds(), 0);
            int intervalIndex = (raidData.getElapsedSeconds() / getTaxInterval());
            double percentage = intervalIndex < getTaxPercentages().length ? getTaxPercentages()[intervalIndex] : getTaxPercentages()[getTaxPercentages().length - 1];
            Component name = Component.literal(String.format("Raid: %s | Tax: %d%% | Time: %02d:%02d/%02d:%02d",
                    status, (int)(percentage * 100), remainingSeconds / 60, remainingSeconds % 60,
                    getMaxRaidDurationSeconds() / 60, getMaxRaidDurationSeconds() % 60));
            raidData.getBossEvent().setName(name);
            raidData.getBossEvent().setProgress(progress);
        });
    }

    private boolean isRaiderInColony(ServerPlayer raider, IColony colony) {
        if (raider == null || colony == null || colony.getWorld() == null) return false;
        BlockPos raiderPos = raider.blockPosition();
        return colony.isCoordInColony(colony.getWorld(), raiderPos);
    }

    private void endRaid(ActiveRaidData raidData, String reason) {
        if (!raidData.isActive()) return;
        raidData.setActive(false);
        
        // Remove GLOW effect from raider when raid ends
        if (raidData.getColony() != null && raidData.getColony().getWorld() != null && raidData.getColony().getWorld().getServer() != null) {
            ServerPlayer raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raidData.getRaider());
            if (raiderPlayer != null) {
                removeGlowEffectFromRaider(raiderPlayer);
            }
        }
        if (raidData.getBossEvent() != null) {
            raidData.getBossEvent().removeAllPlayers();
            raidData.getBossEvent().setVisible(false);
        }
        
        // Disable raid interactions for both colonies involved
        RaidManager.setRaidInteractionPermissions(raidData.getColony(), false);
        if (raidData.getRaiderColony() != null) {
            RaidManager.setRaidInteractionPermissions(raidData.getRaiderColony(), false);
        }
        RAID_GRACE_PERIODS.put(raidData.getRaider(), System.currentTimeMillis() + getRaidGraceDurationMs());
        RaidLoginNotifier.recordCompletedRaid(raidData); // Assumes ActiveRaidData can be used or adapted
        activeRaids.remove(raidData.getRaider());
        MutableComponent raidEndMsgToColony = Component.translatable("raid.end.title")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("\n").append(Component.translatable("raid.end.colony.body", reason).withStyle(ChatFormatting.YELLOW)))
            .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY));
        sendColonyMessage(raidData.getColony(), raidEndMsgToColony);

        ServerPlayer raiderPlayer = null;
        if (raidData.getColony() != null && raidData.getColony().getWorld() != null && raidData.getColony().getWorld().getServer() != null) {
            raiderPlayer = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raidData.getRaider());
        }

        if (raiderPlayer != null) {
            // Only transfer tax revenue if raid completed successfully (not interrupted)
            if (reason.equals("Raid completed successfully")) {
                performTaxRevenueTransfer(raidData, raiderPlayer);
            }
            
            MutableComponent raidEndMsgToRaider = Component.translatable("raid.end.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n").append(Component.translatable("raid.end.raider.body", 
                    raidData.getColony().getName(), reason).withStyle(ChatFormatting.YELLOW)))
                .append(Component.literal("\n----------------------------------------").withStyle(ChatFormatting.DARK_GRAY));
            raiderPlayer.sendSystemMessage(raidEndMsgToRaider);
            PlayerWarDataManager.incrementRaidedColonies(raiderPlayer);
            PlayerWarDataManager.addAmountRaided(raiderPlayer, raidData.getTotalTransferred());
            MinecraftForge.EVENT_BUS.post(new RaidEndEvent(raiderPlayer));
        }

        LOGGER.info("Raid ended: {}", reason);

        UUID raiderUUID = raidData.getRaider();
        String raiderNameFinal = raiderUUID.toString();
        if (raiderPlayer != null) {
            raiderNameFinal = raiderPlayer.getName().getString();
        } else if (raidData.getColony() != null && raidData.getColony().getWorld() != null && raidData.getColony().getWorld().getServer() != null) {
            // Attempt to get profile if player is offline
            ServerPlayer offlinePlayer = raidData.getColony().getWorld().getServer().getPlayerList().getPlayer(raiderUUID);
            if (offlinePlayer != null) { // Should be null if offline, but check anyway
                 raiderNameFinal = offlinePlayer.getName().getString();
            } else {
                // Potentially lookup GameProfile if really needed, for now UUID string is fallback
            }
        }


        String eventString = String.format("[RAID] Colony '%s' was raided by '%s'. Outcome: %s. Amount transferred: %d.",
                raidData.getColony().getName(),
                raiderNameFinal,
                reason,
                raidData.getTotalTransferred());

        HistoryManager.getColonyHistory(raidData.getColony().getID()).addEvent(eventString);
        HistoryManager.saveHistory();

        if (raidData.getTimerTask() != null) raidData.getTimerTask().cancel();
    }

    // Utility methods that might be shared or moved to a more central utility class
    private IColony findColonyByName(String name, Level level) {
        return IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public static void setRaidInteractionPermissions(IColony colony, boolean allowed)
    {

        if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
            return;

        IPermissions perms = colony.getPermissions();
        Rank hostile = perms.getRankHostile();
        for (Action a : RAID_ACTIONS)
        {
            perms.setPermission(hostile, a, allowed);
        }
    }

    private void sendColonyMessage(IColony colony, Component message) {
        if (colony == null || colony.getWorld() == null) return;
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
            if (p != null) p.sendSystemMessage(message);
        });
    }

    private int getMaxRaidDurationSeconds() {
        return TaxConfig.MAX_RAID_DURATION_MINUTES.get() * 60;
    }

    private int getTaxInterval() {
        return TaxConfig.RAID_TAX_INTERVAL_SECONDS.get();
    }

    private double[] getTaxPercentages() {
        return TaxConfig.RAID_TAX_PERCENTAGES.get().stream().mapToDouble(Double::doubleValue).toArray();
    }

    private long getRaidGraceDurationMs() {
        return TimeUnit.MINUTES.toMillis(TaxConfig.RAID_GRACE_PERIOD_MINUTES.get());
    }

    /**
     * Check if a colony is currently under any kind of raid (player or entity)
     */
    public static boolean isColonyUnderRaid(int colonyId) {
        // Check for active player raids
        for (ActiveRaidData raidData : activeRaids.values()) {
            if (raidData.getColony().getID() == colonyId && raidData.isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the active raid data for a colony (if any)
     */
    public static ActiveRaidData getActiveRaidForColony(int colonyId) {
        for (ActiveRaidData raidData : activeRaids.values()) {
            if (raidData.getColony().getID() == colonyId && raidData.isActive()) {
                return raidData;
            }
        }
        return null;
    }
    
    /**
     * Transfer tax revenue from raided colony to raider after successful raid completion
     */
    private void performTaxRevenueTransfer(ActiveRaidData raidData, ServerPlayer raiderPlayer) {
        // Only transfer revenue if guards have been killed
        if (!raidData.hasKilledAnyGuards()) {
            LOGGER.debug("No guards killed during raid, skipping revenue transfer for raid on {}", raidData.getColony().getName());
            return;
        }
        
        // Calculate final tax percentage based on raid duration and guard kills
        int raidDuration = raidData.getElapsedSeconds();
        int intervalsPassed = Math.min(raidDuration / getTaxInterval(), getTaxPercentages().length);
        double totalBasePercentage = 0;
        
        // Sum up all the percentages that would have been applied during the raid
        double[] taxPercentages = getTaxPercentages();
        for (int i = 0; i < intervalsPassed; i++) {
            totalBasePercentage += taxPercentages[Math.min(i, taxPercentages.length - 1)];
        }
        
        // Scale revenue based on percentage of guards killed
        double guardKillPercentage = raidData.getGuardKillPercentage();
        double finalPercentage = totalBasePercentage * guardKillPercentage;
        
        LOGGER.info("Final raid revenue calculation - Guards killed: {}/{} ({}%), Total base rate: {}%, Final rate: {}%", 
            raidData.getGuardsKilled(), raidData.getTotalGuards(), 
            guardKillPercentage * 100, totalBasePercentage * 100, finalPercentage * 100);
        
        // Calculate colony balance to take based on their stored tax
        int colonyBalance = TaxManager.getStoredTaxForColony(raidData.getColony());
        int amountToDeduct = (int) (colonyBalance * finalPercentage);
        
        // Create debt if there's no balance, up to debt limit
        if (amountToDeduct <= 0 && colonyBalance <= 0) {
            // If no tax stored, create debt up to the limit
            int debtLimit = TaxConfig.getDebtLimit();
            // Calculate a base amount to take when no funds are available (scaled by raid success)
            int baseAmount = (int)(100 * finalPercentage); // Base amount scaled by raid effectiveness
            // Check if we can add more debt
            int currentDebt = -colonyBalance; // Current debt is negative balance
            if (currentDebt < debtLimit) {
                // How much more debt can be added
                int availableDebt = debtLimit - currentDebt;
                // Take the smaller of base amount or available debt room
                amountToDeduct = Math.min(baseAmount, availableDebt);
                
                // Only proceed if we can actually add debt
                if (amountToDeduct > 0) {
                    // Creating debt involves reducing the balance further
                    TaxManager.payTaxDebt(raidData.getColony(), -amountToDeduct);
                    LOGGER.info("Raid completion: Creating debt of {} for colony {}", amountToDeduct, raidData.getColony().getName());
                }
            }
        } else if (amountToDeduct > 0) {
            // Deduct from existing balance
            TaxManager.payTaxDebt(raidData.getColony(), -amountToDeduct);
            LOGGER.info("Raid completion: Deducted {} from colony {} balance", amountToDeduct, raidData.getColony().getName());
        }
        
        // Only proceed with transfer if we have an amount to transfer
        if (amountToDeduct > 0) {
            // Transfer to the raider's account
            try {
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    if (SDMShopIntegration.isAvailable()) {
                        // Use SDMShop integration to add currency to player
                        long currentBalance = SDMShopIntegration.getMoney(raiderPlayer);
                        if (SDMShopIntegration.setMoney(raiderPlayer, currentBalance + amountToDeduct)) {
                            LOGGER.info("Raid completion: Added {} currency to player {} via SDMShop API (new balance: {})", 
                                    amountToDeduct, raiderPlayer.getName().getString(), currentBalance + amountToDeduct);
                        } else {
                            LOGGER.error("Failed to transfer {} currency to player {} via SDMShop API", 
                                    amountToDeduct, raiderPlayer.getName().getString());
                        }
                    } else {
                        LOGGER.warn("SDMShop integration is enabled but SDMShop mod is not available. Currency transfer skipped for player: {}", 
                                raiderPlayer.getName().getString());
                    }
                } else {
                    // Fallback to giving items if SDM not enabled
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(TaxConfig.getCurrencyItemName()));
                    if (item != null) {
                        net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item, amountToDeduct);
                        boolean added = raiderPlayer.getInventory().add(itemStack);
                        if (!added) {
                            // If inventory is full, drop items near raider
                            raiderPlayer.drop(itemStack, false);
                            LOGGER.info("Raid completion: Raider's inventory was full, dropped {} items near them", amountToDeduct);
                        } else {
                            LOGGER.info("Raid completion: Gave {} {} items to player {}", 
                                    amountToDeduct, TaxConfig.getCurrencyItemName(), raiderPlayer.getName().getString());
                        }
                    } else {
                        // Fallback to give command if item not found in registry
                        String itemName = TaxConfig.getCurrencyItemName();
                        String command = String.format("give %s %s %d", raiderPlayer.getName().getString(), itemName, amountToDeduct);
                        raidData.getColony().getWorld().getServer().getCommands().performPrefixedCommand(
                                raidData.getColony().getWorld().getServer().createCommandSourceStack(),
                                command);
                        LOGGER.info("Raid completion: Gave {} {} items to player {} (via command)", 
                                amountToDeduct, itemName, raiderPlayer.getName().getString());
                    }
                }
                
                // Update total transferred amount for statistics
                raidData.addToTotalTransferred(amountToDeduct);
                
                // Get proper currency name
                String currencyName;
                if (TaxConfig.isSDMShopConversionEnabled()) {
                    currencyName = "Coins";
                } else {
                    currencyName = TaxConfig.getCurrencyItemName();
                    if (currencyName.contains(":")) {
                        currencyName = currencyName.substring(currencyName.indexOf(":") + 1);
                    }
                }
                
                // Create message to send
                Component taxMessage = Component.literal("⚔ RAID COMPLETED! ⚔\n" + raidData.getColony().getName()
                        + " lost " + amountToDeduct
                        + " " + currencyName + " to raider "
                        + raiderPlayer.getName().getString()
                        + "!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                
                // Send message to all relevant players without duplication
                Set<ServerPlayer> playersToNotify = new HashSet<>();
                
                // Add target colony members
                raidData.getColony().getPermissions().getPlayers().forEach((uuid, data) -> {
                    ServerPlayer p = (ServerPlayer) raidData.getColony().getWorld().getPlayerByUUID(uuid);
                    if (p != null) playersToNotify.add(p);
                });
                
                // Add raider's colony members (if different colony)
                if (raidData.getRaiderColony() != null && !raidData.getRaiderColony().equals(raidData.getColony())) {
                    raidData.getRaiderColony().getPermissions().getPlayers().forEach((uuid, data) -> {
                        ServerPlayer p = (ServerPlayer) raidData.getRaiderColony().getWorld().getPlayerByUUID(uuid);
                        if (p != null) playersToNotify.add(p);
                    });
                }
                
                // Send message to all collected players
                for (ServerPlayer player : playersToNotify) {
                    player.sendSystemMessage(taxMessage);
                }
                
                // Comprehensive logging of the transaction
                LOGGER.info("Raid completion tax transfer: {} {} from {} to {}", 
                        amountToDeduct, currencyName, raidData.getColony().getName(), raiderPlayer.getName().getString());
            } catch (Exception e) {
                LOGGER.error("Error processing raid completion tax transfer: ", e);
            }
        }
    }
    
    /**
     * Apply GLOW effect to raider so defenders can easily locate them
     */
    private static void applyGlowEffectToRaider(ServerPlayer raider) {
        if (raider != null) {
            // Apply GLOW effect with a duration of 70 seconds (longer than tax interval to ensure continuity)
            // Amplifier 0 = level 1 effect, hideParticles = false, showIcon = true
            MobEffectInstance glowEffect = new MobEffectInstance(MobEffects.GLOWING, 70 * 20, 0, false, true, true);
            raider.addEffect(glowEffect);
            LOGGER.debug("Applied GLOW effect to raider: {}", raider.getName().getString());
        }
    }
    
    /**
     * Remove GLOW effect from raider when raid ends
     */
    public static void removeGlowEffectFromRaider(ServerPlayer raider) {
        if (raider != null) {
            raider.removeEffect(MobEffects.GLOWING);
            LOGGER.debug("Removed GLOW effect from raider: {}", raider.getName().getString());
        }
    }
    
    public static ActiveRaidData getActiveRaidByColony(int colonyId) {
        return activeRaids.values().stream()
                .filter(raid -> raid.getColony().getID() == colonyId)
                .findFirst()
                .orElse(null);
    }
}