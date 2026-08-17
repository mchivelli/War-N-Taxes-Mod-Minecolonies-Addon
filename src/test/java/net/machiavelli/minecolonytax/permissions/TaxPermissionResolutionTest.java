package net.machiavelli.minecolonytax.permissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Precedence rules for officer permissions: an individual override beats the colony default,
 * which beats the action's built-in default.
 *
 * <p>These read and seed the manager's maps reflectively rather than through its setters,
 * because the setters log through the Forge config and persist to disk — neither of which
 * exists in a plain unit test. The resolution methods under test touch neither.
 *
 * <p>{@code CLAIM_TAX} is exercised through {@code isGranted} only. Its effective check
 * consults the besiege lock, which reads Forge config that is not loaded here; the branch
 * structure of {@code can()} is identical for every permission and is covered by the others.
 */
public class TaxPermissionResolutionTest {

    private static final int COLONY = 7;
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("officer".getBytes());
    private static final UUID OTHER_PLAYER = UUID.nameUUIDFromBytes("someone-else".getBytes());

    @SuppressWarnings("unchecked")
    private static Map<Integer, Map<ColonyPermission, Boolean>> colonyDefaults() throws Exception {
        Field field = TaxPermissionManager.class.getDeclaredField("COLONY_DEFAULTS");
        field.setAccessible(true);
        return (Map<Integer, Map<ColonyPermission, Boolean>>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Map<UUID, Map<ColonyPermission, Boolean>>> individual() throws Exception {
        Field field = TaxPermissionManager.class.getDeclaredField("INDIVIDUAL");
        field.setAccessible(true);
        return (Map<Integer, Map<UUID, Map<ColonyPermission, Boolean>>>) field.get(null);
    }

    private static void seedColonyDefault(ColonyPermission permission, boolean allowed) throws Exception {
        colonyDefaults().computeIfAbsent(COLONY, k -> new ConcurrentHashMap<>()).put(permission, allowed);
    }

    private static void seedOverride(UUID player, ColonyPermission permission, boolean allowed) throws Exception {
        individual().computeIfAbsent(COLONY, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(permission, allowed);
    }

    @BeforeEach
    void reset() throws Exception {
        colonyDefaults().clear();
        individual().clear();
    }

    @Test
    void unsetPermissionFallsBackToTheActionDefault() {
        for (ColonyPermission permission : ColonyPermission.values()) {
            assertTrue(TaxPermissionManager.getColonyDefault(COLONY, permission) == permission.isDefaultAllowed(),
                    permission + " with nothing stored must report its built-in default");
            assertTrue(TaxPermissionManager.isGranted(COLONY, PLAYER, permission) == permission.isDefaultAllowed(),
                    permission + " with nothing stored must report its built-in default per player");
        }
    }

    @Test
    void colonyDefaultOverridesTheActionDefault() throws Exception {
        seedColonyDefault(ColonyPermission.DEPLOY_SPY, false);

        assertFalse(TaxPermissionManager.getColonyDefault(COLONY, ColonyPermission.DEPLOY_SPY));
        assertFalse(TaxPermissionManager.isGranted(COLONY, PLAYER, ColonyPermission.DEPLOY_SPY));
    }

    @Test
    void individualOverrideBeatsTheColonyDefault() throws Exception {
        seedColonyDefault(ColonyPermission.DEPLOY_SPY, false);
        seedOverride(PLAYER, ColonyPermission.DEPLOY_SPY, true);

        assertTrue(TaxPermissionManager.isGranted(COLONY, PLAYER, ColonyPermission.DEPLOY_SPY),
                "an individual grant must survive a colony-wide block");
        assertFalse(TaxPermissionManager.isGranted(COLONY, OTHER_PLAYER, ColonyPermission.DEPLOY_SPY),
                "the override must apply only to the player it was set for");
    }

    @Test
    void individualDenialBeatsAPermissiveColonyDefault() throws Exception {
        seedColonyDefault(ColonyPermission.WITHDRAW_FUNDS, true);
        seedOverride(PLAYER, ColonyPermission.WITHDRAW_FUNDS, false);

        assertFalse(TaxPermissionManager.isGranted(COLONY, PLAYER, ColonyPermission.WITHDRAW_FUNDS));
    }

    @Test
    void permissionsDoNotLeakBetweenActions() throws Exception {
        seedColonyDefault(ColonyPermission.WITHDRAW_FUNDS, false);

        assertFalse(TaxPermissionManager.isGranted(COLONY, PLAYER, ColonyPermission.WITHDRAW_FUNDS));
        assertTrue(TaxPermissionManager.isGranted(COLONY, PLAYER, ColonyPermission.DEPLOY_SPY),
                "blocking one action must not affect another");
    }

    @Test
    void permissionsDoNotLeakBetweenColonies() throws Exception {
        seedColonyDefault(ColonyPermission.DEPLOY_SPY, false);

        assertTrue(TaxPermissionManager.isGranted(COLONY + 1, PLAYER, ColonyPermission.DEPLOY_SPY),
                "a block on one colony must not affect another colony");
    }

    @Test
    void ownersBypassEveryRestriction() throws Exception {
        seedColonyDefault(ColonyPermission.DECLARE_WAR, false);
        seedOverride(PLAYER, ColonyPermission.DECLARE_WAR, false);

        assertTrue(TaxPermissionManager.can(COLONY, PLAYER, ColonyPermission.DECLARE_WAR, true, true),
                "the colony owner must never be locked out by a permission setting");
    }

    @Test
    void nonOfficersAreRefusedEvenWhenTheActionIsAllowed() {
        assertFalse(TaxPermissionManager.can(COLONY, PLAYER, ColonyPermission.DEPLOY_SPY, false, false),
                "a colony member below officer rank must not pass the gate");
    }

    @Test
    void officersFollowTheStoredGrant() throws Exception {
        assertTrue(TaxPermissionManager.can(COLONY, PLAYER, ColonyPermission.DEPLOY_SPY, false, true));

        seedOverride(PLAYER, ColonyPermission.DEPLOY_SPY, false);
        assertFalse(TaxPermissionManager.can(COLONY, PLAYER, ColonyPermission.DEPLOY_SPY, false, true));
    }

    @Test
    void nullPlayerOrPermissionIsRefusedRatherThanThrowing() {
        assertFalse(TaxPermissionManager.can(COLONY, null, ColonyPermission.DEPLOY_SPY, true, true));
        assertFalse(TaxPermissionManager.can(COLONY, PLAYER, null, true, true));
    }

    /** The legacy tax-claim helpers must stay wired to CLAIM_TAX after the generalisation. */
    @Test
    void legacyClaimHelpersStillTargetTheTaxPermission() throws Exception {
        seedColonyDefault(ColonyPermission.CLAIM_TAX, false);
        assertFalse(TaxPermissionManager.canOfficersClaim(COLONY));
        assertFalse(TaxPermissionManager.isClaimGranted(COLONY, PLAYER));

        seedOverride(PLAYER, ColonyPermission.CLAIM_TAX, true);
        assertTrue(TaxPermissionManager.isClaimGranted(COLONY, PLAYER));
    }
}
