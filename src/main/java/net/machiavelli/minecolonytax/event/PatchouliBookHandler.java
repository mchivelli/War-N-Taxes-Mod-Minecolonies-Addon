package net.machiavelli.minecolonytax.event;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles giving players the Patchouli guide book on first join.
 * Only active when Patchouli mod is loaded.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PatchouliBookHandler {
    private static final Logger LOGGER = LogManager.getLogger(PatchouliBookHandler.class);

    private static final String BOOK_GIVEN_TAG = MineColonyTax.MOD_ID + "_book_given";
    public static final ResourceLocation BOOK_ID = new ResourceLocation(MineColonyTax.MOD_ID, "war_taxes_codex");

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if feature is enabled
        if (!TaxConfig.GIVE_PATCHOULI_BOOK_ON_JOIN.get()) {
            return;
        }

        // Check if Patchouli is loaded
        if (!isPatchouliLoaded()) {
            LOGGER.debug("Patchouli not loaded, skipping book give");
            return;
        }

        // Check if player already received the book
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.getBoolean(BOOK_GIVEN_TAG)) {
            LOGGER.debug("Player {} already has the War & Taxes Codex", player.getName().getString());
            return;
        }

        // Try to give the book using Patchouli API
        if (giveBookToPlayer(player)) {
            // Mark that we gave the book
            persistentData.putBoolean(BOOK_GIVEN_TAG, true);
            LOGGER.info("Gave War & Taxes Codex to new player: {}", player.getName().getString());
        }
    }

    /**
     * Check if Patchouli mod is present
     */
    public static boolean isPatchouliLoaded() {
        return ModList.get().isLoaded("patchouli");
    }

    /**
     * Give the Patchouli book to the player using the API
     */
    private static boolean giveBookToPlayer(ServerPlayer player) {
        try {
            // Use reflection to avoid hard dependency on Patchouli
            Class<?> patchouliAPIClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object apiInstance = patchouliAPIClass.getMethod("get").invoke(null);

            // Get the book item
            java.lang.reflect.Method getBookStackMethod = apiInstance.getClass().getMethod("getBookStack",
                    ResourceLocation.class);
            ItemStack bookStack = (ItemStack) getBookStackMethod.invoke(apiInstance, BOOK_ID);

            if (bookStack == null || bookStack.isEmpty()) {
                LOGGER.warn("Failed to get book stack for {}", BOOK_ID);
                return false;
            }

            // Give to player
            if (!player.getInventory().add(bookStack)) {
                // Inventory full, drop it
                player.drop(bookStack, false);
            }

            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Patchouli API class not found - mod not loaded");
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to give Patchouli book to player: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Reset the book given flag for a player (useful for testing)
     */
    public static void resetBookGiven(ServerPlayer player) {
        player.getPersistentData().remove(BOOK_GIVEN_TAG);
        LOGGER.info("Reset book given flag for player: {}", player.getName().getString());
    }
}
