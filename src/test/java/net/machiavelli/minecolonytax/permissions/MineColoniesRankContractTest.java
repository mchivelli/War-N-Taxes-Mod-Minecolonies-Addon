package net.machiavelli.minecolonytax.permissions;

import com.minecolonies.api.colony.permissions.IPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract test for MineColonies rank numbering.
 *
 * <p>Background: the Officers tab shipped broken because the officer lookup assumed a colony's
 * most privileged rank carried the HIGHEST id. It is the other way round — owner is 0 and the
 * numbers grow as privilege falls. A filter written as "skip everyone at or below 1" therefore
 * skipped the owner and every officer, i.e. exactly the players the tab exists to list, while
 * showing hostile players labelled as officers. The same inversion was independently present in
 * the peace system, where {@code rank.getId() >= 2} let ordinary friends answer peace proposals
 * while locking out officers.
 *
 * <p>Both call sites now use the named accessors ({@code getRankOwner()},
 * {@code isColonyManager()}, {@code isHostile()}) rather than raw ids. This test pins the
 * underlying numbering anyway, so that if a MineColonies bump ever renumbers the ranks, the
 * build fails here with an explanation instead of silently changing who may run a colony.
 *
 * <p>Note: values are read reflectively on purpose. Referencing the constants directly would let
 * javac inline them at compile time, and the test would then assert the constants against
 * themselves rather than against whatever jar is actually on the classpath.
 */
public class MineColoniesRankContractTest {

    private static int rankId(String constantName) {
        try {
            Field field = IPermissions.class.getField(constantName);
            return field.getInt(null);
        } catch (NoSuchFieldException e) {
            fail("MineColonies no longer defines IPermissions." + constantName
                    + " — rank handling in this mod needs review.");
        } catch (IllegalAccessException e) {
            fail("Could not read IPermissions." + constantName + ": " + e);
        }
        return Integer.MIN_VALUE;
    }

    @Test
    void ownerIsTheLowestRankId() {
        assertEquals(0, rankId("OWNER_RANK_ID"),
                "Owner must be rank id 0. If this changed, every numeric rank comparison in the "
                        + "mod is suspect — prefer the named accessors instead.");
    }

    @Test
    void rankIdsGrowAsPrivilegeFalls() {
        int owner = rankId("OWNER_RANK_ID");
        int officer = rankId("OFFICER_RANK_ID");
        int friend = rankId("FRIEND_RANK_ID");
        int neutral = rankId("NEUTRAL_RANK_ID");
        int hostile = rankId("HOSTILE_RANK_ID");

        assertTrue(owner < officer, "owner must outrank officer, so its id must be lower");
        assertTrue(officer < friend, "officer must outrank friend, so its id must be lower");
        assertTrue(friend < neutral, "friend must outrank neutral, so its id must be lower");
        assertTrue(neutral < hostile, "neutral must outrank hostile, so its id must be lower");
    }

    @Test
    void rankIdsHaveTheExpectedValues() {
        assertEquals(0, rankId("OWNER_RANK_ID"));
        assertEquals(1, rankId("OFFICER_RANK_ID"));
        assertEquals(2, rankId("FRIEND_RANK_ID"));
        assertEquals(3, rankId("NEUTRAL_RANK_ID"));
        assertEquals(4, rankId("HOSTILE_RANK_ID"));
    }

    /**
     * Custom ranks are appended above the built-in five. Any code that buckets ranks by number
     * would misfile them, which is the second reason this mod uses the named accessors.
     */
    @Test
    void builtInRanksOccupyTheLowestFiveIds() {
        int highestBuiltIn = rankId("HOSTILE_RANK_ID");
        assertEquals(4, highestBuiltIn,
                "Built-in ranks are expected to occupy ids 0-4, leaving 5+ for colony-defined "
                        + "custom ranks.");
    }
}
