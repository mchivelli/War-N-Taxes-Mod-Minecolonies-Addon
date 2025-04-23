package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.commands.WarCommands;
import net.machiavelli.minecolonytax.commands.WarCommands.RaidData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RaidLoginNotifier {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Use event.getEntity() and cast to ServerPlayer.
        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID playerUUID = player.getUUID();

        // Iterate over all active raids (using our new public accessor).
        for (RaidData raid : WarCommands.getActiveRaids().values()) {
            // Check if the logged-in player is the owner of the raided colony.
            if (raid.getColony().getPermissions().getOwner().equals(playerUUID)) {
                player.sendSystemMessage(
                        Component.literal("Your colony " + raid.getColony().getName() +
                                        " is currently being raided by " + getRaiderName(raid.getRaider()) + "!")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    private static String getRaiderName(UUID raiderUUID) {
        ServerPlayer raider = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(raiderUUID);
        return (raider != null) ? raider.getName().getString() : "an unknown raider";
    }
}
