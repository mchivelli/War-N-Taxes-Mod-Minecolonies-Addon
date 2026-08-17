package net.machiavelli.minecolonytax.mixin;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the harmonised/Explosion't restoration system war-and-raid-aware.
 *
 * Explosion't's {@code WorldTickHandler.handleLevelTick} decrements every
 * pending block's {@code ticksLeft} every tick and heals when it hits zero,
 * with no native concept of wars or raids. Without intervention, blocks blown
 * up during a siege would heal mid-fight (e.g. a 30-second heal delay
 * restores your wall in the middle of the assault).
 *
 * This mixin injects at HEAD of that tick handler and CANCELS the tick when
 * ANY of the following is active in this level:
 *   - WarSystem.ACTIVE_WARS — full war (defender or attacker colony in level)
 *   - RaidManager.getActiveRaids() — MineColonies raid on a colony in level
 *   - BesiegeManager — solo besiege on a colony in level
 *
 * Side-effect analysis of HEAD cancel (per inspection of explosiont
 * 1.20.1-2.4.8 source): the upstream method does idempotent
 * {@code computeIfAbsent} initialization for {@code dimForceHeal},
 * {@code dimWasDay}, and {@code ChunkDataHandler.toHealDimMap}, then loops
 * the heal map, then calls {@code dimWasDay.replace(...)} at the end. We
 * skip all of those; the initializations will recreate-as-needed on the
 * next non-cancelled tick (computeIfAbsent), and the {@code dimWasDay}
 * staleness is only consulted inside the loop we're already skipping. No
 * persistent side effects are lost.
 *
 * Optional-mod safety:
 *   - {@code @Pseudo} + {@code @Mixin(targets = "...")} + {@code remap=false}
 *     means the mixin processor does NOT require the target class to exist
 *     at compile time and silently no-ops if Explosion't isn't installed.
 *   - mixins.json sets {@code required: false} + {@code defaultRequire: 0}
 *     so the whole config gracefully no-ops absent the target.
 *
 * Coarse-grained on purpose: pausing the entire level tick is simpler and
 * race-free vs. a per-block @ModifyVariable hack. The cost is that non-war
 * explosion damage in the same level also waits for war/raid-end, which is
 * acceptable behavior for a siege SMP.
 */
@Pseudo
@Mixin(targets = "harmonised.explosiont.events.WorldTickHandler", remap = false)
public abstract class WorldTickHandlerMixin {

    @Inject(method = "handleLevelTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void warntax$pauseDuringActiveConflict(TickEvent.LevelTickEvent event, CallbackInfo ci) {
        if (event == null || event.level == null || event.level.isClientSide()) return;

        // Fast exits when nothing is active.
        boolean anyWar = !WarSystem.ACTIVE_WARS.isEmpty();
        boolean anyRaid = !RaidManager.getActiveRaids().isEmpty();
        boolean anyBesiege = !BesiegeManager.getAllActiveRaidsByBesieger().isEmpty();
        if (!anyWar && !anyRaid && !anyBesiege) return;

        Level eventLevel = event.level;

        if (anyWar) {
            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                IColony defender = war.getColony();
                if (defender != null && defender.getWorld() == eventLevel) { ci.cancel(); return; }
                IColony attacker = war.getAttackerColony();
                if (attacker != null && attacker.getWorld() == eventLevel) { ci.cancel(); return; }
            }
        }

        if (anyRaid) {
            for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
                IColony c = raid.getColony();
                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
            }
        }

        if (anyBesiege) {
            net.minecraft.resources.ResourceKey<Level> dimKey = eventLevel.dimension();
            for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
                // Fast path: compare cached dimension in O(1) (audit H12).
                if (raid.dimension != null) {
                    if (raid.dimension.equals(dimKey)) { ci.cancel(); return; }
                    continue;
                }
                // Fallback for a raid with no cached dimension (e.g. restored): resolve by id.
                IColony c = net.machiavelli.minecolonytax.util.ColonyLookup.byId(raid.colonyId);
                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
            }
        }
    }
}
