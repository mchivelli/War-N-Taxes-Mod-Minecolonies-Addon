package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Commands for managing colony tax policies.
 *
 * Usage:
 *   /wnt taxpolicy - Show current policy
 *   /wnt taxpolicy list - List all available policies
 *   /wnt taxpolicy set <policy> - Set your colony's policy
 */
public class TaxPolicyCommand {

    private static final SuggestionProvider<CommandSourceStack> POLICY_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(TaxPolicy.values())
                            .map(TaxPolicy::name)
                            .collect(Collectors.toList()),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wnt")
                        .then(Commands.literal("taxpolicy")
                                .executes(TaxPolicyCommand::viewPolicy)
                                .then(Commands.literal("list")
                                        .executes(TaxPolicyCommand::listPolicies))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("policy", StringArgumentType.word())
                                                .suggests(POLICY_SUGGESTIONS)
                                                .executes(TaxPolicyCommand::setPolicy)))
                                .then(Commands.literal("help")
                                        .executes(TaxPolicyCommand::showHelp))
                        )
        );
    }

    private static int viewPolicy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String result = TaxPolicyManager.viewPolicyCommand(player);
        sendMultilineMessage(player, result);
        return Command.SINGLE_SUCCESS;
    }

    private static int listPolicies(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String result = TaxPolicyManager.listPoliciesCommand();
        sendMultilineMessage(player, result);
        return Command.SINGLE_SUCCESS;
    }

    private static int setPolicy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String policyName = StringArgumentType.getString(ctx, "policy");
        String result = TaxPolicyManager.setPolicyCommand(player, policyName);
        sendMultilineMessage(player, result);
        return Command.SINGLE_SUCCESS;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StringBuilder help = new StringBuilder();
        help.append("§6=== Tax Policy Commands ===\n\n");
        help.append("§e/wnt taxpolicy§f - View your colony's current tax policy\n");
        help.append("§e/wnt taxpolicy list§f - List all available policies\n");
        help.append("§e/wnt taxpolicy set <policy>§f - Set your colony's tax policy\n");
        help.append("\n§7Tax policies affect your colony's revenue generation and citizen happiness.");
        sendMultilineMessage(player, help.toString());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Send a multi-line message to a player, splitting on newlines.
     */
    private static void sendMultilineMessage(ServerPlayer player, String message) {
        for (String line : message.split("\n")) {
            player.sendSystemMessage(Component.literal(line));
        }
    }
}
