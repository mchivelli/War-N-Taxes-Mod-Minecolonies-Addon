package net.machiavelli.minecolonytax.integration;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.util.ItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central dispatch for all player-facing currency operations (take / give).
 *
 * Supported sources and destinations:
 *   TAX_BALANCE  — colony's accumulated tax balance (TaxManager server ledger)
 *   WALLET       — player's economy wallet (SDMShop / SDMEconomy)
 *   INVENTORY    — physical currency items in the player's Minecraft inventory
 *
 * To add support for a future economy mod, add a new Source constant and
 * handle it in takeFromPlayer() and giveToPlayer(). No other files need
 * to change for new currency types.
 */
public class CurrencyService {

    private static final Logger LOGGER = LogManager.getLogger(CurrencyService.class);

    public enum Source {
        /** Colony's accumulated tax ledger balance. Default for treasury operations. */
        TAX_BALANCE,
        /** Player's SDMShop / SDMEconomy wallet balance. */
        WALLET,
        /** Physical currency items in the player's inventory slots. */
        INVENTORY
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Remove {@code amount} of currency from {@code source} on behalf of the player.
     *
     * @return the amount actually removed, or 0 if funds are insufficient or
     *         the source is not available.
     */
    public static int takeFromPlayer(ServerPlayer player, IColony colony, int amount, Source source) {
        switch (source) {
            case TAX_BALANCE: {
                int stored = colony != null ? TaxManager.getStoredTaxForColony(colony) : 0;
                if (stored < amount) return 0;
                TaxManager.adjustTax(colony, -amount);
                return amount;
            }
            case WALLET: {
                if (!SDMShopIntegration.isAvailable()) return 0;
                return SDMShopIntegration.deductPlayerBalance(player, amount);
            }
            case INVENTORY: {
                return takeCurrencyFromInventory(player, amount);
            }
            default:
                return 0;
        }
    }

    /**
     * Give {@code amount} of currency to the player via the specified destination.
     *
     * @return the amount actually given, or 0 on failure.
     */
    public static int giveToPlayer(ServerPlayer player, IColony colony, int amount, Source destination) {
        switch (destination) {
            case TAX_BALANCE: {
                if (colony == null) return 0;
                TaxManager.adjustTax(colony, amount);
                return amount;
            }
            case WALLET: {
                if (!SDMShopIntegration.isAvailable()) return 0;
                return SDMShopIntegration.addMoney(player, amount) ? amount : 0;
            }
            case INVENTORY: {
                return ItemUtils.giveCurrencyToPlayer(player, amount) ? amount : 0;
            }
            default:
                return 0;
        }
    }

    /**
     * Deliver tax that was ALREADY claimed (deducted from the colony ledger by
     * {@link TaxManager#claimTax}) to the player, honoring the SDMShop-conversion config, and
     * REFUND the colony tax ledger if delivery fails so taxes can never be silently lost.
     *
     * <p>This fixes the 4.x "claimed coins never appear" bug: the claim paths used to deduct
     * the tax first and then attempt SDM delivery with no refund, so any unavailable-economy
     * or failed-deposit left the player with nothing and the tax gone.</p>
     *
     * <p>Behavior when {@code EnableSDMShopConversion=true} but the shop economy is unavailable:
     * the tax is refunded (kept claimable) and the player is told to check {@code /wnt sdm status},
     * rather than silently dropping surprise currency items.</p>
     *
     * @param claimedAmount amount already removed from the colony ledger; must be &gt; 0
     * @return true if the player actually received the funds; false if delivery failed (and the
     *         ledger was refunded)
     */
    public static boolean deliverClaimedTaxOrRefund(ServerPlayer player, IColony colony, int claimedAmount) {
        if (claimedAmount <= 0) {
            return false;
        }

        boolean useWallet = TaxConfig.isSDMShopConversionEnabled();

        if (useWallet && !SDMShopIntegration.isAvailable()) {
            SDMShopIntegration.warnUnavailableOnce();
            TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes
            player.sendSystemMessage(Component.literal(
                    "Shop economy (SDMShop/SDM-Economy) is not available right now - your "
                  + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt debug sdm status for details.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Source destination = useWallet ? Source.WALLET : Source.INVENTORY;
        int given = giveToPlayer(player, colony, claimedAmount, destination);
        if (given <= 0) {
            TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes
            player.sendSystemMessage(Component.literal(
                    "Failed to deliver " + claimedAmount + " (" + label(destination)
                  + ") - your taxes were refunded and remain claimable.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /**
     * Returns how much currency the player has available from the given source.
     * Used for balance checks and status display.
     */
    public static long getAvailableBalance(ServerPlayer player, IColony colony, Source source) {
        switch (source) {
            case TAX_BALANCE:
                return colony != null ? TaxManager.getStoredTaxForColony(colony) : 0;
            case WALLET:
                return SDMShopIntegration.isAvailable() ? SDMShopIntegration.getMoney(player) : -1;
            case INVENTORY:
                return countCurrencyInInventory(player);
            default:
                return 0;
        }
    }

    /**
     * Returns true if this source/destination is usable in the current environment.
     * WALLET requires SDMShop / SDMEconomy to be installed and initialised.
     */
    public static boolean isAvailable(Source source) {
        switch (source) {
            case TAX_BALANCE: return true;
            case WALLET:      return SDMShopIntegration.isAvailable();
            case INVENTORY:   return true;
            default:          return false;
        }
    }

    /**
     * Human-readable label for a source, used in player-facing messages.
     */
    public static String label(Source source) {
        switch (source) {
            case TAX_BALANCE: return "tax balance";
            case WALLET:      return "wallet";
            case INVENTORY:   return "inventory";
            default:          return source.name().toLowerCase();
        }
    }

    // ── Inventory helpers ──────────────────────────────────────────────────

    /**
     * Count the total monetary value of currency items in the player's inventory.
     * In multi-denomination mode each item type contributes its configured value.
     */
    public static int countCurrencyInInventory(ServerPlayer player) {
        return ItemUtils.countInventoryValue(player.getInventory());
    }

    /**
     * Remove exactly {@code amount} of currency value from the player's inventory.
     * Uses largest denominations first; requires exact change to be possible.
     *
     * @return amount removed, or 0 if insufficient funds or exact change not possible.
     */
    private static int takeCurrencyFromInventory(ServerPlayer player, int amount) {
        return ItemUtils.takeCurrencyFromInventory(player, amount);
    }
}
