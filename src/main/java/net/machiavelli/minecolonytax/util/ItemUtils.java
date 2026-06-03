package net.machiavelli.minecolonytax.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.machiavelli.minecolonytax.TaxConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ItemUtils {

    private static final Logger LOGGER = LogManager.getLogger(ItemUtils.class);

    // ── Denomination support ──────────────────────────────────────────────

    /** A single currency denomination: an item and its monetary value. */
    public static class Denomination {
        public final Item item;
        public final int value;
        public final String itemId;

        public Denomination(Item item, String itemId, int value) {
            this.item = item;
            this.itemId = itemId;
            this.value = value;
        }
    }

    private static List<Denomination> denominationCache = null;
    private static String denominationConfigCache = null;

    /**
     * Returns the active denomination list, sorted highest value first.
     * Falls back to a single entry using CurrencyItemName (value=1) when
     * CurrencyDenominations is empty or unparseable.
     * Result is cached until the config string changes.
     */
    public static List<Denomination> getDenominations() {
        String config = TaxConfig.getCurrencyDenominations();
        if (Objects.equals(config, denominationConfigCache) && denominationCache != null) {
            return denominationCache;
        }

        List<Denomination> result = new ArrayList<>();

        if (config != null && !config.isBlank()) {
            for (String entry : config.split(",")) {
                String trimmed = entry.trim();
                // Format: namespace:path:value  (e.g. "minecraft:emerald:1")
                // Use lastIndexOf so the item's namespace:path is preserved
                int lastColon = trimmed.lastIndexOf(':');
                if (lastColon <= 0 || lastColon == trimmed.length() - 1) {
                    LOGGER.warn("Skipping invalid denomination entry (bad format): '{}'", trimmed);
                    continue;
                }
                String itemId = trimmed.substring(0, lastColon);
                String valueStr = trimmed.substring(lastColon + 1);
                try {
                    int value = Integer.parseInt(valueStr);
                    if (value <= 0) {
                        LOGGER.warn("Skipping denomination with non-positive value: '{}'", trimmed);
                        continue;
                    }
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                    if (item != null) {
                        result.add(new Denomination(item, itemId, value));
                    } else {
                        LOGGER.warn("Currency denomination item '{}' not found in registry — skipping", itemId);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Non-integer value in denomination entry '{}' — skipping", trimmed);
                }
            }
            // Sort highest value first for greedy algorithms
            result.sort((a, b) -> Integer.compare(b.value, a.value));
        }

        // Fall back to single currency item at value 1
        if (result.isEmpty()) {
            String baseId = TaxConfig.getCurrencyItemName();
            try {
                Item base = ForgeRegistries.ITEMS.getValue(new ResourceLocation(baseId));
                if (base != null) {
                    result.add(new Denomination(base, baseId, 1));
                }
            } catch (Exception ignored) {}
        }

        denominationCache = result;
        denominationConfigCache = config;
        return result;
    }

    /** Returns true if multi-denomination mode is active (more than one denomination). */
    public static boolean isMultiDenominationMode() {
        String config = TaxConfig.getCurrencyDenominations();
        return config != null && !config.isBlank();
    }

    // ── Inventory counting ────────────────────────────────────────────────

    /**
     * Count the total monetary value of all currency denominations in the inventory.
     * Works on both client and server. In single-denomination mode this equals
     * the raw item count; in multi-denomination mode each item contributes its value.
     */
    public static int countInventoryValue(Inventory inv) {
        List<Denomination> denoms = getDenominations();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            for (Denomination d : denoms) {
                if (stack.getItem() == d.item) {
                    total += stack.getCount() * d.value;
                    break;
                }
            }
        }
        return total;
    }

    // ── Giving items ──────────────────────────────────────────────────────

    /**
     * Give the player items worth exactly {@code amount} monetary units.
     *
     * In single-denomination mode: gives {@code amount} of CurrencyItemName.
     * In multi-denomination mode: greedily assigns largest denominations first.
     * Any sub-smallest-denomination remainder is rounded up to one extra coin of
     * the smallest denomination so the player is never shorted.
     */
    public static boolean giveCurrencyToPlayer(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return false;

        List<Denomination> denoms = getDenominations();
        if (denoms.isEmpty()) {
            LOGGER.warn("No valid currency denominations configured; cannot give {} currency", amount);
            return false;
        }

        // Single-denomination fast path
        if (denoms.size() == 1 && denoms.get(0).value == 1) {
            return giveItemsToPlayer(player, denoms.get(0).itemId, amount);
        }

        int remaining = amount;
        for (Denomination d : denoms) {
            if (remaining <= 0) break;
            int count = remaining / d.value;
            if (count <= 0) continue;
            remaining -= count * d.value;
            // Give in stacks (player inventory handles splitting)
            ItemStack stack = new ItemStack(d.item, count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        // Remainder can't be expressed in whole denominations — round up to 1 of smallest
        if (remaining > 0) {
            Denomination smallest = denoms.get(denoms.size() - 1);
            ItemStack extra = new ItemStack(smallest.item, 1);
            if (!player.getInventory().add(extra)) {
                player.drop(extra, false);
            }
        }

        return true;
    }

    // ── Taking items ──────────────────────────────────────────────────────

    /**
     * Remove items worth exactly {@code amount} from the player's inventory.
     * Uses largest denominations first to preserve small change.
     *
     * @return the amount actually removed, or 0 if exact change is not possible.
     */
    public static int takeCurrencyFromInventory(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return 0;

        List<Denomination> denoms = getDenominations();
        if (denoms.isEmpty()) return 0;

        // Check total available value first
        int totalAvailable = countInventoryValue(player.getInventory());
        if (totalAvailable < amount) return 0;

        // Verify exact change is possible with a separate pass before modifying inventory
        if (!canMakeExactChange(player.getInventory(), denoms, amount)) {
            LOGGER.warn("Player {} cannot make exact change for {} — request denied", player.getName().getString(), amount);
            player.sendSystemMessage(Component.literal(
                    "Cannot pay exactly " + amount + ". Please break larger coins into smaller ones.")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
            return 0;
        }

        // Now actually remove items
        int remaining = amount;
        for (Denomination d : denoms) {
            if (remaining <= 0) break;
            int needed = remaining / d.value;
            if (needed <= 0) continue;
            for (int i = 0; i < player.getInventory().getContainerSize() && needed > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() == d.item) {
                    int take = Math.min(needed, stack.getCount());
                    stack.shrink(take);
                    needed -= take;
                    remaining -= take * d.value;
                }
            }
        }
        return amount;
    }

    /**
     * Check whether the player can make exact change for {@code amount}
     * using a greedy algorithm on the available denomination counts.
     */
    private static boolean canMakeExactChange(Inventory inv, List<Denomination> denoms, int amount) {
        // Count available items per denomination
        int[] available = new int[denoms.size()];
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            for (int d = 0; d < denoms.size(); d++) {
                if (stack.getItem() == denoms.get(d).item) {
                    available[d] += stack.getCount();
                    break;
                }
            }
        }

        int remaining = amount;
        for (int d = 0; d < denoms.size(); d++) {
            if (remaining <= 0) break;
            int use = Math.min(remaining / denoms.get(d).value, available[d]);
            remaining -= use * denoms.get(d).value;
        }
        return remaining == 0;
    }

    // ── Legacy helpers ────────────────────────────────────────────────────

    public static boolean giveItemsToPlayer(ServerPlayer player, String itemName, int amount) {
        if (player == null || amount <= 0) return false;

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
        if (item != null) {
            ItemStack itemStack = new ItemStack(item, amount);
            boolean added = player.getInventory().add(itemStack);
            if (!added) {
                player.drop(itemStack, false);
                player.sendSystemMessage(Component.translatable("taxmanager.inventory_full", amount, itemName));
                LOGGER.debug("Player's inventory was full, dropped {} {} near them", amount, itemName);
            } else {
                player.sendSystemMessage(Component.translatable("taxmanager.currency_received", amount, itemName));
                LOGGER.debug("Successfully gave {} {} to player {}", amount, itemName, player.getName().getString());
            }
            return true;
        } else {
            LOGGER.warn("Item {} not found in registry, falling back to /give command", itemName);
            String giveCommand = String.format("give %s %s %d", player.getName().getString(), itemName, amount);
            try {
                player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack(),
                        giveCommand);
                LOGGER.debug("Executed fallback give command: {}", giveCommand);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to execute give command: {}", giveCommand, e);
                return false;
            }
        }
    }
}
