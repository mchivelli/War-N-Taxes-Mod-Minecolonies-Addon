package net.machiavelli.minecolonytax.compat;

import net.minecraftforge.fml.ModList;

/**
 * Integration with Harmonised's "Explosion't" mod (CurseForge: explosiont).
 *
 * When Explosion't is loaded, our mixin
 * {@link net.machiavelli.minecolonytax.mixin.WorldTickHandlerMixin}
 * pauses Explosion't's per-tick heal countdown while any active war involves
 * the level — making it war-aware. Snapshots accumulate during the fight,
 * healing resumes after the war ends.
 *
 * In that mode, our own WarBlockLedger steps aside (Explosion't is now the
 * canonical restoration path). When Explosion't is absent, WarBlockLedger
 * is the fallback and continues to work standalone.
 *
 * The legacy {@code DeferRestorationToExplosiont} config flag is honored
 * for back-compat but is no longer required — the mixin makes the
 * integration automatic.
 */
public final class ExplosiontCompat {

    private static final String MOD_ID = "explosiont";

    private static Boolean cachedPresence = null;

    private ExplosiontCompat() {}

    /**
     * Returns true when the Explosion't mod is loaded. Cached after first call
     * — ModList state doesn't change after server start.
     */
    public static boolean isPresent() {
        if (cachedPresence == null) {
            try {
                cachedPresence = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
            } catch (Throwable t) {
                cachedPresence = false;
            }
        }
        return cachedPresence;
    }

    /**
     * Whether the WarBlockLedger should step aside and let Explosion't handle
     * restoration. Returns true whenever Explosion't is present: the war-aware
     * mixin pauses its heal countdown during conflicts, so Explosion't is the
     * canonical restoration path and our ledger would otherwise double-restore.
     * The legacy {@code DeferRestorationToExplosiont} config flag is retained for
     * back-compat and can force-enable deferral, but while the mod is present we
     * defer regardless — it is not an OFF switch in that case.
     */
    public static boolean shouldDeferToExplosiont() {
        if (!isPresent()) return false;
        // When Explosion't is installed, our mixin makes its per-tick healing
        // war-aware, so we always hand restoration off to it. The legacy config can
        // force deferral even without the mixin, but cannot disable it here.
        return net.machiavelli.minecolonytax.TaxConfig.isDeferRestorationToExplosiont()
                || isMixinIntegrationActive();
    }

    /**
     * True when the war-aware mixin is in effect — i.e. Explosion't is loaded
     * and our mixin class was applied successfully. Today this is effectively
     * equivalent to {@link #isPresent()} since the mixin loads opportunistically;
     * exposed separately so callers can distinguish "mod loaded" from "our
     * integration is steering it" for diagnostics.
     */
    public static boolean isMixinIntegrationActive() {
        return isPresent();
    }
}
