package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.fields.IField;
import com.minecolonies.api.colony.fields.modules.IFieldModule;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.api.colony.modules.IModuleContainer;
import com.minecolonies.api.colony.modules.ModuleContainerUtils;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.minecolonies.api.colony.modules.IModuleContainer;

import java.io.ObjectInputFilter;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.managers.interfaces.IReproductionManager;
import com.minecolonies.api.util.BlockPosUtil;

import static com.mojang.text2speech.Narrator.LOGGER;

/*
TODO
remove Set to Hostile feature, buggy
 */


@Mod.EventBusSubscriber
public class WarCommands {
    private static final Logger LOGGER = LogManager.getLogger(WarCommands.class);
    private static final Map<UUID, RaidData> activeRaids = new HashMap<>();
    private static final Map<IColony, WarData> activeWars = new HashMap<>();
    private static final Map<UUID, Long> ATTACKER_GRACE_PERIODS = new HashMap<>();
    private static final Map<UUID, Long> RAID_GRACE_PERIODS = new HashMap<>();
    private static final Map<IColony, WarData> ACTIVE_WARS = new HashMap<>();

    private static final Set<ResourceLocation> GUARD_JOBS = Set.of(
            new ResourceLocation("minecolonies", "guard"),
            new ResourceLocation("minecolonies", "ranger"),
            new ResourceLocation("minecolonies", "knight")
    );



    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raid")
                .then(Commands.argument("colony", StringArgumentType.string())
                        .executes(context -> handleRaid(context))
                ));

        dispatcher.register(Commands.literal("wagewar")
                .then(Commands.argument("colony", StringArgumentType.string())
                        .executes(context -> handleWageWar(context))
                ));

        // Register war sub-commands
        dispatcher.register(Commands.literal("war")
                .then(Commands.literal("accept")
                        .then(Commands.argument("attacker", StringArgumentType.string())
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                        .executes(context -> handleWarResponse(context, true))
                                )
                        )
                )
                .then(Commands.literal("decline")
                        .then(Commands.argument("attacker", StringArgumentType.string())
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                        .executes(context -> handleWarResponse(context, false))
                                )
                        )
                )
        );
    }

    private static int handleRaid(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer raider = context.getSource().getPlayerOrException();
            UUID raiderUUID = raider.getUUID();
            String colonyName = StringArgumentType.getString(context, "colony");
            Level level = context.getSource().getLevel();



            // Check attacker grace period
            Long graceEnd = RAID_GRACE_PERIODS.get(raiderUUID);
            if (graceEnd != null && System.currentTimeMillis() < graceEnd) {
                long remaining = graceEnd - System.currentTimeMillis();
                String timeLeft = String.format("%d minutes %d seconds",
                        TimeUnit.MILLISECONDS.toMinutes(remaining),
                        TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                );
                context.getSource().sendFailure(Component.literal("You must wait " + timeLeft + " before raiding again!"));
                return 0;
            }

            IColony colony = findColonyByName(colonyName, level);
            if (colony == null) {
                context.getSource().sendFailure(Component.literal("Colony not found!"));
                return 0;
            }

            raider.sendSystemMessage(
                    Component.literal("Raid started on " + colony.getName() + "! Stay in the colony!")
                            .withStyle(ChatFormatting.GOLD)
            );


            // Prevent self-raids and set hostile rank (same as wageWar logic)
            if (colony.getPermissions().getOwner().equals(raiderUUID)) {
                context.getSource().sendFailure(Component.literal("You cannot raid your own colony!"));
                return 0;
            }
            colony.getPermissions().setPlayerRank(raiderUUID, colony.getPermissions().getRankHostile(), level);

            // Initialize raid
            RaidData raidData = new RaidData(raiderUUID, colony);
            activeRaids.put(raiderUUID, raidData);
            startRaidCountdown(raidData);
            sendColonyMessage(colony,
                    Component.literal("Raid started by " + raider.getName().getString())
                            .withStyle(ChatFormatting.RED)
            );

            return 1;
        } catch (Exception e) {
            LOGGER.error("Raid command failed", e);
            context.getSource().sendFailure(Component.literal("Raid failed: " + e.getMessage()));
        }
        return 0;
    }

    private static boolean isRaiderInColony(ServerPlayer raider, IColony colony) {
        if (raider == null || colony == null || colony.getWorld() == null) return false;

        BlockPos raiderPos = raider.blockPosition();
        return colony.isCoordInColony(colony.getWorld(), raiderPos);
    }

    private static void startRaidCountdown(RaidData raidData) {
        raidData.timerTask = new TimerTask() {
            @Override
            public void run() {
                raidData.colony.getWorld().getServer().execute(()->{
                    if (!raidData.isActive){
                        this.cancel();
                        return;
                    }
                });

                ServerPlayer raiderPlayer = raidData.colony.getWorld()
                        .getServer()
                        .getPlayerList()
                        .getPlayer(raidData.raider);

                // Check if raider left colony
                if (raiderPlayer == null || !isRaiderInColony(raiderPlayer, raidData.colony)) {
                    sendColonyMessage(raidData.colony,
                            Component.literal("Raid stopped! Raider left the colony.").withStyle(ChatFormatting.RED));
                    endRaid(raidData, "Raider left colony");
                    this.cancel();
                    return;
                }

                if (raidData.elapsedSeconds >= getMaxRaidDurationSeconds()) {
                    endRaid(raidData, "Raid completed successfully");
                    this.cancel();
                    return;
                }

                // Check raider presence
                if (raiderPlayer == null || !isRaiderInColony(raiderPlayer, raidData.colony)) {
                    endRaid(raidData, "Raider abandoned the raid");
                    this.cancel();
                    return;
                }

                raidData.elapsedSeconds++;
                updateRaidBossBar(raidData);

                // Tax transfer logic at intervals
                if (raidData.elapsedSeconds % getTaxInterval() == 0) {
                    int intervalIndex = (raidData.elapsedSeconds / getTaxInterval()) - 1;
                    double[] taxPercentages = getTaxPercentages();
                    double percentage = intervalIndex < taxPercentages.length ? taxPercentages[intervalIndex] : taxPercentages[taxPercentages.length - 1];

                    int taxToTransfer = (int)(TaxManager.getStoredTaxForColony(raidData.colony) * percentage);
                    int claimed = TaxManager.claimTax(raidData.colony, taxToTransfer);

                    if (claimed > 0) {
                        String command = String.format("sdmshop add %s %d",
                                raiderPlayer.getName().getString(),
                                claimed
                        );
                        raidData.colony.getWorld().getServer().getCommands()
                                .performPrefixedCommand(
                                        raidData.colony.getWorld().getServer().createCommandSourceStack(),
                                        command
                                );

                        raidData.colony.getWorld().getServer().getPlayerList().broadcastSystemMessage(
                                Component.literal(raidData.colony.getName() + " lost " + claimed + " tax to raider " + raiderPlayer.getName().getString() + "!")
                                        .withStyle(ChatFormatting.DARK_PURPLE),
                                false
                        );
                    }
                }
            }
        };
        new Timer().scheduleAtFixedRate(raidData.timerTask, 1000, 1000);
    }

    private static void updateRaidBossBar(RaidData raidData) {
        // Always run on server thread
        raidData.colony.getWorld().getServer().execute(() -> {
            if (!raidData.isActive) return;

            ServerPlayer raiderPlayer = raidData.colony.getWorld()
                    .getServer()
                    .getPlayerList()
                    .getPlayer(raidData.raider);

            String status = "Active";
            if (raiderPlayer == null || !isRaiderInColony(raiderPlayer, raidData.colony)) {
                status = "Leaving Colony!";
            }

            // Calculate progress with cap at 100%
            float progress = Math.min((float) raidData.elapsedSeconds / getMaxRaidDurationSeconds(), 1.0f);
            int remainingSeconds = Math.max(getMaxRaidDurationSeconds() - raidData.elapsedSeconds, 0);
            int intervalIndex = (raidData.elapsedSeconds / getTaxInterval());
            double percentage = intervalIndex < getTaxPercentages().length ?
                    getTaxPercentages()[intervalIndex] :
                    getTaxPercentages()[getTaxPercentages().length - 1];

            Component name = Component.literal(String.format(
                    "Raid: %s | Tax: %d%% | Time: %02d:%02d/%02d:%02d",
                    status,
                    (int)(percentage * 100),
                    remainingSeconds / 60,
                    remainingSeconds % 60,
                    getMaxRaidDurationSeconds() / 60,
                    getMaxRaidDurationSeconds() % 60
            ));

            raidData.bossEvent.setName(name);
            raidData.bossEvent.setProgress(progress);
        });
    }

    private static void endRaid(RaidData raidData, String reason) {
        if (!raidData.isActive) return;

        raidData.isActive = false;
        raidData.bossEvent.removeAllPlayers();
        raidData.bossEvent.setVisible(false);

        // Apply grace period
        RAID_GRACE_PERIODS.put(raidData.raider, System.currentTimeMillis() + getRaidGraceDurationMs());

        // Remove from active raids
        activeRaids.remove(raidData.raider);

        // Notify colony
        sendColonyMessage(raidData.colony,
                Component.literal("Raid ended: " + reason)
                        .withStyle(ChatFormatting.GOLD)
        );

        ServerPlayer raiderPlayer = raidData.colony.getWorld().getServer().getPlayerList().getPlayer(raidData.raider);
        if (raiderPlayer != null) {
            raiderPlayer.sendSystemMessage(
                    Component.literal("Raid on " + raidData.colony.getName() + " ended: " + reason)
                            .withStyle(ChatFormatting.GOLD)
            );
        }

        LOGGER.info("Raid ended: {}", reason);

        // Cancel timer last
        if (raidData.timerTask != null) {
            raidData.timerTask.cancel();
        }
    }

    @SubscribeEvent
    public static void onRaiderDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RaidData raid = activeRaids.get(player.getUUID());
            if (raid != null) {
                endRaid(raid, "Raider died");
                activeRaids.remove(player.getUUID());
            }
        }
    }


    private static int handleWageWar(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer attacker = context.getSource().getPlayerOrException();
            String colonyName = StringArgumentType.getString(context, "colony");
            Level level = context.getSource().getLevel();

            IColony colony = findColonyByName(colonyName, level);
            if (colony == null) {
                context.getSource().sendFailure(Component.literal("Colony not found!"));
                return 0;
            }

            // Check existing wars
            if (ACTIVE_WARS.containsKey(colony)) {
                context.getSource().sendFailure(Component.literal("This colony is already at war!"));
                return 0;
            }

            // Check grace period
            UUID attackerUUID = attacker.getUUID();
            Long graceEnd = ATTACKER_GRACE_PERIODS.get(attackerUUID);
            if (graceEnd != null && System.currentTimeMillis() < graceEnd) {
                long remaining = graceEnd - System.currentTimeMillis();
                String timeLeft = String.format("%d minutes %d seconds",
                        TimeUnit.MILLISECONDS.toMinutes(remaining),
                        TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                );
                context.getSource().sendFailure(Component.literal("You must wait " + timeLeft + " before declaring war again!"));
                return 0;
            }


            // Prevent self-wars and existing grace period
            if (colony.getPermissions().getOwner().equals(attacker.getUUID())) {
                context.getSource().sendFailure(Component.literal("Cannot declare war on yourself!"));
                return 0;
            }

            int guardCount = countGuardsDebug(colony);
            if (guardCount < getMinGuardsToWageWar()) {
                context.getSource().sendFailure(Component.literal(
                        String.format("Target colony needs at least %d guards! (Found: %d)", getMinGuardsToWageWar(), guardCount)
                ));
                return 0;
            }

            // Retrieve the colony owner.
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(colony.getPermissions().getOwner());
            if (owner == null) {
                context.getSource().sendFailure(Component.literal("Colony owner is offline!"));
                return 0;
            }



            // Retrieve the attacker's Rank.
            Rank playerRank = colony.getPermissions().getRank(attacker.getUUID());
            if (playerRank == null) {
                // Add the attacker using the hostile rank.
                Rank hostileRank = colony.getPermissions().getRankHostile();
                colony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
                playerRank = colony.getPermissions().getRank(attacker.getUUID());
            } else {
                // Update the player's rank to hostile.
                colony.getPermissions().setPlayerRank(attacker.getUUID(), colony.getPermissions().getRankHostile(), level);
                playerRank = colony.getPermissions().getRank(attacker.getUUID());
            }
            if (playerRank != null) {
                playerRank.setHostile(true);
            } else {
                LOGGER.warn("Player Rank is null even after adding/updating the attacker!");
            }

            // Create and send the war request message to the colony owner.
            Component message = Component.literal(attacker.getName().getString() + " wants to declare war! ")
                    .append(createAcceptButton(attacker, colony))
                    .append(" ")
                    .append(createDeclineButton(attacker, colony));
            owner.sendSystemMessage(message);

            attacker.sendSystemMessage(
                    Component.literal("War request sent to " + colony.getName() + "!")
                            .withStyle(ChatFormatting.YELLOW)
            );

            LOGGER.info("[War] Attacker UUID: {}", attacker.getUUID());
            LOGGER.info("[War] Defender Colony Owner: {}", colony.getPermissions().getOwner());

            return 1;
        } catch (Exception e) {
            LOGGER.error("Wagewar command failed", e);
            context.getSource().sendFailure(Component.literal("War declaration failed: " + e.getMessage()));
            return 0;
        }
    }


    private static Component createAcceptButton(ServerPlayer attacker, IColony colony) {
        return Component.literal("[Accept]")
                .setStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                String.format("/war accept %s %d",
                                        attacker.getUUID().toString(),
                                        colony.getID()
                                )
                        ))
                );
    }

    private static Component createDeclineButton(ServerPlayer attacker, IColony colony) {
        return Component.literal("[Decline]")
                .setStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                String.format("/war decline %s %d",
                                        attacker.getUUID().toString(),
                                        colony.getID()
                                )
                        ))
                );
    }

    // Separate command handler for accept/decline
    private static int handleWarResponse(CommandContext<CommandSourceStack> context, boolean accepted) {
        try {
            String attackerUUID = StringArgumentType.getString(context, "attacker");
            int colonyId = IntegerArgumentType.getInteger(context, "colonyId");

            IColony colony = IColonyManager.getInstance().getColonyByDimension(
                    colonyId,
                    context.getSource().getLevel().dimension()
            );

            ServerPlayer owner = context.getSource().getPlayerOrException();
            UUID attackerUuid = UUID.fromString(attackerUUID);

            if (colony == null || !colony.getPermissions().getOwner().equals(owner.getUUID())) {
                context.getSource().sendFailure(Component.literal("Invalid colony!"));
                return 0;
            }

            ServerPlayer attacker = context.getSource().getServer().getPlayerList().getPlayer(attackerUuid);
            if (attacker == null) {
                context.getSource().sendFailure(Component.literal("Attacker is offline!"));
                return 0;
            }

            if (accepted) {
                startWar(colony, attacker, owner);
            } else {
                owner.sendSystemMessage(
                        Component.literal("War declaration declined!")
                                .withStyle(ChatFormatting.RED)
                );
                attacker.sendSystemMessage(
                        Component.literal(colony.getName() + " declined your war request!")
                                .withStyle(ChatFormatting.RED)
                );
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error handling war response", e);
            context.getSource().sendFailure(Component.literal("Failed to process war response!"));
            return 0;
        }
    }

    // Fixed BossEvent creation
    private static void startWar(IColony colony, ServerPlayer attacker, ServerPlayer defender) {
        colony.getPermissions().addPlayer(
                attacker.getUUID(),
                attacker.getScoreboardName(),
                colony.getPermissions().getRankHostile()
        );

        Component warStartMsg = Component.literal("War has begun for " + colony.getName() + "!")
                .withStyle(ChatFormatting.DARK_RED);
        attacker.sendSystemMessage(warStartMsg);
        defender.sendSystemMessage(warStartMsg);


        ServerBossEvent bossEvent = new ServerBossEvent(
                Component.literal("War for " + colony.getName()),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bossEvent.setProgress(1.0f);
        bossEvent.setVisible(true);

        // Add ALL online colony members to the boss bar
        colony.getPermissions().getPlayers().forEach((uuid, rank) -> {
            ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player != null) bossEvent.addPlayer(player);

        });

        sendColonyMessage(colony,
                Component.literal("Prepare for battle! Guards are now visible!")
                        .withStyle(ChatFormatting.GOLD)
        );

        applyGuardGlow(colony, true);

        WarData warData = new WarData(
                attacker.getUUID(),
                defender.getUUID(),
                System.currentTimeMillis(),
                bossEvent,
                colony
        );
        ACTIVE_WARS.put(colony, warData);

        startWarCountdown(colony, warData);
    }

    // Fixed guard check method
    private static boolean isGuardJob(IJob<?> job) {
        return job != null && job.isGuard();
    }


    private static void startWarCountdown(IColony colony, WarData warData) {
        warData.timerTask = new TimerTask() {
            @Override
            public void run() {
                long elapsedSeconds = (System.currentTimeMillis() - warData.startTime) / 1000;
                long remaining = Math.max(0, (TaxConfig.WAR_DURATION_MINUTES.get() * 60) - elapsedSeconds);

                Component newName = Component.literal(
                        String.format("War: %d/%d Guards | Time: %02d:%02d",
                                warData.remainingGuards,
                                warData.totalGuards,
                                remaining / 60,
                                remaining % 60)
                );
                float newProgress = (float) remaining / (TaxConfig.WAR_DURATION_MINUTES.get() * 60.0f);

                warData.bossEvent.setName(newName);
                warData.bossEvent.setProgress(newProgress);
                warData.bossEvent.setVisible(true);

                if (warData.remainingGuards <= 0 || remaining <= 0) {
                    if (warData.remainingGuards <= 0) {
                        transferOwnership(colony, warData.attacker);
                    } else {
                        // Attacker loses: Timer ran out
                        handleAttackerLoss(colony, warData.attacker, warData.defender);
                    }
                    endWar(colony);
                    this.cancel();
                }
            }
        };
        new Timer().scheduleAtFixedRate(warData.timerTask, 0, 1000);
    }

    private static void handleAttackerLoss(IColony defenderColony, UUID attackerUUID, UUID defenderUUID) {
        MinecraftServer server = defenderColony.getWorld().getServer();
        ServerPlayer attacker = server.getPlayerList().getPlayer(attackerUUID);

        // Get defender's username from UUID
        GameProfile defenderProfile = server.getProfileCache().get(defenderUUID).orElse(null);
        if (defenderProfile == null) {
            LOGGER.warn("[War] Defender profile not found!");
            return;
        }
        String defenderName = defenderProfile.getName();

        LOGGER.info("[War] Tax transfer initiated. Defender: {}", defenderName);

        IColonyManager colonyManager = IColonyManager.getInstance();
        List<IColony> attackerColonies = colonyManager.getAllColonies().stream()
                .filter(c -> c.getPermissions().getOwner().equals(attackerUUID))
                .toList();

        int totalTransferred = 0;
        for (IColony colony : attackerColonies) {
            int tax = TaxManager.getStoredTaxForColony(colony);
            if (tax <= 0) continue;

            int transferAmount = tax / 2;
            int claimed = TaxManager.claimTax(colony, transferAmount);

            if (claimed > 0) {
                // Transfer to defender via SDMShop command
                String command = String.format("sdmshop add %s %d", defenderName, claimed);
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(),
                        command
                );
                totalTransferred += claimed;
            }
        }

        // Notify players
        if (totalTransferred > 0) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§eTransferred §6" + totalTransferred + "§e tax to §b" + defenderName)
                            .withStyle(ChatFormatting.GOLD),
                    false
            );
        }
    }

    // Updated death handler with null checks
    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;

        ICitizenData data = citizen.getCitizenData();
        if (data == null) return;

        IColony colony = data.getColony(); // Correct colony retrieval
        WarData warData = WarCommands.ACTIVE_WARS.get(colony);
        if (warData == null) return;

        // Check if the dead citizen was a pre-registered guard
        if (warData.guardIDs.remove(data.getId())) {
            warData.remainingGuards--; // Decrement remaining guards
            colony.getWorld().getServer().execute(() -> { // Run on server thread
                updateBossBar(warData);

                if (warData.remainingGuards <= 0) {
                    transferOwnership(colony, warData.attacker);
                    endWar(colony);
                }
            });
        }
    }

    private static void updateBossBar(WarData warData) {
        long elapsed = (System.currentTimeMillis() - warData.startTime) / 1000;
        long remaining = Math.max(0, (TaxConfig.WAR_DURATION_MINUTES.get() * 60) - elapsed);

        Component name = Component.literal(
                String.format("War: %d/%d Guards | Time: %02d:%02d",
                        warData.remainingGuards,
                        warData.totalGuards,
                        remaining / 60,
                        remaining % 60
                )
        );

        warData.bossEvent.setName(name);
        warData.bossEvent.setProgress((float) remaining / (TaxConfig.WAR_DURATION_MINUTES.get()));
    }


    private static void transferOwnership(IColony colony, UUID newOwnerUUID) {
        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null) return;

        // Use colony permissions to set the new owner
        if (colony.getPermissions().setOwner(newOwner)) {
            colony.markDirty(); // Ensure changes are saved

            // Broadcast conquest message
            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                    .withStyle(ChatFormatting.DARK_RED);
            colony.getWorld().getServer().getPlayerList().broadcastSystemMessage(msg, false);
        } else {
            LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
        }
    }

    private static void endWar(IColony colony) {
        WarData warData = ACTIVE_WARS.remove(colony);
        if (warData != null) {
            // Apply grace period to the attacker
            ATTACKER_GRACE_PERIODS.put(warData.attacker, System.currentTimeMillis() + getAttackerGraceDurationMs());
            // Existing cleanup code (boss bar, glowing effect, etc.)
            warData.bossEvent.removeAllPlayers();
            warData.bossEvent.setVisible(false);
            warData.timerTask.cancel();
            applyGuardGlow(colony, false);
        }
    }

    // Updated methods using work building
    private static void applyGuardGlow(IColony colony, boolean enable) {
        colony.getCitizenManager().getCitizens().stream()
                .map(ICitizenData::getJob)
                .filter(job -> job != null && job.isGuard())
                .map(IJob::getCitizen)
                .map(ICitizenData::getEntity)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(e -> {
                    if (enable) {
                        e.addEffect(new MobEffectInstance(
                                MobEffects.GLOWING,
                                999999,
                                0,
                                false, // No particles
                                false  // No icon
                        ));
                    } else {
                        e.removeEffect(MobEffects.GLOWING); // Explicitly remove effect
                    }
                });
    }

    private static class WarData {
        final UUID attacker;
        final UUID defender;
        final ServerBossEvent bossEvent;
        final Set<Integer> guardIDs; // Track guard citizen IDs (integers)
        final int totalGuards; // Total initial guards
        public int defenderColonyId;
        int remainingGuards; // Guards remaining
        TimerTask timerTask;
        final long startTime;

        public WarData(UUID attacker, UUID defender, long startTime,
                       ServerBossEvent bossEvent, IColony colony) {
            this.attacker = attacker;
            this.defender = defender;
            this.defenderColonyId = colony.getID();
            this.startTime = startTime;
            this.bossEvent = bossEvent;
            this.guardIDs = colony.getCitizenManager().getCitizens().stream()
                    .filter(c -> WarCommands.isGuardJob(c.getJob()))
                    .map(ICitizenData::getId)
                    .collect(Collectors.toSet());
            this.totalGuards = this.guardIDs.size(); // Set total guards
            this.remainingGuards = this.totalGuards; // Initialize remaining guards
        }
    }

    private static IColony findColonyByName(String name, Level level) {
        return IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static int countGuards(IColony colony) {
        return (int) colony.getCitizenManager().getCitizens().stream()
                .map(ICitizenData::getJob)
                .filter(Objects::nonNull)
                .peek(job -> LOGGER.debug("Found job: {}", job.getJobRegistryEntry().getKey())) // Debug line
                .filter(job -> WarCommands.GUARD_JOBS.contains(job.getJobRegistryEntry().getKey()))
                .count();
    }

    private static int countGuardsDebug(IColony colony) {
        int count = 0;
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            IJob<?> job = citizen.getJob();
            if (job != null) {
                JobEntry entry = job.getJobRegistryEntry();
                LOGGER.info("Citizen {} has job: {}", citizen.getName(),
                        (entry != null) ? entry.getKey().toString() : "null");
                if (entry != null && WarCommands.GUARD_JOBS.contains(entry.getKey())) {
                    count++;
                }
            }
        }
        LOGGER.info("Total guards counted: {}", count);
        return count;
    }


    private static void sendColonyMessage(IColony colony, Component message) {
        colony.getPermissions().getPlayers().forEach((uuid, data) -> {
            ServerPlayer p = (ServerPlayer) colony.getWorld().getPlayerByUUID(uuid);
            if (p != null) p.sendSystemMessage(message);
        });
    }


    private static long getAttackerGraceDurationMs() {
        return TimeUnit.MINUTES.toMillis(TaxConfig.ATTACKER_GRACE_PERIOD_MINUTES.get());
    }

    private static long getRaidGraceDurationMs() {
        return TimeUnit.MINUTES.toMillis(TaxConfig.RAID_GRACE_PERIOD_MINUTES.get());
    }

    private static int getMaxRaidDurationSeconds() {
        return TaxConfig.MAX_RAID_DURATION_MINUTES.get() * 60;
    }

    private static int getTaxInterval() {
        return TaxConfig.RAID_TAX_INTERVAL_SECONDS.get();
    }

    private static double[] getTaxPercentages() {
        return TaxConfig.RAID_TAX_PERCENTAGES.get().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
    }

    private static int getMinGuardsToWageWar() {
        return TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get();
    }





    private static class RaidData {
        final UUID raider;
        final IColony colony;
        final ServerBossEvent bossEvent;
        int elapsedSeconds;
        TimerTask timerTask;
        boolean isActive;

        public RaidData(UUID raider, IColony colony) {
            this.raider = raider;
            this.colony = colony;
            this.bossEvent = new ServerBossEvent(
                    Component.literal("Raid in Progress: " + colony.getName()),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            this.bossEvent.setProgress(0.0f);
            this.bossEvent.setVisible(true);
            this.elapsedSeconds = 0;
            this.isActive = true;

            // Add all colony members to bossbar
            colony.getPermissions().getPlayers().keySet().forEach(uuid -> {
                ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    this.bossEvent.addPlayer(player);
                }
            });
        }
    }

}
