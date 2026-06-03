package net.machiavelli.minecolonytax.peace;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.compat.FtbTeamsCompat;
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

import java.util.Map;
import java.util.UUID;

public class PeaceProposalManager {

    private static final Logger LOGGER = LogManager.getLogger(PeaceProposalManager.class);

    public int suePeaceWhite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return handleSuePeaceProposal(ctx, PeaceProposal.Type.WHITEPEACE, 0);
    }

    public int suePeaceReparations(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        return handleSuePeaceProposal(ctx, PeaceProposal.Type.REPARATIONS, amount);
    }

    public int suePeaceSurrender(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return handleSuePeaceProposal(ctx, PeaceProposal.Type.SURRENDER, 0);
    }

    private int handleSuePeaceProposal(CommandContext<CommandSourceStack> ctx, PeaceProposal.Type type, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            ctx.getSource().sendFailure(Component.literal("No active war."));
            return 0;
        }
        if (war.getActiveProposal() != null) {
            ctx.getSource().sendFailure(Component.literal("A peace proposal is already active!"));
            return 0;
        }
        // Block peace proposals during join phase - only allow during INWAR status
        if (war.getStatus() != WarData.WarStatus.INWAR) {
            ctx.getSource().sendFailure(Component.literal("Peace proposals can only be made during active war, not during the join phase!"));
            return 0;
        }

        // Finding 6: Require proposer to be owner or manager of their side's colony.
        // Previously ANY participant in lives (including Friend rank that just joined)
        // could propose SURRENDER on behalf of their whole colony.
        if (!isAuthorizedToProposePeace(player, war)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Only the colony owner or an officer/manager may propose peace."));
            return 0;
        }

        PeaceProposal proposal = new PeaceProposal(type, amount, player.getUUID());
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
                .append(Component.literal(type.toString() + " Peace").withStyle(ChatFormatting.AQUA));
        
        // Make reparations direction explicit
        if (type == PeaceProposal.Type.REPARATIONS) {
            msg.append(Component.literal("! They OFFER to pay " + amount + " coins to end the war.\n").withStyle(ChatFormatting.GOLD));
        } else {
            msg.append(Component.literal("!\n").withStyle(ChatFormatting.GOLD));
        }
        
        msg.append(acceptButton)
                .append(Component.literal(" "))
                .append(declineButton);

        FtbTeamsCompat.TeamHandle userTeam = FtbTeamsCompat.isInstalled()
                ? FtbTeamsCompat.getTeamForPlayer(player.getUUID()).orElse(null)
                : null;

        if (FtbTeamsCompat.isInstalled() && userTeam != null) {
            UUID userTeamId = FtbTeamsCompat.getTeamId(userTeam);
            if (userTeamId != null && userTeamId.equals(war.getAttackerTeamID())) {
                sendMessageToTeamFallback(war, false, msg); // Send to defender
            } else if (userTeamId != null && userTeamId.equals(war.getDefenderTeamID())) {
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
        long timeoutMillis = TaxConfig.PEACE_PROPOSAL_TIMEOUT_SECONDS.get() * 1000L;
        if (proposal.isExpired(timeoutMillis)) {
            ctx.getSource().sendFailure(Component.literal("Peace proposal has expired!"));
            war.setActiveProposal(null);
            return 0;
        }

        if (!isAuthorizedToRespondToPeace(player, war)) {
             ctx.getSource().sendFailure(Component.literal("Only an authorized player from the opposing side can accept/decline the peace proposal!"));
             return 0;
        }

        // Finding 8: atomic check-and-clear BEFORE doing work. If two players
        // race to accept (chat click + command), only the first thread sees
        // the proposal non-null. The second sees null and bails. This avoids
        // double reparations transfers and double-fires of endWar().
        synchronized (war) {
            PeaceProposal current = war.getActiveProposal();
            if (current == null || current != proposal) {
                ctx.getSource().sendFailure(Component.literal("Peace proposal already resolved."));
                return 0;
            }
            war.setActiveProposal(null);
        }

        finalizePeaceProposal(war, true, player, proposal); // Pass the captured proposal we cleared
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
        long timeoutMillis = TaxConfig.PEACE_PROPOSAL_TIMEOUT_SECONDS.get() * 1000L;
        if (proposal.isExpired(timeoutMillis)) {
            ctx.getSource().sendFailure(Component.literal("Peace proposal has expired!"));
            war.setActiveProposal(null);
            return 0;
        }

        if (!isAuthorizedToRespondToPeace(player, war)) {
             ctx.getSource().sendFailure(Component.literal("Only an authorized player from the opposing side can accept/decline the peace proposal!"));
             return 0;
        }

        // Finding 8: atomic check-and-clear before the notification block so
        // concurrent decline+accept (or duplicate declines) only resolves once.
        synchronized (war) {
            PeaceProposal current = war.getActiveProposal();
            if (current == null || current != proposal) {
                ctx.getSource().sendFailure(Component.literal("Peace proposal already resolved."));
                return 0;
            }
            war.setActiveProposal(null);
        }

        ServerPlayer proposer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(proposal.getProposer());
        if(proposer != null) {
            proposer.sendSystemMessage(Component.literal("Your peace proposal was declined by " + player.getName().getString()).withStyle(ChatFormatting.RED));
        }
        player.sendSystemMessage(Component.literal("You have declined the peace proposal.").withStyle(ChatFormatting.YELLOW));

        return 1;
    }
    
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

        // Officer rank (id >= 2) or owner may accept/decline on behalf of their colony.
        // Finding 7: getRank(...) can return null for non-members (e.g. team-based
        // participants without a permissions entry). Null-guard before deref.
        if (responderIsAttacker && war.getAttackerColony() != null) {
            IColony colony = war.getAttackerColony();
            UUID owner = colony.getPermissions().getOwner();
            if (owner != null && owner.equals(responderId)) return true;
            Rank rank = colony.getPermissions().getRank(responder);
            if (rank == null) return false;
            if (rank.isHostile()) return false;
            if (rank.getId() >= 2) return true;
        }
        if (responderIsDefender && war.getColony() != null) {
            IColony colony = war.getColony();
            UUID owner = colony.getPermissions().getOwner();
            if (owner != null && owner.equals(responderId)) return true;
            Rank rank = colony.getPermissions().getRank(responder);
            if (rank == null) return false;
            if (rank.isHostile()) return false;
            if (rank.getId() >= 2) return true;
        }
        return false;
    }


    private void finalizePeaceProposal(WarData war, boolean accepted, ServerPlayer responder, PeaceProposal proposal) {
        if (proposal == null) return;

        ServerPlayer proposer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(proposal.getProposer());

        if (!accepted) {
            if (proposer != null) {
                proposer.sendSystemMessage(Component.literal("Your peace proposal was declined by " + responder.getName().getString()).withStyle(ChatFormatting.RED));
            }
            responder.sendSystemMessage(Component.literal("You have declined the peace proposal.").withStyle(ChatFormatting.YELLOW));
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
                // Set penalty report before endWar so war history logging captures peace outcome
                war.setPenaltyReport("White Peace: War ended by mutual agreement, no reparations");
                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
                break;
            case REPARATIONS:
                UUID losingTeamId;
                UUID winningPlayerId;

                // The proposer is the side offering to pay reparations; the responder's side receives.
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
                
                Component losingTeamMsg = Component.literal("Reparations accepted! " + demandedAmount + " coins were paid by your side. War is ended.").withStyle(ChatFormatting.GREEN);
                Component winningTeamMsg = Component.literal("Reparations accepted! " + demandedAmount + " coins were received by your side. War is ended.").withStyle(ChatFormatting.GREEN);

                if (proposerWasAttacker) {
                    sendMessageToTeamFallback(war, true, losingTeamMsg); // Attacker team (lost)
                    sendMessageToTeamFallback(war, false, winningTeamMsg); // Defender team (won)
                } else {
                    sendMessageToTeamFallback(war, false, losingTeamMsg); // Defender team (lost)
                    sendMessageToTeamFallback(war, true, winningTeamMsg); // Attacker team (won)
                }
                // Set penalty report before endWar so war history logging captures peace outcome
                String payerSide = proposerWasAttacker ? "Attackers" : "Defenders";
                war.setPenaltyReport("Peace via Reparations: " + payerSide + " paid " + demandedAmount + " coins");
                WarSystem.endWar(war.getColony());
                break;
            case SURRENDER:
                // Surrender: proposer unconditionally surrenders, responder's side wins
                boolean surrendererWasAttacker = war.getAttackerLives().containsKey(proposal.getProposer());
                
                Component surrenderMsg = Component.literal("Surrender accepted! ").withStyle(ChatFormatting.GOLD);
                if (proposer != null) proposer.sendSystemMessage(Component.literal(acceptedMessageToProposer).append(surrenderMsg).append(Component.literal("Your side has surrendered.")));
                responder.sendSystemMessage(Component.literal(acceptedMessageToResponder).append(surrenderMsg).append(Component.literal("Your side has won by surrender!")));
                
                // Notify teams
                Component surrenderingTeamMsg = Component.literal("Surrender accepted! Your side has surrendered and lost the war.").withStyle(ChatFormatting.RED);
                Component victoriousTeamMsg = Component.literal("Surrender accepted! Your side has won the war!").withStyle(ChatFormatting.GREEN);
                
                if (surrendererWasAttacker) {
                    sendMessageToTeamFallback(war, true, surrenderingTeamMsg); // Attacker team (surrendered)
                    sendMessageToTeamFallback(war, false, victoriousTeamMsg); // Defender team (won)
                    // Record attacker's loss (they surrendered)
                    if (war.getAttackerColony() != null) {
                        net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getAttackerColony().getID());
                    }
                } else {
                    sendMessageToTeamFallback(war, false, surrenderingTeamMsg); // Defender team (surrendered)
                    sendMessageToTeamFallback(war, true, victoriousTeamMsg); // Attacker team (won)
                    // Record defender's loss (they surrendered)
                    net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
                }
                
                // Set penalty report before endWar
                String surrenderingSide = surrendererWasAttacker ? "Attackers" : "Defenders";
                war.setPenaltyReport("Surrender: " + surrenderingSide + " surrendered unconditionally");
                WarSystem.endWar(war.getColony());
                break;
        }
        war.setActiveProposal(null);
    }

    private void sendMessageToTeamFallback(WarData war, boolean sendToAttacker, Component msg) {
        if (FtbTeamsCompat.isInstalled()) {
            UUID targetTeamId = sendToAttacker ? war.getAttackerTeamID() : war.getDefenderTeamID();
            FtbTeamsCompat.TeamHandle team = targetTeamId == null ? null
                    : FtbTeamsCompat.getTeamById(targetTeamId).orElse(null);
            if (team != null) {
                for (UUID member : FtbTeamsCompat.getTeamMembers(team)) {
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

    /**
     * Finding 6: proposer must be at minimum a colony manager (owner or officer)
     * on the side they're proposing peace for. Friend-rank participants can
     * fight but not surrender their colony.
     */
    private boolean isAuthorizedToProposePeace(ServerPlayer player, WarData war) {
        if (player == null || war == null) return false;
        UUID id = player.getUUID();
        boolean isAttacker = war.getAttackerLives().containsKey(id);
        boolean isDefender = war.getDefenderLives().containsKey(id);
        if (!isAttacker && !isDefender) return false;
        IColony colony = isAttacker ? war.getAttackerColony() : war.getColony();
        if (colony == null) {
            // Solo/team attacker without a registered colony — they ARE the
            // decision-maker for their own side.
            return true;
        }
        // Owner can always propose
        UUID owner = colony.getPermissions().getOwner();
        if (owner != null && owner.equals(id)) return true;
        // Otherwise require colony-manager rank (officer or higher) and not hostile.
        Rank rank = colony.getPermissions().getRank(player);
        if (rank == null) return false;
        if (rank.isHostile()) return false;
        return rank.isColonyManager();
    }
}
