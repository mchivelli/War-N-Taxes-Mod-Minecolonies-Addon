package net.machiavelli.minecolonytax.permissions;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for the officer permission set rendered by the Officers tab. */
public class ColonyPermissionTest {

    /**
     * Ordinals are the wire format for both permission packets. Reordering the enum silently
     * remaps every stored and in-flight permission, so the order is pinned here.
     */
    @Test
    void wireOrderIsStable() {
        ColonyPermission[] values = ColonyPermission.values();
        assertEquals(ColonyPermission.CLAIM_TAX, values[0]);
        assertEquals(ColonyPermission.WITHDRAW_FUNDS, values[1]);
        assertEquals(ColonyPermission.DEPLOY_SPY, values[2]);
        assertEquals(ColonyPermission.DECLARE_WAR, values[3]);
        assertEquals(4, values.length,
                "A new permission must be appended, never inserted, and needs a server-side gate "
                        + "plus a wiki entry before it ships.");
    }

    @Test
    void byOrdinalRejectsOutOfRangeValues() {
        // The decoder feeds these straight from the network, so a hostile client must not be
        // able to index past the enum.
        assertNull(ColonyPermission.byOrdinal(-1));
        assertNull(ColonyPermission.byOrdinal(ColonyPermission.values().length));
        assertNull(ColonyPermission.byOrdinal(Integer.MAX_VALUE));
        assertNull(ColonyPermission.byOrdinal(Integer.MIN_VALUE));
    }

    @Test
    void byOrdinalRoundTripsEveryValue() {
        for (ColonyPermission permission : ColonyPermission.values()) {
            assertEquals(permission, ColonyPermission.byOrdinal(permission.ordinal()));
        }
    }

    /**
     * Every action is permitted by default. Officers already had all of these before the
     * permission system existed, so a default of "denied" would silently strip abilities from
     * every officer on every existing server the moment the update lands.
     */
    @Test
    void everyPermissionIsAllowedByDefault() {
        for (ColonyPermission permission : ColonyPermission.values()) {
            assertTrue(permission.isDefaultAllowed(),
                    permission + " must default to allowed so updating the mod does not change "
                            + "what officers can do until an owner decides otherwise.");
        }
    }

    /**
     * The besiege lock exists to stop a former owner draining a colony that is being taken from
     * them. It has only ever applied to tax claiming; widening it silently would be an
     * unannounced gameplay change.
     */
    @Test
    void onlyTaxClaimingIsSuppressedByASiege() {
        assertTrue(ColonyPermission.CLAIM_TAX.isBlockedWhileBesieged());
        assertFalse(ColonyPermission.WITHDRAW_FUNDS.isBlockedWhileBesieged());
        assertFalse(ColonyPermission.DEPLOY_SPY.isBlockedWhileBesieged());
        assertFalse(ColonyPermission.DECLARE_WAR.isBlockedWhileBesieged());
    }

    @Test
    void displayNamesAreUniqueAndFitTheBookPage() {
        Set<String> seen = new HashSet<>();
        for (ColonyPermission permission : ColonyPermission.values()) {
            String name = permission.getDisplayName();
            assertNotNull(name, permission + " needs a display name");
            assertFalse(name.isBlank(), permission + " needs a non-blank display name");
            assertTrue(seen.add(name), "Duplicate display name in the permission list: " + name);
            // The right book page reserves roughly 90px next to a 28px toggle; 16 characters is
            // the point where the label starts being truncated with an ellipsis.
            assertTrue(name.length() <= 16,
                    permission + " display name \"" + name + "\" is too long for the book page");
        }
    }
}
