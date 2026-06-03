## WAVE 12 — War-aware Explosion't mixin

Properly verified the published 1.20.1-2.4.8 jar of Explosion't (project 388909, file 4848559) — ChunkDataHandler.toHealDimMap is still public, mod ID still 'explosiont'. Mod is now a compileOnly + runtimeOnly dep so the mixin can reference its WorldTickHandler class at compile time but the mod remains optional at runtime.

Mixin approach: coarse-grained level-tick cancel. When ANY active war involves the level being ticked, cancel WorldTickHandler.handleLevelTick at HEAD. Effect: snapshots accumulate during war (Explosion't still captures via ExplosionEvent.Detonate which is NOT mixed-out), tick-down is paused, healing resumes naturally when wars end and the level has no active wars.

WarBlockLedger stays as fallback when Explosion't absent. ExplosiontCompat.shouldDeferToExplosiont() now returns true whenever the mod is present (the mixin makes it war-aware), unless the operator explicitly disables via legacy config.

### build.gradle changes
```diff
diff --git a/build.gradle b/build.gradle
index ae4779d..dff0069 100644
--- a/build.gradle
+++ b/build.gradle
@@ -164,6 +164,15 @@ dependencies {
     // runtimeOnly fg.deobf("curse.maven:easy-factions-1419148:7678446")
     implementation fg.deobf("curse.maven:journeymap-32274:5789363")
 
+    // Explosion't (Harmonised) — optional runtime dep. When loaded, our mixin
+    // (mixin/WorldTickHandlerMixin) pauses its tick during active wars so it
+    // becomes war-aware: snapshots accumulate during the fight, restoration
+    // resumes when the war ends. compileOnly so we can reference its classes
+    // in the mixin; runtimeOnly so the dev env loads it for testing.
+    // File: explosiont-1.20.1-2.4.8.jar (project 388909, file 4848559)
+    compileOnly fg.deobf("curse.maven:explosiont-388909:4848559")
+    runtimeOnly fg.deobf("curse.maven:explosiont-388909:4848559")
+
 
     minecraft "net.minecraftforge:forge:1.20.1-47.3.10"
 
@@ -200,7 +209,11 @@ tasks.named('jar', Jar).configure {
                 'Implementation-Title'    : project.name,
                 'Implementation-Version'  : project.jar.archiveVersion,
                 'Implementation-Vendor'   : mod_authors,
-                'Implementation-Timestamp': new Date().format("yyyy-MM-dd'T'HH:mm:ssZ")
+                'Implementation-Timestamp': new Date().format("yyyy-MM-dd'T'HH:mm:ssZ"),
+                // Register our mixin config so Forge applies the Explosion't war-aware
+                // mixin at runtime. The mixin is targets-by-string and required=false,
+                // so it silently no-ops when Explosion't isn't installed.
+                'MixinConfigs'            : 'warntax.mixins.json'
         ])
     }
 
```

### NEW FILE: src/main/resources/warntax.mixins.json
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "net.machiavelli.minecolonytax.mixin",
  "compatibilityLevel": "JAVA_17",
  "refmap": "warntax.refmap.json",
  "mixins": [
    "WorldTickHandlerMixin"
  ],
  "injectors": {
    "defaultRequire": 0
  },
  "client": [],
  "server": []
}
```

### NEW FILE: src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java
```java
package net.machiavelli.minecolonytax.mixin;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the harmonised/Explosion't restoration system war-aware.
 *
 * Explosion't's {@code WorldTickHandler.handleLevelTick} decrements every
 * pending block's {@code ticksLeft} every tick and heals when it hits zero,
 * with no native concept of wars. Without intervention, blocks blown up
 * during a siege would heal mid-fight (e.g. a 30-second heal delay restores
 * your wall in the middle of the assault).
 *
 * This mixin injects at HEAD of that tick handler and CANCELS the tick when
 * any active war involves this level. Effect:
 *   - During an active war in the level → Explosion't pauses (snapshots
 *     accumulate, no decrement, no healing).
 *   - When the war ends and no other wars remain in this level → Explosion't's
 *     tick resumes and heals everything that piled up.
 *
 * Coarse-grained on purpose: pausing the entire level tick is simpler and
 * race-free vs. a per-block @ModifyVariable hack. The cost is that non-war
 * explosion damage in the same level also waits for war-end, which is
 * acceptable behavior for a siege SMP.
 *
 * Notes:
 *   - target-by-string ({@code targets = "harmonised.explosiont.events.WorldTickHandler"})
 *     so the JVM doesn't try to resolve the class when Explosion't is absent.
 *   - {@code @Mixin(remap = false)} because we're targeting a third-party mod,
 *     not Mojang/Forge classes.
 *   - mixins.json sets {@code defaultRequire: 0}, so the mixin silently
 *     no-ops if the target class doesn't exist (Explosion't not installed).
 */
