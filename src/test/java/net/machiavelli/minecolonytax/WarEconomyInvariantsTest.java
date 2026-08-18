package net.machiavelli.minecolonytax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the war-economy money invariants at the source level.
 *
 * <p>Background (release 5.0.7 audit): the time-expiry victory path deducted the losing
 * colony in {@code handleVictoryRewards}, told the winners they had "received war spoils"
 * without ever crediting anyone, and then {@code endWar}'s "TOTAL VICTORY" branch deducted
 * the loser a SECOND time with a third, unrelated amount formula. The invariants below make
 * that whole bug class fail the build if it creeps back:
 *
 * <ol>
 *   <li>{@code endWar} records history — it must never MOVE money. Every live resolution
 *       path settles the economy before calling it.</li>
 *   <li>{@code handleVictoryRewards} must route economic spoils through
 *       {@code applyWarEconomyTransfers} (the only settlement that debits AND credits as a
 *       pair) instead of deducting on its own.</li>
 *   <li>The raid loot transfer must refund the colony when the raider credit fails —
 *       a debit without a matching credit or refund destroys coins.</li>
 * </ol>
 *
 * <p>Asserted against the SOURCE because these classes pull in Minecraft/Forge types that
 * cannot load in a plain JVM. Comments are stripped first so prose explaining the old bug
 * cannot trip the checks.
 */
class WarEconomyInvariantsTest {

    private static final Path WAR_SYSTEM = Path.of(
            "src/main/java/net/machiavelli/minecolonytax/WarSystem.java");
    private static final Path RAID_MANAGER = Path.of(
            "src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java");
    private static final Path TAX_MANAGER = Path.of(
            "src/main/java/net/machiavelli/minecolonytax/TaxManager.java");

    /** Calls that move coins between ledgers/players. endWar must contain none of them. */
    private static final String[] MONEY_MOVERS = {
            "deductColonyTax(", "payTaxDebt(", "drainTreasury(", "drainWarChest(",
            "deductFromTreasury(", "addToTreasury(", ".setMoney(", "giveCurrencyToPlayer(",
            "transferBalanceToPlayer(", "deductTeamBalanceWithReport(",
            "transferTeamBalanceToSinglePlayer("
    };

    @Test
    @DisplayName("endWar records outcomes but never moves money")
    void endWarMovesNoMoney() throws Exception {
        String body = methodBody(WAR_SYSTEM, "public static void endWar");
        for (String mover : MONEY_MOVERS) {
            assertFalse(body.contains(mover),
                    "endWar() calls " + mover + " - money must be settled by the resolution path "
                            + "BEFORE endWar runs, otherwise it double-charges (this exact bug shipped once).");
        }
    }

    @Test
    @DisplayName("endWar uses the recorded transfer amount, not a formula of its own")
    void endWarUsesRecordedAmount() throws Exception {
        String body = methodBody(WAR_SYSTEM, "public static void endWar");
        assertTrue(body.contains("economyTransferTotal"),
                "endWar() no longer reads warData.economyTransferTotal - the history/DB record "
                        + "would fall back to inventing an amount unrelated to what actually moved.");
    }

    @Test
    @DisplayName("handleVictoryRewards settles spoils through the paired transfer machinery")
    void victoryRewardsUsePairedTransfers() throws Exception {
        String body = methodBody(WAR_SYSTEM, "private static void handleVictoryRewards");
        assertTrue(body.contains("applyWarEconomyTransfers("),
                "handleVictoryRewards() must route economic spoils through applyWarEconomyTransfers "
                        + "(debit AND credit as a pair).");
        assertFalse(body.contains("deductColonyTax("),
                "handleVictoryRewards() deducts the loser colony directly - that is a debit "
                        + "without a credit; the winners get nothing and endWar history lies about it.");
        assertFalse(body.contains("as war spoils"),
                "handleVictoryRewards() tells players they 'received war spoils' - the old message "
                        + "that claimed a payout which never happened. The transfer machinery sends "
                        + "its own, truthful messages.");
    }

    @Test
    @DisplayName("raid loot transfer refunds the colony when the credit fails")
    void raidTransferRefundsOnFailure() throws Exception {
        String body = methodBody(RAID_MANAGER, "private static void transferTaxRevenue");
        assertTrue(body.contains("payTaxDebt(raidData.getColony(), -amountToDeduct)"),
                "transferTaxRevenue() debit call not found - if the debit moved or was renamed, "
                        + "update this guard so the refund pairing stays pinned.");
        assertTrue(body.contains("payTaxDebt(raidData.getColony(), amountToDeduct)"),
                "transferTaxRevenue() has no refund of the colony debit - a failed raider credit "
                        + "(SDMShop missing/failing, broken currency config) then destroys the coins.");
        assertTrue(body.contains("credited"),
                "transferTaxRevenue() no longer tracks whether the raider credit succeeded.");
    }

    @Test
    @DisplayName("percentage penalties never credit a colony that is in debt")
    void percentagePenaltiesClampToPositiveBalance() throws Exception {
        String body = methodBody(TAX_MANAGER, "public static void deductColonyTax");
        assertTrue(body.contains("Math.max(0, currentTax)"),
                "deductColonyTax() multiplies the raw balance by the percentage - with a negative "
                        + "balance the deduction goes negative and the penalty PAYS DOWN the debt.");
    }

    // ==================== helpers ====================

    /**
     * Extracts the body of the first method whose signature starts with {@code signaturePrefix}.
     * Comment stripping and brace matching are done with a small state machine that skips
     * string/char literals — regexes would miscount braces inside log messages ({@code "{}"})
     * and choke on {@code "//"} inside string literals (URLs).
     */
    private static String methodBody(Path source, String signaturePrefix) throws Exception {
        assertTrue(Files.exists(source), source + " not found (run tests from the project root)");
        String code = stripComments(Files.readString(source));
        int sigIdx = code.indexOf(signaturePrefix);
        assertTrue(sigIdx >= 0, "Method signature not found in " + source.getFileName() + ": "
                + signaturePrefix + " - update this guard if the method was renamed.");
        int open = code.indexOf('{', sigIdx);
        assertTrue(open > sigIdx, "No opening brace after signature " + signaturePrefix);
        int depth = 0;
        boolean inString = false, inChar = false;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (inChar) {
                if (c == '\\') i++;
                else if (c == '\'') inChar = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(open + 1, i);
                }
            }
        }
        throw new AssertionError("Unbalanced braces after " + signaturePrefix + " in " + source.getFileName());
    }

    /** Drops line and block comments; string literals (and their contents) are preserved. */
    private static String stripComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        boolean inString = false, inChar = false, inLine = false, inBlock = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') { inLine = false; out.append(c); }
            } else if (inBlock) {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            } else if (inString) {
                out.append(c);
                if (c == '\\') { out.append(next); i++; }
                else if (c == '"') inString = false;
            } else if (inChar) {
                out.append(c);
                if (c == '\\') { out.append(next); i++; }
                else if (c == '\'') inChar = false;
            } else if (c == '/' && next == '/') {
                inLine = true; i++;
            } else if (c == '/' && next == '*') {
                inBlock = true; i++;
            } else {
                out.append(c);
                if (c == '"') inString = true;
                else if (c == '\'') inChar = true;
            }
        }
        return out.toString();
    }
}
