package net.machiavelli.minecolonytax.peace;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.event.WarEconomyHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map; // Added import
import java.util.UUID;

public class PeaceProposalManager {

    private static final Logger LOGGER = LogManager.getLogger(PeaceProposalManager.class);

    public int suePeaceWhite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return handleSuePeaceProposal(ctx, PeaceProposal.Type.WHITEPEACE, 0);
    }

    public int suePeaceReparations(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        return handleSuePeaceProposal(ctx, PeaceProposal.Type.REPARATIONS, amount);
    }

    private int handleSuePeaceProposal(CommandContext<CommandSourceStack> ctx, PeaceProposal.Type type, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player); // Assumes WarSystem provides this
        if (war == null) {
            ctx.getSource().sendFailure(Component.literal("No active war."));
            return 0;
        }
        if (war.getActiveProposal() != null) {
            ctx.getSource().sendFailure(Component.literal("A peace proposal is already active!"));
            return 0;
        }

        PeaceProposal proposal = new PeaceProposal(type, amount, player.getUUID()); // Added proposer
        war.setActiveProposal(proposal);

        MutableComponent acceptButton = Component.literal("[Accept Peace]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt peace accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Only the colony owner may confirm!"))));

        MutableComponent declineButton = Component.literal("[Decline]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt peace decline"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Only the colony owner may decline!"))));

        MutableComponent msg = Component.literal("")
                .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" proposes ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(type.toString() + " Peace").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("! (Reparations = " + amount + " coins)\n").withStyle(ChatFormatting.GOLD))
                .append(acceptButton)
                .append(Component.literal(" "))
                .append(declineButton);

        Team userTeam = WarSystem.FTB_TEAMS_INSTALLED ?
                WarSystem.FTB_TEAM_MANAGER.getPlayerTeamForPlayerID(player.getUUID()).orElse(null) : null;

        if (WarSystem.FTB_TEAMS_INSTALLED && userTeam != null) {
            if (userTeam.getId().equals(war.getAttackerTeamID())) {
                sendMessageToTeamFallback(war, false, msg); // Send to defender
            } else if (userTeam.getId().equals(war.getDefenderTeamID())) {
                sendMessageToTeamFallback(war, true, msg); // Send to attacker
            } else {
                ctx.getSource().sendFailure(Component.literal("Error: Your team is not part of this war."));
                return 0;
            }
        } else {
            if (war.getAttackerLives().containsKey(player.getUUID())) {
                sendMessageToTeamFallback(war, false, msg); // Send to defender
            } else if (war.getDefenderLives().containsKey(player.getUUID())) {
                sendMessageToTeamFallback(war, true, msg); // Send to attacker
            } else {
                ctx.getSource().sendFailure(Component.literal("Error: You are not registered in this war."));
                return 0;
            }
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Peace proposal sent to the opposing side."), false);
        return 1;
    }

    public int acceptPeace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            ctx.getSource().sendFailure(Component.literal("You are not currently in a war."));
            return 0;
        }

        PeaceProposal proposal = war.getActiveProposal();
        if (proposal == null) {
            ctx.getSource().sendFailure(Component.literal("No active peace proposal to accept."));
            return 0;
        }

        UUID owner = war.getColony().getPermissions().getOwner();
        // This check might need adjustment based on who is allowed to accept.
        // Original WarCommands logic: "Only the 'defending' colony's owner may finalize"
        // This implies the player accepting must be the owner of the *target* colony of the peace proposal.
        // The current player might be the one who *received* the proposal.
        // Let's assume for now the player executing /peace accept is the one who should be authorized.
        boolean isPlayerAttackerSide = war.getAttackerLives().containsKey(player.getUUID()) || (WarSystem.FTB_TEAMS_INSTALLED && WarSystem.FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID()).map(t -> t.getId().equals(war.getAttackerTeamID())).orElse(false));
        boolean isPlayerDefenderSide = war.getDefenderLives().containsKey(player.getUUID()) || (WarSystem.FTB_TEAMS_INSTALLED && WarSystem.FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID()).map(t -> t.getId().equals(war.getDefenderTeamID())).orElse(false));

        // The player accepting should be on the *opposite* side of the proposer.
        // And they must be an owner/authorized person of their colony.
        // This logic needs to be robust. For now, a simplified check:
        if (!isAuthorizedToRespondToPeace(player, war)) {
             ctx.getSource().sendFailure(Component.literal("Only an authorized player from the opposing side can accept/decline the peace proposal!"));
             return 0;
        }


        finalizePeaceProposal(war, true, player); // Pass player to identify who accepted
        return 1;
    }

    public int declinePeace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            ctx.getSource().sendFailure(Component.literal("You are not currently in a war."));
            return 0;
        }

        PeaceProposal proposal = war.getActiveProposal();
        if (proposal == null) {
            ctx.getSource().sendFailure(Component.literal("No active peace proposal to decline."));
            return 0;
        }

        if (!isAuthorizedToRespondToPeace(player, war)) {
             ctx.getSource().sendFailure(Component.literal("Only an authorized player from the opposing side can accept/decline the peace proposal!"));
             return 0;
        }

        // Notify the proposing side that it was declined
        boolean wasPlayerOnAttackerSideWhenProposed = war.getAttackerLives().containsKey(proposal.getProposer()); // Assuming PeaceProposal stores proposer
        // This needs PeaceProposal to store the proposer's UUID. Let's add that.
        // For now, assume we can determine the proposing side.
        // Let's assume the player declining is on the opposite side of who made the proposal.
        // The original proposal sender needs to be notified.

        ServerPlayer proposer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(proposal.getProposer());
        if(proposer != null) {
            proposer.sendSystemMessage(Component.literal("Your peace proposal was declined by " + player.getName().getString()).withStyle(ChatFormatting.RED));
        }
        player.sendSystemMessage(Component.literal("You have declined the peace proposal.").withStyle(ChatFormatting.YELLOW));


        war.setActiveProposal(null);
        return 1;
    }
    
    // Helper to check authorization for responding to peace
    // This logic might need to be more sophisticated depending on game rules
    private boolean isAuthorizedToRespondToPeace(ServerPlayer responder, WarData war) {
        PeaceProposal proposal = war.getActiveProposal();
        if (proposal == null || proposal.getProposer() == null) return false; // Cannot respond if no proposal or proposer

        UUID responderId = responder.getUUID();
        UUID proposerId = proposal.getProposer();

        boolean responderIsAttacker = war.getAttackerLives().containsKey(responderId);
        boolean responderIsDefender = war.getDefenderLives().containsKey(responderId);
        boolean proposerIsAttacker = war.getAttackerLives().containsKey(proposerId);
        boolean proposerIsDefender = war.getDefenderLives().containsKey(proposerId);

        // Responder must be on the opposite side of the proposer
        if ((responderIsAttacker && proposerIsAttacker) || (responderIsDefender && proposerIsDefender)) {
            return false; 
        }

        // And the responder must be an owner of their respective colony
        if (responderIsAttacker && war.getAttackerColony() != null && war.getAttackerColony().getPermissions().getOwner().equals(responderId)) {
            return true;
        }
        if (responderIsDefender && war.getColony() != null && war.getColony().getPermissions().getOwner().equals(responderId)) {
            return true;
        }
        return false;
    }


    private void finalizePeaceProposal(WarData war, boolean accepted, ServerPlayer responder) {
        PeaceProposal proposal = war.getActiveProposal();
        if (proposal == null) return;

        ServerPlayer proposer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(proposal.getProposer());

        if (!accepted) { // Though current flow always calls with true for acceptPeace
            if (proposer != null) {
                proposer.sendSystemMessage(Component.literal("Your peace proposal was declined by " + responder.getName().getString()).withStyle(ChatFormatting.RED));
            }
            responder.sendSystemMessage(Component.literal("You have declined the peace proposal.").withStyle(ChatFormatting.YELLOW));
            war.setActiveProposal(null);
            return;
        }

        String acceptedMessageToProposer = "Your peace proposal was accepted by " + responder.getName().getString() + "! ";
        String acceptedMessageToResponder = "You have accepted the peace proposal! ";

        switch (proposal.getType()) {
            case WHITEPEACE:
                Component whitePeaceMsg = Component.literal("White Peace agreed! War ends now, no reparations needed.").withStyle(ChatFormatting.GREEN);
                if (proposer != null) proposer.sendSystemMessage(Component.literal(acceptedMessageToProposer).append(whitePeaceMsg));
                responder.sendSystemMessage(Component.literal(acceptedMessageToResponder).append(whitePeaceMsg));
                // Broadcast to teams
                sendMessageToTeamFallback(war, true, whitePeaceMsg); // Attacker team
                sendMessageToTeamFallback(war, false, whitePeaceMsg); // Defender team
                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
                break;
            case REPARATIONS:
                UUID losingTeamId; // Side that pays
                UUID winningPlayerId; // Player on the side that receives

                // Determine who pays based on who accepted.
                // If responder is on attacker side, defender proposed and attacker pays.
                // If responder is on defender side, attacker proposed and defender pays.
                // This needs careful thought. The original logic assumed attacker pays if they proposed reparations.
                // Let's assume the *proposer* is offering to pay if it's reparations.
                // So, the responder's side is the winner.

                boolean proposerWasAttacker = war.getAttackerLives().containsKey(proposal.getProposer());
                
                if (proposerWasAttacker) { // Attacker proposed to pay reparations
                    losingTeamId = war.getAttackerTeamID();
                    winningPlayerId = war.getColony().getPermissions().getOwner(); // Defender colony owner
                } else { // Defender proposed to pay reparations
                    losingTeamId = war.getDefenderTeamID();
                    winningPlayerId = war.getAttackerColony().getPermissions().getOwner(); // Attacker colony owner
                }

                long demandedAmount = proposal.getAmount();
                long teamTotal = WarEconomyHandler.getTeamTotalBalance(losingTeamId);

                if (teamTotal < demandedAmount) {
                    Component notEnoughFundsMsg = Component.literal("Reparations proposal failed: Not enough funds to pay " + demandedAmount).withStyle(ChatFormatting.RED);
                    if (proposer != null) proposer.sendSystemMessage(notEnoughFundsMsg);
                    responder.sendSystemMessage(notEnoughFundsMsg);
                    war.setActiveProposal(null);
                    return;
                }

                boolean success = WarEconomyHandler.payReparationsProportionally(losingTeamId, winningPlayerId, demandedAmount);
                if (!success) {
                    Component unexpectedErrorMsg = Component.literal("Reparations payment failed unexpectedly.").withStyle(ChatFormatting.RED);
                    if (proposer != null) proposer.sendSystemMessage(unexpectedErrorMsg);
                    responder.sendSystemMessage(unexpectedErrorMsg);
                    war.setActiveProposal(null);
                    return;
                }

                Component reparationsPaidMsg = Component.literal("Reparations paid! " + demandedAmount + " coins transferred. War is ended.").withStyle(ChatFormatting.GREEN);
                if (proposer != null) proposer.sendSystemMessage(Component.literal(acceptedMessageToProposer).append(reparationsPaidMsg));
                responder.sendSystemMessage(Component.literal(acceptedMessageToResponder).append(reparationsPaidMsg));
                
                // Notify teams
                Component losingTeamMsg = Component.literal("Reparations accepted! " + demandedAmount + " coins were paid by your side. War is ended.").withStyle(ChatFormatting.GREEN);
                Component winningTeamMsg = Component.literal("Reparations accepted! " + demandedAmount + " coins were received by your side. War is ended.").withStyle(ChatFormatting.GREEN);

                if (proposerWasAttacker) {
                    sendMessageToTeamFallback(war, true, losingTeamMsg); // Attacker team (lost)
                    sendMessageToTeamFallback(war, false, winningTeamMsg); // Defender team (won)
                } else {
                    sendMessageToTeamFallback(war, false, losingTeamMsg); // Defender team (lost)
                    sendMessageToTeamFallback(war, true, winningTeamMsg); // Attacker team (won)
                }
                WarSystem.endWar(war.getColony());
                break;
        }
        war.setActiveProposal(null);
    }

    // Helper method from WarCommands, might need to be static in WarSystem or a utility class
    private void sendMessageToTeamFallback(WarData war, boolean sendToAttacker, Component msg) {
        if (WarSystem.FTB_TEAMS_INSTALLED) {
            TeamManager manager = WarSystem.FTB_TEAM_MANAGER;
            if (manager == null) return; // Should not happen if FTB_TEAMS_INSTALLED is true

            Team team = sendToAttacker ?
                    manager.getTeamByID(war.getAttackerTeamID()).orElse(null) :
                    manager.getTeamByID(war.getDefenderTeamID()).orElse(null);
            if (team != null) {
                for (UUID member : team.getMembers()) {
                    ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
                    if (sp != null) {
                        sp.sendSystemMessage(msg);
                    }
                }
                return;
            }
        }
        // Fallback: use the war's internal maps.
        Map<UUID, Integer> livesMap = sendToAttacker ? war.getAttackerLives() : war.getDefenderLives();
        livesMap.forEach((uuid, lives) -> {
            ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
            if (sp != null) sp.sendSystemMessage(msg);
        });
    }
}