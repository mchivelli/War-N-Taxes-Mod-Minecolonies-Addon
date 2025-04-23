package net.machiavelli.minecolonytax.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "minecolonytax", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdminTaxGenCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("taxgen")
                        .requires(src -> src.hasPermission(2)) // OP only
                        .then(Commands.literal("disable")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int id = IntegerArgumentType.getInteger(ctx, "colonyId");
                                            TaxManager.disableTaxGeneration(id);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Disabled tax generation for colony " + id), false
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("enable")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int id = IntegerArgumentType.getInteger(ctx, "colonyId");
                                            TaxManager.enableTaxGeneration(id);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Enabled tax generation for colony " + id), false
                                            );
                                            return 1;
                                        })
                                )
                        )
        );
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }
}
