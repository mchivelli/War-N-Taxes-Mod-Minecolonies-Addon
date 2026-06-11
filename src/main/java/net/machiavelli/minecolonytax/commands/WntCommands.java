package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import java.util.Map;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.peace.PeaceProposalManager;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;
import net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager;
import net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.machiavelli.minecolonytax.upgrade.UpgradeType;
import java.util.Arrays;
import com.minecolonies.api.colony.permissions.IPermissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;

@Mod.EventBusSubscriber(modid = "minecolonytax", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WntCommands {

        private static final Logger LOGGER = LogManager.getLogger(WntCommands.class);

        private static RaidManager raidManagerInstance;
        private static PeaceProposalManager peaceProposalManagerInstance;

        private static RaidManager getRaidManager() {
                if (raidManagerInstance == null) {
                        raidManagerInstance = new RaidManager();
                }
                return raidManagerInstance;
        }

        private static PeaceProposalManager getPeaceProposalManager() {
                if (peaceProposalManagerInstance == null) {
                        peaceProposalManagerInstance = new PeaceProposalManager();
                }
                return peaceProposalManagerInstance;
        }

        private static final SuggestionProvider<CommandSourceStack> COLONY_SUGGESTIONS = (context, builder) -> {
                try {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                        List<String> colonyNames = colonyManager.getAllColonies().stream()
                                        .map(IColony::getName)
                                        .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                                        .collect(Collectors.toList());
                        return SharedSuggestionProvider.suggest(colonyNames, builder);
                } catch (Exception e) {
                        return builder.buildFuture();
                }
        };

        // Filters to colonies the player manages.
        private static final SuggestionProvider<CommandSourceStack> PLAYER_COLONY_SUGGESTIONS = (context, builder) -> {
                try {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                        List<String> colonyNames = colonyManager.getAllColonies().stream()
                                        .filter(colony -> colony.getPermissions().getRank(player.getUUID())
                                                        .isColonyManager())
                                        .map(IColony::getName)
                                        .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                                        .collect(Collectors.toList());
                        return SharedSuggestionProvider.suggest(colonyNames, builder);
                } catch (Exception e) {
                        return builder.buildFuture();
                }
        };

        // Filters to abandoned colonies that are currently claimable.
        private static final SuggestionProvider<CommandSourceStack> ABANDONED_COLONY_SUGGESTIONS = (context,
                        builder) -> {
                try {
                        context.getSource().getPlayerOrException();
                        List<String> colonyNames = net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                                        .getClaimableColonies(context.getSource().getServer()).stream()
                                        .map(IColony::getName)
                                        .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                                        .collect(Collectors.toList());
                        return SharedSuggestionProvider.suggest(colonyNames, builder);
                } catch (Exception e) {
                        return builder.buildFuture();
                }
        };

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("wnt")
                                .executes(WntCommands::showMainHelp)

                                .then(Commands.literal("help")
                                                .executes(WntCommands::showHelp)
                                                .then(Commands.argument("command", StringArgumentType.word())
                                                                .suggests((context, builder) -> SharedSuggestionProvider
                                                                                .suggest(
                                                                                                List.of("wagewar",
                                                                                                                "raid",
                                                                                                                "claimtax",
                                                                                                                "checktax",
                                                                                                                "taxdebt",
                                                                                                                "joinwar",
                                                                                                                "leavewar",
                                                                                                                "war",
                                                                                                                "peace",
                                                                                                                "warinfo",
                                                                                                                "warstop",
                                                                                                                "warstopall",
                                                                                                                "raidstop",
                                                                                                                "warhistory",
                                                                                                                "raidhistory",
                                                                                                                "warstats",
                                                                                                                "taxgen",
                                                                                                                "vasalize",
                                                                                                                "vassalaccept",
                                                                                                                "vassaldecline",
                                                                                                                "revoke",
                                                                                                                "vasals",
                                                                                                                "debug",
                                                                                                                "permissions"),
                                                                                                builder))
                                                                .executes(WntCommands::showSpecificHelp)))

                                .then(Commands.literal("wagewar")
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::handleWageWarCommand)
                                                                .then(Commands.argument("extortionPercent",
                                                                                IntegerArgumentType.integer(1, 100))
                                                                                .executes(WntCommands::handleWageWarWithExtortionCommand))))

                                .then(Commands.literal("raid")
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::handleRaidCommand)))

                                .then(Commands.literal("besiege")
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::handleBesiegeCommand)))

                                .then(Commands.literal("joinwar")
                                                .executes(WntCommands::joinWarCommand))

                                .then(Commands.literal("leavewar")
                                                .executes(WntCommands::leaveWarCommand))

                                .then(Commands.literal("payextortion")
                                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                                                .then(Commands.argument("extortionPercent",
                                                                                IntegerArgumentType.integer(1, 100))
                                                                                .executes(WntCommands::handlePayExtortionCommand))))

                                .then(Commands.literal("war")
                                                .then(Commands.literal("accept")
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer())
                                                                                .suggests((context, builder) -> {
                                                                                        List<String> pendingIds = new ArrayList<>();
                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
                                                                                                        .values()) {
                                                                                                if (war.isJoinPhaseActive()) {
                                                                                                        pendingIds.add(String
                                                                                                                        .valueOf(war.getColony()
                                                                                                                                        .getID()));
                                                                                                }
                                                                                        }
                                                                                        return SharedSuggestionProvider
                                                                                                        .suggest(pendingIds,
                                                                                                                        builder);
                                                                                })
                                                                                .executes(ctx -> handleWarResponseCommand(
                                                                                                ctx, true))))
                                                .then(Commands.literal("decline")
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer())
                                                                                .suggests((context, builder) -> {
                                                                                        List<String> pendingIds = new ArrayList<>();
                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
                                                                                                        .values()) {
                                                                                                if (war.isJoinPhaseActive()) {
                                                                                                        pendingIds.add(String
                                                                                                                        .valueOf(war.getColony()
                                                                                                                                        .getID()));
                                                                                                }
                                                                                        }
                                                                                        return SharedSuggestionProvider
                                                                                                        .suggest(pendingIds,
                                                                                                                        builder);
                                                                                })
                                                                                .executes(ctx -> handleWarResponseCommand(
                                                                                                ctx, false)))))

                                .then(Commands.literal("peace")
                                                .then(Commands.literal("whitepeace")
                                                                .executes(WntCommands::suePeaceWhiteCommand))
                                                .then(Commands.literal("reparations")
                                                                .then(Commands.argument("amount",
                                                                                IntegerArgumentType.integer(1))
                                                                                .executes(ctx -> suePeaceReparationsCommand(
                                                                                                ctx,
                                                                                                IntegerArgumentType
                                                                                                                .getInteger(ctx, "amount")))))
                                                .then(Commands.literal("accept")
                                                                .executes(WntCommands::acceptPeaceCommand))
                                                .then(Commands.literal("decline")
                                                                .executes(WntCommands::declinePeaceCommand)))

                                .then(Commands.literal("warinfo")
                                                .executes(WntCommands::warInfoCommand))


                                .then(Commands.literal("warstop")
                                                .requires(src -> src.hasPermission(2))
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::stopWarCommand)))

                                .then(Commands.literal("warstopall")
                                                .requires(src -> src.hasPermission(2))
                                                .executes(WntCommands::stopAllWarsCommand))

                                .then(Commands.literal("raidstop")
                                                .requires(src -> src.hasPermission(2))
                                                .executes(WntCommands::stopRaidCommand))

                                // Permissions health check (admin)
                                .then(Commands.literal("permcheck")
                                                .requires(src -> src.hasPermission(2))
                                                .executes(WntCommands::handlePermCheckCommand))

                                // Tax commands
                                .then(Commands.literal("claimtax")
                                                .executes(context -> executeClaimTax(context, null, -1))
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(PLAYER_COLONY_SUGGESTIONS)
                                                                .executes(context -> {
                                                                        String colonyName = extractColonyName(
                                                                                        StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "colony"));
                                                                        return executeClaimTax(context, colonyName, -1);
                                                                })
                                                                .then(Commands.argument("amount",
                                                                                IntegerArgumentType.integer(1))
                                                                                .executes(context -> {
                                                                                        String colonyName = extractColonyName(
                                                                                                        StringArgumentType
                                                                                                                        .getString(context,
                                                                                                                                        "colony"));
                                                                                        int amount = IntegerArgumentType
                                                                                                        .getInteger(context,
                                                                                                                        "amount");
                                                                                        return executeClaimTax(context,
                                                                                                        colonyName,
                                                                                                        amount);
                                                                                }))))

                                .then(Commands.literal("checktax")
                                                .executes(WntCommands::checkTaxForSelf)
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                                .requires(src -> src.hasPermission(2))
                                                                .suggests((context, builder) -> SharedSuggestionProvider
                                                                                .suggest(
                                                                                                context.getSource()
                                                                                                                .getServer()
                                                                                                                .getPlayerNames(),
                                                                                                builder))
                                                                .executes(ctx -> checkTaxForPlayer(ctx,
                                                                                StringArgumentType.getString(ctx,
                                                                                                "player")))))

                                .then(Commands.literal("taxdebt")
                                                .then(Commands.literal("pay")
                                                                .then(Commands.argument("amount",
                                                                                IntegerArgumentType.integer(1))
                                                                                .then(Commands.argument("colony",
                                                                                                StringArgumentType
                                                                                                                .string())
                                                                                                .suggests(PLAYER_COLONY_SUGGESTIONS)
                                                                                                .executes(context -> {
                                                                                                        int amount = IntegerArgumentType
                                                                                                                        .getInteger(context,
                                                                                                                                        "amount");
                                                                                                        String colonyName = extractColonyName(
                                                                                                                        StringArgumentType
                                                                                                                                        .getString(context,
                                                                                                                                                        "colony"));
                                                                                                        return executeTaxDebt(
                                                                                                                        context,
                                                                                                                        colonyName,
                                                                                                                        amount);
                                                                                                })))))

                                .then(Commands.literal("warhistory")
                                                .executes(ctx -> executeWarHistory(ctx, null))
                                                .then(Commands.argument("colony", StringArgumentType.word())
                                                                .suggests(PLAYER_COLONY_SUGGESTIONS)
                                                                .executes(ctx -> executeWarHistory(ctx,
                                                                                StringArgumentType.getString(ctx,
                                                                                                "colony")))))

                                .then(Commands.literal("raidhistory")
                                                .executes(ctx -> executeRaidHistory(ctx, null))
                                                .then(Commands.argument("colony", StringArgumentType.word())
                                                                .suggests(PLAYER_COLONY_SUGGESTIONS)
                                                                .executes(ctx -> executeRaidHistory(ctx,
                                                                                StringArgumentType.getString(ctx,
                                                                                                "colony")))))

                                .then(Commands.literal("colonylog")
                                                .requires(src -> src.hasPermission(2))
                                                .executes(ctx -> executeColonyLog(ctx, null, 1))
                                                .then(Commands.argument("colony", StringArgumentType.word())
                                                                .suggests(PLAYER_COLONY_SUGGESTIONS)
                                                                .executes(ctx -> executeColonyLog(ctx,
                                                                                StringArgumentType.getString(ctx, "colony"), 1))
                                                                .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                                                .executes(ctx -> executeColonyLog(ctx,
                                                                                                StringArgumentType.getString(ctx, "colony"),
                                                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page"))))))

                                .then(Commands.literal("warstats")
                                                .executes(WntCommands::showWarStats))

                                .then(Commands.literal("vasalize")
                                                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                                                                .then(Commands.argument("colony",
                                                                                StringArgumentType.string())
                                                                                .suggests(COLONY_SUGGESTIONS)
                                                                                .executes(ctx -> handleVassalize(ctx,
                                                                                                IntegerArgumentType
                                                                                                                .getInteger(ctx, "percent"),
                                                                                                extractColonyName(
                                                                                                                StringArgumentType
                                                                                                                                .getString(ctx, "colony")))))))
                                .then(Commands.literal("vassalaccept")
                                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                                                .executes(ctx -> handleVassalAccept(ctx,
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                                "colonyId")))))
                                .then(Commands.literal("vassaldecline")
                                                .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                                                .executes(ctx -> handleVassalDecline(ctx,
                                                                                IntegerArgumentType.getInteger(ctx,
                                                                                                "colonyId")))))
                                .then(Commands.literal("revoke")
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                                .executes(ctx -> handleVassalRevoke(ctx,
                                                                                StringArgumentType.getString(ctx,
                                                                                                "player")))))
                                .then(Commands.literal("vasals")
                                                .executes(WntCommands::handleVassalList))

                                .then(Commands.literal("taxgen")
                                                .requires(src -> src.hasPermission(2))
                                                .then(Commands.literal("disable")
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer(0))
                                                                                .executes(ctx -> {
                                                                                        int id = IntegerArgumentType
                                                                                                        .getInteger(ctx, "colonyId");
                                                                                        net.machiavelli.minecolonytax.TaxManager
                                                                                                        .disableTaxGeneration(
                                                                                                                        id);
                                                                                        ctx.getSource().sendSuccess(
                                                                                                        () -> Component.literal(
                                                                                                                        "Disabled tax generation for colony "
                                                                                                                                        + id),
                                                                                                        false);
                                                                                        return 1;
                                                                                })))
                                                .then(Commands.literal("enable")
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer(0))
                                                                                .executes(ctx -> {
                                                                                        int id = IntegerArgumentType
                                                                                                        .getInteger(ctx, "colonyId");
                                                                                        net.machiavelli.minecolonytax.TaxManager
                                                                                                        .enableTaxGeneration(
                                                                                                                        id);
                                                                                        ctx.getSource().sendSuccess(
                                                                                                        () -> Component.literal(
                                                                                                                        "Enabled tax generation for colony "
                                                                                                                                        + id),
                                                                                                        false);
                                                                                        return 1;
                                                                                }))))

                                .then(Commands.literal("debug")
                                                .then(Commands.literal("sdm")
                                                                .then(Commands.literal("status")
                                                                                .executes(WntCommands::showSdmStatus)))
                                                .then(Commands.literal("emergencyfix")
                                                                .requires(source -> source.hasPermission(2))
                                                                .executes(WntCommands::handleEmergencyFix))
                                                .then(Commands.literal("fixnullowners")
                                                                .requires(source -> source.hasPermission(2))
                                                                .executes(WntCommands::handleFixNullOwners))
                                                .then(Commands.literal("cleanupabandonedentries")
                                                                .requires(source -> source.hasPermission(2))
                                                                .executes(WntCommands::handleCleanupAbandonedEntries))
                                                .then(Commands.literal("forcecleanupcolony")
                                                                .requires(source -> source.hasPermission(2))
                                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                                .suggests(COLONY_SUGGESTIONS)
                                                                                .executes(WntCommands::handleForceCleanupColony)))
                                                .then(Commands.literal("bossbar")
                                                                .requires(source -> source.hasPermission(2))
                                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                                .suggests(COLONY_SUGGESTIONS)
                                                                                .executes(WntCommands::handleDebugBossBar)))
                                                .then(Commands.literal("claimraidstatus")
                                                                .requires(source -> source.hasPermission(2))
                                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                                .suggests(ABANDONED_COLONY_SUGGESTIONS)
                                                                                .executes(WntCommands::checkClaimingRaidStatus)))
                                                .then(Commands.literal("war")
                                                                .requires(src -> src.hasPermission(2))
                                                                .executes(WntCommands::debugWarCommand))
                                                .then(Commands.literal("guards")
                                                                .requires(src -> src.hasPermission(2))
                                                                .executes(WntCommands::debugGuardCounts)
                                                                .then(Commands.argument("colony",
                                                                                StringArgumentType.greedyString())
                                                                                .suggests(COLONY_SUGGESTIONS)
                                                                                .executes(WntCommands::debugGuardCountsForColony)))
                                                .then(Commands.literal("tax")
                                                                .executes(ctx -> DebugTaxCommand.execute(ctx, null))
                                                                .then(Commands.argument("colony",
                                                                                StringArgumentType.string())
                                                                                .suggests(COLONY_SUGGESTIONS)
                                                                                .executes(ctx -> DebugTaxCommand.execute(
                                                                                                ctx,
                                                                                                extractColonyName(StringArgumentType
                                                                                                                .getString(ctx, "colony"))))))
                                                .then(Commands.literal("officertracking")
                                                                .executes(OfficerTrackingDebugCommand::checkCurrentColony)
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer())
                                                                                .executes(OfficerTrackingDebugCommand::checkSpecificColony)))
                                                .then(Commands.literal("besiege")
                                                                .executes(WntCommands::handleBesiegeStatus))
                                                .then(Commands.literal("entityraid")
                                                                .requires(src -> src.hasPermission(2))
                                                                .then(Commands.literal("status")
                                                                                .executes(ctx -> EntityRaidCommands
                                                                                                .showEntityRaidStatus(ctx)))
                                                                .then(Commands.literal("config")
                                                                                .executes(ctx -> EntityRaidCommands
                                                                                                .showEntityRaidConfig(ctx)))
                                                                .then(Commands.literal("end")
                                                                                .then(Commands.argument("colonyId",
                                                                                                IntegerArgumentType.integer())
                                                                                                .executes(ctx -> EntityRaidCommands
                                                                                                                .endEntityRaid(ctx))))
                                                                .then(Commands.literal("test")
                                                                                .then(Commands.argument("colonyName",
                                                                                                StringArgumentType.string())
                                                                                                .suggests(COLONY_SUGGESTIONS)
                                                                                                .executes(ctx -> EntityRaidCommands
                                                                                                                .testEntityRaid(ctx))))
                                                                .then(Commands.literal("reload")
                                                                                .executes(ctx -> EntityRaidCommands
                                                                                                .reloadEntityRaidConfig(ctx)))))

                                .then(Commands.literal("permissions")
                                                .requires(source -> source.hasPermission(2))
                                                .then(Commands.literal("status")
                                                                .executes(ctx -> {
                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                        .showPermissionsStatus(ctx);
                                                                }))
                                                .then(Commands.literal("config")
                                                                .executes(ctx -> {
                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                        .showPermissionsConfig(ctx);
                                                                }))
                                                .then(Commands.literal("apply")
                                                                .executes(ctx -> {
                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                        .applyGeneralPermissions(ctx);
                                                                })
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer())
                                                                                .executes(ctx -> {
                                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                                        .applyToSpecificColony(
                                                                                                                        ctx);
                                                                                })))
                                                .then(Commands.literal("remove")
                                                                .executes(ctx -> {
                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                        .removeGeneralPermissions(ctx);
                                                                })
                                                                .then(Commands.argument("colonyId",
                                                                                IntegerArgumentType.integer())
                                                                                .executes(ctx -> {
                                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                                        .removeFromSpecificColony(
                                                                                                                        ctx);
                                                                                })))
                                                .then(Commands.literal("reload")
                                                                .executes(ctx -> {
                                                                        return net.machiavelli.minecolonytax.commands.GeneralPermissionsCommands
                                                                                        .reloadGeneralPermissions(ctx);
                                                                })))

                                .then(Commands.literal("claimcolony")
                                                .executes(WntCommands::showClaimableColonies)
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(ABANDONED_COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::handleClaimColony)))

                                .then(Commands.literal("listabandoned")
                                                .requires(source -> TaxConfig.isListAbandonedForAllEnabled()
                                                                || source.hasPermission(2))
                                                .executes(WntCommands::listAbandonedColonies))
                                .then(Commands.literal("claimstatus")
                                                .executes(WntCommands::checkClaimingStatus))
                                .then(Commands.literal("protectcolony")
                                                .requires(source -> source.hasPermission(2))
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::protectColonyFromClaiming)))
                                .then(Commands.literal("unprotectcolony")
                                                .requires(source -> source.hasPermission(2))
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::unprotectColonyFromClaiming)))

                                .then(Commands.literal("listprotected")
                                                .requires(source -> source.hasPermission(2))
                                                .executes(WntCommands::listProtectedColonies))

                                .then(Commands.literal("forceabandon")
                                                .requires(source -> source.hasPermission(2))
                                                .then(Commands.argument("colony", StringArgumentType.string())
                                                                .suggests(PLAYER_COLONY_SUGGESTIONS)
                                                                .executes(WntCommands::handleForceAbandonColony)))

                                .then(Commands.literal("invest")
                                                .executes(WntCommands::handleInvestHelp)
                                                .then(Commands.literal("list")
                                                                .executes(WntCommands::handleInvestList))
                                                .then(Commands.literal("status")
                                                                .executes(WntCommands::handleInvestStatus))
                                                .then(Commands.literal("buy")
                                                                .then(Commands.argument("type", StringArgumentType.word())
                                                                                .suggests((context, builder) -> SharedSuggestionProvider
                                                                                                .suggest(Arrays.stream(UpgradeType.values())
                                                                                                                .map(Enum::name)
                                                                                                                .collect(Collectors.toList()),
                                                                                                                builder))
                                                                                .executes(WntCommands::handleInvestBuy)))));
        }

        private static int showMainHelp(CommandContext<CommandSourceStack> context) {
                return showHelp(context);
        }

        private static int showHelp(CommandContext<CommandSourceStack> context) {
                CommandSourceStack source = context.getSource();

                source.sendSuccess(() -> Component.literal("§6§l=== War 'N Taxes Commands ==="), false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt help [command] §7- Show detailed help for a specific command"),
                                false);
                source.sendSuccess(() -> Component.literal(""), false);

                source.sendSuccess(() -> Component.literal("§6War Commands:"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt wagewar <colony> §7- Declare war on a colony"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt raid <colony> §7- Start a raid on a colony"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt joinwar §7- Join an active war"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt leavewar §7- Leave current war"), false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt war accept/decline <colonyId> §7- Respond to war declaration"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt peace whitepeace §7- Propose white peace"), false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt peace reparations <amount> §7- Propose peace with reparations"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt peace accept/decline §7- Respond to peace proposal"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt warinfo §7- Show current war information"), false);
                source.sendSuccess(() -> Component.literal(""), false);

                source.sendSuccess(() -> Component.literal("§6Tax Commands:"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt claimtax [colony] [amount] §7- Claim tax revenue"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt checktax [player] §7- Check tax revenue"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt taxdebt pay <amount> <colony> §7- Pay colony debt"),
                                false);
                source.sendSuccess(() -> Component.literal(""), false);

                source.sendSuccess(() -> Component.literal("§6Info Commands:"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt warhistory [colony] §7- View war history"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt raidhistory [colony] §7- View raid history"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt warstats §7- View your war statistics"), false);
                source.sendSuccess(() -> Component.literal(""), false);

                source.sendSuccess(() -> Component.literal("§6Vassal Commands:"), false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt vasalize <percent> <colony> §7- Offer vassalization to a colony"),
                                false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt vassalaccept <colonyId> §7- Accept a vassalization proposal"),
                                false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt vassaldecline <colonyId> §7- Decline a vassalization proposal"),
                                false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt revoke <player> §7- Revoke a vassalization relationship"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt vasals §7- List your vassals"), false);
                source.sendSuccess(() -> Component.literal(""), false);

                source.sendSuccess(() -> Component.literal("§6Colony Claiming Commands:"), false);
                source.sendSuccess(() -> Component.literal("§e/wnt claimcolony [colony] §7- Claim an abandoned colony"),
                                false);
                source.sendSuccess(() -> Component.literal("§e/wnt listabandoned §7- List all abandoned colonies"),
                                false);
                source.sendSuccess(
                                () -> Component.literal(
                                                "§e/wnt claimstatus §7- Check your claiming eligibility and cooldown"),
                                false);
                source.sendSuccess(() -> Component.literal(""), false);

                if (source.hasPermission(2)) {
                        source.sendSuccess(() -> Component.literal("§cAdmin Commands:"), false);
                        source.sendSuccess(() -> Component.literal("§e/wnt warstop <colony> §7- Stop specific war"),
                                        false);
                        source.sendSuccess(() -> Component.literal("§e/wnt warstopall §7- Stop all wars"), false);
                        source.sendSuccess(() -> Component.literal("§e/wnt raidstop §7- Stop active raid"), false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt forceabandon <colony> §7- Force abandon a colony (admin only)"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt protectcolony <colony> §7- Protect colony from claiming"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt unprotectcolony <colony> §7- Remove claiming protection"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal("§e/wnt listprotected §7- List all protected colonies"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt claimraidstatus <colony> §7- Check claiming raid status"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt taxgen disable/enable <colonyId> §7- Control tax generation"),
                                        false);
                        source.sendSuccess(() -> Component.literal("§cDebug Commands:"), false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt debug war §7- Debug all active wars (admin)"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt debug guards [colony] §7- Debug guard/tower counting (admin)"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt debug tax [colony] §7- Per-building tax breakdown"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt debug officertracking [colonyId] §7- Officer visit tracking"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt debug besiege §7- Active siege/occupation status"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt debug entityraid <status|config|end|test|reload> §7- Entity raid tools (admin)"),
                                        false);
                        source.sendSuccess(() -> Component
                                        .literal("§e/wnt permissions status §7- Show general permissions status"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt permissions config §7- Show permissions configuration"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt permissions apply/remove §7- Apply/remove general permissions"),
                                        false);
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "§e/wnt permissions reload §7- Reload general permissions"),
                                        false);
                }

                return 1;
        }

        private static int showSpecificHelp(CommandContext<CommandSourceStack> context) {
                CommandSourceStack source = context.getSource();
                String command = StringArgumentType.getString(context, "command");

                switch (command.toLowerCase()) {
                        case "wagewar":
                                source.sendSuccess(() -> Component.literal("§6/wnt wagewar <colony>"), false);
                                source.sendSuccess(() -> Component.literal("§7Declare war on the specified colony."),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7Requirements:"), false);

                                // Show requirements based on priority system
                                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
                                        // Building requirements are enabled - show those instead of guard count
                                        source.sendSuccess(() -> Component.literal("§7- Building requirements: " +
                                                        String.join(", ",
                                                                        net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                                                                                        .getFormattedRequirements(
                                                                                                        TaxConfig.getWarBuildingRequirements()))),
                                                        false);
                                } else {
                                        // Fall back to legacy guard count requirements
                                        source.sendSuccess(() -> Component.literal(
                                                        "§7- Your colony must have at least "
                                                                        + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()
                                                                        + " guards"),
                                                        false);
                                }

                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- Target colony owner must be online (unless configured otherwise)"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- You must wait for grace period between wars"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7- No active raid is ongoing"), false);
                                break;

                        case "raid":
                                source.sendSuccess(() -> Component.literal("§6/wnt raid <colony>"), false);
                                source.sendSuccess(() -> Component.literal("§7Start a raid on the specified colony."),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7Requirements:"), false);

                                // Show requirements based on priority system
                                if (TaxConfig.isRaidBuildingRequirementsEnabled()) {
                                        // Building requirements are enabled - show those instead of guard count
                                        source.sendSuccess(() -> Component.literal("§7- Building requirements: " +
                                                        String.join(", ",
                                                                        net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                                                                                        .getFormattedRequirements(
                                                                                                        TaxConfig.getRaidBuildingRequirements()))),
                                                        false);
                                } else {
                                        // Fall back to legacy guard count requirements
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7- Your colony must have at least "
                                                                                        + TaxConfig.getMinGuardsToRaid()
                                                                                        + " guards"),
                                                        false);
                                }

                                if (TaxConfig.isRaidGuardProtectionEnabled()) {
                                        source.sendSuccess(() -> Component.literal(
                                                        "§7- Target colony must have at least "
                                                                        + TaxConfig.getMinGuardsToBeRaided()
                                                                        + " guards"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal("§7- Target colony must have at least "
                                                                        + TaxConfig.getMinGuardTowersToBeRaided()
                                                                        + " guard towers"),
                                                        false);
                                }
                                source.sendSuccess(() -> Component.literal("§7During a raid:"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- Tax is awarded only after a successful raid (not periodically)"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- If you die, you pay a penalty to your killer"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- Raid lasts up to "
                                                                                + TaxConfig.MAX_RAID_DURATION_MINUTES
                                                                                                .get()
                                                                                + " minutes"),
                                                false);
                                break;

                        case "claimtax":
                                source.sendSuccess(() -> Component.literal("§6/wnt claimtax [colony] [amount]"), false);
                                source.sendSuccess(() -> Component.literal("§7Claim tax revenue from your colonies."),
                                                false);
                                source.sendSuccess(() -> Component
                                                .literal("§7- No arguments: Claims all tax from all your colonies"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- [colony]: Claims all tax from specified colony"),
                                                false);
                                source.sendSuccess(() -> Component
                                                .literal("§7- [colony] [amount]: Claims specific amount from colony"),
                                                false);
                                break;

                        case "checktax":
                                source.sendSuccess(() -> Component.literal("§6/wnt checktax [player]"), false);
                                source.sendSuccess(() -> Component.literal("§7Check stored tax revenue."), false);
                                source.sendSuccess(
                                                () -> Component.literal("§7- No arguments: Shows your colonies' tax"),
                                                false);
                                source.sendSuccess(() -> Component
                                                .literal("§7- [player]: Shows another player's tax (admin only)"),
                                                false);
                                break;

                        case "taxdebt":
                                source.sendSuccess(() -> Component.literal("§6/wnt taxdebt pay <amount> <colony>"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7Pay debt for your colony."), false);
                                source.sendSuccess(() -> Component.literal("§7Uses "
                                                + (TaxConfig.isSDMShopConversionEnabled() ? "SDMShop balance"
                                                                : "emeralds from inventory")),
                                                false);
                                break;

                        case "joinwar":
                                source.sendSuccess(() -> Component.literal("§6/wnt joinwar"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Join the current war during the join phase."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Join phase lasts "
                                                                                + TaxConfig.JOIN_PHASE_DURATION_MINUTES
                                                                                                .get()
                                                                                + " minutes."),
                                                false);
                                break;

                        case "leavewar":
                                source.sendSuccess(() -> Component.literal("§6/wnt leavewar"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Leave the current war during the join phase."),
                                                false);
                                break;

                        case "war":
                                source.sendSuccess(() -> Component.literal("§6/wnt war accept/decline <colonyId>"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7Respond to a war declaration."), false);
                                source.sendSuccess(() -> Component
                                                .literal("§7Only colony owners/officers can accept/decline wars."),
                                                false);
                                break;

                        case "peace":
                                source.sendSuccess(() -> Component.literal("§6/wnt peace whitepeace"), false);
                                source.sendSuccess(() -> Component.literal("§6/wnt peace reparations <amount>"), false);
                                source.sendSuccess(() -> Component.literal("§6/wnt peace accept/decline"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Propose or respond to peace proposals during war."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal("§7- whitepeace: End war with no reparations"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7- reparations: End war with payment"),
                                                false);
                                break;

                        case "warinfo":
                                source.sendSuccess(() -> Component.literal("§6/wnt warinfo"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Show detailed information about current wars."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Displays lives, guards, timers, and penalties."),
                                                false);
                                break;

                        case "warhistory":
                                source.sendSuccess(() -> Component.literal("§6/wnt warhistory [colony]"), false);
                                source.sendSuccess(() -> Component.literal("§7View war history for a colony."), false);
                                source.sendSuccess(() -> Component.literal("§7Shows war outcomes and results."), false);
                                break;

                        case "raidhistory":
                                source.sendSuccess(() -> Component.literal("§6/wnt raidhistory [colony]"), false);
                                source.sendSuccess(() -> Component.literal("§7View raid history for a colony."), false);
                                source.sendSuccess(() -> Component.literal("§7Shows who raided and amounts stolen."),
                                                false);
                                source.sendSuccess(() -> Component
                                                .literal("§7Officers can view their colonies, admins can view any."),
                                                false);
                                break;

                        case "warstats":
                                source.sendSuccess(() -> Component.literal("§6/wnt warstats"), false);
                                source.sendSuccess(() -> Component.literal("§7View your personal war statistics."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Shows kills, raids, amount gained, wars won, etc."),
                                                false);
                                break;

                        case "debug":
                                source.sendSuccess(() -> Component.literal("§6/wnt debug <subcommand>"), false);
                                source.sendSuccess(() -> Component.literal("§7Debug and status tools for all systems."), false);
                                source.sendSuccess(() -> Component.literal("§7Subcommands:"), false);
                                source.sendSuccess(() -> Component.literal("§7- tax [colony] §8— per-building tax/maint breakdown (own colony or admin any)"), false);
                                source.sendSuccess(() -> Component.literal("§7- officertracking [colonyId] §8— officer visit data (own colony or admin any)"), false);
                                source.sendSuccess(() -> Component.literal("§7- besiege §8— active siege and occupation status"), false);
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c- war §8— all active war debug info (admin)"), false);
                                        source.sendSuccess(() -> Component.literal("§c- guards [colony] §8— guard/tower detection (admin)"), false);
                                        source.sendSuccess(() -> Component.literal("§c- entityraid status|config|end|test|reload §8— entity raid tools (admin)"), false);
                                }
                                break;

                        // Vassal commands
                        case "vasalize":
                                source.sendSuccess(() -> Component.literal("§6/wnt vasalize <percent> <colony>"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7Offer vassalization to a colony."),
                                                false);
                                source.sendSuccess(
                                                () -> Component
                                                                .literal("§7- percent: The percentage of tax revenue to be paid to the overlord"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- colony: The name of the colony to offer vassalization to"),
                                                false);
                                break;

                        case "vassalaccept":
                                source.sendSuccess(() -> Component.literal("§6/wnt vassalaccept <colonyId>"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Accept a vassalization proposal from a colony."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- colonyId: The ID of the colony that proposed vassalization"),
                                                false);
                                break;

                        case "vassaldecline":
                                source.sendSuccess(() -> Component.literal("§6/wnt vassaldecline <colonyId>"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Decline a vassalization proposal from a colony."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- colonyId: The ID of the colony that proposed vassalization"),
                                                false);
                                break;

                        case "revoke":
                                source.sendSuccess(() -> Component.literal("§6/wnt revoke <player>"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Revoke a vassalization relationship with a player."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- player: The name of the player to revoke the relationship with"),
                                                false);
                                break;

                        case "vasals":
                                source.sendSuccess(() -> Component.literal("§6/wnt vasals"), false);
                                source.sendSuccess(() -> Component.literal("§7List your vassals."), false);
                                break;

                        // Admin commands
                        case "warstop":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt warstop <colony>"), false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Force stop a specific war by colony name."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to view this command."));
                                }
                                break;

                        case "warstopall":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt warstopall"), false);
                                        source.sendSuccess(() -> Component.literal("§7Force stop all active wars."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to view this command."));
                                }
                                break;

                        case "raidstop":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt raidstop"), false);
                                        source.sendSuccess(() -> Component.literal("§7Force stop the active raid."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to view this command."));
                                }
                                break;

                        case "taxgen":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§c/wnt taxgen disable/enable <colonyId>"),
                                                        false);
                                        source.sendSuccess(() -> Component
                                                        .literal("§7Control tax generation for specific colonies."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to view this command."));
                                }
                                break;

                        case "permissions":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt permissions <subcommand>"),
                                                        false);
                                        source.sendSuccess(() -> Component.literal(
                                                        "§7Manage general colony permissions for all players."),
                                                        false);
                                        source.sendSuccess(() -> Component.literal("§7Subcommands:"), false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7- status: Show current permissions status"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7- config: Show current configuration"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7- apply [colonyId]: Apply permissions to all/specific colony"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component
                                                                        .literal("§7- remove [colonyId]: Remove permissions from all/specific colony"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7- reload: Reload permissions based on current config"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7General permissions allow ALL players (including non-allies)"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component
                                                                        .literal("§7to toss items and pickup items within colony boundaries when enabled."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component
                                                                        .literal("§7Additional actions like block placement can be configured as needed."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to view this command."));
                                }
                                break;

                        case "claimcolony":
                                source.sendSuccess(() -> Component.literal("§6/wnt claimcolony [colony]"), false);
                                source.sendSuccess(
                                                () -> Component.literal("§7Claim an abandoned colony through combat."),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7Requirements:"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- You must have at least " + TaxConfig
                                                                                .getMinGuardsForClaimingRaid()
                                                                                + " guards"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7- Target colony must be abandoned"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- " + TaxConfig.getClaimingGracePeriodHours()
                                                                                + "-hour cooldown between claims"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7When claiming:"), false);
                                source.sendSuccess(() -> Component.literal("§7- All citizens become hostile militia"),
                                                false);
                                source.sendSuccess(() -> Component.literal("§7- Citizens get resistance effects"),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7- Mercenaries may spawn if too few defenders"),
                                                false);
                                source.sendSuccess(() -> Component
                                                .literal("§7- You must defeat all defenders to claim the colony"),
                                                false);
                                source.sendSuccess(() -> Component.literal(
                                                "§7- Claiming raid lasts " + TaxConfig.getClaimingRaidDurationMinutes()
                                                                + " minutes maximum"),
                                                false);
                                break;

                        case "listabandoned":
                                source.sendSuccess(() -> Component.literal("§6/wnt listabandoned"), false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7List all abandoned colonies that can be claimed."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Shows colony name, ID, location, and time since abandonment."),
                                                false);
                                break;

                        case "claimstatus":
                                source.sendSuccess(() -> Component.literal("§6/wnt claimstatus"), false);
                                source.sendSuccess(
                                                () -> Component.literal("§7Check your eligibility to claim colonies."),
                                                false);
                                source.sendSuccess(
                                                () -> Component.literal(
                                                                "§7Shows requirements status and cooldown time."),
                                                false);
                                break;

                        case "forceabandon":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt forceabandon <colony>"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Force abandon a colony (admin only)."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7This removes all owners and officers from the colony."),
                                                        false);
                                        source.sendSuccess(() -> Component.literal(
                                                        "§7The colony will become claimable by other players."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§cWarning: This action cannot be undone!"),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to use this command."));
                                }
                                break;

                        case "protectcolony":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt protectcolony <colony>"),
                                                        false);
                                        source.sendSuccess(() -> Component
                                                        .literal("§7Protect a colony from being claimed (admin only)."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Protected colonies cannot be claimed even when abandoned."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Useful for spawn towns, admin colonies, or special areas."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to use this command."));
                                }
                                break;

                        case "unprotectcolony":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt unprotectcolony <colony>"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Remove claiming protection from a colony (admin only)."),
                                                        false);
                                        source.sendSuccess(() -> Component
                                                        .literal("§7The colony will become claimable when abandoned."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to use this command."));
                                }
                                break;

                        case "listprotected":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt listprotected"), false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7List all colonies protected from claiming (admin only)."),
                                                        false);
                                        source.sendSuccess(() -> Component
                                                        .literal("§7Shows colony name, status, and protecting admin."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to use this command."));
                                }
                                break;

                        case "claimraidstatus":
                                if (source.hasPermission(2)) {
                                        source.sendSuccess(() -> Component.literal("§c/wnt claimraidstatus <colony>"),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Check the status of an active claiming raid (admin only)."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Shows defender counts and forces victory condition check."),
                                                        false);
                                        source.sendSuccess(
                                                        () -> Component.literal(
                                                                        "§7Useful for debugging stuck claiming raids."),
                                                        false);
                                } else {
                                        source.sendFailure(Component
                                                        .literal("§cYou don't have permission to use this command."));
                                }
                                break;

                        default:
                                source.sendFailure(Component.literal("§cUnknown command: " + command));
                                return 0;
                }

                return 1;
        }

        // Delegate methods to existing command handlers
        private static int handleWageWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                ServerPlayer attacker = ctx.getSource().getPlayerOrException();
                String colonyName = extractColonyName(StringArgumentType.getString(ctx, "colony"));
                Level level = ctx.getSource().getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        ctx.getSource().sendFailure(Component.literal("Target colony not found!"));
                        return 0;
                }
                if (!RaidManager.getActiveRaids().isEmpty()) {
                        ctx.getSource().sendFailure(
                                        Component.literal("A raid is currently active! You cannot declare war."));
                        return 0;
                }
                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
                        ctx.getSource().sendFailure(Component.literal("A war is already active for this colony!"));
                        return 0;
                }

                // Check if extortion system is enabled, if so use default percentage
                if (TaxConfig.ENABLE_EXTORTION_SYSTEM.get()) {
                        // Use default extortion percentage from config (convert from 0.0-1.0 to 1-100)
                        int defaultExtortionPercent = (int) Math
                                        .round(TaxConfig.DEFAULT_EXTORTION_PERCENTAGE.get() * 100);
                        return WarSystem.processWageWarRequestWithExtortion(attacker, targetColony, ctx.getSource(),
                                        defaultExtortionPercent);
                } else {
                        // Extortion disabled, use regular war declaration
                        return WarSystem.processWageWarRequest(attacker, targetColony, ctx.getSource());
                }
        }

        private static int handleWageWarWithExtortionCommand(CommandContext<CommandSourceStack> ctx)
                        throws CommandSyntaxException {
                ServerPlayer attacker = ctx.getSource().getPlayerOrException();
                String colonyName = extractColonyName(StringArgumentType.getString(ctx, "colony"));
                int extortionPercent = IntegerArgumentType.getInteger(ctx, "extortionPercent");
                Level level = ctx.getSource().getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        ctx.getSource().sendFailure(Component.literal("Target colony not found!"));
                        return 0;
                }
                if (!RaidManager.getActiveRaids().isEmpty()) {
                        ctx.getSource().sendFailure(
                                        Component.literal("A raid is currently active! You cannot declare war."));
                        return 0;
                }
                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
                        ctx.getSource().sendFailure(Component.literal("A war is already active for this colony!"));
                        return 0;
                }
                return WarSystem.processWageWarRequestWithExtortion(attacker, targetColony, ctx.getSource(),
                                extortionPercent);
        }

        private static int handleRaidCommand(CommandContext<CommandSourceStack> context) {
                return getRaidManager().handleRaid(context);
        }

        private static int handlePermCheckCommand(CommandContext<CommandSourceStack> ctx) {
                net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
                net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.Result result =
                        net.machiavelli.minecolonytax.permissions.PermissionsHealthCheck.run(server);
                ctx.getSource().sendSuccess(
                        () -> net.minecraft.network.chat.Component.literal(
                                "§a[PermCheck] §f" + result.summary()),
                        true);
                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }

        private static int handleBesiegeCommand(CommandContext<CommandSourceStack> ctx) {
                try {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String colonyArg = StringArgumentType.getString(ctx, "colony");

                        if (!TaxConfig.isBesiegeSystemEnabled()) {
                                player.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                        // Resolve colony by name
                        IColony target = IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                                        .filter(c -> c.getName().equalsIgnoreCase(colonyArg))
                                        .findFirst()
                                        .orElse(null);

                        if (target == null) {
                                player.sendSystemMessage(Component.literal("Colony not found: " + colonyArg)
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                        // If the colony is already besieged AND the player is the former owner -> reclaim
                        if (BesiegeManager.isColonyBesieged(target.getID())) {
                                BesiegeManager.BesiegeOccupationData occ = BesiegeManager.getOccupation(target.getID());
                                if (occ != null && player.getUUID().equals(occ.formerOwnerUUID)) {
                                        boolean started = BesiegeManager.startReclaim(target, player);
                                        return started ? 1 : 0;
                                }
                                player.sendSystemMessage(Component.literal(
                                                "This colony is already under besiege occupation. Only the former owner can reclaim it.")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                        boolean started = BesiegeManager.startBesiege(target, player);
                        return started ? 1 : 0;

                } catch (CommandSyntaxException e) {
                        ctx.getSource().sendFailure(Component.literal("You must be a player to use this command."));
                        return 0;
                } catch (Exception e) {
                        LOGGER.error("Error handling besiege command", e);
                        return 0;
                }
        }

        private static int handleBesiegeStatus(CommandContext<CommandSourceStack> ctx) {
                CommandSourceStack src = ctx.getSource();
                if (src.hasPermission(2)) {
                        return showBesiegeStatusAdmin(src);
                }
                try {
                        return showBesiegeStatusPlayer(src, src.getPlayerOrException());
                } catch (CommandSyntaxException e) {
                        src.sendFailure(Component.literal("You must be a player to use this command."));
                        return 0;
                }
        }

        private static int showBesiegeStatusAdmin(CommandSourceStack src) {
                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();

                src.sendSuccess(() -> Component.literal("=== Besiege Status (Admin) ===")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                src.sendSuccess(() -> Component.literal("Active Raids (" + raids.size() + "):")
                                .withStyle(ChatFormatting.YELLOW), false);
                if (raids.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
                } else {
                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
                                String colonyName = cm.getAllColonies().stream()
                                                .filter(c -> c.getID() == raid.colonyId)
                                                .map(IColony::getName)
                                                .findFirst().orElse("Colony#" + raid.colonyId);
                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
                                String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
                                long elapsedMin = (System.currentTimeMillis() - raid.startTime) / 60000;
                                String label = raid.isReclaim ? " (RECLAIM)" : "";
                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s \u2014 %dm elapsed%s",
                                                colonyName, raid.colonyId, besiegerName, elapsedMin, label);
                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
                        }
                }

                src.sendSuccess(() -> Component.literal("Occupations (" + occupations.size() + "):")
                                .withStyle(ChatFormatting.YELLOW), false);
                if (occupations.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
                } else {
                        for (BesiegeManager.BesiegeOccupationData occ : occupations.values()) {
                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
                                String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
                                ServerPlayer formerOwner = occ.formerOwnerUUID != null
                                                ? src.getServer().getPlayerList().getPlayer(occ.formerOwnerUUID) : null;
                                String formerOwnerName = formerOwner != null ? formerOwner.getName().getString()
                                                : (occ.formerOwnerUUID != null ? occ.formerOwnerUUID.toString() : "unknown");
                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s, former owner: %s \u2014 tribute: %d%%",
                                                occ.colonyName, occ.colonyId, besiegerName, formerOwnerName, occ.tributePercent);
                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
                        }
                }

                src.sendSuccess(() -> Component.literal("==============================")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                return 1;
        }

        private static int showBesiegeStatusPlayer(CommandSourceStack src, ServerPlayer player) {
                UUID playerUUID = player.getUUID();
                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();

                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
                                .filter(r -> {
                                        if (r.besiegingPlayerUUID.equals(playerUUID)) return true;
                                        return cm.getAllColonies().stream()
                                                        .filter(c -> c.getID() == r.colonyId)
                                                        .anyMatch(c -> {
                                                                var rank = c.getPermissions().getRank(playerUUID);
                                                                return rank != null && rank.isColonyManager();
                                                        });
                                })
                                .collect(Collectors.toList());

                List<BesiegeManager.BesiegeOccupationData> myOccupations = occupations.values().stream()
                                .filter(o -> o.besiegingPlayerUUID.equals(playerUUID)
                                                || (o.formerOwnerUUID != null && o.formerOwnerUUID.equals(playerUUID)))
                                .collect(Collectors.toList());

                if (myRaids.isEmpty() && myOccupations.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("No active besiegements for your colonies.")
                                        .withStyle(ChatFormatting.GRAY), false);
                        return 1;
                }

                src.sendSuccess(() -> Component.literal("=== Besiege Status ===")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                if (!myRaids.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("Active Raids:").withStyle(ChatFormatting.YELLOW), false);
                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
                                String colonyName = cm.getAllColonies().stream()
                                                .filter(c -> c.getID() == raid.colonyId)
                                                .map(IColony::getName)
                                                .findFirst().orElse("Colony#" + raid.colonyId);
                                long elapsedMin = (System.currentTimeMillis() - raid.startTime) / 60000;
                                long remainingMin = Math.max(0, raid.endTime - System.currentTimeMillis()) / 60000;
                                final String line;
                                if (raid.besiegingPlayerUUID.equals(playerUUID)) {
                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you are besieging \u2014 %dm elapsed, %dm remaining",
                                                        colonyName, raid.colonyId, elapsedMin, remainingMin);
                                } else {
                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
                                        String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
                                        line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s \u2014 %dm elapsed, %dm remaining",
                                                        colonyName, raid.colonyId, besiegerName, elapsedMin, remainingMin);
                                }
                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
                        }
                }

                if (!myOccupations.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("Occupied Colonies:").withStyle(ChatFormatting.YELLOW), false);
                        for (BesiegeManager.BesiegeOccupationData occ : myOccupations) {
                                final String line;
                                if (occ.besiegingPlayerUUID.equals(playerUUID)) {
                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you occupy it \u2014 tribute: %d%%",
                                                        occ.colonyName, occ.colonyId, occ.tributePercent);
                                } else {
                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
                                        String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
                                        line = String.format("  \u2022 %s (ID: %d) \u2014 occupied by %s \u2014 tribute: %d%%",
                                                        occ.colonyName, occ.colonyId, besiegerName, occ.tributePercent);
                                }
                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
                        }
                }

                src.sendSuccess(() -> Component.literal("======================")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                return 1;
        }

        private static int joinWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                WarData war = WarSystem.getActiveWarForPlayer(player);
                if (war == null) {
                        ctx.getSource()
                                        .sendFailure(Component.translatable("command.joinwar.error.none")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }
                return WarSystem.processJoinWar(player, ctx.getSource());
        }

        private static int leaveWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                WarData war = WarSystem.getActiveWarForPlayer(player);
                if (war == null) {
                        ctx.getSource()
                                        .sendFailure(Component.translatable("command.joinwar.error.none")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
                                player.getUUID().equals(war.getAttacker()) ||
                                (war.getAttackerColony() != null
                                                && player.getUUID().equals(
                                                                war.getAttackerColony().getPermissions().getOwner()))) {
                        ctx.getSource()
                                        .sendFailure(Component.translatable("command.leavewar.error.owner")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                return WarSystem.processLeaveWar(player, ctx.getSource());
        }

        private static int handleWarResponseCommand(CommandContext<CommandSourceStack> ctx, boolean accepted)
                        throws CommandSyntaxException {
                ServerPlayer executor = ctx.getSource().getPlayerOrException();
                int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
                return WarSystem.processWarResponse(executor, colonyId, accepted, ctx.getSource());
        }

        private static int suePeaceWhiteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                return getPeaceProposalManager().suePeaceWhite(ctx);
        }

        private static int suePeaceReparationsCommand(CommandContext<CommandSourceStack> ctx, int amount)
                        throws CommandSyntaxException {
                return getPeaceProposalManager().suePeaceReparations(ctx, amount);
        }

        private static int acceptPeaceCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                return getPeaceProposalManager().acceptPeace(ctx);
        }

        private static int declinePeaceCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                return getPeaceProposalManager().declinePeace(ctx);
        }

        private static int warInfoCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                WarData war = WarSystem.getActiveWarForPlayer(player);

                if (war == null) {
                        ctx.getSource().sendFailure(
                                        Component.literal("You are not currently in an active war!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                StringBuilder sb = new StringBuilder();
                String attackerColName = (war.getAttackerColony() != null) ? war.getAttackerColony().getName()
                                : "UnknownAttackerColony";
                String defenderColName = (war.getColony() != null) ? war.getColony().getName()
                                : "UnknownDefenderColony";
                String status = (war.getStatus() != null) ? war.getStatus().toString() : "UNKNOWN";

                long now = System.currentTimeMillis();
                long remaining;
                String remainingStr;

                if (war.isJoinPhaseActive()) {
                        // During join phase, show time until join phase ends
                        remaining = Math.max(0, (war.getJoinPhaseEndTime() - now) / 1000);
                        remainingStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
                } else {
                        // During active war, show time until war ends
                        long warDuration = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
                        long elapsed = (now - war.warStartTime) / 1000;
                        remaining = Math.max(0, warDuration - elapsed);
                        remainingStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
                }

                sb.append("§a§lWar Report: ").append(attackerColName).append(" vs ").append(defenderColName)
                                .append("\n");
                sb.append("§aWar ID: §f").append(war.getWarID()).append("\n");
                sb.append("§aStatus: §f").append(status).append("\n");
                sb.append("§aTime Remaining: §f").append(remainingStr).append("\n");
                sb.append("------------------------------------------\n");
                sb.append("§bAttacker Team:\n");
                war.getAttackerLives().forEach((uuid, lives) -> {
                        ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                        String name = (sp != null) ? sp.getName().getString() : "OfflinePlayer";
                        sb.append("  ").append(name).append(" - ").append(lives).append(" lives\n");
                });
                sb.append("§bDefender Team:\n");
                war.getDefenderLives().forEach((uuid, lives) -> {
                        ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                        String name = (sp != null) ? sp.getName().getString() : "OfflinePlayer";
                        sb.append("  ").append(name).append(" - ").append(lives).append(" lives\n");
                });
                sb.append("§aGuard Count:\n");
                sb.append(attackerColName).append(" = ").append(war.getRemainingAttackerGuards()).append("\n");
                sb.append(defenderColName).append(" = ").append(war.getRemainingDefenderGuards()).append("\n");
                sb.append("§6==========================\n");
                if (!war.getPenaltyReport().isEmpty()) {
                        sb.append("§cPenalty Report: §f").append(war.getPenaltyReport()).append("\n");
                }
                ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                return 1;
        }

        private static int debugWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                CommandSourceStack src = ctx.getSource();
                if (WarSystem.ACTIVE_WARS.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("No active wars.").withStyle(ChatFormatting.GRAY),
                                        false);
                        return 1;
                }
                src.sendSuccess(
                                () -> Component.literal("=== War Debug (" + WarSystem.ACTIVE_WARS.size() + " active) ===")
                                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                                false);
                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                        String atkName = war.getAttackerColony() != null ? war.getAttackerColony().getName() : "?";
                        String defName = war.getColony() != null ? war.getColony().getName() : "?";
                        String status = war.getStatus() != null ? war.getStatus().toString() : "UNKNOWN";
                        long now = System.currentTimeMillis();
                        long remaining;
                        String phase;
                        if (war.isJoinPhaseActive()) {
                                remaining = Math.max(0, (war.getJoinPhaseEndTime() - now) / 1000);
                                phase = "JOIN";
                        } else {
                                long warDuration = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
                                remaining = Math.max(0, warDuration - (now - war.warStartTime) / 1000);
                                phase = "ACTIVE";
                        }
                        final String timeStr = String.format("%dm%ds", remaining / 60, remaining % 60);
                        final String heading = "§e§l" + atkName + " vs " + defName
                                        + "§r  §7[" + phase + " | " + status + "]  " + timeStr + " left";
                        src.sendSuccess(() -> Component.literal(heading), false);

                        StringBuilder atkBuf = new StringBuilder("  §bATK: ");
                        war.getAttackerLives().forEach((uuid, lives) -> {
                                ServerPlayer sp = src.getServer().getPlayerList().getPlayer(uuid);
                                String pName = sp != null ? sp.getName().getString()
                                                : uuid.toString().substring(0, 8) + "~";
                                atkBuf.append(pName).append("(").append(lives).append("L) ");
                        });
                        if (war.getAttackerLives().isEmpty())
                                atkBuf.append("§7none");
                        final String atkLine = atkBuf.toString();
                        src.sendSuccess(() -> Component.literal(atkLine), false);

                        StringBuilder defBuf = new StringBuilder("  §aDEF: ");
                        war.getDefenderLives().forEach((uuid, lives) -> {
                                ServerPlayer sp = src.getServer().getPlayerList().getPlayer(uuid);
                                String pName = sp != null ? sp.getName().getString()
                                                : uuid.toString().substring(0, 8) + "~";
                                defBuf.append(pName).append("(").append(lives).append("L) ");
                        });
                        if (war.getDefenderLives().isEmpty())
                                defBuf.append("§7none");
                        final String defLine = defBuf.toString();
                        src.sendSuccess(() -> Component.literal(defLine), false);

                        final int atkGuards = war.getRemainingAttackerGuards();
                        final int defGuards = war.getRemainingDefenderGuards();
                        final String guardLine = "  §fGuards: §7" + atkName + "=" + atkGuards
                                        + "   " + defName + "=" + defGuards;
                        src.sendSuccess(() -> Component.literal(guardLine), false);
                }
                src.sendSuccess(() -> Component.literal("===========================")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                return 1;
        }

        private static int stopWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                String colonyName = extractColonyName(StringArgumentType.getString(ctx, "colony"));
                IColony colony = WarSystem.findColonyByName(colonyName, ctx.getSource().getLevel());
                if (colony == null) {
                        ctx.getSource().sendFailure(Component.literal("Colony not found: " + colonyName));
                        return 0;
                }

                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
                if (war == null) {
                        ctx.getSource().sendFailure(Component.literal("No active war found for colony: " + colonyName));
                        return 0;
                }

                WarSystem.endWar(colony);
                ctx.getSource().sendSuccess(() -> Component.literal("War stopped for colony: " + colonyName), false);
                return 1;
        }

        private static int stopAllWarsCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
                if (activeWars.isEmpty()) {
                        ctx.getSource().sendFailure(
                                        Component.literal("No active wars to stop.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                List<IColony> coloniesToStop = new ArrayList<>();
                for (WarData war : activeWars) {
                        coloniesToStop.add(war.getColony());
                }

                for (IColony colony : coloniesToStop) {
                        WarSystem.endWar(colony);
                }

                ctx.getSource().sendSuccess(
                                () -> Component.literal("Stopped " + coloniesToStop.size() + " active wars."),
                                false);
                return 1;
        }

        private static int stopRaidCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                var raidManager = getRaidManager();
                var activeRaids = raidManager.getActiveRaids();
                if (activeRaids.isEmpty()) {
                        ctx.getSource().sendFailure(
                                        Component.literal("No active raids to stop.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Properly end all active raids rather than just clearing the collection
                // Create a new ArrayList to avoid ConcurrentModificationException
                List<UUID> raiderIds = new ArrayList<>(activeRaids.keySet());
                for (UUID raiderId : raiderIds) {
                        ActiveRaidData raidData = activeRaids.get(raiderId);
                        if (raidData != null) {
                                RaidManager.endActiveRaid(raidData,
                                                "Raid administratively stopped via /wnt raidstop command");
                        }
                }

                // Just in case any weren't properly removed
                activeRaids.clear();

                ctx.getSource().sendSuccess(() -> Component.literal("All active raids stopped."), false);
                return 1;
        }

        // Tax command wrappers
        private static int executeClaimTax(CommandContext<CommandSourceStack> context, String colonyName, int amount)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                ServerPlayer player = source.getPlayerOrException();

                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                List<IColony> colonies = colonyManager.getAllColonies();

                boolean foundColonies = false;

                for (IColony colony : colonies) {
                        var playerRank = colony.getPermissions().getRank(player.getUUID());

                        // Skip if the colony name doesn't match
                        if (colonyName != null && !colony.getName().equalsIgnoreCase(colonyName)) {
                                continue;
                        }

                        if (playerRank != null && playerRank.isColonyManager()) {
                                foundColonies = true;

                                int claimedAmount = net.machiavelli.minecolonytax.TaxManager.claimTax(colony, amount);
                                if (claimedAmount > 0) {
                                        // Deliver the claimed tax, refunding the colony ledger if delivery fails so
                                        // taxes are never silently lost (4.x "coins never appear" fix). The helper
                                        // emits its own failure/refund message; only confirm on success.
                                        if (net.machiavelli.minecolonytax.integration.CurrencyService
                                                        .deliverClaimedTaxOrRefund(player, colony, claimedAmount)) {
                                                player.sendSystemMessage(
                                                                Component.translatable("command.claimtax.success",
                                                                                claimedAmount, colony.getName()));
                                        }
                                } else {
                                        player.sendSystemMessage(Component.translatable("command.claimtax.no_tax",
                                                        colony.getName()));
                                }
                        }
                }

                if (!foundColonies) {
                        if (colonyName != null) {
                                source.sendFailure(Component.translatable("command.claimtax.colony_not_found",
                                                colonyName));
                        } else {
                                source.sendFailure(Component.translatable("command.claimtax.no_colonies"));
                        }
                }

                return 1;
        }

        private static int checkTaxForSelf(CommandContext<CommandSourceStack> context) {
                CommandSourceStack source = context.getSource();
                ServerPlayer player;

                try {
                        player = source.getPlayerOrException();
                        var server = player.getServer();

                        if (server == null) {
                                source.sendFailure(Component.literal("Unable to retrieve server instance."));
                                return 0;
                        }

                        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                        List<IColony> colonies = colonyManager.getAllColonies();

                        boolean foundColonies = false;

                        for (IColony colony : colonies) {
                                var playerRank = colony.getPermissions().getRank(player.getUUID());

                                if (playerRank.isColonyManager()) {
                                        foundColonies = true;

                                        int taxRevenue = net.machiavelli.minecolonytax.TaxManager
                                                        .getStoredTaxForColony(colony);
                                        source.sendSuccess(
                                                        () -> Component.translatable("command.checktax.self",
                                                                        colony.getName(), taxRevenue),
                                                        false);
                                }
                        }

                        if (!foundColonies) {
                                source.sendFailure(Component.translatable("command.checktax.no_colonies"));
                        }

                        return 1;
                } catch (CommandSyntaxException e) {
                        source.sendFailure(Component.literal("An error occurred while processing the command."));
                        return 0;
                } catch (Exception e) {
                        source.sendFailure(Component.literal("An unexpected error occurred."));
                        return 0;
                }
        }

        private static int checkTaxForPlayer(CommandContext<CommandSourceStack> context, String playerArg) {
                CommandSourceStack source = context.getSource();
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                var targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerArg);

                if (targetPlayer != null) {
                        // Online player — search by UUID
                        boolean foundColonies = false;
                        for (IColony colony : colonyManager.getAllColonies()) {
                                var playerRank = colony.getPermissions().getRank(targetPlayer.getUUID());
                                if (playerRank.isColonyManager()) {
                                        foundColonies = true;
                                        int taxRevenue = net.machiavelli.minecolonytax.TaxManager
                                                        .getStoredTaxForColony(colony);
                                        source.sendSuccess(() -> Component.translatable("command.checktax.other",
                                                        playerArg, colony.getName(), taxRevenue), false);
                                }
                        }
                        if (!foundColonies) {
                                source.sendFailure(Component.translatable("command.checktax.no_colonies"));
                        }
                } else {
                        // Offline player — fall back to colony name or numeric ID
                        IColony colony = resolveColonyByNameOrId(colonyManager, playerArg);
                        if (colony == null) {
                                source.sendFailure(Component.literal("Player or colony not found: " + playerArg));
                                return 0;
                        }
                        int taxRevenue = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(colony);
                        final String colonyName = colony.getName();
                        source.sendSuccess(() -> Component.literal("[Admin] Colony '" + colonyName + "' stored tax: " + taxRevenue), false);
                }
                return 1;
        }

        private static IColony resolveColonyByNameOrId(IColonyManager mgr, String arg) {
                try {
                        int id = Integer.parseInt(arg);
                        for (IColony c : mgr.getAllColonies()) {
                                if (c.getID() == id) return c;
                        }
                } catch (NumberFormatException ignored) {}
                for (IColony c : mgr.getAllColonies()) {
                        if (c.getName().equalsIgnoreCase(arg)) return c;
                }
                return null;
        }

        private static int executeTaxDebt(CommandContext<CommandSourceStack> context, String colonyName, int amount)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                ServerPlayer player = source.getPlayerOrException();

                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                boolean foundColony = false;

                for (IColony colony : colonyManager.getAllColonies()) {
                        var playerRank = colony.getPermissions().getRank(player.getUUID());
                        if (playerRank == null || !playerRank.isColonyManager()) {
                                continue;
                        }
                        if (colonyName != null && !colony.getName().equalsIgnoreCase(colonyName)) {
                                continue;
                        }
                        foundColony = true;

                        boolean deducted = deductCurrency(player, amount);
                        if (!deducted) {
                                source.sendFailure(
                                                Component.translatable("command.taxdebt.insufficient_funds", amount));
                                continue;
                        }

                        int paid = net.machiavelli.minecolonytax.TaxManager.payTaxDebt(colony, amount);
                        source.sendSuccess(() -> Component.translatable("command.taxdebt.success", paid,
                                        colony.getName(),
                                        net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(colony)), false);
                }

                if (!foundColony) {
                        source.sendFailure(Component.translatable("command.taxdebt.colony_not_found", colonyName));
                }
                return 1;
        }

        private static boolean deductCurrency(ServerPlayer player, int amount) {
                if (TaxConfig.isSDMShopConversionEnabled()
                                && net.machiavelli.minecolonytax.integration.SDMShopIntegration.isAvailable()) {
                        long balance = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getMoney(player);
                        if (balance < amount) {
                                return false;
                        }
                        net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(player, balance - amount);
                        return true;
                } else {
                        return deductCurrencyFromInventory(player, amount);
                }
        }

        private static boolean deductCurrencyFromInventory(ServerPlayer player, int amount) {
                int remaining = amount;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        var stack = player.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                                var registryName = net.minecraftforge.registries.ForgeRegistries.ITEMS
                                                .getKey(stack.getItem());
                                if (registryName != null
                                                && registryName.toString().equals(TaxConfig.getCurrencyItemName())) {
                                        int available = stack.getCount();
                                        if (available >= remaining) {
                                                stack.shrink(remaining);
                                                return true;
                                        } else {
                                                remaining -= available;
                                                stack.setCount(0);
                                        }
                                }
                        }
                }
                return remaining <= 0;
        }

        private static int executeWarHistory(CommandContext<CommandSourceStack> ctx, String colonyArg)
                        throws CommandSyntaxException {
                CommandSourceStack src = ctx.getSource();
                ServerPlayer player;
                try {
                        player = src.getPlayerOrException();
                } catch (Exception e) {
                        src.sendFailure(Component.literal("You must be a player to run this command."));
                        return 0;
                }

                IColony colony = resolveColony(src, player, colonyArg);
                if (colony == null) {
                        src.sendFailure(Component.literal("Colony not found or you're not a manager of any colony."));
                        return 0;
                }

                var rank = colony.getPermissions().getRank(player.getUUID());
                boolean isAdmin = src.hasPermission(2);
                if (!isAdmin && (rank == null || !rank.isColonyManager())) {
                        src.sendFailure(Component.literal("You must be a colony officer or admin to view war history."));
                        return 0;
                }

                net.machiavelli.minecolonytax.data.HistoryManager.ColonyHistory history = net.machiavelli.minecolonytax.data.HistoryManager
                                .getColonyHistory(colony.getID());
                if (history == null || history.getEvents().isEmpty()) {
                        src.sendSuccess(() -> Component.literal("No war history for colony " + colony.getName()),
                                        false);
                        return 1;
                }

                src.sendSuccess(() -> Component.literal("§6War History for " + colony.getName() + ":"), false);
                for (String event : history.getEvents()) {
                        src.sendSuccess(() -> Component.literal(event), false);
                }
                return 1;
        }

        private static int executeRaidHistory(CommandContext<CommandSourceStack> ctx, String colonyArg)
                        throws CommandSyntaxException {
                CommandSourceStack src = ctx.getSource();
                ServerPlayer player;
                try {
                        player = src.getPlayerOrException();
                } catch (Exception e) {
                        src.sendFailure(Component.literal("You must be a player to run this command."));
                        return 0;
                }

                IColony colony = resolveColony(src, player, colonyArg);
                if (colony == null) {
                        src.sendFailure(Component.literal("Colony not found or you're not a manager of any colony."));
                        return 0;
                }

                // Permission check: Colony managers/officers OR admins (permission level 2+)
                var rank = colony.getPermissions().getRank(player.getUUID());
                boolean isAdmin = src.hasPermission(2);

                if (!isAdmin && (rank == null || !rank.isColonyManager())) {
                        src.sendFailure(Component
                                        .literal("You must be a colony officer or admin to view raid history."));
                        return 0;
                }

                net.machiavelli.minecolonytax.data.HistoryManager.ColonyHistory history = net.machiavelli.minecolonytax.data.HistoryManager
                                .getColonyHistory(colony.getID());
                if (history == null || history.getRaidEvents().isEmpty()) {
                        src.sendSuccess(() -> Component
                                        .literal("No raid history for colony \"" + colony.getName() + "\""), false);
                        return 1;
                }

                src.sendSuccess(() -> Component.literal("§6=== Raid History for \"" + colony.getName() + "\" ==="),
                                false);
                src.sendSuccess(() -> Component.literal(""), false);

                java.util.List<String> raidEvents = history.getRaidEvents();
                int eventCount = Math.min(raidEvents.size(), 50); // Show last 50 raids

                // Show most recent raids first
                for (int i = raidEvents.size() - 1; i >= raidEvents.size() - eventCount; i--) {
                        String event = raidEvents.get(i);
                        src.sendSuccess(() -> Component.literal(event), false);
                }

                if (raidEvents.size() > 50) {
                        src.sendSuccess(() -> Component.literal(""), false);
                        src.sendSuccess(() -> Component
                                        .literal("§7(Showing last 50 of " + raidEvents.size() + " raids)"), false);
                }

                return 1;
        }

        private static int executeColonyLog(CommandContext<CommandSourceStack> ctx, String colonyArg, int page)
                        throws CommandSyntaxException {
                CommandSourceStack src = ctx.getSource();
                ServerPlayer player;
                try {
                        player = src.getPlayerOrException();
                } catch (Exception e) {
                        src.sendFailure(Component.literal("You must be a player to run this command."));
                        return 0;
                }

                IColony colony = resolveColony(src, player, colonyArg);
                if (colony == null) {
                        src.sendFailure(Component.literal("Colony not found. Specify a colony name or stand near one."));
                        return 0;
                }

                java.util.List<net.machiavelli.minecolonytax.data.HistoryManager.ColonyLogEntry> log =
                        net.machiavelli.minecolonytax.data.HistoryManager.getLog(colony.getID());

                if (log.isEmpty()) {
                        src.sendSuccess(() -> Component.literal("No activity log entries for colony \""
                                + colony.getName() + "\" yet."), false);
                        return 1;
                }

                final int PAGE_SIZE = 10;
                int totalPages = (log.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                int p = Math.max(1, Math.min(page, totalPages));
                // Show newest-first: reverse slice
                int fromEnd = (p - 1) * PAGE_SIZE;
                int startIdx = Math.max(0, log.size() - fromEnd - PAGE_SIZE);
                int endIdx   = log.size() - fromEnd;

                src.sendSuccess(() -> Component.literal(String.format(
                        "§6=== Colony Log: %s (page %d/%d, %d entries) ===",
                        colony.getName(), p, totalPages, log.size())), false);

                String[] COLORS = { "§a", "§b", "§e", "§d", "§c" };
                java.util.Map<String, String> catColors = new java.util.HashMap<>();
                catColors.put("TAX", "§7");
                catColors.put("RAID", "§c");
                catColors.put("WAR", "§e");
                catColors.put("SPY", "§5");
                catColors.put("EVENT", "§b");
                catColors.put("TRIBUTE", "§a");
                catColors.put("TREASURY", "§6");

                for (int i = endIdx - 1; i >= startIdx; i--) {
                        net.machiavelli.minecolonytax.data.HistoryManager.ColonyLogEntry entry = log.get(i);
                        String color = catColors.getOrDefault(entry.getCategory(), "§f");
                        String line = String.format("§8[%s] %s[%-8s]§r %s",
                                entry.getFormattedTimestamp(), color, entry.getCategory(), entry.getSummary());
                        src.sendSuccess(() -> Component.literal(line), false);
                }

                if (totalPages > 1) {
                        int nextPage = p + 1;
                        String hint = nextPage <= totalPages
                                ? String.format("§7Page %d/%d — use /wnt colonylog %s %d for next page",
                                        p, totalPages, colony.getName(), nextPage)
                                : String.format("§7Page %d/%d (last)", p, totalPages);
                        src.sendSuccess(() -> Component.literal(hint), false);
                }

                return 1;
        }

        private static IColony resolveColony(CommandSourceStack src, ServerPlayer player, String colonyArg) {
                boolean isAdmin = src.hasPermission(2);
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();

                if (colonyArg != null) {
                        // Try to find by name or ID
                        try {
                                int id = Integer.parseInt(colonyArg);
                                return colonyManager.getColonyByDimension(id, player.level().dimension());
                        } catch (NumberFormatException ignored) {
                                // Not an ID, try by name — admins can access any colony
                                return colonyManager.getAllColonies().stream()
                                                .filter(c -> c.getName().equalsIgnoreCase(colonyArg))
                                                .filter(c -> isAdmin || c.getPermissions().getRank(player.getUUID())
                                                                .isColonyManager())
                                                .findFirst().orElse(null);
                        }
                } else {
                        // Default to first colony the player manages
                        return colonyManager.getAllColonies().stream()
                                        .filter(c -> c.getPermissions().getRank(player.getUUID()).isColonyManager())
                                        .findFirst().orElse(null);
                }
        }

        /** Diagnostic for the "claimed tax coins never appear" issue — shows the live state of
         *  the SDMShop/SDM-Economy integration and the caller's wallet balance. */
        private static int showSdmStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();

                boolean conversionEnabled = TaxConfig.isSDMShopConversionEnabled();
                boolean modPresent = net.machiavelli.minecolonytax.integration.SDMShopIntegration.isModPresent();
                String mode = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getIntegrationMode();
                boolean serverReady = net.machiavelli.minecolonytax.integration.SDMShopIntegration.isServerInstanceReady();
                boolean available = net.machiavelli.minecolonytax.integration.SDMShopIntegration.isAvailable();
                String currency = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getCurrencyName();
                long balance = available
                                ? net.machiavelli.minecolonytax.integration.SDMShopIntegration.getMoney(player)
                                : -1L;

                net.minecraft.network.chat.MutableComponent msg = Component
                                .literal("SDMShop / SDM-Economy Integration Status")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                msg.append(sdmStatusLine("Conversion enabled (EnableSDMShopConversion)", conversionEnabled));
                msg.append(sdmStatusLine("SDMShop/SDM-Economy mod loaded", modPresent));
                msg.append(Component.literal("\n- Integration mode: ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(mode).withStyle(ChatFormatting.WHITE)));
                msg.append(sdmStatusLine("Economy server instance ready", serverReady));
                msg.append(sdmStatusLine("Available for payouts", available));
                msg.append(Component.literal("\n- Currency id (SDMCurrencyName): ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(currency).withStyle(ChatFormatting.WHITE)));
                msg.append(Component.literal("\n- Your balance: ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(balance >= 0 ? String.valueOf(balance) : "n/a")
                                                .withStyle(ChatFormatting.WHITE)));

                if (conversionEnabled && !available) {
                        msg.append(Component.literal(
                                        "\nConversion is ON but the economy is unavailable — claimed taxes are refunded (not lost). "
                                      + "Check that SDMShop/SDM-Economy is installed, that the currency id above matches your "
                                      + "SDM-Economy config, and (single-player) that you have fully loaded into the world.")
                                        .withStyle(ChatFormatting.RED));
                }

                player.sendSystemMessage(msg);
                return 1;
        }

        private static net.minecraft.network.chat.MutableComponent sdmStatusLine(String label, boolean ok) {
                return Component.literal("\n- " + label + ": ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(ok ? "YES" : "NO")
                                                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        private static int showWarStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();

                var warDataOptional = net.machiavelli.minecolonytax.capability.PlayerWarDataCapability.get(player);

                if (warDataOptional.isPresent()) {
                        var warData = warDataOptional.resolve().get();

                        // Debug logging to help troubleshoot data persistence
                        net.machiavelli.minecolonytax.MineColonyTax.LOGGER
                                        .info("War stats retrieved for " + player.getName().getString() +
                                                        ": PlayersKilled=" + warData.getPlayersKilledInWar() +
                                                        ", RaidedColonies=" + warData.getRaidedColonies() +
                                                        ", AmountRaided=" + warData.getAmountRaided() +
                                                        ", WarsWon=" + warData.getWarsWon() +
                                                        ", WarStalemates=" + warData.getWarStalemates());

                        Component message = Component.literal("Your War Statistics")
                                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true))
                                        .append(Component.literal("\n- Players killed in wars: ")
                                                        .withStyle(ChatFormatting.YELLOW)
                                                        .append(Component.literal(
                                                                        String.valueOf(warData.getPlayersKilledInWar()))
                                                                        .withStyle(ChatFormatting.WHITE)))
                                        .append(Component.literal("\n- Colonies raided: ")
                                                        .withStyle(ChatFormatting.YELLOW)
                                                        .append(Component
                                                                        .literal(String.valueOf(
                                                                                        warData.getRaidedColonies()))
                                                                        .withStyle(ChatFormatting.WHITE)))
                                        .append(Component.literal("\n- Total amount raided: ")
                                                        .withStyle(ChatFormatting.YELLOW)
                                                        .append(Component
                                                                        .literal(String.valueOf(
                                                                                        warData.getAmountRaided()))
                                                                        .withStyle(ChatFormatting.WHITE)))
                                        .append(Component.literal("\n- Wars won: ")
                                                        .withStyle(ChatFormatting.YELLOW)
                                                        .append(Component.literal(String.valueOf(warData.getWarsWon()))
                                                                        .withStyle(ChatFormatting.WHITE)))
                                        .append(Component.literal("\n- War stalemates: ")
                                                        .withStyle(ChatFormatting.YELLOW)
                                                        .append(Component
                                                                        .literal(String.valueOf(
                                                                                        warData.getWarStalemates()))
                                                                        .withStyle(ChatFormatting.WHITE)));

                        player.sendSystemMessage(message);

                        // Mark that player data should be saved
                        player.getPersistentData().putBoolean("minecolonytax:save_requested", true);
                        net.machiavelli.minecolonytax.MineColonyTax.LOGGER.debug(
                                        "Marked player data for save after displaying war stats for "
                                                        + player.getName().getString());

                        return 1;
                } else {
                        player.sendSystemMessage(Component.literal("No war statistics available."));
                        net.machiavelli.minecolonytax.MineColonyTax.LOGGER
                                        .warn("War data capability not found for player "
                                                        + player.getName().getString());
                        return 0;
                }
        }

        // Vassal command handlers
        private static int handleVassalize(CommandContext<CommandSourceStack> ctx, int percent, String colonyName)
                        throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                IColony target = IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                                .filter(c -> c.getName().equalsIgnoreCase(colonyName))
                                .findFirst().orElse(null);
                if (target == null) {
                        ctx.getSource().sendFailure(Component.literal("Colony not found"));
                        return 0;
                }

                // Prevent players from vassalizing their own colony
                // Check if player is an officer with sufficient permissions, not just any
                // colony member
                if (target.getPermissions().getRank(player).isColonyManager() ||
                                target.getPermissions().isColonyMember(player)) {
                        ctx.getSource()
                                        .sendFailure(Component.literal(
                                                        "You cannot vassalize a colony you are a member or officer of"));
                        return 0;
                }

                return net.machiavelli.minecolonytax.vassalization.VassalManager.requestVassalization(player, target,
                                percent);
        }

        private static int handleVassalAccept(CommandContext<CommandSourceStack> ctx, int colonyId)
                        throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return net.machiavelli.minecolonytax.vassalization.VassalManager.acceptProposal(player, colonyId);
        }

        private static int handleVassalDecline(CommandContext<CommandSourceStack> ctx, int colonyId)
                        throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return net.machiavelli.minecolonytax.vassalization.VassalManager.declineProposal(player, colonyId);
        }

        private static int handleVassalRevoke(CommandContext<CommandSourceStack> ctx, String playerName)
                        throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return net.machiavelli.minecolonytax.vassalization.VassalManager.revokeRelation(player, playerName);
        }

        private static int handleVassalList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return net.machiavelli.minecolonytax.vassalization.VassalManager.listVassals(player);
        }

        // Helper method to extract colony name from quote format
        private static String extractColonyName(String input) {
                if (input.startsWith("\"") && input.endsWith("\"")) {
                        return input.substring(1, input.length() - 1);
                }
                return input; // Fallback for manually typed names without quotes
        }

        private static int debugGuardCounts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Level level = context.getSource().getLevel();

                // Find player's colony
                IColony playerColony = IColonyManager.getInstance().getColonies(level).stream()
                                .filter(c -> c.getPermissions().getPlayers().containsKey(player.getUUID()))
                                .findFirst().orElse(null);

                if (playerColony == null) {
                        context.getSource().sendFailure(Component
                                        .literal("You must be a member of a colony to use this command without specifying a colony name."));
                        return 0;
                }

                return performGuardDebug(context.getSource(), playerColony);
        }

        private static int debugGuardCountsForColony(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                Level level = context.getSource().getLevel();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                IColony targetColony = net.machiavelli.minecolonytax.WarSystem.findColonyByName(colonyName, level);
                if (targetColony == null) {
                        context.getSource().sendFailure(Component.literal("Colony '" + colonyName + "' not found!"));
                        return 0;
                }

                return performGuardDebug(context.getSource(), targetColony);
        }

        private static int performGuardDebug(CommandSourceStack source, IColony colony) {
                int guardCount = net.machiavelli.minecolonytax.WarSystem.countGuards(colony);
                int guardTowerCount = net.machiavelli.minecolonytax.WarSystem.countGuardTowers(colony);
                int minGuards = net.machiavelli.minecolonytax.TaxConfig.getMinGuardsToBeRaided();
                int minTowers = net.machiavelli.minecolonytax.TaxConfig.getMinGuardTowersToBeRaided();
                boolean protectionEnabled = net.machiavelli.minecolonytax.TaxConfig.isRaidGuardProtectionEnabled();
                boolean guardOk = guardCount >= minGuards;
                boolean towerOk = guardTowerCount >= minTowers;
                boolean immune = protectionEnabled && guardOk && towerOk;

                source.sendSuccess(
                                () -> Component.literal("=== Guard Debug: " + colony.getName()
                                                + " (ID: " + colony.getID() + ") ===")
                                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                                false);

                final String countLine = "  Guards: " + guardCount + "/" + minGuards + " "
                                + (guardOk ? "§a✓" : "§c✗") + "   Towers: " + guardTowerCount + "/" + minTowers + " "
                                + (towerOk ? "§a✓" : "§c✗");
                source.sendSuccess(() -> Component.literal(countLine), false);

                final String statusLine = "  Raid protection: "
                                + (protectionEnabled ? "" : "§7disabled — ")
                                + (immune ? "§cImmune (protected)" : "§aVulnerable to raids");
                source.sendSuccess(() -> Component.literal(statusLine), false);

                // Collect guard tower names
                List<String> towerList = new ArrayList<>();
                int totalBuildings = 0;
                for (com.minecolonies.api.colony.buildings.IBuilding b : ColonyBuildingUtil.getBuildings(colony)) {
                        totalBuildings++;
                        if (net.machiavelli.minecolonytax.WarSystem.isGuardTower(b)) {
                                towerList.add(b.getBuildingDisplayName() + "(L" + b.getBuildingLevel() + ")");
                        }
                }
                final int tb = totalBuildings;
                source.sendSuccess(() -> Component.literal("  §7Buildings scanned: " + tb), false);
                if (!towerList.isEmpty()) {
                        final String towerLine = "  §bTowers: " + String.join(", ", towerList);
                        source.sendSuccess(() -> Component.literal(towerLine), false);
                }
                if (towerList.size() != guardTowerCount) {
                        final String mismatch = "  §c⚠ Count mismatch: listed=" + towerList.size()
                                        + " vs countGuardTowers()=" + guardTowerCount;
                        source.sendSuccess(() -> Component.literal(mismatch), false);
                }
                return 1;
        }

        private static int handlePayExtortionCommand(CommandContext<CommandSourceStack> ctx)
                        throws CommandSyntaxException {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
                int extortionPercent = IntegerArgumentType.getInteger(ctx, "extortionPercent");

                // Find the target colony
                Level level = ctx.getSource().getLevel();
                IColony targetColony = IColonyManager.getInstance().getColonyByDimension(colonyId, level.dimension());
                if (targetColony == null) {
                        ctx.getSource().sendFailure(
                                        Component.literal("Colony not found!").withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Check if player has permission to pay for this colony
                Rank playerRank = targetColony.getPermissions().getRank(player.getUUID());
                boolean isAuthorized = targetColony.getPermissions().getOwner().equals(player.getUUID()) ||
                                (playerRank != null && playerRank.isColonyManager());

                if (!isAuthorized) {
                        ctx.getSource().sendFailure(
                                        Component.literal("You are not authorized to pay extortion for this colony!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Check if there's a pending war request with extortion for this colony
                Object requestObj = WarSystem.pendingWarRequests.get(colonyId);
                if (requestObj == null) {
                        ctx.getSource().sendFailure(
                                        Component.literal("No pending war request found for this colony!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                WarSystem.WarRequestWithExtortion extortionRequest = null;
                if (requestObj instanceof WarSystem.WarRequestWithExtortion) {
                        extortionRequest = (WarSystem.WarRequestWithExtortion) requestObj;
                } else {
                        ctx.getSource().sendFailure(
                                        Component.literal("This war request does not have extortion terms!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                if (extortionRequest.extortionPercent() != extortionPercent) {
                        ctx.getSource()
                                        .sendFailure(Component.literal(
                                                        "Extortion percentage mismatch! Expected: "
                                                                        + extortionRequest.extortionPercent() + "%")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Determine available funds: prefer SDMShop wallet, fallback to colony tax
                boolean sdmAvailable = SDMShopIntegration.isAvailable();
                long playerBalance = sdmAvailable ? SDMShopIntegration.getMoney(player) : 0L;
                int colonyBalance = TaxManager.getStoredTaxForColony(targetColony);
                long baseBalance = (playerBalance > 0) ? playerBalance : colonyBalance;

                if (baseBalance <= 0) {
                        ctx.getSource()
                                        .sendFailure(Component.literal(
                                                        "You have no personal balance or colony funds to pay extortion!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Calculate extortion amount against the base balance
                long extortionAmount = Math.round(baseBalance * (extortionPercent / 100.0));

                if (extortionAmount <= 0) {
                        ctx.getSource().sendFailure(
                                        Component.literal("Calculated extortion amount is too small!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Find the attacker
                if (level.getServer() == null) {
                        ctx.getSource().sendFailure(
                                        Component.literal("Server not available!").withStyle(ChatFormatting.RED));
                        return 0;
                }
                ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(extortionRequest.attacker());
                if (attacker == null) {
                        ctx.getSource().sendFailure(
                                        Component.literal("Attacker is offline! Cannot process extortion payment.")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Ensure total available funds can cover the amount
                if (sdmAvailable) {
                        if ((playerBalance + colonyBalance) < extortionAmount) {
                                ctx.getSource()
                                                .sendFailure(Component
                                                                .literal("Insufficient funds to pay extortion! Needed: "
                                                                                + extortionAmount
                                                                                + ", Available: "
                                                                                + (playerBalance + colonyBalance))
                                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }
                } else {
                        if (colonyBalance < extortionAmount) {
                                ctx.getSource()
                                                .sendFailure(Component
                                                                .literal("Colony funds are insufficient to pay extortion! Needed: "
                                                                                + extortionAmount
                                                                                + ", Available: " + colonyBalance)
                                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }
                }

                // Process the payment with SDMShop-first, then colony fallback
                long takenFromPlayer = 0L;
                int takenFromColony = 0;

                if (sdmAvailable && playerBalance > 0) {
                        takenFromPlayer = Math.min(playerBalance, extortionAmount);
                        if (takenFromPlayer > 0 && !SDMShopIntegration.removeMoney(player, takenFromPlayer)) {
                                ctx.getSource().sendFailure(
                                                Component.literal("Failed to deduct from your SDMShop balance!")
                                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }
                }

                long remaining = extortionAmount - takenFromPlayer;
                if (remaining > 0) {
                        if (colonyBalance < remaining) {
                                ctx.getSource()
                                                .sendFailure(Component
                                                                .literal("Colony funds are insufficient to cover the remaining extortion amount!")
                                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }
                        takenFromColony = (int) remaining;
                        TaxManager.adjustTax(targetColony, -takenFromColony);
                }

                // Credit the attacker accordingly
                if (takenFromPlayer > 0) {
                        SDMShopIntegration.addMoney(attacker, takenFromPlayer);
                }
                if (takenFromColony > 0) {
                        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
                                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
                                        .findFirst().orElse(null);
                        if (attackerColony != null) {
                                TaxManager.adjustTax(attackerColony, takenFromColony);
                        } else if (sdmAvailable) {
                                // Fallback: if attacker has no colony, deposit to their wallet when SDMShop is
                                // available
                                SDMShopIntegration.addMoney(attacker, takenFromColony);
                        }
                }

                // Grant immunity to prevent repeated extortion
                WarSystem.grantExtortionImmunity(colonyId);

                // Remove the pending war request
                WarSystem.pendingWarRequests.remove(colonyId);

                // Notify both parties
                MutableComponent successMessage = Component.literal("💰 EXTORTION PAID! 💰")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                .append(Component
                                                .literal("\n" + targetColony.getName() + " has paid "
                                                                + String.format("%.2f", extortionAmount)
                                                                + " coins to avoid war!")
                                                .withStyle(ChatFormatting.GREEN));

                player.sendSystemMessage(successMessage);
                attacker.sendSystemMessage(Component
                                .literal("💰 " + targetColony.getName() + " has paid you "
                                                + String.format("%.2f", extortionAmount)
                                                + " coins to avoid war!")
                                .withStyle(ChatFormatting.GOLD));

                // Broadcast to server
                if (level.getServer() != null) {
                        MutableComponent broadcastMsg = Component.literal("💰 EXTORTION PAID! 💰")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                        .append(Component.literal("\n----------------------------------------")
                                                        .withStyle(ChatFormatting.DARK_GRAY))
                                        .append(Component.literal("\nThe colony of ").withStyle(ChatFormatting.YELLOW))
                                        .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
                                                        ChatFormatting.BOLD))
                                        .append(Component.literal(" has paid ").withStyle(ChatFormatting.YELLOW))
                                        .append(Component.literal(String.format("%.2f", extortionAmount) + " coins")
                                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                                        .append(Component.literal(" to ").withStyle(ChatFormatting.YELLOW))
                                        .append(Component.literal(attacker.getName().getString()).withStyle(
                                                        ChatFormatting.DARK_RED,
                                                        ChatFormatting.BOLD))
                                        .append(Component
                                                        .literal(" to avoid war! Peace is maintained through commerce.")
                                                        .withStyle(ChatFormatting.GREEN))
                                        .append(Component.literal("\n----------------------------------------")
                                                        .withStyle(ChatFormatting.DARK_GRAY));

                        level.getServer().getPlayerList().broadcastSystemMessage(broadcastMsg, false);
                }

                LOGGER.info("Extortion payment processed: {} paid {} coins to {} to avoid war on colony {}",
                                player.getName().getString(), extortionAmount, attacker.getName().getString(),
                                targetColony.getName());

                return 1;
        }

        // Colony claiming command handlers
        private static int showClaimableColonies(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();

                List<IColony> claimableColonies = net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                                .getClaimableColonies(player.getServer());

                if (claimableColonies.isEmpty()) {
                        player.sendSystemMessage(
                                        Component.literal("No abandoned colonies are currently available for claiming.")
                                                        .withStyle(ChatFormatting.YELLOW));
                        return 1;
                }

                player.sendSystemMessage(Component.literal("Available abandoned colonies:")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                for (IColony colony : claimableColonies) {
                        int citizenCount = colony.getCitizenManager().getCurrentCitizenCount();
                        int guardCount = WarSystem.countGuards(colony);

                        Component colonyInfo = Component.literal("• ")
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal(colony.getName())
                                                        .withStyle(ChatFormatting.GOLD))
                                        .append(Component.literal(" (ID: " + colony.getID() + ")")
                                                        .withStyle(ChatFormatting.GRAY))
                                        .append(Component
                                                        .literal(" - Citizens: " + citizenCount + ", Guards: "
                                                                        + guardCount)
                                                        .withStyle(ChatFormatting.WHITE));

                        player.sendSystemMessage(colonyInfo);
                }

                // Show player's eligibility status
                player.sendSystemMessage(Component.literal(""));
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult eligibility = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .checkClaimingRequirements(player);

                if (eligibility.canClaim) {
                        player.sendSystemMessage(Component.literal("✓ You are eligible to claim colonies!")
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                        player.sendSystemMessage(
                                        Component.literal("Use '/wnt claimcolony <colony>' to start a claiming raid!")
                                                        .withStyle(ChatFormatting.GREEN));
                } else {
                        player.sendSystemMessage(
                                        Component.literal("✗ You cannot claim colonies: " + eligibility.message)
                                                        .withStyle(ChatFormatting.RED));
                        player.sendSystemMessage(
                                        Component.literal(
                                                        "Meet the requirements first, then use '/wnt claimcolony <colony>'")
                                                        .withStyle(ChatFormatting.YELLOW));
                }

                return 1;
        }

        private static int handleClaimColony(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                // Find the target colony
                Level level = context.getSource().getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        player.sendSystemMessage(Component.literal("Colony '" + colonyName + "' not found!")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Check if the player is already trying to claim this colony
                if (net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .isColonyUnderClaimingRaid(targetColony.getID())) {
                        player.sendSystemMessage(
                                        Component.literal("A claiming raid is already in progress for this colony!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Check if player meets claiming requirements (pass target colony for former
                // owner/officer bypass)
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult requirementResult = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .checkClaimingRequirements(player, targetColony);

                if (!requirementResult.canClaim) {
                        player.sendSystemMessage(Component.literal("Cannot claim colony: " + requirementResult.message)
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Start the claiming raid
                boolean success = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .startClaimingRaid(targetColony, player);

                if (success) {
                        player.sendSystemMessage(
                                        Component.literal("Claiming raid started for colony " + targetColony.getName()
                                                        + "!")
                                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                                        .append(Component.literal(
                                                                        "\nDefeat all defenders to claim the colony!")
                                                                        .withStyle(ChatFormatting.YELLOW)));
                        return 1;
                } else {
                        return 0;
                }
        }

        private static int listAbandonedColonies(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();

                List<IColony> abandonedColonies = net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                                .getClaimableColonies(player.getServer());

                if (abandonedColonies.isEmpty()) {
                        player.sendSystemMessage(Component.literal("No abandoned colonies found.")
                                        .withStyle(ChatFormatting.YELLOW));
                        return 1;
                }

                player.sendSystemMessage(Component.literal("=== Abandoned Colonies ===")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                for (IColony colony : abandonedColonies) {
                        int citizenCount = colony.getCitizenManager().getCurrentCitizenCount();
                        int guardCount = WarSystem.countGuards(colony);
                        int lastContactHours = colony.getLastContactInHours();

                        // Check if colony is protected
                        boolean isProtected = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                        .isColonyProtected(colony.getID());
                        String protectedBy = isProtected
                                        ? net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                                        .getProtectedBy(colony.getID())
                                        : null;

                        MutableComponent colonyDetails = Component.literal("Colony: ")
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal(colony.getName())
                                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                                        .append(Component.literal("\n  ID: " + colony.getID())
                                                        .withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal("\n  Location: " + colony.getCenter().getX() + ", " +
                                                        colony.getCenter().getY() + ", " + colony.getCenter().getZ())
                                                        .withStyle(ChatFormatting.GRAY))
                                        .append(Component
                                                        .literal("\n  Citizens: " + citizenCount + ", Guards: "
                                                                        + guardCount)
                                                        .withStyle(ChatFormatting.WHITE))
                                        .append(Component
                                                        .literal("\n  Last contact: " + lastContactHours + " hours ago")
                                                        .withStyle(ChatFormatting.RED))
                                        .append(Component.literal("\n  Status: ")
                                                        .withStyle(ChatFormatting.WHITE));

                        if (isProtected) {
                                colonyDetails.append(Component.literal("Protected (by " + protectedBy + ")")
                                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                        } else {
                                colonyDetails.append(Component.literal("Claimable")
                                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                        }

                        player.sendSystemMessage(colonyDetails);
                        player.sendSystemMessage(Component.literal(""));
                }

                player.sendSystemMessage(Component.literal("Use '/wnt claimcolony <colony>' to claim a colony!")
                                .withStyle(ChatFormatting.GREEN));

                return 1;
        }

        private static int checkClaimingStatus(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();

                // Check claiming requirements
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult eligibility = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .checkClaimingRequirements(player);

                player.sendSystemMessage(Component.literal("=== Colony Claiming Status ===")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                if (eligibility.canClaim) {
                        player.sendSystemMessage(Component.literal("✓ You are eligible to claim colonies!")
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                        player.sendSystemMessage(
                                        Component.literal("Use '/wnt claimcolony <colony>' to start a claiming raid!")
                                                        .withStyle(ChatFormatting.GREEN));
                } else {
                        player.sendSystemMessage(
                                        Component.literal("✗ You cannot claim colonies: " + eligibility.message)
                                                        .withStyle(ChatFormatting.RED));

                        // Show remaining cooldown time if that's the issue
                        long remainingGracePeriod = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                        .getRemainingGracePeriod(player.getUUID());

                        if (remainingGracePeriod > 0) {
                                long remainingHours = remainingGracePeriod / (60 * 60 * 1000);
                                long remainingMinutes = (remainingGracePeriod % (60 * 60 * 1000)) / (60 * 1000);

                                String timeRemaining = remainingHours > 0
                                                ? remainingHours + "h " + remainingMinutes + "m"
                                                : remainingMinutes + "m";

                                player.sendSystemMessage(Component.literal("Cooldown remaining: " + timeRemaining)
                                                .withStyle(ChatFormatting.YELLOW));
                        } else {
                                player.sendSystemMessage(
                                                Component.literal(
                                                                "Meet the requirements first, then use '/wnt claimcolony <colony>'")
                                                                .withStyle(ChatFormatting.YELLOW));
                        }
                }

                return 1;
        }

        private static int checkClaimingRaidStatus(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                // Find the target colony
                Level level = context.getSource().getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        source.sendSuccess(() -> Component.literal("Colony '" + colonyName + "' not found!")
                                        .withStyle(ChatFormatting.RED), false);
                        return 0;
                }

                // Check if there's an active claiming raid
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRaidData raidData = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .getClaimingRaid(targetColony.getID());

                if (raidData == null) {
                        source.sendSuccess(() -> Component
                                        .literal("No active claiming raid for colony " + targetColony.getName())
                                        .withStyle(ChatFormatting.YELLOW), false);
                        return 1;
                }

                // Count defenders
                int aliveCitizensCount = 0;
                int aliveMercenariesCount = 0;

                for (Integer citizenId : raidData.hostileCitizens) {
                        ICitizenData citizenData = targetColony.getCitizenManager().getCivilian(citizenId);
                        if (citizenData != null && citizenData.getEntity().isPresent() &&
                                        citizenData.getEntity().get().isAlive()) {
                                aliveCitizensCount++;
                        }
                }

                for (net.minecraft.world.entity.Entity mercenary : raidData.spawnedMercenaries) {
                        if (mercenary.isAlive()) {
                                aliveMercenariesCount++;
                        }
                }

                final int aliveCitizens = aliveCitizensCount;
                final int aliveMercenaries = aliveMercenariesCount;
                final int totalDefenders = aliveCitizens + aliveMercenaries;

                source.sendSuccess(() -> Component
                                .literal("=== Claiming Raid Status for " + targetColony.getName() + " ===")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                source.sendSuccess(() -> Component.literal("Claimer: " + raidData.claimingPlayerId)
                                .withStyle(ChatFormatting.YELLOW), false);
                source.sendSuccess(() -> Component.literal("Defenders remaining: " + totalDefenders)
                                .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  - Citizens: " + aliveCitizens)
                                .withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal("  - Mercenaries: " + aliveMercenaries)
                                .withStyle(ChatFormatting.GRAY), false);

                long remaining = raidData.getRemainingTime();
                int minutes = (int) (remaining / 60000);
                int seconds = (int) ((remaining % 60000) / 1000);
                source.sendSuccess(() -> Component
                                .literal("Time remaining: " + String.format("%02d:%02d", minutes, seconds))
                                .withStyle(ChatFormatting.AQUA), false);

                // Force check victory condition
                source.sendSuccess(() -> Component.literal("Forcing victory condition check...")
                                .withStyle(ChatFormatting.GREEN), false);
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .forceCheckVictoryCondition(targetColony.getID());

                // Force refresh boss bar
                source.sendSuccess(() -> Component.literal("Forcing boss bar refresh...")
                                .withStyle(ChatFormatting.GREEN), false);
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .forceRefreshBossBar(targetColony.getID());

                // Debug claiming raid
                source.sendSuccess(() -> Component.literal("Debug info logged to console...")
                                .withStyle(ChatFormatting.AQUA), false);
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.debugClaimingRaid(targetColony.getID());

                return 1;
        }

        private static int protectColonyFromClaiming(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                // Find the target colony
                Level level = context.getSource().getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        source.sendSuccess(() -> Component.literal("Colony '" + colonyName + "' not found!")
                                        .withStyle(ChatFormatting.RED), false);
                        return 0;
                }

                // Check if already protected
                if (net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .isColonyProtected(targetColony.getID())) {
                        String protectedBy = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                        .getProtectedBy(targetColony.getID());
                        source.sendSuccess(() -> Component
                                        .literal("Colony " + targetColony.getName() + " is already protected by "
                                                        + protectedBy)
                                        .withStyle(ChatFormatting.YELLOW), false);
                        return 1;
                }

                // Protect the colony
                String adminName = source.getTextName();
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.protectColony(targetColony.getID(),
                                adminName);

                source.sendSuccess(
                                () -> Component.literal(
                                                "Colony " + targetColony.getName() + " is now protected from claiming!")
                                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                                false);
                source.sendSuccess(() -> Component.literal("This colony cannot be claimed even when abandoned.")
                                .withStyle(ChatFormatting.GRAY), false);

                return 1;
        }

        private static int unprotectColonyFromClaiming(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                // Find the target colony
                Level level = context.getSource().getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        source.sendSuccess(() -> Component.literal("Colony '" + colonyName + "' not found!")
                                        .withStyle(ChatFormatting.RED), false);
                        return 0;
                }

                // Check if protected
                if (!net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .isColonyProtected(targetColony.getID())) {
                        source.sendSuccess(() -> Component
                                        .literal("Colony " + targetColony.getName() + " is not protected.")
                                        .withStyle(ChatFormatting.YELLOW), false);
                        return 1;
                }

                // Unprotect the colony
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.unprotectColony(targetColony.getID());

                source.sendSuccess(() -> Component.literal("Colony " + targetColony.getName() + " protection removed!")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
                source.sendSuccess(() -> Component.literal("This colony can now be claimed when abandoned.")
                                .withStyle(ChatFormatting.GRAY), false);

                return 1;
        }

        private static int listProtectedColonies(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();

                Map<Integer, String> protectedColonies = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .getProtectedColonies();

                if (protectedColonies.isEmpty()) {
                        source.sendSuccess(() -> Component.literal("No colonies are currently protected from claiming.")
                                        .withStyle(ChatFormatting.YELLOW), false);
                        return 1;
                }

                source.sendSuccess(() -> Component.literal("=== Protected Colonies ===")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                for (Map.Entry<Integer, String> entry : protectedColonies.entrySet()) {
                        int colonyId = entry.getKey();
                        String protectedBy = entry.getValue();

                        // Try to get colony info
                        IColony colony = null;
                        try {
                                colony = com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager()
                                                .getColonyByWorld(colonyId, null);
                        } catch (Exception e) {
                                // Colony might not exist anymore
                        }

                        if (colony != null) {
                                final String colonyName = colony.getName();
                                final boolean isAbandoned = net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                                                .isColonyAbandoned(colony);

                                source.sendSuccess(() -> Component.literal("Colony: ")
                                                .withStyle(ChatFormatting.YELLOW)
                                                .append(Component.literal(colonyName)
                                                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                                                .append(Component.literal(" (ID: " + colonyId + ")")
                                                                .withStyle(ChatFormatting.GRAY))
                                                .append(Component.literal("\n  Status: ")
                                                                .withStyle(ChatFormatting.WHITE)
                                                                .append(Component
                                                                                .literal(isAbandoned ? "Abandoned"
                                                                                                : "Active")
                                                                                .withStyle(isAbandoned
                                                                                                ? ChatFormatting.RED
                                                                                                : ChatFormatting.GREEN)))
                                                .append(Component.literal("\n  Protected by: " + protectedBy)
                                                                .withStyle(ChatFormatting.AQUA)),
                                                false);
                        } else {
                                source.sendSuccess(() -> Component.literal("Colony ID: " + colonyId + " (Not Found)")
                                                .withStyle(ChatFormatting.GRAY)
                                                .append(Component.literal("\n  Protected by: " + protectedBy)
                                                                .withStyle(ChatFormatting.AQUA)),
                                                false);
                        }

                        source.sendSuccess(() -> Component.literal(""), false);
                }

                return 1;
        }

        private static int handleForceAbandonColony(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                if (!source.hasPermission(2)) {
                        source.sendFailure(
                                        Component.literal("You don't have permission to use this command!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));
                ServerPlayer admin = source.getPlayerOrException();

                // Find the target colony
                IColony targetColony = WarSystem.findColonyByName(colonyName, admin.level());
                if (targetColony == null) {
                        source.sendFailure(
                                        Component.literal("Colony '" + colonyName + "' not found!")
                                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Check if colony is already abandoned
                if (net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.isColonyAbandoned(targetColony)) {
                        source.sendFailure(Component
                                        .literal("Colony '" + targetColony.getName() + "' is already abandoned!")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                try {
                        // Get all current owners and officers for notification
                        List<UUID> managersToNotify = new ArrayList<>();
                        for (ColonyPlayer colonyPlayer : targetColony.getPermissions().getPlayers().values()) {
                                if (colonyPlayer.getRank().isColonyManager()) {
                                        managersToNotify.add(colonyPlayer.getID());
                                }
                        }

                        // Force abandon the colony (remove all owners and officers)
                        if (net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.forceAbandonColony(
                                        targetColony,
                                        admin.getServer(), admin.getName().getString())) {
                                source.sendSuccess(
                                                () -> Component.literal("Successfully force abandoned colony '"
                                                                + targetColony.getName() + "'!")
                                                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                                                true);

                                // Notify the admin about the details
                                source.sendSuccess(() -> Component.literal("Colony ID: " + targetColony.getID() +
                                                ", Notified " + managersToNotify.size() + " former managers.")
                                                .withStyle(ChatFormatting.GRAY), false);

                                return 1;
                        } else {
                                source.sendFailure(Component
                                                .literal("Failed to abandon colony '" + targetColony.getName() + "'!")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                } catch (Exception e) {
                        source.sendFailure(Component.literal("Error while abandoning colony: " + e.getMessage())
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }
        }

        /**
         * Handle cleanup of [abandoned] entries command.
         */
        private static int handleCleanupAbandonedEntries(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();

                source.sendSuccess(() -> Component
                                .literal("Starting cleanup of [abandoned] entries across all colonies...")
                                .withStyle(ChatFormatting.YELLOW), false);

                try {
                        // Run the cleanup
                        net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                                        .cleanupAllColoniesAbandonedEntries();

                        source.sendSuccess(() -> Component
                                        .literal("Cleanup completed successfully! Check server logs for details.")
                                        .withStyle(ChatFormatting.GREEN), false);

                } catch (Exception e) {
                        source.sendFailure(Component.literal("Cleanup failed: " + e.getMessage())
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                return 1;
        }

        /**
         * Handle debug boss bar command.
         */
        private static int handleDebugBossBar(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                // Find the target colony
                Level level = source.getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        source.sendFailure(Component.literal("Colony '" + colonyName + "' not found!")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                // Debug claiming raid status
                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.debugClaimingRaid(targetColony.getID());

                // Try to force refresh boss bar if there's an active raid
                if (net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                .isColonyUnderClaimingRaid(targetColony.getID())) {
                        net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                                        .forceRefreshBossBar(targetColony.getID());
                        source.sendSuccess(() -> Component
                                        .literal("Forced boss bar refresh for colony '" + colonyName
                                                        + "'. Check server logs for debug info.")
                                        .withStyle(ChatFormatting.GREEN), false);
                } else {
                        source.sendSuccess(() -> Component
                                        .literal("No active claiming raid for colony '" + colonyName
                                                        + "'. Debug info logged.")
                                        .withStyle(ChatFormatting.YELLOW), false);
                }

                return 1;
        }

        /**
         * Handle force cleanup of a specific colony.
         */
        private static int handleForceCleanupColony(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();
                String colonyName = extractColonyName(StringArgumentType.getString(context, "colony"));

                // Find the target colony
                Level level = source.getLevel();
                IColony targetColony = WarSystem.findColonyByName(colonyName, level);

                if (targetColony == null) {
                        source.sendFailure(Component.literal("Colony '" + colonyName + "' not found!")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                source.sendSuccess(() -> Component
                                .literal("Starting force cleanup of [abandoned] entries for colony '" + colonyName
                                                + "'...")
                                .withStyle(ChatFormatting.YELLOW), false);

                try {
                        // Get player count before cleanup
                        int playersBefore = targetColony.getPermissions().getPlayers().size();

                        // Run targeted cleanup
                        java.lang.reflect.Method cleanupMethod = net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.class
                                        .getDeclaredMethod("cleanupAbandonedEntries",
                                                        com.minecolonies.api.colony.permissions.IPermissions.class);
                        cleanupMethod.setAccessible(true);
                        cleanupMethod.invoke(null, targetColony.getPermissions());

                        int playersAfter = targetColony.getPermissions().getPlayers().size();
                        int removedEntries = playersBefore - playersAfter;

                        if (removedEntries > 0) {
                                source.sendSuccess(() -> Component
                                                .literal("Force cleanup completed! Removed " + removedEntries
                                                                + " problematic entries from colony '" + colonyName
                                                                + "'.")
                                                .withStyle(ChatFormatting.GREEN), false);
                        } else {
                                source.sendSuccess(() -> Component
                                                .literal(
                                                                "Force cleanup completed! No problematic entries found in colony '"
                                                                                + colonyName + "'.")
                                                .withStyle(ChatFormatting.YELLOW), false);
                        }

                } catch (Exception e) {
                        source.sendFailure(Component.literal("Force cleanup failed: " + e.getMessage())
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                return 1;
        }

        /**
         * Handle emergency fix command - fixes ALL the issues at once.
         */
        private static int handleEmergencyFix(CommandContext<CommandSourceStack> context)
                        throws CommandSyntaxException {
                CommandSourceStack source = context.getSource();

                source.sendSuccess(() -> Component.literal("Emergency fix started...")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);

                try {
                        source.sendSuccess(() -> Component.literal("Step 0: Fixing null owners...")
                                        .withStyle(ChatFormatting.YELLOW), false);
                        ColonyAbandonmentManager.emergencyFixAllNullOwners();

                        // STEP 1: Force cleanup all [abandoned] entries AND fix null owners
                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "Step 1: Cleaning up [abandoned] entries AND fixing null owners...")
                                                        .withStyle(ChatFormatting.YELLOW),
                                        false);
                        ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();

                        // STEP 2: Fix all abandoned colonies with system owners
                        source.sendSuccess(() -> Component.literal("Step 2: Fixing abandoned colonies...")
                                        .withStyle(ChatFormatting.YELLOW), false);
                        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                        final int[] fixedColonies = { 0 }; // Use array to allow modification in lambda

                        for (IColony colony : colonyManager.getAllColonies()) {
                                try {
                                        IPermissions permissions = colony.getPermissions();
                                        UUID owner = permissions.getOwner();

                                        // Check if colony has [abandoned] issues or no owner
                                        boolean needsFix = false;
                                        final String[] issue = { "" };

                                        if (owner == null) {
                                                needsFix = true;
                                                issue[0] = "null owner";
                                        } else {
                                                // Check for problematic player entries
                                                for (ColonyPlayer player : permissions.getPlayers().values()) {
                                                        if (player.getName() != null &&
                                                                        (player.getName().contains("[abandoned]") ||
                                                                                        player.getName().toLowerCase()
                                                                                                        .contains("abandoned"))) {
                                                                needsFix = true;
                                                                issue[0] = "has [abandoned] entries";
                                                                break;
                                                        }
                                                }
                                        }

                                        if (needsFix) {
                                                final String colonyName = colony.getName();
                                                final String fixIssue = issue[0];
                                                source.sendSuccess(() -> Component
                                                                .literal("  Fixing " + colonyName + " - " + fixIssue)
                                                                .withStyle(ChatFormatting.AQUA), false);

                                                // Apply emergency fix using our new system
                                                ColonyAbandonmentManager.cleanupAbandonedEntries(permissions);

                                                // If still no valid owner, create system owner
                                                if (permissions.getOwner() == null ||
                                                                ColonyAbandonmentManager.isSystemOwner(
                                                                                permissions.getOwner())) {

                                                        UUID systemOwner = ColonyAbandonmentManager.createSystemOwner();
                                                        permissions.addPlayer(systemOwner, "[SYSTEM_ABANDONED]",
                                                                        permissions.getRankOwner());

                                                        // CRITICAL FIX: ensure the system owner holds the OWNER rank so
                                                        // getOwner() resolves (prevents GUI crashes). Version-stable
                                                        // setPlayerRank, not reflected setOwner — MineColonies changed
                                                        // setOwner(UUID) -> setOwner(Player) in 1.1.1237, breaking the
                                                        // old reflection. [1.21-PORT] re-verify — see PORTING_NOTES.md.
                                                        try {
                                                                // Null-world guard (world-brick hardening): never pass a null
                                                                // Level into MineColonies' permission system.
                                                                if (colony.getWorld() != null) {
                                                                        permissions.setPlayerRank(systemOwner,
                                                                                        permissions.getRankOwner(),
                                                                                        colony.getWorld());
                                                                        source.sendSuccess(() -> Component
                                                                                        .literal("    Set system owner as actual owner to prevent GUI crashes")
                                                                                        .withStyle(ChatFormatting.GREEN),
                                                                                        false);
                                                                } else {
                                                                        source.sendFailure(Component
                                                                                        .literal("    Skipped owner assignment - colony world not loaded")
                                                                                        .withStyle(ChatFormatting.YELLOW));
                                                                }
                                                        } catch (Exception e) {
                                                                source.sendFailure(Component
                                                                                .literal("    WARNING: Could not set actual owner - GUI may crash!")
                                                                                .withStyle(ChatFormatting.RED));
                                                        }

                                                        // Set all real players to neutral with no permissions
                                                        Rank neutralRank = permissions.getRankNeutral();
                                                        for (UUID playerId : permissions.getPlayers().keySet()) {
                                                                if (!ColonyAbandonmentManager.isSystemOwner(playerId)
                                                                                && colony.getWorld() != null) {
                                                                        permissions.setPlayerRank(playerId, neutralRank,
                                                                                        colony.getWorld());
                                                                }
                                                        }

                                                        // Disable all griefing permissions for neutral players
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS,
                                                                        false);
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS,
                                                                        false);
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK,
                                                                        false);
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER,
                                                                        false);
                                                }

                                                fixedColonies[0]++;
                                        }
                                } catch (Exception e) {
                                        final String colonyName = colony.getName();
                                        source.sendFailure(Component
                                                        .literal("  Error fixing " + colonyName + ": " + e.getMessage())
                                                        .withStyle(ChatFormatting.RED));
                                }
                        }

                        // STEP 3: End any failed claiming raids
                        source.sendSuccess(() -> Component.literal("Step 3: Cleaning up failed claiming raids...")
                                        .withStyle(ChatFormatting.YELLOW), false);
                        ColonyClaimingRaidManager.cleanupAllFailedRaids();

                        source.sendSuccess(() -> Component.literal("Emergency fix complete.")
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
                        source.sendSuccess(() -> Component.literal("Fixed " + fixedColonies[0] + " colonies")
                                        .withStyle(ChatFormatting.GREEN), false);
                        source.sendSuccess(() -> Component.literal("Try your claiming raids again!")
                                        .withStyle(ChatFormatting.GREEN), false);

                        return 1;

                } catch (Exception e) {
                        source.sendFailure(Component.literal("Emergency fix failed: " + e.getMessage())
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                        return 0;
                }
        }

        private static int handleFixNullOwners(CommandContext<CommandSourceStack> context) {
                CommandSourceStack source = context.getSource();

                source.sendSuccess(() -> Component.literal("Fixing null colony owners...")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);

                try {
                        source.sendSuccess(() -> Component.literal("Scanning all colonies for null owners...")
                                        .withStyle(ChatFormatting.YELLOW), false);

                        ColonyAbandonmentManager.emergencyFixAllNullOwners();

                        source.sendSuccess(() -> Component.literal("Null owner fix completed.")
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);

                        source.sendSuccess(
                                        () -> Component.literal(
                                                        "All colonies now have valid owners - GUI crashes should be prevented!")
                                                        .withStyle(ChatFormatting.GREEN),
                                        false);

                } catch (Exception e) {
                        source.sendFailure(Component.literal("Error during null owner fix: " + e.getMessage())
                                        .withStyle(ChatFormatting.RED));
                        LOGGER.error("Error during /wnt fixnullowners command", e);
                }

                return 1;
        }

        /**
         * Debug command to show detailed tax breakdown for a colony
         */
        private static int debugTaxBreakdown(CommandContext<CommandSourceStack> context, String colonyName)
                        throws CommandSyntaxException {
                ServerPlayer player = context.getSource().getPlayerOrException();
                CommandSourceStack source = context.getSource();

                // Find colony by name
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                IColony tempColony = null;
                for (IColony c : colonyManager.getAllColonies()) {
                        if (c.getName().equalsIgnoreCase(colonyName)) {
                                tempColony = c;
                                break;
                        }
                }

                if (tempColony == null) {
                        source.sendFailure(Component.literal("Colony not found: " + colonyName)
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                final IColony colony = tempColony; // Make final for lambda capture

                try {
                        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                        source.sendSuccess(() -> Component.literal("📊 TAX DEBUG BREAKDOWN: " + colony.getName())
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                        // Current tax balance
                        int currentBalance = TaxManager.getStoredTaxForColony(colony);
                        source.sendSuccess(() -> Component.literal("Current Balance: " + currentBalance)
                                        .withStyle(currentBalance >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED),
                                        false);

                        // Calculate happiness modifier
                        double colonyAvgHappiness = TaxManager.calculateColonyAverageHappiness(colony);
                        double happinessMultiplier = TaxConfig.calculateHappinessTaxMultiplier(colonyAvgHappiness);
                        boolean happinessEnabled = TaxConfig.isHappinessTaxModifierEnabled();

                        source.sendSuccess(() -> Component.literal("\n🎭 Happiness Modifier:")
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
                        source.sendSuccess(() -> Component.literal("  Enabled: " + (happinessEnabled ? "YES" : "NO"))
                                        .withStyle(ChatFormatting.WHITE), false);
                        if (happinessEnabled) {
                                source.sendSuccess(
                                                () -> Component.literal(String.format("  Avg Happiness: %.2f/10.0",
                                                                colonyAvgHappiness))
                                                                .withStyle(ChatFormatting.WHITE),
                                                false);
                                source.sendSuccess(() -> Component
                                                .literal(String.format("  Multiplier: %.2fx (%.0f%%)",
                                                                happinessMultiplier,
                                                                happinessMultiplier * 100))
                                                .withStyle(happinessMultiplier > 1.0 ? ChatFormatting.GREEN
                                                                : happinessMultiplier < 1.0 ? ChatFormatting.RED
                                                                                : ChatFormatting.YELLOW),
                                                false);
                        }

                        // Count guard towers
                        int guardTowerCount = 0;
                        for (com.minecolonies.api.colony.buildings.IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                                if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                                        String displayName = building.getBuildingDisplayName();
                                        String className = building.getClass().getName().toLowerCase();
                                        String toString = building.toString().toLowerCase();

                                        if ((displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) ||
                                                        className.contains("guardtower") ||
                                                        toString.contains("guardtower") ||
                                                        toString.contains("guard_tower")) {
                                                guardTowerCount++;
                                        }
                                }
                        }

                        final int finalGuardTowerCount = guardTowerCount;
                        int requiredGuardTowers = TaxConfig.getRequiredGuardTowersForBoost();
                        double guardBoostPercentage = TaxConfig.getGuardTowerTaxBoostPercentage();
                        boolean hasGuardBoost = guardTowerCount >= requiredGuardTowers;

                        source.sendSuccess(() -> Component.literal("\n🏰 Guard Tower Boost:")
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
                        source.sendSuccess(() -> Component
                                        .literal("  Guard Towers: " + finalGuardTowerCount + " / " + requiredGuardTowers
                                                        + " required")
                                        .withStyle(hasGuardBoost ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                                        false);
                        source.sendSuccess(() -> Component
                                        .literal(String.format("  Boost: %.0f%% %s", guardBoostPercentage * 100,
                                                        hasGuardBoost ? "(ACTIVE)" : "(INACTIVE)"))
                                        .withStyle(hasGuardBoost ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

                        // Building breakdown
                        source.sendSuccess(() -> Component.literal("\n🏘️ Building Breakdown:")
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);

                        int totalGeneratedTax = 0;
                        int totalBaseTax = 0;
                        int totalMaintenance = 0;
                        int buildingCount = 0;

                        for (com.minecolonies.api.colony.buildings.IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                                if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                                        buildingCount++;
                                        String buildingType = building.getBuildingDisplayName();
                                        int buildingLevel = building.getBuildingLevel();

                                        double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                                        double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType)
                                                        * buildingLevel;
                                        double rawTax = baseTax + upgradeTax;
                                        int generatedTax = (int) (rawTax * happinessMultiplier);

                                        double baseMaintenance = TaxConfig.getBaseMaintenanceForBuilding(buildingType);
                                        double upgradeMaintenance = TaxConfig
                                                        .getUpgradeMaintenanceForBuilding(buildingType)
                                                        * buildingLevel;
                                        int maintenance = (int) (baseMaintenance + upgradeMaintenance);

                                        totalBaseTax += (int) rawTax;
                                        totalGeneratedTax += generatedTax;
                                        totalMaintenance += maintenance;

                                        if (buildingCount <= 15) { // Show first 15 buildings
                                                final String bType = buildingType;
                                                final int bLevel = buildingLevel;
                                                final int bTax = generatedTax;
                                                final int bMaint = maintenance;
                                                final int bNet = generatedTax - maintenance;

                                                source.sendSuccess(() -> Component
                                                                .literal(String.format(
                                                                                "  %s (L%d): +%d tax, -%d maint = %s%d net",
                                                                                bType, bLevel, bTax, bMaint,
                                                                                bNet >= 0 ? "+" : "", bNet))
                                                                .withStyle(bNet >= 0 ? ChatFormatting.WHITE
                                                                                : ChatFormatting.GRAY),
                                                                false);
                                        }
                                }
                        }

                        if (buildingCount > 15) {
                                final int remaining = buildingCount - 15;
                                source.sendSuccess(() -> Component.literal("  ... and " + remaining + " more buildings")
                                                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
                        }

                        // Summary - make variables final for lambda capture
                        final int finalBuildingCount = buildingCount;
                        final int finalTotalBaseTax = totalBaseTax;
                        final int finalTotalGeneratedTax = totalGeneratedTax;
                        final int finalTotalMaintenance = totalMaintenance;
                        int netIncome = totalGeneratedTax - totalMaintenance;
                        int boostAmount = hasGuardBoost ? (int) (totalGeneratedTax * guardBoostPercentage) : 0;
                        final int finalBoostAmount = boostAmount;
                        int finalNetIncome = netIncome + boostAmount;
                        final int finalFinalNetIncome = finalNetIncome;

                        source.sendSuccess(() -> Component.literal("\n📋 Summary:")
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
                        source.sendSuccess(() -> Component.literal("  Total Buildings: " + finalBuildingCount)
                                        .withStyle(ChatFormatting.WHITE), false);
                        source.sendSuccess(
                                        () -> Component.literal("  Base Tax (before happiness): " + finalTotalBaseTax)
                                                        .withStyle(ChatFormatting.WHITE),
                                        false);
                        source.sendSuccess(() -> Component
                                        .literal("  Generated Tax (with happiness): " + finalTotalGeneratedTax)
                                        .withStyle(ChatFormatting.GREEN), false);

                        if (hasGuardBoost) {
                                source.sendSuccess(() -> Component.literal("  Guard Tower Boost: +" + finalBoostAmount)
                                                .withStyle(ChatFormatting.GREEN), false);
                        }

                        source.sendSuccess(() -> Component.literal("  Total Maintenance: -" + finalTotalMaintenance)
                                        .withStyle(ChatFormatting.RED), false);
                        source.sendSuccess(() -> Component
                                        .literal(
                                                        "  Net Income Per Interval: "
                                                                        + (finalFinalNetIncome >= 0 ? "+" : "")
                                                                        + finalFinalNetIncome)
                                        .withStyle(finalFinalNetIncome >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED,
                                                        ChatFormatting.BOLD),
                                        false);

                        int maxTaxRevenue = TaxConfig.getMaxTaxRevenue();
                        source.sendSuccess(() -> Component.literal("  Max Tax Cap: " + maxTaxRevenue)
                                        .withStyle(ChatFormatting.YELLOW), false);

                        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                        return 1;
                } catch (Exception e) {
                        source.sendFailure(Component.literal("Error generating tax breakdown: " + e.getMessage())
                                        .withStyle(ChatFormatting.RED));
                        LOGGER.error("Error in debugTaxBreakdown", e);
                        return 0;
                }
        }

        // --- Invest command handlers ---

        private static int handleInvestHelp(CommandContext<CommandSourceStack> context) {
                ServerPlayer player;
                try { player = context.getSource().getPlayerOrException(); } catch (Exception e) { return 0; }
                player.sendSystemMessage(Component.literal("=== Investments ===").withStyle(ChatFormatting.GOLD));
                player.sendSystemMessage(Component.literal("/wnt invest list - Show all investment types and costs").withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("/wnt invest status - Show your colony's current investment levels").withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("/wnt invest buy <TYPE> - Purchase the next level of an investment").withStyle(ChatFormatting.YELLOW));
                return 1;
        }

        private static int handleInvestList(CommandContext<CommandSourceStack> context) {
                ServerPlayer player;
                try { player = context.getSource().getPlayerOrException(); } catch (Exception e) { return 0; }

                if (!TaxConfig.isUpgradesEnabled()) {
                        player.sendSystemMessage(Component.literal("Investments are disabled on this server.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                IColony colony = getPlayerColony(player);
                if (colony == null) {
                        player.sendSystemMessage(Component.literal("You must own a colony to view investments.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                int colonyId = colony.getID();
                int treasuryBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);
                int maxLevel = TaxConfig.getUpgradeMaxLevel();

                player.sendSystemMessage(Component.literal("=== Colony Investments ===").withStyle(ChatFormatting.GOLD));
                player.sendSystemMessage(Component.literal("Treasury: " + treasuryBalance + " | Max level per type: " + maxLevel).withStyle(ChatFormatting.GRAY));

                for (UpgradeType type : UpgradeType.values()) {
                        int level = ColonyUpgradeManager.getLevel(colonyId, type);
                        int cost = ColonyUpgradeManager.getUpgradeCost(colonyId, type);
                        String levelStr = level + "/" + maxLevel;
                        String costStr = level >= maxLevel ? "MAX" : (cost + " treasury");
                        ChatFormatting color = level >= maxLevel ? ChatFormatting.GREEN : (treasuryBalance >= cost ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
                        player.sendSystemMessage(Component.literal(String.format("  %-20s Lv %s  Next: %s", type.name(), levelStr, costStr)).withStyle(color));
                }
                return 1;
        }

        private static int handleInvestStatus(CommandContext<CommandSourceStack> context) {
                ServerPlayer player;
                try { player = context.getSource().getPlayerOrException(); } catch (Exception e) { return 0; }

                if (!TaxConfig.isUpgradesEnabled()) {
                        player.sendSystemMessage(Component.literal("Investments are disabled on this server.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                IColony colony = getPlayerColony(player);
                if (colony == null) {
                        player.sendSystemMessage(Component.literal("You must own a colony to view investments.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                int colonyId = colony.getID();
                player.sendSystemMessage(Component.literal("=== " + colony.getName() + " — Investment Status ===").withStyle(ChatFormatting.GOLD));
                player.sendSystemMessage(Component.literal("Treasury: " + net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId)).withStyle(ChatFormatting.AQUA));

                for (UpgradeType type : UpgradeType.values()) {
                        int level = ColonyUpgradeManager.getLevel(colonyId, type);
                        if (level > 0) {
                                player.sendSystemMessage(Component.literal("  " + type.name() + ": Level " + level).withStyle(ChatFormatting.GREEN));
                        }
                }
                return 1;
        }

        private static int handleInvestBuy(CommandContext<CommandSourceStack> context) {
                ServerPlayer player;
                try { player = context.getSource().getPlayerOrException(); } catch (Exception e) { return 0; }

                if (!TaxConfig.isUpgradesEnabled()) {
                        player.sendSystemMessage(Component.literal("Investments are disabled on this server.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                IColony colony = getPlayerColony(player);
                if (colony == null) {
                        player.sendSystemMessage(Component.literal("You must own a colony to purchase investments.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                if (!colony.getPermissions().getOwner().equals(player.getUUID())) {
                        player.sendSystemMessage(Component.literal("Only the colony owner can purchase investments.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                String typeName = StringArgumentType.getString(context, "type").toUpperCase();
                UpgradeType type;
                try {
                        type = UpgradeType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                        player.sendSystemMessage(Component.literal("Unknown investment type: " + typeName + ". Use /wnt invest list to see valid types.").withStyle(ChatFormatting.RED));
                        return 0;
                }

                int colonyId = colony.getID();
                int currentLevel = ColonyUpgradeManager.getLevel(colonyId, type);
                int maxLevel = TaxConfig.getUpgradeMaxLevel();

                if (currentLevel >= maxLevel) {
                        player.sendSystemMessage(Component.literal(type.name() + " is already at max level (" + maxLevel + ").").withStyle(ChatFormatting.YELLOW));
                        return 0;
                }

                int cost = ColonyUpgradeManager.getUpgradeCost(colonyId, type);
                int balance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);

                if (balance < cost) {
                        player.sendSystemMessage(Component.literal("Insufficient treasury. Need " + cost + ", have " + balance + ".").withStyle(ChatFormatting.RED));
                        return 0;
                }

                boolean success = ColonyUpgradeManager.purchase(colonyId, type, context.getSource().getServer());
                if (success) {
                        int newLevel = currentLevel + 1;
                        player.sendSystemMessage(Component.literal(type.name() + " upgraded to level " + newLevel + "/" + maxLevel + "! Cost: " + cost).withStyle(ChatFormatting.GREEN));
                } else {
                        player.sendSystemMessage(Component.literal("Purchase failed. Check treasury balance and try again.").withStyle(ChatFormatting.RED));
                }
                return success ? 1 : 0;
        }

        private static IColony getPlayerColony(ServerPlayer player) {
                IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();
                for (net.minecraft.server.level.ServerLevel world : player.getServer().getAllLevels()) {
                        for (IColony colony : mgr.getColonies(world)) {
                                if (colony.getPermissions().getOwner().equals(player.getUUID())) {
                                        return colony;
                                }
                        }
                }
                return null;
        }
}
