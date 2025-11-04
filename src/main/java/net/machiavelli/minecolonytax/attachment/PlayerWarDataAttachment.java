package net.machiavelli.minecolonytax.attachment;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.data.PlayerWarData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Data Attachment system for player war statistics in NeoForge 1.21.1+
 * 
 * This replaces the old Forge Capability system with NeoForge's Data Attachments.
 * Data Attachments automatically handle serialization/deserialization and are much simpler.
 * 
 * Usage:
 *   PlayerWarData data = player.getData(PlayerWarDataAttachment.PLAYER_WAR_DATA);
 *   data.incrementPlayersKilledInWar();
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID)
public class PlayerWarDataAttachment {

    // DeferredRegister for attachment types (register on MOD bus)
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = 
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MineColonyTax.MOD_ID);

    /**
     * The attachment type for player war data.
     * This is automatically serialized to player NBT data using Codec.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerWarData>> PLAYER_WAR_DATA = 
        ATTACHMENT_TYPES.register("player_war_data", () -> 
            AttachmentType.builder(PlayerWarData::new)
                .serialize(PlayerWarData.CODEC)
                .build()
        );

    /**
     * Get player war data from a player entity.
     * This method never returns null - if no data exists, a new instance is created.
     * 
     * @param player The player to get data from
     * @return The player's war data
     */
    public static PlayerWarData get(Player player) {
        return player.getData(PLAYER_WAR_DATA);
    }

    /**
     * Set player war data on a player entity.
     * Useful for cloning or resetting data.
     * 
     * @param player The player to set data on
     * @param data The war data to set
     */
    public static void set(Player player, PlayerWarData data) {
        player.setData(PLAYER_WAR_DATA, data);
    }

    /**
     * Check if a player has war data attached.
     * Note: In NeoForge attachments, data is always present (defaults are created automatically).
     * 
     * @param player The player to check
     * @return Always true for attachments with default constructors
     */
    public static boolean has(Player player) {
        return player.hasData(PLAYER_WAR_DATA);
    }

    // ==================== Event Handlers ====================

    /**
     * Handle player login - log the loaded data for debugging
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerWarData data = get(player);
            MineColonyTax.LOGGER.debug("📊 Player {} logged in with war stats - Kills: {}, Raids: {}, Wars Won: {}", 
                player.getName().getString(),
                data.getPlayersKilledInWar(),
                data.getRaidedColonies(),
                data.getWarsWon()
            );
        }
    }

    /**
     * Handle player logout - log the saved data for debugging
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerWarData data = get(player);
            MineColonyTax.LOGGER.debug("📊 Player {} logged out with war stats - Kills: {}, Raids: {}, Wars Won: {}", 
                player.getName().getString(),
                data.getPlayersKilledInWar(),
                data.getRaidedColonies(),
                data.getWarsWon()
            );
            // Data is automatically saved by NeoForge - no need for manual save
        }
    }

    /**
     * Handle player cloning (respawn, returning from End, etc.)
     * Copy war data from old player to new player instance.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer && event.getOriginal() instanceof ServerPlayer) {
            ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
            ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();

            // Copy war data from old player to new player
            PlayerWarData oldData = get(oldPlayer);
            PlayerWarData newData = new PlayerWarData();
            newData.copyFrom(oldData);
            set(newPlayer, newData);

            MineColonyTax.LOGGER.debug("📊 Cloned war data for player {} (death: {})", 
                newPlayer.getName().getString(), 
                event.isWasDeath()
            );
        }
    }

    /**
     * Handle player save - log that data is being persisted
     * Note: Actual saving is handled automatically by NeoForge
     */
    @SubscribeEvent
    public static void onPlayerSave(PlayerEvent.SaveToFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerWarData data = get(player);
            MineColonyTax.LOGGER.debug("💾 Saving war data for player {}: Kills={}, Raids={}, Wars={}", 
                player.getName().getString(),
                data.getPlayersKilledInWar(),
                data.getRaidedColonies(),
                data.getWarsWon()
            );
            // NeoForge automatically serializes the attachment data to NBT
        }
    }

    /**
     * Handle player load - log that data has been loaded
     * Note: Actual loading is handled automatically by NeoForge
     */
    @SubscribeEvent
    public static void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerWarData data = get(player);
            MineColonyTax.LOGGER.debug("📂 Loaded war data for player {}: Kills={}, Raids={}, Wars={}", 
                player.getName().getString(),
                data.getPlayersKilledInWar(),
                data.getRaidedColonies(),
                data.getWarsWon()
            );
            // NeoForge automatically deserializes the attachment data from NBT
        }
    }
}
