package net.machiavelli.minecolonytax.compat;

import net.neoforged.fml.ModList;

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
 * <p><b>NeoForge port note:</b> the only adaptation from the Forge original is
 * the ModList package ({@code net.neoforged.fml.ModList}). This class is purely
 * {@link ModList}-guarded with no compile-time reference to Explosion't, so it
 * compiles and degrades gracefully whether or not the mod is present.
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
     * Whether the WarBlockLedger should step aside and let Explosion't handle restoration.
     *
     * <p>Driven by the EXPLICIT admin config {@code DeferRestorationToExplosiont} (default false),
     * which does now exist in {@code TaxConfig}. It deliberately does NOT auto-defer merely because
     * Explosion't is loaded: the war-aware {@code WorldTickHandlerMixin} the old note assumed is not
     * present in this NeoForge port, so auto-deferring would leave NEITHER system restoring war damage
     * (WarBlockLedger steps aside while Explosion't heals ungated). Default (false) therefore keeps the
     * WarBlockLedger snapshot/restore path — the one that actually works — and an admin who has wired
     * Explosion't up themselves can opt out via the config.
     */
    public static boolean shouldDeferToExplosiont() {
        if (!isPresent()) return false;
        return net.machiavelli.minecolonytax.TaxConfig.isDeferRestorationToExplosiont();
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
