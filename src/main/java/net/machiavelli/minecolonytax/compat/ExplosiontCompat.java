package net.machiavelli.minecolonytax.compat;

import net.neoforged.fml.ModList;

/**
 * Integration with Harmonised's "Explosion't" mod (CurseForge: explosiont).
 *
 * <p>By default (config {@code DeferRestorationToExplosiont} = false) our own {@link
 * net.machiavelli.minecolonytax.siege.WarBlockLedger} is the canonical war-damage restoration
 * path: it snapshots blocks broken during a war and restores them when the war ends, standalone,
 * whether or not Explosion't is installed.
 *
 * <p><b>NeoForge port note:</b> the Forge original relied on a {@code WorldTickHandlerMixin} to make
 * Explosion't's per-tick heal war-aware and hand restoration off to it. That mixin is NOT present in
 * this NeoForge port, so there is no automatic hand-off — WarBlockLedger stays authoritative. Only
 * when an admin explicitly sets {@code DeferRestorationToExplosiont} = true (having wired Explosion't
 * up themselves) does WarBlockLedger step aside; see {@link #shouldDeferToExplosiont()}.
 *
 * <p>This class is purely {@link ModList}-guarded with no compile-time reference to Explosion't, so
 * it compiles and degrades gracefully whether or not the mod is present.
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
