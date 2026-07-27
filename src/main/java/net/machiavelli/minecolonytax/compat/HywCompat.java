package net.machiavelli.minecolonytax.compat;

import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Pure-reflection bridge to <b>Hundred Years Warfare</b> (modid {@code hundred_years_war}).
 *
 * <p>This class holds the only reference in the entire mod to HYW types, and even that is
 * reflective and string-based, so War 'n Taxes builds and runs as a standalone mod whether
 * or not HYW is installed. Everything is guarded by {@link ModList#isLoaded(String)} plus a
 * one-time reflective resolution; any failure (mod absent, signature drift after an HYW
 * update) permanently disables the bridge for the JVM lifetime so we degrade silently
 * instead of crashing or spamming logs.
 *
 * <p>Modelled on {@link EasyFactionsBridge} (the mod's existing reflection-only compat
 * pattern). The HYW soldier type and its owner/target API (verified by decompiling
 * {@code HundredYearsWar-0.6.4r-1.21.1-neoforge.jar}):
 * <pre>
 *   ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity extends PathfinderMob
 *     UUID        getOwnerUUID()   // reads the synched OWNER_UUID data → resolves offline
 *     LivingEntity getHywTarget()
 *     void        setHywTarget(LivingEntity)
 *     void        setStartAttacking(boolean)
 *     void        setHasTarget(boolean)
 * </pre>
 * We resolve the owner via a prioritized chain — {@code getOwnerUUID()} first, falling back to
 * {@code getOwner():ServerPlayer} — so a future HYW that renames the UUID getter still attributes
 * owners instead of silently disabling all protection.
 *
 * <p>Every commandable HYW unit — foot troops, workers, all cavalry riders, every siege engine
 * (cannon/bombard/mangonel/trebuchet/nest-of-bees/battering-ram/siege-tower), undead, puppets
 * and bandits — decompiles to a subclass of {@code BaseCombatEntity}, so the single
 * {@code isInstance} check covers the whole roster (the rideable {@code NpcHorse} is the only
 * non-combat exception and never attacks on its own; its rider is a {@code BaseCombatEntity}).
 *
 * <p>If HYW moves these, the bridge disables itself and colonists simply lose the extra
 * protection layer (vanilla behaviour resumes) rather than breaking the mod.
 */
public final class HywCompat {

    private static final Logger LOGGER = LogManager.getLogger(HywCompat.class);

    private static final String MOD_ID = "hundred_years_war";
    private static final String SOLDIER_CLASS =
            "ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity";

    private static volatile State state = State.UNRESOLVED;
    // All handles volatile so a reader observing state==READY also sees fully-published refs.
    private static volatile Class<?> soldierClass;
    // Owner attribution: offline-safe UUID getter preferred, online-only getOwner() as fallback.
    private static volatile Method getOwnerUuid;   // no-arg, returns UUID (offline-safe); may be null
    private static volatile Method getOwner;        // no-arg, returns the owning Player; may be null
    private static volatile Method getHywTarget;
    private static volatile Method setHywTarget;
    private static volatile Method setStartAttacking;
    private static volatile Method setHasTarget;

    private enum State { UNRESOLVED, READY, ABSENT }

    private HywCompat() {}

    public static boolean isAvailable() {
        if (state == State.READY) return true;
        if (state == State.ABSENT) return false;
        return resolve();
    }

    private static synchronized boolean resolve() {
        if (state != State.UNRESOLVED) return state == State.READY;

        if (!ModList.get().isLoaded(MOD_ID)) {
            state = State.ABSENT;
            return false;
        }

        try {
            Class<?> c = Class.forName(SOLDIER_CLASS);
            getHywTarget = c.getMethod("getHywTarget");
            setHywTarget = c.getMethod("setHywTarget", LivingEntity.class);
            setStartAttacking = c.getMethod("setStartAttacking", boolean.class);
            setHasTarget = c.getMethod("setHasTarget", boolean.class);
            // Owner accessor chain: source-named getOwnerUUID() → getOwner() (drift fallback).
            getOwnerUuid = firstNoArgMethod(c, "getOwnerUUID");
            getOwner = firstNoArgMethod(c, "getOwner");
            if (getOwnerUuid == null && getOwner == null) {
                throw new NoSuchMethodException(
                        "HYW BaseCombatEntity exposes no owner accessor (getOwnerUUID/getOwner)");
            }
            soldierClass = c;
            state = State.READY;
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Hundred Years Warfare detected - troop friendly-fire protection enabled.");
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Hundred Years Warfare present but its API could not be resolved ({}). "
                    + "HYW compatibility disabled.", t.toString());
            state = State.ABSENT;
            return false;
        }
    }

    /** True if the entity is an HYW commandable soldier. Cheap once resolved. */
    public static boolean isHywSoldier(Entity e) {
        if (e == null || !isAvailable()) return false;
        Class<?> c = soldierClass;
        return c != null && c.isInstance(e);
    }

    /** The UUID of the player commanding this HYW soldier, or {@code null} if unowned. */
    public static UUID getSoldierOwner(Entity soldier) {
        if (!isHywSoldier(soldier)) return null;
        try {
            // Offline-safe path: read the synched owner UUID directly (resolves even while the
            // owner is logged out). An empty owner yields null (e.g. bandits) → not attributable.
            if (getOwnerUuid != null) {
                Object v = getOwnerUuid.invoke(soldier);
                if (v instanceof UUID u) return u;
            }
            // Fallback: resolve via the owning player entity (null when that player is offline).
            if (getOwner != null) {
                Object v = getOwner.invoke(soldier);
                if (v instanceof net.minecraft.world.entity.player.Player p) return p.getUUID();
            }
            return null;
        } catch (Throwable t) {
            disableOnDrift(t);
            return null;
        }
    }

    /** The soldier's current combat target, or {@code null}. */
    public static LivingEntity getSoldierTarget(Entity soldier) {
        if (!isHywSoldier(soldier)) return null;
        try {
            Object v = getHywTarget.invoke(soldier);
            return (v instanceof LivingEntity le) ? le : null;
        } catch (Throwable t) {
            disableOnDrift(t);
            return null;
        }
    }

    /**
     * Reflectively clears this soldier's current combat target and stops it attacking.
     *
     * <p>Used as the proactive layer: the instant a friendly-fire hit is cancelled we also
     * strip the target so an HYW soldier whose custom AI bypassed the targeting event stops
     * trying to harm a protected colonist, instead of swinging at it forever.
     */
    public static void clearHostileTarget(Entity soldier) {
        if (!isHywSoldier(soldier)) return;
        try {
            setHywTarget.invoke(soldier, (Object) null);
            setStartAttacking.invoke(soldier, false);
            setHasTarget.invoke(soldier, false);
            if (soldier instanceof Mob mob) {
                mob.setTarget(null);
            }
        } catch (Throwable t) {
            disableOnDrift(t);
        }
    }

    /** First resolvable no-arg method among {@code names}, or {@code null} if none exist. */
    private static Method firstNoArgMethod(Class<?> c, String... names) {
        for (String n : names) {
            try {
                return c.getMethod(n);
            } catch (NoSuchMethodException ignored) {
                // try the next candidate name
            }
        }
        return null;
    }

    /** Permanently disable the bridge only on structural drift; ignore transient runtime blips. */
    private static void disableOnDrift(Throwable t) {
        // InvocationTargetException means the correctly-resolved HYW method threw INTERNALLY at
        // runtime (e.g. a momentary null in HYW's own AI state) — a transient blip, NOT an API/shape
        // change. It is itself a ReflectiveOperationException, so it must be handled BEFORE the check
        // below; otherwise one such throw would flip the bridge to ABSENT for the whole JVM session
        // and silently kill all friendly-fire protection (the exact disaster this class exists to
        // prevent). Log and keep the bridge live.
        if (t instanceof java.lang.reflect.InvocationTargetException) {
            LOGGER.warn("HYW call threw internally (transient — bridge stays active): {}", t.toString());
            return;
        }
        // Genuine structural drift: method renamed/removed, illegal access, or class-version mismatch.
        if (t instanceof ReflectiveOperationException || t instanceof LinkageError) {
            LOGGER.warn("HYW API shape no longer matches; compatibility disabled: {}", t.toString());
            state = State.ABSENT;
        }
    }
}