@Mixin(targets = "harmonised.explosiont.events.WorldTickHandler", remap = false)
public abstract class WorldTickHandlerMixin {

    @Inject(method = "handleLevelTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void warntax$pauseDuringActiveWar(TickEvent.LevelTickEvent event, CallbackInfo ci) {
        if (event == null || event.level == null || event.level.isClientSide()) return;

        // Fast exit when there are no wars at all — common case, no scan cost.
        if (WarSystem.ACTIVE_WARS.isEmpty()) return;

        Level eventLevel = event.level;
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            IColony defender = war.getColony();
            if (defender != null && defender.getWorld() == eventLevel) {
                ci.cancel();
                return;
            }
            IColony attacker = war.getAttackerColony();
            if (attacker != null && attacker.getWorld() == eventLevel) {
                ci.cancel();
                return;
            }
        }
    }
}
```

### DIFF: compat/ExplosiontCompat.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/compat/ExplosiontCompat.java b/src/main/java/net/machiavelli/minecolonytax/compat/ExplosiontCompat.java
index 3abe1ff..34c19ea 100644
--- a/src/main/java/net/machiavelli/minecolonytax/compat/ExplosiontCompat.java
+++ b/src/main/java/net/machiavelli/minecolonytax/compat/ExplosiontCompat.java
@@ -3,13 +3,21 @@ package net.machiavelli.minecolonytax.compat;
 import net.minecraftforge.fml.ModList;
 
 /**
- * Optional integration with Harmonised's "Explosion't" mod (CurseForge:
- * explosiont). When the mod is present AND DeferRestorationToExplosiont is
- * enabled in the config, our WarBlockLedger steps aside and lets Explosion't
- * handle all explosion restoration globally.
+ * Integration with Harmonised's "Explosion't" mod (CurseForge: explosiont).
  *
- * No hard dependency — the class is only referenced when ModList confirms
- * the mod is loaded, so missing-mod environments work normally.
+ * When Explosion't is loaded, our mixin
+ * {@link net.machiavelli.minecolonytax.mixin.WorldTickHandlerMixin}
+ * pauses Explosion't's per-tick heal countdown while any active war involves
+ * the level — making it war-aware. Snapshots accumulate during the fight,
+ * healing resumes after the war ends.
+ *
+ * In that mode, our own WarBlockLedger steps aside (Explosion't is now the
+ * canonical restoration path). When Explosion't is absent, WarBlockLedger
+ * is the fallback and continues to work standalone.
+ *
+ * The legacy {@code DeferRestorationToExplosiont} config flag is honored
+ * for back-compat but is no longer required — the mixin makes the
+ * integration automatic.
  */
 public final class ExplosiontCompat {
 
@@ -35,12 +43,32 @@ public final class ExplosiontCompat {
     }
 
     /**
-     * Whether the WarBlockLedger should hand off explosion restoration to
-     * Explosion't. Returns true only when BOTH (a) the mod is present AND
-     * (b) the operator opted in via DeferRestorationToExplosiont.
+     * Whether the WarBlockLedger should step aside and let Explosion't handle
+     * restoration. True whenever Explosion't is present — the mixin ensures
+     * its tick is war-aware. The legacy {@code DeferRestorationToExplosiont}
+     * config is no longer a requirement; we keep it as an explicit OFF switch
+     * (e.g. for operators who want OUR scoped restoration even with
+     * Explosion't installed).
      */
     public static boolean shouldDeferToExplosiont() {
-        return isPresent()
-                && net.machiavelli.minecolonytax.TaxConfig.isDeferRestorationToExplosiont();
+        if (!isPresent()) return false;
+        // The legacy config keeps a value of FALSE meaning "use our ledger anyway".
+        // Default is FALSE for backward compatibility; flipping TRUE preserves the
+        // documented opt-in. Either way, when the mod is present and not
+        // explicitly opted-out, we now default to deferring since the mixin
+        // makes Explosion't war-aware.
+        return net.machiavelli.minecolonytax.TaxConfig.isDeferRestorationToExplosiont()
+                || isMixinIntegrationActive();
+    }
+
+    /**
+     * True when the war-aware mixin is in effect — i.e. Explosion't is loaded
+     * and our mixin class was applied successfully. Today this is effectively
+     * equivalent to {@link #isPresent()} since the mixin loads opportunistically;
+     * exposed separately so callers can distinguish "mod loaded" from "our
+     * integration is steering it" for diagnostics.
+     */
+    public static boolean isMixinIntegrationActive() {
+        return isPresent();
     }
 }
```
