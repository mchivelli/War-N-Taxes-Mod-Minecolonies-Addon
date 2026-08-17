package net.machiavelli.minecolonytax.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Support for the Corpse mod (<a href="https://github.com/henkelmax/corpse">henkelmax/corpse</a>),
 * which spawns a lootable corpse holding a player's inventory where they died.
 *
 * <p><b>Why this exists.</b> Dying inside somebody else's colony is routine in this mod — wars,
 * raids and besieges all happen there. MineColonies cancels entity interaction for players who lack
 * {@code RIGHTCLICK_ENTITY} in that colony, which is every non-member, so the corpse is standing
 * right there and its owner cannot open it. Their inventory is effectively held hostage by the
 * colony border.
 *
 * <p><b>Deliberately dependency-free.</b> Corpse is not a compile dependency and none of its types
 * appear here. The entity is recognised by its registry id ({@code corpse:corpse}) and the owner is
 * read reflectively — so this file cannot cause the {@code NoClassDefFoundError} that a direct
 * import of an optional mod causes, and it keeps working across Corpse's Forge and NeoForge builds
 * where the internals differ.
 *
 * <p><b>Ownership is checked here on purpose.</b> Corpse's own {@code corpse.access.only_owner}
 * setting defaults to <em>false</em>, i.e. out of the box anyone may open anyone's corpse. Relying
 * on it would mean that unblocking interaction also lets a besieging player strip every defender's
 * corpse. So the exemption is granted only for a player's OWN corpse, regardless of how Corpse is
 * configured; other people's corpses stay subject to the normal colony rules.
 *
 * <p>Every lookup fails CLOSED: if the owner cannot be determined, no exemption is granted and
 * behaviour is exactly as before.
 */
public final class CorpseCompat {

    private static final Logger LOGGER = LogManager.getLogger(CorpseCompat.class);

    /** Mod id and the namespace of its entity registry entry. */
    public static final String MOD_ID = "corpse";

    private static Boolean cachedPresent = null;
    private static volatile Method ownerGetter = null;
    private static volatile boolean ownerGetterResolved = false;
    private static volatile boolean warnedAboutOwnerLookup = false;

    private CorpseCompat() {}

    /** True when the Corpse mod is loaded. */
    public static boolean isInstalled() {
        Boolean cached = cachedPresent;
        if (cached != null) return cached;
        boolean present;
        try {
            present = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        } catch (Throwable t) {
            present = false;
        }
        cachedPresent = present;
        return present;
    }

    /**
     * True when {@code entity} is a Corpse-mod corpse.
     *
     * <p>Matched on the entity type's registry namespace rather than its class, so no Corpse type is
     * ever referenced and a renamed implementation class does not break detection.
     */
    public static boolean isCorpse(Entity entity) {
        if (entity == null || !isInstalled()) return false;
        try {
            ResourceLocation key = EntityType.getKey(entity.getType());
            return key != null && MOD_ID.equals(key.getNamespace());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True only when {@code entity} is a corpse that demonstrably belongs to {@code playerId}.
     *
     * <p>Fails closed: an unknown owner returns false, so an unrecognised Corpse build removes the
     * convenience rather than opening every corpse in a colony to everyone.
     */
    public static boolean belongsTo(Entity entity, UUID playerId) {
        if (playerId == null || !isCorpse(entity)) return false;
        Method getter = resolveOwnerGetter(entity.getClass());
        if (getter == null) return false;
        try {
            return playerId.equals(unwrapUuid(getter.invoke(entity)));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Candidate names for the corpse's owner accessor, newest-known first.
     *
     * <p>The shape differs across Corpse releases, which is exactly why this is resolved rather than
     * assumed: the shipping 1.20.1 and 1.21.1 builds expose {@code Optional<UUID> getCorpseUUID()},
     * while the development line has moved to {@code UUID getPlayerUuid()}. Reading only one of the
     * two would have left the feature quietly dead on every current server.
     */
    private static final String[] OWNER_GETTER_CANDIDATES = {
            "getCorpseUUID", "getPlayerUuid", "getPlayerUUID", "getOwnerUuid"
    };

    /**
     * Find the owner accessor on a corpse class. Package-visible and class-based (not
     * instance-based) so a contract test can run it against the real Corpse jar.
     *
     * @return the accessor, or null when none of the known shapes is present
     */
    static Method findOwnerGetter(Class<?> corpseClass) {
        if (corpseClass == null) return null;
        for (String name : OWNER_GETTER_CANDIDATES) {
            try {
                Method m = corpseClass.getMethod(name);
                Class<?> returnType = m.getReturnType();
                // Accept both the plain and the Optional-wrapped shape.
                if (UUID.class.isAssignableFrom(returnType)
                        || java.util.Optional.class.isAssignableFrom(returnType)) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // try the next candidate name
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Accepts a UUID or an Optional&lt;UUID&gt; and yields the UUID (or null). */
    private static UUID unwrapUuid(Object value) {
        if (value instanceof java.util.Optional<?> optional) {
            value = optional.orElse(null);
        }
        return value instanceof UUID uuid ? uuid : null;
    }

    /**
     * Resolved once off the live entity class. If Corpse ever changes the accessor beyond every
     * known shape this is reported once at WARN — a silently-disabled feature is the failure mode
     * this codebase keeps getting bitten by.
     */
    private static Method resolveOwnerGetter(Class<?> corpseClass) {
        if (ownerGetterResolved) return ownerGetter;
        synchronized (CorpseCompat.class) {
            if (ownerGetterResolved) return ownerGetter;
            Method found = findOwnerGetter(corpseClass);
            ownerGetter = found;
            ownerGetterResolved = true;
            if (found == null && !warnedAboutOwnerLookup) {
                warnedAboutOwnerLookup = true;
                LOGGER.warn("[WnT] Corpse is installed but its owner accessor could not be found on {}. "
                        + "Players will NOT be able to retrieve their corpses inside foreign colonies; "
                        + "colony interaction rules apply unchanged.", corpseClass.getName());
            }
            return found;
        }
    }
}
