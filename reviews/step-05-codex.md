Reading additional input from stdin...
OpenAI Codex v0.133.0
--------
workdir: C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: xhigh
reasoning summaries: none
session id: 019e5ce1-452b-71e1-8131-190472716a69
--------
user
Reviewing step 5 of 11. New LivingHurtEvent handler enforces 'solo besiege' on the attacker side only. Source = besieger's colony-mate (not the besieger themselves) AND target = defender-side → cancel damage. Review for: (1) correctness of areColonyMates colony-walking logic — does it correctly identify mates without false positives on Neutral/Hostile rank players, (2) isDefenderSideTarget logic — does it cover all defender entity types correctly, (3) early returns ordering (hot path - this fires on EVERY damage event), (4) any way an attacker's ally could bypass this. Respond: STATUS, up to 5 bullets, up to 3 file:line fix suggestions. Max 300 words.

<stdin>
## STEP 5 — Asymmetric solo besiege damage shield

New file BesiegeDamageShieldHandler.java. Subscribes to LivingHurtEvent. If source is a player who is a colony-mate of any active besieger (but NOT the besieger themselves), and target is on the defender side (citizen or non-hostile player of the besieged colony), cancel the damage. One-way: defender allies are NOT blocked.

```java
package net.machiavelli.minecolonytax.besiege;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces the Siege SMP "solo besiege" rule on the attacker side only:
 *
 * If the damage source is a player belonging to an active besieger's colony
 * (but is NOT the besieger themselves), cancel the damage. The besieger fights
 * alone — their friends and officers can be present but cannot deal damage to
 * defenders.
 *
 * The defender side is asymmetric: defenders may rally allies freely (the
 * call-to-arms message from step 4 invites them). This shield does NOT block
 * defender damage to the attacker.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BesiegeDamageShieldHandler {

    // Throttle the chat message so spam-clicking attacks doesn't flood the chat
    private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
    private static final long BLOCK_MESSAGE_COOLDOWN_MS = 3000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof ServerPlayer source)) return;

        LivingEntity target = event.getEntity();
        UUID sourceUUID = source.getUUID();

        // If the source player is themselves an active besieger, they can do whatever damage they want.
        BesiegeManager.BesiegeRaidData sourceOwnRaid = BesiegeManager.getRaidForBesieger(sourceUUID);
        if (sourceOwnRaid != null) return;

        // Look for any active besiege whose besieger is in the same colony as this source player.
        // If found, the source is an attacker's colony-mate and is blocked from helping.
        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
            if (raid.besiegingPlayerUUID == null) continue;
            // Skip if it's their own raid (already handled above)
            if (raid.besiegingPlayerUUID.equals(sourceUUID)) continue;

            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;

            // Confirm the target belongs to the besieged side: a defender citizen of the
            // besieged colony OR a player who is on the defender side (any non-besieger
            // player in the besieged colony's permission list, including notified allies).
            if (!isDefenderSideTarget(target, raid.colonyId)) continue;

            event.setCanceled(true);
            event.setAmount(0f);
            sendBlockedMessage(source);
            return;
        }
    }

    /**
     * True when both players hold an officer/friend rank in any single shared colony.
     * Best-effort — relies on MineColonies permissions. A null besieger lookup returns false.
     */
    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
        if (source.level().getServer() == null) return false;
        // Walk all colonies the source player has any rank in. If the besieger also has
        // any non-neutral rank in the same colony, they are colony-mates.
        try {
            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
                Rank sourceRank = colony.getPermissions().getRank(source.getUUID());
                Rank besiegerRank = colony.getPermissions().getRank(besiegerUUID);
                if (sourceRank == null || besiegerRank == null) continue;
                // Either party being neutral on this colony means they're not "mates" via this colony
                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
        // Citizen of the besieged colony
        if (target instanceof AbstractEntityCitizen citizen) {
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null
                        && data.getColony().getID() == besiegedColonyId) {
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }
        // Player on defender side: any player who has a non-hostile rank on the besieged colony
        if (target instanceof ServerPlayer player) {
            try {
                IColony besieged = com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().stream()
                        .filter(c -> c.getID() == besiegedColonyId)
                        .findFirst().orElse(null);
                if (besieged == null) return false;
                Rank targetRank = besieged.getPermissions().getRank(player.getUUID());
                if (targetRank == null) return false;
                return !targetRank.equals(besieged.getPermissions().getRankHostile())
                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static void sendBlockedMessage(ServerPlayer source) {
        long now = System.currentTimeMillis();
        Long last = LAST_BLOCK_MESSAGE.get(source.getUUID());
        if (last != null && now - last < BLOCK_MESSAGE_COOLDOWN_MS) return;
        LAST_BLOCK_MESSAGE.put(source.getUUID(), now);
        source.sendSystemMessage(Component.literal(
                "You cannot interfere in a solo besiege — your colony-mate must fight alone.")
                .withStyle(ChatFormatting.RED));
    }
}
```
</stdin>
codex
I’ll verify this against the indexed code paths and local MineColonies usage before calling out issues, since the key risk is whether the new handler matches existing rank semantics and raid data shape.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class BesiegeDamageShieldHandler|areColonyMates|isDefenderSideTarget|LivingHurtEvent|BesiegeRaidData|getAllActiveRaidsByBesieger|getRaidForBesieger|RankNeutral|RankHostile" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-ChildItem -Path . -Filter AGENTS.md -Recurse -Force | Select-Object -ExpandProperty FullName' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 8137ms:
.\reviews\step-03-bundle.md:3:Step 3 changes ACTIVE_RAIDS from Map<Integer, BesiegeRaidData> (colony-keyed, one raid per colony) to Map<UUID, BesiegeRaidData> (besieger-keyed). This unblocks multiple besiegers attacking the same colony concurrently AND allows besieging primary colonies (outcome routes via OccupationManager.TAX_ONLY from step 2). The legacy getActiveRaids() now returns a backward-compat colonyâ†’first-raid view. New getRaidsForColony() and getRaidForBesieger() helpers added.
.\reviews\step-03-bundle.md:36:-    private static final Map<Integer, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-bundle.md:47:+    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-bundle.md:55:-        for (Iterator<Map.Entry<Integer, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-03-bundle.md:56:-            Map.Entry<Integer, BesiegeRaidData> entry = it.next();
.\reviews\step-03-bundle.md:57:+        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-03-bundle.md:58:+            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\reviews\step-03-bundle.md:59:             BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-bundle.md:115:             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\reviews\step-03-bundle.md:139:     private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-03-bundle.md:165:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-bundle.md:172:+    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-bundle.md:173:+        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-03-bundle.md:174:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-bundle.md:183:         BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-03-bundle.md:207:+     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-03-bundle.md:209:     public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-03-bundle.md:210:+        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-03-bundle.md:211:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-bundle.md:218:+    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-03-bundle.md:223:+    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-03-bundle.md:244:+            Rank hostile = perms.getRankHostile();
.\reviews\step-03-bundle.md:271:+            Rank hostile = perms.getRankHostile();
.\reviews\step-03-bundle.md:279:+                Rank neutral = perms.getRankNeutral();
.\reviews\step-04-bundle.md:30:-    private static final Map<Integer, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-04-bundle.md:41:+    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-04-bundle.md:49:-        for (Iterator<Map.Entry<Integer, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-04-bundle.md:50:-            Map.Entry<Integer, BesiegeRaidData> entry = it.next();
.\reviews\step-04-bundle.md:51:+        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-04-bundle.md:52:+            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\reviews\step-04-bundle.md:53:             BesiegeRaidData raid = entry.getValue();
.\reviews\step-04-bundle.md:109:             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\reviews\step-04-bundle.md:177:     private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-04-bundle.md:203:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-04-bundle.md:210:+    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-04-bundle.md:211:+        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-04-bundle.md:212:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-04-bundle.md:235:-        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-04-bundle.md:239:+        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
.\reviews\step-04-bundle.md:242:+        for (BesiegeRaidData raid : raids) {
.\reviews\step-04-bundle.md:260:+     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-04-bundle.md:262:     public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-04-bundle.md:263:+        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-04-bundle.md:264:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-04-bundle.md:271:+    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-04-bundle.md:276:+    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-04-bundle.md:335:+            Rank hostile = perms.getRankHostile();
.\reviews\step-04-bundle.md:362:+            Rank hostile = perms.getRankHostile();
.\reviews\step-04-bundle.md:370:+                Rank neutral = perms.getRankNeutral();
.\reviews\step-01-codex.md:561:src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:274:            colony.getPermissions().setPlayerRank(raiderUUID, colony.getPermissions().getRankHostile(), level);
.\reviews\step-01-codex.md:979:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
.\reviews\step-01-codex.md:996:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
.\reviews\step-01-codex.md:1042:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
.\reviews\step-01-codex.md:1733:            Rank neutralRank = permissions.getRankNeutral();
.\reviews\step-01-codex.md:2211:src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:543:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
.\reviews\step-05-bundle.md:3:New file BesiegeDamageShieldHandler.java. Subscribes to LivingHurtEvent. If source is a player who is a colony-mate of any active besieger (but NOT the besieger themselves), and target is on the defender side (citizen or non-hostile player of the besieged colony), cancel the damage. One-way: defender allies are NOT blocked.
.\reviews\step-05-bundle.md:18:import net.minecraftforge.event.entity.living.LivingHurtEvent;
.\reviews\step-05-bundle.md:40:public class BesiegeDamageShieldHandler {
.\reviews\step-05-bundle.md:47:    public static void onLivingHurt(LivingHurtEvent event) {
.\reviews\step-05-bundle.md:49:        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;
.\reviews\step-05-bundle.md:58:        BesiegeManager.BesiegeRaidData sourceOwnRaid = BesiegeManager.getRaidForBesieger(sourceUUID);
.\reviews\step-05-bundle.md:63:        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
.\reviews\step-05-bundle.md:68:            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;
.\reviews\step-05-bundle.md:73:            if (!isDefenderSideTarget(target, raid.colonyId)) continue;
.\reviews\step-05-bundle.md:86:    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
.\reviews\step-05-bundle.md:96:                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
.\reviews\step-05-bundle.md:97:                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
.\reviews\step-05-bundle.md:98:                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
.\reviews\step-05-bundle.md:99:                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
.\reviews\step-05-bundle.md:106:    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
.\reviews\step-05-bundle.md:127:                return !targetRank.equals(besieged.getPermissions().getRankHostile())
.\reviews\step-05-bundle.md:128:                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
.\reviews\step-04-codex.md:46:-    private static final Map<Integer, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-04-codex.md:57:+    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-04-codex.md:65:-        for (Iterator<Map.Entry<Integer, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-04-codex.md:66:-            Map.Entry<Integer, BesiegeRaidData> entry = it.next();
.\reviews\step-04-codex.md:67:+        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-04-codex.md:68:+            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\reviews\step-04-codex.md:69:             BesiegeRaidData raid = entry.getValue();
.\reviews\step-04-codex.md:125:             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\reviews\step-04-codex.md:193:     private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-04-codex.md:219:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-04-codex.md:226:+    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-04-codex.md:227:+        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-04-codex.md:228:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-04-codex.md:251:-        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-04-codex.md:255:+        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
.\reviews\step-04-codex.md:258:+        for (BesiegeRaidData raid : raids) {
.\reviews\step-04-codex.md:276:+     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-04-codex.md:278:     public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-04-codex.md:279:+        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-04-codex.md:280:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-04-codex.md:287:+    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-04-codex.md:292:+    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-04-codex.md:351:+            Rank hostile = perms.getRankHostile();
.\reviews\step-04-codex.md:378:+            Rank hostile = perms.getRankHostile();
.\reviews\step-04-codex.md:386:+                Rank neutral = perms.getRankNeutral();
.\reviews\step-03-codex.md:14:Reviewing step 3 of an 11-step Java refactor (Minecraft 1.20.1 Forge mod, War 'N Taxes / MineColonies addon). Step 3 partially implements multi-besieger support by re-keying ACTIVE_RAIDS from colonyId to besiegerUUID and removing the primary-colony block + the double-besiege block. Phase 2 follow-ups are intentionally deferred (see bundle preamble). Review for: correctness of the re-keying (do all internal call sites consistently use the new key?), backward-compat correctness of getActiveRaids() view (does putIfAbsent semantics matter?), race conditions in the view construction, whether cleanupRaid still removes the correct entry, whether the iterator-based tick loop is still safe with the new key, and any caller of ACTIVE_RAIDS or BesiegeRaidData that the refactor may have missed. The Phase 2 deferrals (shared defender pool, last-kill-credit) are KNOWN gaps â€” please flag them but don't count them against the status. Respond: (1) STATUS APPROVE/REWORK/REJECT, (2) up to 5 bullet findings ranked by severity, (3) up to 3 file:line fix suggestions. Max 350 words.
.\reviews\step-03-codex.md:19:Step 3 changes ACTIVE_RAIDS from Map<Integer, BesiegeRaidData> (colony-keyed, one raid per colony) to Map<UUID, BesiegeRaidData> (besieger-keyed). This unblocks multiple besiegers attacking the same colony concurrently AND allows besieging primary colonies (outcome routes via OccupationManager.TAX_ONLY from step 2). The legacy getActiveRaids() now returns a backward-compat colonyâ†’first-raid view. New getRaidsForColony() and getRaidForBesieger() helpers added.
.\reviews\step-03-codex.md:52:-    private static final Map<Integer, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-codex.md:63:+    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-codex.md:71:-        for (Iterator<Map.Entry<Integer, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-03-codex.md:72:-            Map.Entry<Integer, BesiegeRaidData> entry = it.next();
.\reviews\step-03-codex.md:73:+        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-03-codex.md:74:+            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\reviews\step-03-codex.md:75:             BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:131:             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\reviews\step-03-codex.md:155:     private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-03-codex.md:181:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:188:+    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-codex.md:189:+        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-03-codex.md:190:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:199:         BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-03-codex.md:223:+     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-03-codex.md:225:     public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-03-codex.md:226:+        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-03-codex.md:227:+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:234:+    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-03-codex.md:239:+    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-03-codex.md:260:+            Rank hostile = perms.getRankHostile();
.\reviews\step-03-codex.md:287:+            Rank hostile = perms.getRankHostile();
.\reviews\step-03-codex.md:295:+                Rank neutral = perms.getRankNeutral();
.\reviews\step-03-codex.md:348:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ACTIVE_RAIDS|getActiveRaids\\(|getRaidsForColony\\(|getRaidForBesieger\\(|registerAlly\\(|BesiegeRaidData\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:354:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:69:    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-codex.md:355:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:87:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:358:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:97:        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-03-codex.md:359:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:98:            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\reviews\step-03-codex.md:360:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:99:            BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:363:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:306:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\reviews\step-03-codex.md:366:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:364:    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:367:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:384:    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:368:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:421:    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:369:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:455:    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:370:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:484:    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:371:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:526:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
.\reviews\step-03-codex.md:372:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:543:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:373:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:589:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:374:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:613:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-03-codex.md:376:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:674:    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
.\reviews\step-03-codex.md:377:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:704:    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:378:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:738:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:379:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-codex.md:380:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:746:        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-03-codex.md:381:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:747:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:383:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:771:        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-03-codex.md:385:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:800:     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-03-codex.md:386:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:802:    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-03-codex.md:387:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:803:        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-03-codex.md:388:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:804:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:389:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:811:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-03-codex.md:391:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:816:    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-03-codex.md:393:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:825:    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:394:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:829:    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:395:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1039:    public static class BesiegeRaidData {
.\reviews\step-03-codex.md:396:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1051:        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
.\reviews\step-03-codex.md:399:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:400:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
.\reviews\step-03-codex.md:401:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:402:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
.\reviews\step-03-codex.md:403:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
.\reviews\step-03-codex.md:412:src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\reviews\step-03-codex.md:414:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\reviews\step-03-codex.md:415:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:115:                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:420:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
.\reviews\step-03-codex.md:434:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 10 \"ACTIVE_RAIDS\\.remove|registerAlly\\(|getActiveRaids\\(\\)\\.get\\(|BesiegeManager\\.getActiveRaids\\(|getRaidsForColony\\(|getRaidForBesieger\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:438:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"getAllActiveRaidsByBesieger|getRaidForBesieger|getRaidsForColony|getActiveRaids\\(\\)\\.get\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:457:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-69-    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-codex.md:478:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:499:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-674-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
.\reviews\step-03-codex.md:509:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-738-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:516:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-codex.md:517:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-746-        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-03-codex.md:518:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-747-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:539:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-771-        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-03-codex.md:561:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:800:     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-03-codex.md:563:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-802-    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-03-codex.md:564:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-803-        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-03-codex.md:565:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-804-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:572:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:811:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-03-codex.md:577:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-816-    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-03-codex.md:594:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:616:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:619:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1490-                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
.\reviews\step-03-codex.md:638:src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\reviews\step-03-codex.md:660:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\reviews\step-03-codex.md:663:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-115-                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:683:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-435-            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
.\reviews\step-03-codex.md:717:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-codex.md:719:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:800:     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-03-codex.md:720:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:811:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-03-codex.md:721:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:816:    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-03-codex.md:722:src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\reviews\step-03-codex.md:735:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 35 "class BesiegeRaidData|hostileCitizenIds|alliedPlayers" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:737:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 70 "private static boolean startRaidInternal|BesiegeRaidData raid = new BesiegeRaidData" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:779:69-    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\reviews\step-03-codex.md:797:87-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:807:97-        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\reviews\step-03-codex.md:808:98-            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\reviews\step-03-codex.md:809:99-            BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:904:364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:924:384-    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:961:421-    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:995:455-    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1024:484-    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1048:589-    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:1072:613-    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-03-codex.md:1119:738-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:1126:745-    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-codex.md:1127:746-        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-03-codex.md:1128:747-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:1152:771-        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
.\reviews\step-03-codex.md:1181:800-     * raid should use {@link #getRaidForBesieger(UUID)}.
.\reviews\step-03-codex.md:1183:802-    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-03-codex.md:1184:803-        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\reviews\step-03-codex.md:1185:804-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:1192:811-    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\reviews\step-03-codex.md:1197:816-    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\reviews\step-03-codex.md:1206:825-    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:1210:829-    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:1285:1039:    public static class BesiegeRaidData {
.\reviews\step-03-codex.md:1297:1051-        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
.\reviews\step-03-codex.md:1408:306:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\reviews\step-03-codex.md:1466:364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1561:364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1581:384-    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1618:421-    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1645:484-    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\reviews\step-03-codex.md:1687:526-    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
.\reviews\step-03-codex.md:1752:674-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
.\reviews\step-03-codex.md:1782:704-    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
.\reviews\step-03-codex.md:1816:738-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:1823:745-    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\reviews\step-03-codex.md:1824:746-        List<BesiegeRaidData> matches = new ArrayList<>();
.\reviews\step-03-codex.md:1825:747-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\reviews\step-03-codex.md:1836:953-            Rank hostile = perms.getRankHostile();
.\reviews\step-03-codex.md:1863:980-            Rank hostile = perms.getRankHostile();
.\reviews\step-03-codex.md:1871:988-                Rank neutral = perms.getRankNeutral();
.\reviews\step-03-codex.md:1922:1039-    public static class BesiegeRaidData {
.\reviews\step-03-codex.md:1934:1051-        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
.\reviews\step-03-codex.md:1949:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"BesiegeRaidData\" src/main/java --glob '"'!src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'"'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:1955:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\reviews\step-03-codex.md:1982:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:526:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
.\reviews\step-03-codex.md:1984:src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:613:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-03-codex.md:1990:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:526:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
.\reviews\step-03-codex.md:1992:src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:613:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\reviews\step-03-codex.md:2003:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2004:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
.\reviews\step-03-codex.md:2005:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2006:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
.\reviews\step-03-codex.md:2007:src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
.\reviews\step-03-codex.md:2008:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\reviews\step-03-codex.md:2009:src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:115:                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:2010:src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
.\reviews\step-03-codex.md:2011:src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\reviews\step-03-codex.md:2022:The compatibility view is preserving the old `Map<Integer, BesiegeRaidData>` type, but several current readers treat it as the authoritative active-raid list. That means same-colony concurrent raids can disappear from UI/status logic even if the core storage is re-keyed correctly.
.\reviews\step-03-codex.md:2076:1434-                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2088:1446-                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
.\reviews\step-03-codex.md:2129:1487-                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2132:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
.\reviews\step-03-codex.md:2160:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
.\reviews\step-03-codex.md:2303:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\reviews\step-03-codex.md:2306:115-                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
.\reviews\step-03-codex.md:2391:src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-435-            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
.\reviews\step-03-codex.md:2450:src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\reviews\step-03-codex.md:2499:src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
.\reviews\step-03-codex.md:2512:src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:802:    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\reviews\step-03-codex.md:2518:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2519:src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2523:src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\reviews\step-03-codex.md:2531:src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\reviews\step-03-codex.md:2584:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 30 "spawnedMercenaries|BesiegeRaidData" src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:2586:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 45 "BesiegeManager|BesiegeRaidData|spawnedMercenaries|hostileCitizenIds|registerAlly" src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\reviews\step-03-codex.md:2618:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2630:1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
.\reviews\step-03-codex.md:2671:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\reviews\step-03-codex.md:2674:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
.\reviews\step-03-codex.md:2702:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
.\reviews\step-03-codex.md:2979:- `WntCommands.java:1434`, `WntCommands.java:1487`, `WarEventHandler.java:434`: use `getAllActiveRaidsByBesieger()` / `getRaidForBesieger(UUID)` where callers need all raids or a player-specific raid.
.\reviews\step-03-codex.md:3002:- `WntCommands.java:1434`, `WntCommands.java:1487`, `WarEventHandler.java:434`: use `getAllActiveRaidsByBesieger()` / `getRaidForBesieger(UUID)` where callers need all raids or a player-specific raid.
.\reviews\step-05-codex.md:14:Reviewing step 5 of 11. New LivingHurtEvent handler enforces 'solo besiege' on the attacker side only. Source = besieger's colony-mate (not the besieger themselves) AND target = defender-side â†’ cancel damage. Review for: (1) correctness of areColonyMates colony-walking logic â€” does it correctly identify mates without false positives on Neutral/Hostile rank players, (2) isDefenderSideTarget logic â€” does it cover all defender entity types correctly, (3) early returns ordering (hot path - this fires on EVERY damage event), (4) any way an attacker's ally could bypass this. Respond: STATUS, up to 5 bullets, up to 3 file:line fix suggestions. Max 300 words.
.\reviews\step-05-codex.md:19:New file BesiegeDamageShieldHandler.java. Subscribes to LivingHurtEvent. If source is a player who is a colony-mate of any active besieger (but NOT the besieger themselves), and target is on the defender side (citizen or non-hostile player of the besieged colony), cancel the damage. One-way: defender allies are NOT blocked.
.\reviews\step-05-codex.md:34:import net.minecraftforge.event.entity.living.LivingHurtEvent;
.\reviews\step-05-codex.md:56:public class BesiegeDamageShieldHandler {
.\reviews\step-05-codex.md:63:    public static void onLivingHurt(LivingHurtEvent event) {
.\reviews\step-05-codex.md:65:        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;
.\reviews\step-05-codex.md:74:        BesiegeManager.BesiegeRaidData sourceOwnRaid = BesiegeManager.getRaidForBesieger(sourceUUID);
.\reviews\step-05-codex.md:79:        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
.\reviews\step-05-codex.md:84:            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;
.\reviews\step-05-codex.md:89:            if (!isDefenderSideTarget(target, raid.colonyId)) continue;
.\reviews\step-05-codex.md:102:    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
.\reviews\step-05-codex.md:112:                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
.\reviews\step-05-codex.md:113:                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
.\reviews\step-05-codex.md:114:                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
.\reviews\step-05-codex.md:115:                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
.\reviews\step-05-codex.md:122:    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
.\reviews\step-05-codex.md:143:                return !targetRank.equals(besieged.getPermissions().getRankHostile())
.\reviews\step-05-codex.md:144:                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
.\reviews\step-05-codex.md:165:"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class BesiegeDamageShieldHandler|areColonyMates|isDefenderSideTarget|LivingHurtEvent|BesiegeRaidData|getAllActiveRaidsByBesieger|getRaidForBesieger|RankNeutral|RankHostile" -S .' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:361:        Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:388:                defenderPerms.setPlayerRank(playerUUID, defenderPerms.getRankHostile(), defenderColony.getWorld());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:397:                    attackerPerms.setPlayerRank(playerUUID, attackerPerms.getRankHostile(), attackerColony.getWorld());
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:415:        Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:470:            Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:474:            Rank neutral = perms.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:496:        Rank neutral = perms.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2850:            Rank hostileRank = targetColony.getPermissions().getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2854:                    targetColony.getPermissions().getRankHostile(), level);
.\src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2988:        Rank hostileRank = targetColony.getPermissions().getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:274:            colony.getPermissions().setPlayerRank(raiderUUID, colony.getPermissions().getRankHostile(), level);
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:447:                    perms.setPlayerRank(raiderUUID, perms.getRankNeutral(), targetColony.getWorld());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1191:                    perms.setPlayerRank(raiderUUID, perms.getRankNeutral(), targetColony.getWorld());
.\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java:1340:        Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:66:            Rank hostile = colony.getPermissions().getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:110:            Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:39: *  - getRankNeutral()  returns ranks.get(NEUTRAL_RANK_ID) â€” can return null if the colony's
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:41: *  - getRankHostile()  same â€” can return null. Null-checked before use.
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:139:     * Neutrals, Hostile). getRankNeutral() null-checked â€” if Neutral rank is missing the
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:147:        // Null-guard: getRankNeutral() returns ranks.get(NEUTRAL_RANK_ID) which can be null
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:149:        Rank neutral = perms.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:251:     * Both getRankHostile() and getRankNeutral() can return null on corrupted colony data;
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:268:            Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:297:            Rank neutral = perms.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:120:            Rank neutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:153:                Rank neutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:196:        Rank neutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
.\src\main\java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:115:                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
.\src\main\java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:326:            perms.setPlayerRank(memberUUID, perms.getRankNeutral(), world);
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
.\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3451:                                                        Rank neutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:69:    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:87:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:97:        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:98:            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:99:            BesiegeRaidData raid = entry.getValue();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:306:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:382:    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:402:    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:439:    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:473:    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:502:    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:544:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:561:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:607:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:631:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:692:    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:722:    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:756:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:763:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:764:        List<BesiegeRaidData> matches = new ArrayList<>();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:765:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:799:        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:802:        for (BesiegeRaidData raid : raids) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:826:     * raid should use {@link #getRaidForBesieger(UUID)}.
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:828:    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:829:        Map<Integer, BesiegeRaidData> view = new HashMap<>();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:830:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:837:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:842:    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:851:    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:855:    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1010:            Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1037:            Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1045:                Rank neutral = perms.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1096:    public static class BesiegeRaidData {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1108:        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:13:import net.minecraftforge.event.entity.living.LivingHurtEvent;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:35:public class BesiegeDamageShieldHandler {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:42:    public static void onLivingHurt(LivingHurtEvent event) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:44:        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:53:        BesiegeManager.BesiegeRaidData sourceOwnRaid = BesiegeManager.getRaidForBesieger(sourceUUID);
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:58:        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:63:            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:68:            if (!isDefenderSideTarget(target, raid.colonyId)) continue;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:81:    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:91:                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:92:                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:93:                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:94:                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:101:    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:122:                return !targetRank.equals(besieged.getPermissions().getRankHostile())
.\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:123:                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:808:            Rank neutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1307:        Rank hostile = perms.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1308:        Rank neutral = perms.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:201:            Rank colonyNeutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:240:            Rank hostileRank = permissions.getRankHostile();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:616:            Rank neutralRank = permissions.getRankNeutral();
.\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:875:            Rank neutralRank = permissions.getRankNeutral();
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\PermissionsView.java:350:    public Rank getRankNeutral()
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\PermissionsView.java:356:    public Rank getRankHostile()
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\Permissions.java:641:        if (rank == getRankNeutral() && (action == Action.EDIT_PERMISSIONS || action == Action.TELEPORT_TO_COLONY))
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\Permissions.java:1004:    public Rank getRankHostile()
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\Permissions.java:1026:    public Rank getRankNeutral()
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\Permissions.java:1086:            player.setRank(getRankNeutral());
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:47:import net.minecraftforge.event.entity.living.LivingHurtEvent;
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:568:     * LivingHurtEvent handler.
.\minecolonies\src\main\java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:576:    public void on(final LivingHurtEvent event)
.\minecolonies\src\main\java\com\minecolonies\api\colony\permissions\IPermissions.java:63:    Rank getRankHostile();
.\minecolonies\src\main\java\com\minecolonies\api\colony\permissions\IPermissions.java:69:    Rank getRankNeutral();
.\minecolonies\src\main\java\com\minecolonies\core\commands\generalcommands\CommandGetRanks.java:81:            .filter(colony -> !colony.getPermissions().getRankNeutral().equals(colony.getPermissions().getRank(profile.getId())))
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 8399ms:
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\AGENTS.md
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\.claude\commands\monitoring\agents.md
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\gitnexus\AGENTS.md
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The handler exists in `src/main/java` and the line numbers are slightly earlier than the pasted bundle. I’m reading the surrounding MineColonies permission APIs now, because `getRank()` default behavior is what decides whether neutral/hostile false positives can happen.
2026-05-25T02:06:11.208679Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$start=990; $end=1075; $lines=Get-Content '"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; for("'$i=$start;$i -le $end;$i++){ '"'{0,4}: {1}' -f "'$i, $lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$i=1; Get-Content '"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java' | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$start=990; $end=1075; $lines=Get-Content '"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; for("'$i=$start;$i -le $end;$i++){ '"'{0,4}: {1}' -f "'$i, $lines[$i-1] }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$start=990; $end=1075; $lines=Get-Content '"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; for("'$i=$start;$i -le $end;$i++){ '"'{0,4}: {1}' -f "'$i, $lines[$i-1] }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$i=1; Get-Content '"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java' | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
2026-05-25T02:06:11.209075Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$i=1; Get-Content '"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java' | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$start=980; $end=1045; $lines=Get-Content '"'minecolonies/src/main/java/com/minecolonies/core/colony/permissions/Permissions.java'; for("'$i=$start;$i -le $end;$i++){ '"'{0,4}: {1}' -f "'$i, $lines[$i-1] }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$start=980; $end=1045; $lines=Get-Content '"'minecolonies/src/main/java/com/minecolonies/core/colony/permissions/Permissions.java'; for("'$i=$start;$i -le $end;$i++){ '"'{0,4}: {1}' -f "'$i, $lines[$i-1] }'` rejected: blocked by policy
2026-05-25T02:06:11.211667Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command '$start=980; $end=1045; $lines=Get-Content '"'minecolonies/src/main/java/com/minecolonies/core/colony/permissions/Permissions.java'; for("'$i=$start;$i -le $end;$i++){ '"'{0,4}: {1}' -f "'$i, $lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -Command "rg -n \"Rank getRank\\(|getRank\\(final UUID|getPlayersByRank|getOwner|isSubscriber|getRankOfficer|getRankFriend|getRankNeutral|getRankHostile\" minecolonies/src/main/java/com/minecolonies -S" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 348ms:
minecolonies/src/main/java/com/minecolonies\core\client\gui\townhall\WindowPermissionsPage.java:117:        actionsRank = building.getColony().getPermissions().getRankOfficer();
minecolonies/src/main/java/com/minecolonies\core\client\gui\townhall\WindowPermissionsPage.java:277:            actionsRank = buildingView.getColony().getPermissions().getRankOfficer();
minecolonies/src/main/java/com/minecolonies\core\client\gui\townhall\WindowMainPage.java:261:        final boolean isOwner = buildingView.getColony().getPermissions().getOwner().equals(Minecraft.getInstance().player.getUUID());
minecolonies/src/main/java/com/minecolonies\core\colony\pvp\AttackingPlayer.java:63:        if (colony.getPermissions().getRank(guardColony.getPermissions().getOwner()).isHostile())
minecolonies/src/main/java/com/minecolonies\core\colony\pvp\AttackingPlayer.java:68:        return guardColony.getPermissions().getRank(colony.getPermissions().getOwner()).isHostile()
minecolonies/src/main/java/com/minecolonies\core\colony\ColonyManager.java:557:            if (colony.getPermissions().getOwner().equals(owner))
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:54:    public Set<ColonyPlayer> getPlayersByRank(final Rank rank)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:88:    public Set<ColonyPlayer> getPlayersByRank(@NotNull final Set<Rank> ranks)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:183:    public Map.Entry<UUID, ColonyPlayer> getOwnerEntry()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:208:    public UUID getOwner()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:212:            final Map.Entry<UUID, ColonyPlayer> owner = getOwnerEntry();
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:259:    public Rank getRank(@NotNull final Player player)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:265:    public Rank getRank(final int id)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:278:    public Rank getRank(final UUID id)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:309:    public String getOwnerName()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:313:            final Map.Entry<UUID, ColonyPlayer> owner = getOwnerEntry();
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:338:    public Rank getRankOfficer()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:344:    public Rank getRankFriend()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:350:    public Rank getRankNeutral()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\PermissionsView.java:356:    public Rank getRankHostile()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:455:        final Map.Entry<UUID, ColonyPlayer> owner = getOwnerEntry();
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:480:    public Map.Entry<UUID, ColonyPlayer> getOwnerEntry()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:501:        players.remove(getOwner());
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:538:    public UUID getOwner()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:542:            final Map.Entry<UUID, ColonyPlayer> owner = getOwnerEntry();
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:641:        if (rank == getRankNeutral() && (action == Action.EDIT_PERMISSIONS || action == Action.TELEPORT_TO_COLONY))
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:657:    public Set<ColonyPlayer> getPlayersByRank(final Rank rank)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:671:    public Set<ColonyPlayer> getPlayersByRank(@NotNull final Set<Rank> ranks)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:709:    public Rank getRank(@NotNull final Player player)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:789:    public Rank getRank(final UUID id)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:896:    public String getOwnerName()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:900:            final Map.Entry<UUID, ColonyPlayer> owner = getOwnerEntry();
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:970:        if (getOwnerName().equals("[abandoned]") && getPlayersByRank(ranks.get(OFFICER_RANK_ID)).isEmpty())
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:993:    public Rank getRankOfficer()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:1004:    public Rank getRankHostile()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:1015:    public Rank getRankFriend()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:1026:    public Rank getRankNeutral()
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:1032:    public Rank getRank(final int id)
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:1084:        for (ColonyPlayer player : getPlayersByRank(rank))
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\Permissions.java:1086:            player.setRank(getRankNeutral());
minecolonies/src/main/java/com/minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:527:        if (event.getEntity().equals(event.getItem().getOwner())) return;   // always allowed to pick up your own thrown items
minecolonies/src/main/java/com/minecolonies\core\colony\IColonyManagerCapability.java:142:                      .error("Colony: " + colony.getName() + " id:" + colony.getID() + " owner:" + colony.getPermissions().getOwnerName() + " could not be saved! Error:", e);
minecolonies/src/main/java/com/minecolonies\core\client\gui\map\WindowColonyMap.java:412:            colonyPane.setID(colonyInfo.getId() + colonyInfo.getOwner());
minecolonies/src/main/java/com/minecolonies\core\client\gui\map\WindowColonyMap.java:416:              .append(Component.literal("Owner:" + colonyInfo.getOwner()))
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:30:    Set<ColonyPlayer> getPlayersByRank(Rank rank);
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:32:    Set<ColonyPlayer> getPlayersByRank(@NotNull Set<Rank> ranks);
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:45:    Rank getRank(int id);
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:57:    Rank getRankOfficer();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:63:    Rank getRankHostile();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:69:    Rank getRankNeutral();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:75:    Rank getRankFriend();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:91:    String getOwnerName();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:113:    Map.Entry<UUID, ColonyPlayer> getOwnerEntry();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:132:    UUID getOwner();
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:153:    Rank getRank(UUID player);
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\IPermissions.java:161:    Rank getRank(Player player);
minecolonies/src/main/java/com/minecolonies\api\colony\permissions\ColonyPlayer.java:47:    public Rank getRank()
minecolonies/src/main/java/com/minecolonies\core\network\messages\server\colony\ColonyAbandonOwnMessage.java:52:            colony.getPermissions().addPlayer(player.getGameProfile(), colony.getPermissions().getRankOfficer());
minecolonies/src/main/java/com/minecolonies\core\entity\other\SpearEntity.java:114:        Entity ownerEntity = this.getOwner();
minecolonies/src/main/java/com/minecolonies\core\entity\other\CustomArrowEntity.java:102:                Entity shooter = this.getOwner();
minecolonies/src/main/java/com/minecolonies\core\entity\other\DruidPotionEntity.java:76:        final AbstractEntityCitizen citizen = this.getOwner();
minecolonies/src/main/java/com/minecolonies\core\entity\other\DruidPotionEntity.java:102:                                        effect.applyInstantenousEffect(this, this.getOwner(), livingentity, effectinstance.getAmplifier(), d1);
minecolonies/src/main/java/com/minecolonies\core\entity\other\DruidPotionEntity.java:128:    public AbstractEntityCitizen getOwner()
minecolonies/src/main/java/com/minecolonies\core\entity\other\DruidPotionEntity.java:130:        final Entity owner = super.getOwner();
minecolonies/src/main/java/com/minecolonies\core\entity\other\cavalry\CavalryHorseEntity.java:569:        final UUID owner = vanilla.getOwnerUUID();
minecolonies/src/main/java/com/minecolonies\core\commands\generalcommands\CommandGetRanks.java:81:            .filter(colony -> !colony.getPermissions().getRankNeutral().equals(colony.getPermissions().getRank(profile.getId())))
minecolonies/src/main/java/com/minecolonies\core\commands\colonycommands\CommandSetAbandoned.java:40:            colony.getPermissions().addPlayer(((Player) sender).getGameProfile(), colony.getPermissions().getRankOfficer());
minecolonies/src/main/java/com/minecolonies\core\items\ItemPharaoScepter.java:137:        if (arrow.getOwner() == null)
minecolonies/src/main/java/com/minecolonies\core\items\ItemPharaoScepter.java:142:        AbstractArrow entity = ((ArrowItem) ModItems.firearrow).createArrow(arrow.level, new ItemStack(ModItems.firearrow, 1), (LivingEntity) arrow.getOwner());
minecolonies/src/main/java/com/minecolonies\core\commands\colonycommands\CommandListColonies.java:102:                ID_AND_NAME_TEXT, colony.getID(), colony.getName()) + " " + MAYOR_TEXT + colony.getPermissions().getOwnerName())
minecolonies/src/main/java/com/minecolonies\core\entity\citizen\EntityCitizen.java:1315:                citizenColonyHandler.getColonyOrRegister().getPermissions().addPlayer(permission.getOwner(), permission.getOwnerName(), permission.getRank(permission.HOSTILE_RANK_ID));
minecolonies/src/main/java/com/minecolonies\core\commands\colonycommands\CommandColonyPrintStats.java:54:        final String mayor = colony.getPermissions().getOwnerName();
minecolonies/src/main/java/com/minecolonies\core\commands\colonycommands\CommandColonyInfo.java:49:        final String mayor = colony.getPermissions().getOwnerName();
minecolonies/src/main/java/com/minecolonies\core\network\messages\client\colony\ColonyListMessage.java:70:            buf.writeUtf(colony.getPermissions().getOwnerName());
minecolonies/src/main/java/com/minecolonies\core\network\messages\client\colony\ColonyListMessage.java:120:        public String getOwner()
minecolonies/src/main/java/com/minecolonies\core\network\messages\server\AbstractColonyServerMessage.java:132:            else if (ownerOnly() && (player == null || colony.getPermissions().getOwner().equals(player.getUUID())))
minecolonies/src/main/java/com/minecolonies\core\entity\ai\workers\AbstractEntityAIBasic.java:1923:        return FakePlayerFactory.get((ServerLevel) world, new GameProfile(permissions.getOwner(), permissions.getOwnerName()));
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 10 \"public Rank getRank\\(final UUID id\\)|public Rank getRankHostile\\(\\)|public Rank getRankNeutral\\(\\)|public Rank getRankFriend\\(\\)|public Rank getRankOfficer\\(\\)\" minecolonies/src/main/java/com/minecolonies/core/colony/permissions/Permissions.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "onLivingHurt|areColonyMates|isDefenderSideTarget|sendBlockedMessage" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"public Rank getRank\\(final UUID id\\)|public Rank getRankHostile\\(\\)|public Rank getRankNeutral\\(\\)\" minecolonies/src/main/java/com/minecolonies/core/colony/permissions/PermissionsView.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "class BesiegeRaidData|registeredAllies|notified|ally|besiegingPlayerUUID|colonyId" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 290ms:
34-@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
35-public class BesiegeDamageShieldHandler {
36-
37-    // Throttle the chat message so spam-clicking attacks doesn't flood the chat
38-    private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
39-    private static final long BLOCK_MESSAGE_COOLDOWN_MS = 3000;
40-
41-    @SubscribeEvent(priority = EventPriority.HIGH)
42:    public static void onLivingHurt(LivingHurtEvent event) {
43-        if (!TaxConfig.isBesiegeSystemEnabled()) return;
44-        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;
45-
46-        Entity sourceEntity = event.getSource().getEntity();
47-        if (!(sourceEntity instanceof ServerPlayer source)) return;
48-
49-        LivingEntity target = event.getEntity();
50-        UUID sourceUUID = source.getUUID();
--
55-
56-        // Look for any active besiege whose besieger is in the same colony as this source player.
57-        // If found, the source is an attacker's colony-mate and is blocked from helping.
58-        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
59-            if (raid.besiegingPlayerUUID == null) continue;
60-            // Skip if it's their own raid (already handled above)
61-            if (raid.besiegingPlayerUUID.equals(sourceUUID)) continue;
62-
63:            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;
64-
65-            // Confirm the target belongs to the besieged side: a defender citizen of the
66-            // besieged colony OR a player who is on the defender side (any non-besieger
67-            // player in the besieged colony's permission list, including notified allies).
68:            if (!isDefenderSideTarget(target, raid.colonyId)) continue;
69-
70-            event.setCanceled(true);
71-            event.setAmount(0f);
72:            sendBlockedMessage(source);
73-            return;
74-        }
75-    }
76-
77-    /**
78-     * True when both players hold an officer/friend rank in any single shared colony.
79-     * Best-effort â€” relies on MineColonies permissions. A null besieger lookup returns false.
80-     */
81:    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
82-        if (source.level().getServer() == null) return false;
83-        // Walk all colonies the source player has any rank in. If the besieger also has
84-        // any non-neutral rank in the same colony, they are colony-mates.
85-        try {
86-            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
87-                Rank sourceRank = colony.getPermissions().getRank(source.getUUID());
88-                Rank besiegerRank = colony.getPermissions().getRank(besiegerUUID);
89-                if (sourceRank == null || besiegerRank == null) continue;
--
93-                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
94-                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
95-                return true;
96-            }
97-        } catch (Exception ignored) {}
98-        return false;
99-    }
100-
101:    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
102-        // Citizen of the besieged colony
103-        if (target instanceof AbstractEntityCitizen citizen) {
104-            try {
105-                var data = citizen.getCitizenData();
106-                if (data != null && data.getColony() != null
107-                        && data.getColony().getID() == besiegedColonyId) {
108-                    return true;
109-                }
--
121-                if (targetRank == null) return false;
122-                return !targetRank.equals(besieged.getPermissions().getRankHostile())
123-                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
124-            } catch (Exception ignored) {}
125-        }
126-        return false;
127-    }
128-
129:    private static void sendBlockedMessage(ServerPlayer source) {
130-        long now = System.currentTimeMillis();
131-        Long last = LAST_BLOCK_MESSAGE.get(source.getUUID());
132-        if (last != null && now - last < BLOCK_MESSAGE_COOLDOWN_MS) return;
133-        LAST_BLOCK_MESSAGE.put(source.getUUID(), now);
134-        source.sendSystemMessage(Component.literal(
135-                "You cannot interfere in a solo besiege â€” your colony-mate must fight alone.")
136-                .withStyle(ChatFormatting.RED));
137-    }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 296ms:
779-    }
780-
781-    /**
782-     * Returns the rank belonging to the UUID.
783-     *
784-     * @param id UUID that you want to check rank of.
785-     * @return Rank of the UUID.
786-     */
787-    @NotNull
788-    @Override
789:    public Rank getRank(final UUID id)
790-    {
791-        final ColonyPlayer player = players.get(id);
792-        return player != null ? player.getRank() : ranks.get(NEUTRAL_RANK_ID);
793-    }
794-
795-    /**
796-     * Add a player to the rankings.
797-     *
798-     * @param player String playername of the player to add.
799-     * @param rank   Rank desired starting rank.
--
983-    {
984-        return ranks.get(OWNER_RANK_ID);
985-    }
986-
987-    /**
988-     * Get rank instance of officer
989-     *
990-     * @return the rank
991-     */
992-    @Override
993:    public Rank getRankOfficer()
994-    {
995-        return ranks.get(OFFICER_RANK_ID);
996-    }
997-
998-    /**
999-     * Get rank instance of hostile
1000-     *
1001-     * @return the rank
1002-     */
1003-    @Override
1004:    public Rank getRankHostile()
1005-    {
1006-        return ranks.get(HOSTILE_RANK_ID);
1007-    }
1008-
1009-    /**
1010-     * Get rank instance of friend
1011-     *
1012-     * @return the rank
1013-     */
1014-    @Override
1015:    public Rank getRankFriend()
1016-    {
1017-        return ranks.get(FRIEND_RANK_ID);
1018-    }
1019-
1020-    /**
1021-     * Get rank instance of neutral
1022-     *
1023-     * @return the rank
1024-     */
1025-    @Override
1026:    public Rank getRankNeutral()
1027-    {
1028-        return ranks.get(NEUTRAL_RANK_ID);
1029-    }
1030-
1031-    @Override
1032-    public Rank getRank(final int id)
1033-    {
1034-        return ranks.get(id);
1035-    }
1036-
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 303ms:
270-    @Override
271-    public void restoreOwnerIfNull()
272-    {
273-        //Noop happens on the server side.
274-    }
275-
276-    @NotNull
277-    @Override
278:    public Rank getRank(final UUID id)
279-    {
280-        final ColonyPlayer player = players.get(id);
281-        return player == null ? ranks.getOrDefault(NEUTRAL_RANK_ID, MISSINGNO_RANK) : player.getRank();
282-    }
283-
284-    @Override
285-    public boolean hasPermission(@NotNull final Player player, @NotNull final Action action)
286-    {
--
342-
343-    @Override
344-    public Rank getRankFriend()
345-    {
346-        return ranks.get(FRIEND_RANK_ID);
347-    }
348-
349-    @Override
350:    public Rank getRankNeutral()
351-    {
352-        return ranks.get(NEUTRAL_RANK_ID);
353-    }
354-
355-    @Override
356:    public Rank getRankHostile()
357-    {
358-        return ranks.get(HOSTILE_RANK_ID);
359-    }
360-
361-    @Override
362-    public void addRank(String name)
363-    {
364-    }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 281ms:
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-380-        if (TaxConfig.isSuppressColonyLevitation()
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-381-                || (event.getEntity() instanceof ServerPlayer sp && isActiveConflictParticipant(sp.getUUID()))) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-382-            event.setResult(Event.Result.DENY);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-383-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-384-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-385-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-386-    /**
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-387-     * FALLBACK: fires AFTER addEffect() stores the effect (if Applicable was bypassed).
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:388:     * Removes it immediately so the player never actually floats.
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-389-     * Also handles any forceAddEffect() path that skips canBeAffected().
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-390-     */
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-391-    @SubscribeEvent(priority = EventPriority.HIGHEST)
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-392-    public static void onLevitationAdded(MobEffectEvent.Added event) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-393-        if (event.getEffectInstance().getEffect() != MobEffects.LEVITATION) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-394-        if (!(event.getEntity() instanceof Player player)) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-395-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-396-        boolean shouldStrip = TaxConfig.isSuppressColonyLevitation()
--
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-429-                        || (war.getDefenderLives() != null && war.getDefenderLives().containsKey(playerId))) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-430-                    return true;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-431-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-432-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-433-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-434-        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-435-            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-436-                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:437:                if (besiege.besiegingPlayerUUID != null && besiege.besiegingPlayerUUID.equals(playerId)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-438-                    return true;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-439-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-440-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-441-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-442-        return false;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-443-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-444-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-63-     *
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-64-     * Phase 2 follow-up: defender pool is still per-raid, not shared across
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-65-     * concurrent besiegers on the same colony. Each besieger currently spawns its
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-66-     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-67-     * are not yet implemented.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-68-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-69-    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-70-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:71:    /** Persistent occupation records (colonyId -> occupation data). */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-72-    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-73-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-74-    /** Per-player cooldown map (playerUUID -> timestamp when cooldown expires). */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-75-    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-76-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-77-    private static MinecraftServer SERVER;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-78-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-79-    public static void initialize(MinecraftServer server) {
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-94-    public static void tick() {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-95-        if (ACTIVE_RAIDS.isEmpty()) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-96-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-97-        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-98-            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-99-            BesiegeRaidData raid = entry.getValue();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-100-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-101-            try {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:102:                IColony colony = getColonyById(raid.colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-103-                if (colony == null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:104:                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-105-                    cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-106-                    it.remove();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-107-                    continue;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-108-                }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-109-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:110:                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-111-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-112-                // --- Timer expired: defenders win ---
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-113-                if (System.currentTimeMillis() >= raid.endTime) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-114-                    if (TaxConfig.isNormalLogging())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-115-                        LOGGER.info("Besiege raid on colony {} timed out тАФ defenders win", colony.getName());
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:116:                    sendToPlayer(raid.besiegingPlayerUUID,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-117-                            Component.literal("The besiege of " + colony.getName() + " has failed тАФ the defenders held out!")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-118-                                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-119-                    broadcastToNearbyPlayers(colony,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-120-                            Component.literal(colony.getName() + " successfully repelled the besiege!")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-121-                                    .withStyle(ChatFormatting.GREEN), 200);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-122-                    cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:123:                    applyCooldown(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-124-                    it.remove();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-125-                    continue;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-126-                }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-127-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-128-                // --- Besieger left the area ---
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-129-                if (besieger != null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-130-                    BlockPos center = colony.getCenter();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-131-                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-132-                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-133-                    if (dist > (double) maxRadius * maxRadius) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-134-                        besieger.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-135-                                "You left the besiege area тАФ the raid has been cancelled!")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-136-                                .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-137-                        cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:138:                        applyCooldown(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-139-                        it.remove();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-140-                        continue;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-141-                    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-142-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-143-                    // Track allies: anyone who recently damaged a defender
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:144:                    // (ally tracking is done in the kill/hurt event тАФ see RaidKillTracker integration)
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-145-                }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-146-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-147-                // --- Victory: all defenders dead ---
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-148-                if (allDefendersDead(raid, colony)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-149-                    if (TaxConfig.isNormalLogging())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-150-                        LOGGER.info("Besiege raid on colony {} successful тАФ besieger wins", colony.getName());
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-151-                    completeBesiege(raid, true, colony);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-152-                    it.remove();
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-170-    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-171-        if (!TaxConfig.isBesiegeSystemEnabled()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-172-            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-173-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-174-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-175-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-176-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-177-        UUID besiegerUUID = besieger.getUUID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:178:        int colonyId = colony.getID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-179-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-180-        // 1. Must own at least one colony themselves
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-181-        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-182-        if (besiegerColony == null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-183-            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-184-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-185-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-186-        }
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-223-            besieger.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-224-                    "Target colony is too small to besiege (needs at least "
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-225-                            + TaxConfig.getBesiegeMinColonySize() + " citizens).")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-226-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-227-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-228-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-229-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-230-        // 7. Cannot besiege a colony that is currently in an active war
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:231:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-232-            besieger.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-233-                    "This colony is already engaged in an active war. Besiege is not available during wartime.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-234-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-235-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-236-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-237-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-238-        // 8. Cannot besiege a colony that is already a vassal of this player
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:239:        if (VassalManager.isColonyVassal(colonyId)
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:240:                && VassalManager.getVassalOverlordUUID(colonyId) != null
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:241:                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-242-            besieger.sendSystemMessage(Component.literal("This colony is already your vassal.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-243-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-244-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-245-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-246-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-247-        return launchRaid(colony, besieger, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-248-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-249-
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-252-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-253-    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-254-        if (!TaxConfig.isBesiegeSystemEnabled()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-255-            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-256-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-257-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-258-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-259-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:260:        int colonyId = colony.getID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:261:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-262-        if (occ == null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-263-            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-264-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-265-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-266-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-267-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-268-        UUID playerUUID = reclaimingPlayer.getUUID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-269-        if (!occ.formerOwnerUUID.equals(playerUUID)) {
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-294-        reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-295-                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-296-                .withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-297-        return launchRaid(colony, reclaimingPlayer, true);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-298-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-299-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-300-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-301-    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:302:        int colonyId = colony.getID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-303-        UUID besiegerUUID = besieger.getUUID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-304-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-305-        try {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:306:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-307-            ACTIVE_RAIDS.put(besiegerUUID, raid);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-308-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-309-            // Grant the besieger hostile rank + combat permissions on the colony
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-310-            // so MineColonies allows the player to attack citizens.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-311-            grantBesiegeCombatPermissions(colony, besiegerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-312-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-313-            // Convert guards to hostile
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-314-            int guardCount = makeGuardsHostile(colony, besieger, raid);
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-336-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-337-            broadcastToNearbyPlayers(colony,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-338-                    Component.literal("Nearby colony " + colony.getName()
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-339-                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-340-                            + " by " + besieger.getName().getString() + "!")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-341-                            .withStyle(ChatFormatting.YELLOW), 200);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-342-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-343-            // Notify owner + officers + friends тАФ the defender's call-to-arms.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:344:            // Friends are included per the Siege SMP defender-ally rule: defenders may
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-345-            // mobilize allies even when the attacker must stand alone.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-346-            BlockPos colonyCenter = colony.getCenter();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-347-            net.minecraft.network.chat.Component callToArms = Component.literal("WARNING: ")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-348-                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-349-                    .append(Component.literal(besieger.getName().getString())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-350-                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-351-                    .append(Component.literal(" is besieging your colony ")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-352-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-361-                                            "/tp " + colonyCenter.getX() + " " + colonyCenter.getY() + " " + colonyCenter.getZ()))
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-362-                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-363-                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-364-                                            Component.literal("Teleport near the colony center to join the defense")))));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-365-            notifyColonyDefenders(colony, callToArms);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-366-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-367-            if (TaxConfig.isNormalLogging())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-368-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:369:                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-370-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-371-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-372-            return true;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-373-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-374-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-375-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-376-            ACTIVE_RAIDS.remove(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-377-            return false;
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-538-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-539-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-540-        return spawned;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-541-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-542-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-543-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-544-    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-545-        cleanupRaid(raid, true);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:546:        applyCooldown(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-547-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-548-        if (attackerWon) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-549-            if (raid.isReclaim) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-550-                completeReclaim(raid, colony);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-551-            } else {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-552-                completeBesiegeVictory(raid, colony);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-553-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-554-        } else {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:555:            sendToPlayer(raid.besiegingPlayerUUID,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-556-                    Component.literal("The besiege of " + colony.getName() + " failed.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-557-                            .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-558-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-559-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-560-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-561-    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-562-        int tributePct = TaxConfig.getBesiegeTributePercent();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-563-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-564-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:565:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-566-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-567-        if (vassalized) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-568-            // Store occupation record
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-569-            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-570-            BesiegeOccupationData occ = new BesiegeOccupationData(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-571-                    colony.getID(), colony.getName(),
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:572:                    raid.besiegingPlayerUUID, ownerUUID,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-573-                    System.currentTimeMillis(), tributePct);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-574-            OCCUPATIONS.put(colony.getID(), occ);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-575-            saveData();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-576-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-577-            // Notify former owner
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-578-            if (ownerUUID != null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:579:                String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-580-                sendToPlayer(ownerUUID, Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-581-                        "Your colony " + colony.getName() + " has been besieged by " + besiegerName
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-582-                                + "! Tax tribute (" + tributePct + "%) now flows to them. "
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-583-                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-584-                        .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-585-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-586-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:587:            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-588-                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-589-                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-590-                    .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-591-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-592-            broadcastToNearbyPlayers(colony,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-593-                    Component.literal(colony.getName() + " has fallen under besiege occupation!")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-594-                            .withStyle(ChatFormatting.DARK_RED), 300);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-595-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-596-            if (TaxConfig.isNormalLogging())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-597-                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:598:                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-599-        } else {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-600-            // Colony was already a vassal (edge case) тАФ just notify
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:601:            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-602-                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-603-                    .withStyle(ChatFormatting.YELLOW));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-604-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-605-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-606-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-607-    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:608:        int colonyId = colony.getID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-609-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-610-        // Remove vassalization
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:611:        VassalManager.removeVassalRelation(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-612-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-613-        // Remove occupation record
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:614:        OCCUPATIONS.remove(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-615-        saveData();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-616-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:617:        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-618-                "Reclaim successful! " + colony.getName() + " is free from occupation.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-619-                .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-620-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-621-        broadcastToNearbyPlayers(colony,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-622-                Component.literal(colony.getName() + " has been reclaimed by its owner!")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-623-                        .withStyle(ChatFormatting.GOLD), 300);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-624-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-625-        if (TaxConfig.isNormalLogging())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-626-            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:627:                    getPlayerName(raid.besiegingPlayerUUID));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-628-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-629-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-630-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-631-    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:632:        IColony colony = getColonyById(raid.colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-633-        if (colony != null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-634-            // Revoke combat permissions from the besieger (and any allies)
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:635:            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:636:            for (UUID ally : raid.alliedPlayers) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:637:                revokeBesiegeCombatPermissions(colony, ally);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-638-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-639-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-640-            // Restore citizen AI
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-641-            for (int citizenId : raid.hostileCitizenIds) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-642-                try {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-643-                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-644-                    if (citizen != null && citizen.getEntity().isPresent()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-645-                        AbstractEntityCitizen entity = citizen.getEntity().get();
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-679-                raid.bossEvent.removeAllPlayers();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-680-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-681-                LOGGER.warn("Failed to remove besiege boss bar", e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-682-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-683-            raid.bossEvent = null;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-684-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-685-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-686-        if (removeFromMap) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:687:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-688-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-689-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-690-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-691-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-692-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-693-        try {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-694-            int minutes = TaxConfig.getBesiegeDurationMinutes();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-695-            Component text = Component.literal(
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-743-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-744-                            colony.getName(), aliveDefenders, mm, ss)));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-745-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-746-            LOGGER.warn("Failed to update besiege boss bar", e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-747-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-748-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-749-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-750-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:751:    public static boolean isColonyBesieged(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:752:        return OCCUPATIONS.containsKey(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-753-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-754-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:755:    public static boolean isActiveRaidOnColony(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-756-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:757:            if (raid.colonyId == colonyId) return true;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-758-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-759-        return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-760-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-761-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-762-    /** All currently active besiege raids targeting this colony. */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:763:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-764-        List<BesiegeRaidData> matches = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-765-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:766:            if (raid.colonyId == colonyId) matches.add(raid);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-767-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-768-        return matches;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-769-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-770-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-771-    /**
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-772-     * Returns true if the player is locked out of the colony due to besiege occupation.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-773-     * The former owner is locked out; the besieging player is the new effective controller.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-774-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:775:    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:776:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-777-        if (occ == null) return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-778-        // Former owner is locked out
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-779-        return occ.formerOwnerUUID != null && occ.formerOwnerUUID.equals(playerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-780-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-781-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:782:    public static boolean isBesiegingPlayer(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:783:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:784:        return occ != null && occ.besiegingPlayerUUID.equals(playerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-785-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-786-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-787-    /**
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-788-     * Called from RaidKillTracker to register an allied player to ALL raids
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-789-     * targeting this colony. With multi-besieger, several besiegers may target
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:790:     * the same colony тАФ registering the ally on each gives them combat rights
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-791-     * regardless of which besieger they're supporting.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-792-     *
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-793-     * Note: the new Siege SMP solo-besiege rule (step 5) blocks attacker-side
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:794:     * allies via a damage shield. This method remains for any defender-ally
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-795-     * tracking and for legacy callers; the besiege-allies config still gates it.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-796-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:797:    public static void registerAlly(int colonyId, UUID allyUUID) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-798-        if (!TaxConfig.isBesiegeAlliesEnabled()) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:799:        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-800-        if (raids.isEmpty()) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:801:        IColony colony = getColonyById(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-802-        for (BesiegeRaidData raid : raids) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:803:            if (raid.alliedPlayers.add(allyUUID) && colony != null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:804:                grantBesiegeCombatPermissions(colony, allyUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-805-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-806-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-807-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-808-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-809-    /** Check whether a player is on cooldown. */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-810-    public static boolean isOnCooldown(UUID playerUUID) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-811-        Long expiry = PLAYER_COOLDOWNS.get(playerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-812-        return expiry != null && System.currentTimeMillis() < expiry;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-813-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-814-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:815:    public static BesiegeOccupationData getOccupation(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:816:        return OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-817-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-818-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-819-    /**
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:820:     * Backward-compatible view of active raids keyed by colonyId.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-821-     *
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-822-     * Since multi-besieger support landed, the internal storage is keyed by
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-823-     * besieger UUID. This view returns at most ONE raid per colony (the first
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-824-     * one encountered). Callers that need ALL raids for a colony must use
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-825-     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-826-     * raid should use {@link #getRaidForBesieger(UUID)}.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-827-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-828-    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-829-        Map<Integer, BesiegeRaidData> view = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-830-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:831:            view.putIfAbsent(raid.colonyId, raid);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-832-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-833-        return Collections.unmodifiableMap(view);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-834-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-835-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-836-    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-837-    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-838-        return ACTIVE_RAIDS.get(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-839-    }
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-934-    /**
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-935-     * Defender call-to-arms: notifies owner, officers, AND friends.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-936-     * Per the Siege SMP rule, defender allies may answer the besiege; attacker
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-937-     * allies are blocked by the solo-besiege damage shield (step 5).
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-938-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-939-    private static void notifyColonyDefenders(IColony colony, Component message) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-940-        if (SERVER == null) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-941-        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:942:        java.util.Set<UUID> notified = new java.util.HashSet<>();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-943-        UUID owner = perms.getOwner();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:944:        if (owner != null && notified.add(owner)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-945-            sendToPlayer(owner, message);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-946-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-947-        try {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-948-            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-949-                    : perms.getPlayersByRank(perms.getRankOfficer())) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:950:                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-951-                    sendToPlayer(cp.getID(), message);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-952-                }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-953-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-954-        } catch (Exception ignored) {}
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-955-        try {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-956-            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-957-                    : perms.getPlayersByRank(perms.getRankFriend())) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:958:                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-959-                    sendToPlayer(cp.getID(), message);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-960-                }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-961-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-962-        } catch (Exception ignored) {}
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-963-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-964-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-965-    private static String getPlayerName(UUID uuid) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-966-        if (SERVER == null) return uuid.toString();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-967-        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-968-        return p != null ? p.getName().getString() : uuid.toString();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-969-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-970-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:971:    private static IColony getColonyById(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-972-        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:973:                .filter(c -> c.getID() == colonyId)
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-974-                .findFirst()
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-975-                .orElse(null);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-976-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-977-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-978-    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-979-        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-980-        // Prefer FCT: it tracks the true first colony regardless of permissions state
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-981-        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1058-    private static void loadData(MinecraftServer server) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1059-        File f = new File(server.getServerDirectory(), STORAGE_FILE);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1060-        if (!f.exists()) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1061-        try (FileReader r = new FileReader(f)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1062-            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1063-            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1064-            if (list != null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1065-                for (BesiegeOccupationData occ : list) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1066:                    OCCUPATIONS.put(occ.colonyId, occ);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1067-                }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1068-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1069-            if (TaxConfig.isNormalLogging())
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1070-                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1071-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1072-            LOGGER.error("Failed to load besiege occupation data", e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1073-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1074-    }
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1088-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1089-                LOGGER.error("Failed to save besiege occupation data", e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1090-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1091-        });
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1092-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1093-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1094-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1095-    /** Transient raid state тАФ not persisted. */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1096:    public static class BesiegeRaidData {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1097:        public final int colonyId;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1098:        public final UUID besiegingPlayerUUID;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1099-        public final long startTime;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1100-        public final long endTime;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1101-        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1102-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1103-        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1104-        public final BlockPos colonyCenter;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1105-        public ServerBossEvent bossEvent;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1106-        public final boolean isReclaim;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1107-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1108:        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1109:            this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1110:            this.besiegingPlayerUUID = besiegingPlayerUUID;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1111-            this.colonyCenter = colonyCenter;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1112-            this.isReclaim = isReclaim;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1113-            this.startTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1114-            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1115-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1116-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1117-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1118-    /** Persisted occupation record тАФ survives server restart. */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1119-    public static class BesiegeOccupationData {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1120:        public int colonyId;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1121-        public String colonyName;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1122:        public UUID besiegingPlayerUUID;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1123-        public UUID formerOwnerUUID;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1124-        public long besiegeTime;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1125-        public int tributePercent;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1126-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1127-        // For Gson deserialization
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1128-        public BesiegeOccupationData() {}
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1129-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1130:        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1131-                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1132:            this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1133-            this.colonyName = colonyName;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:1134:            this.besiegingPlayerUUID = besiegingPlayerUUID;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1135-            this.formerOwnerUUID = formerOwnerUUID;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1136-            this.besiegeTime = besiegeTime;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1137-            this.tributePercent = tributePercent;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1138-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1139-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-1140-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fБr 
Kerntypen unterstБtzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 10 \"getSource\\(\\)\\.getEntity\\(|getSource\\(\\)\\.getDirectEntity\\(|LivingHurtEvent\" src/main/java minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java -S" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"getRank\\(.*getRankNeutral|isHostile\\(|getRankHostile\\(\\).*equals|equals\\(.*getRankHostile|getRankNeutral\\(\\).*equals\" src/main/java/net/machiavelli/minecolonytax -S" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 12 \"registerAlly\\(|grantBesiegeCombatPermissions|restoreBesiegeCombatPermissions|alliedPlayers|hostileCitizenIds\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 284ms:
299-
300-
301-    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
302-        int colonyId = colony.getID();
303-        UUID besiegerUUID = besieger.getUUID();
304-
305-        try {
306-            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
307-            ACTIVE_RAIDS.put(besiegerUUID, raid);
308-
309-            // Grant the besieger hostile rank + combat permissions on the colony
310-            // so MineColonies allows the player to attack citizens.
311:            grantBesiegeCombatPermissions(colony, besiegerUUID);
312-
313-            // Convert guards to hostile
314-            int guardCount = makeGuardsHostile(colony, besieger, raid);
315-
316-            // Convert militia (non-guard eligible citizens)
317-            int militiaCount = convertCitizensToMilitia(colony, besieger, raid);
318-
319-            // Spawn mercenaries
320-            int mercCount = spawnMercenaries(colony, besieger, raid);
321-
322-            int totalDefenders = guardCount + militiaCount + mercCount;
323-
--
381-
382-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
383-        int count = 0;
384-        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
385-            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
386-            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
387-            if (entityOpt.isEmpty()) continue;
388-            AbstractEntityCitizen guard = entityOpt.get();
389-
390-            try {
391-                applyDefenderAI(guard, besieger, raid);
392-                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
393:                raid.hostileCitizenIds.add(citizenData.getId());
394-                count++;
395-            } catch (Exception e) {
396-                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
397-            }
398-        }
399-        return count;
400-    }
401-
402-    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
403-        List<ICitizenData> eligible = new ArrayList<>();
404-        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
405-            if (citizen.isChild()) continue;
--
418-
419-        for (ICitizenData citizen : eligible) {
420-            if (count >= target) break;
421-            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
422-            if (entityOpt.isEmpty()) continue;
423-            AbstractEntityCitizen entity = entityOpt.get();
424-
425-            try {
426-                // Equip wooden sword
427-                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
428-                applyDefenderAI(entity, besieger, raid);
429-                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
430:                raid.hostileCitizenIds.add(citizen.getId());
431-                count++;
432-            } catch (Exception e) {
433-                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
434-            }
435-        }
436-        return count;
437-    }
438-
439-    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
440-        entity.goalSelector.removeAllGoals(g -> true);
441-        entity.targetSelector.removeAllGoals(g -> true);
442-
--
444-        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
445-        // crash with IllegalArgumentException.
446-        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
447-
448-        // Retaliate against anyone who hits them (covers allies)
449-        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
450-
451-        // Proactively hunt the besieger (and any allies)
452-        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
453-                20, true, false, (target) -> {
454-                    if (!(target instanceof ServerPlayer sp)) return false;
455-                    return sp.getUUID().equals(besieger.getUUID())
456:                            || raid.alliedPlayers.contains(sp.getUUID());
457-                }));
458-    }
459-
460-    private static void applyDefenderEffects(AbstractEntityCitizen entity, int durationMinutes) {
461-        int ticks = durationMinutes * 60 * 20;
462-        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
463-        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
464-        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
465-    }
466-
467-    /**
468-     * Applies bonus DAMAGE_RESISTANCE to all besiege defenders based on the
--
471-     * Each 20% damage reduction from the investment adds +1 resistance amplifier.
472-     */
473-    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
474-        if (!TaxConfig.isUpgradesEnabled()) return;
475-        double dmgReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
476-                .getFortificationDamageReduction(colony.getID());
477-        if (dmgReduction <= 0) return;
478-        int extraAmplifier = (int) (dmgReduction / 0.20);
479-        if (extraAmplifier <= 0) return;
480-
481-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
482-        // Re-apply DAMAGE_RESISTANCE to citizens with the boosted amplifier
483:        for (int citizenId : raid.hostileCitizenIds) {
484-            colony.getCitizenManager().getCitizens().stream()
485-                    .filter(c -> c.getId() == citizenId)
486-                    .findFirst()
487-                    .flatMap(ICitizenData::getEntity)
488-                    .ifPresent(entity -> entity.addEffect(
489-                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
490-        }
491-        // Also boost mercenaries
492-        for (Entity merc : raid.spawnedMercenaries) {
493-            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
494-                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
495-            }
--
624-
625-        if (TaxConfig.isNormalLogging())
626-            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
627-                    getPlayerName(raid.besiegingPlayerUUID));
628-    }
629-
630-
631-    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
632-        IColony colony = getColonyById(raid.colonyId);
633-        if (colony != null) {
634-            // Revoke combat permissions from the besieger (and any allies)
635-            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
636:            for (UUID ally : raid.alliedPlayers) {
637-                revokeBesiegeCombatPermissions(colony, ally);
638-            }
639-
640-            // Restore citizen AI
641:            for (int citizenId : raid.hostileCitizenIds) {
642-                try {
643-                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
644-                    if (citizen != null && citizen.getEntity().isPresent()) {
645-                        AbstractEntityCitizen entity = citizen.getEntity().get();
646-                        entity.goalSelector.removeAllGoals(g -> true);
647-                        entity.targetSelector.removeAllGoals(g -> true);
648-                        // Remove militia sword if present
649-                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
650-                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
651-                        }
652-                        // Remove combat effects
653-                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
--
785-    }
786-
787-    /**
788-     * Called from RaidKillTracker to register an allied player to ALL raids
789-     * targeting this colony. With multi-besieger, several besiegers may target
790-     * the same colony â€” registering the ally on each gives them combat rights
791-     * regardless of which besieger they're supporting.
792-     *
793-     * Note: the new Siege SMP solo-besiege rule (step 5) blocks attacker-side
794-     * allies via a damage shield. This method remains for any defender-ally
795-     * tracking and for legacy callers; the besiege-allies config still gates it.
796-     */
797:    public static void registerAlly(int colonyId, UUID allyUUID) {
798-        if (!TaxConfig.isBesiegeAlliesEnabled()) return;
799-        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
800-        if (raids.isEmpty()) return;
801-        IColony colony = getColonyById(colonyId);
802-        for (BesiegeRaidData raid : raids) {
803:            if (raid.alliedPlayers.add(allyUUID) && colony != null) {
804:                grantBesiegeCombatPermissions(colony, allyUUID);
805-            }
806-        }
807-    }
808-
809-    /** Check whether a player is on cooldown. */
810-    public static boolean isOnCooldown(UUID playerUUID) {
811-        Long expiry = PLAYER_COOLDOWNS.get(playerUUID);
812-        return expiry != null && System.currentTimeMillis() < expiry;
813-    }
814-
815-    public static BesiegeOccupationData getOccupation(int colonyId) {
816-        return OCCUPATIONS.get(colonyId);
--
846-    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
847-        return Collections.unmodifiableMap(OCCUPATIONS);
848-    }
849-
850-
851-    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
852-        return countAliveDefenders(raid, colony) == 0;
853-    }
854-
855-    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
856-        int alive = 0;
857-        // Citizens
858:        for (int citizenId : raid.hostileCitizenIds) {
859-            try {
860-                ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
861-                if (citizen != null && citizen.getEntity().isPresent()
862-                        && citizen.getEntity().get().isAlive()) {
863-                    alive++;
864-                }
865-            } catch (Exception ignored) {}
866-        }
867-        // Mercenaries
868-        for (Entity merc : raid.spawnedMercenaries) {
869-            if (merc.isAlive()) alive++;
870-        }
--
990-            UUID owner = c.getPermissions().getOwner();
991-            if (owner != null && owner.equals(playerId)) return c;
992-        }
993-        return null;
994-    }
995-
996-
997-    /**
998-     * Grants the besieging player hostile rank and combat permissions on the target colony.
999-     * Without this, MineColonies blocks all player attacks on citizens with
1000-     * "You do not have permission to do this in this colony!".
1001-     */
1002:    private static void grantBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
1003-        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
1004-        try {
1005-            IPermissions perms = colony.getPermissions();
1006-            // Snapshot before modifying (for restore on cleanup)
1007-            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
1008-
1009-            // Assign the player to Hostile rank so guards treat them as enemy
1010-            Rank hostile = perms.getRankHostile();
1011-            perms.setPlayerRank(playerUUID, hostile, colony.getWorld());
1012-
1013-            // Enable combat actions on the hostile rank
1014-            for (Action a : TaxConfig.getWarActions()) {
--
1089-                LOGGER.error("Failed to save besiege occupation data", e);
1090-            }
1091-        });
1092-    }
1093-
1094-
1095-    /** Transient raid state â€” not persisted. */
1096-    public static class BesiegeRaidData {
1097-        public final int colonyId;
1098-        public final UUID besiegingPlayerUUID;
1099-        public final long startTime;
1100-        public final long endTime;
1101:        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
1102-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
1103:        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
1104-        public final BlockPos colonyCenter;
1105-        public ServerBossEvent bossEvent;
1106-        public final boolean isReclaim;
1107-
1108-        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
1109-            this.colonyId = colonyId;
1110-            this.besiegingPlayerUUID = besiegingPlayerUUID;
1111-            this.colonyCenter = colonyCenter;
1112-            this.isReclaim = isReclaim;
1113-            this.startTime = System.currentTimeMillis();
1114-            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
1115-        }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 308ms:
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-37-import net.minecraft.world.level.Level;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-38-import net.minecraft.world.level.LevelAccessor;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-39-import net.minecraft.world.level.block.AirBlock;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-40-import net.minecraft.world.level.block.BaseEntityBlock;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-41-import net.minecraft.world.level.block.Block;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-42-import net.minecraft.world.level.block.state.BlockState;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-43-import net.minecraft.world.phys.BlockHitResult;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-44-import net.minecraft.world.phys.EntityHitResult;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-45-import net.minecraftforge.common.util.FakePlayer;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-46-import net.minecraftforge.event.entity.item.ItemTossEvent;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java:47:import net.minecraftforge.event.entity.living.LivingHurtEvent;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-48-import net.minecraftforge.event.entity.player.*;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-49-import net.minecraftforge.event.level.BlockEvent;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-50-import net.minecraftforge.event.level.ExplosionEvent;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-51-import net.minecraftforge.eventbus.api.Event;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-52-import net.minecraftforge.eventbus.api.SubscribeEvent;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-53-import org.jetbrains.annotations.NotNull;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-54-import org.jetbrains.annotations.Nullable;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-55-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-56-import java.util.HashMap;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-57-import java.util.List;
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-558-     *
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-559-     * @param event ItemEntityPickupEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-560-     */
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-561-    @SubscribeEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-562-    public void on(final ArrowLooseEvent event)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-563-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-564-        checkEventCancelation(Action.SHOOT_ARROW, event.getEntity(), event.getEntity().getCommandSenderWorld(), event, event.getEntity().blockPosition());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-565-    }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-566-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-567-    /**
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java:568:     * LivingHurtEvent handler.
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-569-     * <p>
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-570-     * Check if the entity that is getting hurt is a player,
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-571-     * players that get hurt by other players are handled elsewhere,
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-572-     * this here is handling players getting hurt by citizens.
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-573-     * @param event
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-574-     */
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-575-    @SubscribeEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java:576:    public void on(final LivingHurtEvent event)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-577-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-578-        if (event.getEntity() instanceof ServerPlayer
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java:579:              && event.getSource().getEntity() instanceof EntityCitizen
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java:580:              && ((EntityCitizen) event.getSource().getEntity()).getCitizenColonyHandler().getColonyId() == colony.getID()
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-581-              && colony.getRaiderManager().isRaided()
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-582-              && !colony.getPermissions().getRank((Player) event.getEntity()).isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-583-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-584-            event.setCanceled(true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-585-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-586-    }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-587-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-588-    /**
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-589-     * AttackEntityEvent handler.
minecolonies/src/main/java/com/minecolonies/core/colony/permissions/ColonyPermissionEventHandler.java-590-     * <p>
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-3-import com.minecolonies.api.colony.IColony;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-4-import com.minecolonies.api.colony.permissions.Rank;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-5-import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-6-import net.machiavelli.minecolonytax.MineColonyTax;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-7-import net.machiavelli.minecolonytax.TaxConfig;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-8-import net.minecraft.ChatFormatting;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-9-import net.minecraft.network.chat.Component;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-10-import net.minecraft.server.level.ServerPlayer;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-11-import net.minecraft.world.entity.Entity;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-12-import net.minecraft.world.entity.LivingEntity;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:13:import net.minecraftforge.event.entity.living.LivingHurtEvent;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-14-import net.minecraftforge.eventbus.api.EventPriority;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-15-import net.minecraftforge.eventbus.api.SubscribeEvent;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-16-import net.minecraftforge.fml.common.Mod;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-17-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-18-import java.util.HashMap;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-19-import java.util.Map;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-20-import java.util.UUID;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-21-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-22-/**
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-23- * Enforces the Siege SMP "solo besiege" rule on the attacker side only:
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-32- * defender damage to the attacker.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-33- */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-34-@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-35-public class BesiegeDamageShieldHandler {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-36-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-37-    // Throttle the chat message so spam-clicking attacks doesn't flood the chat
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-38-    private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-39-    private static final long BLOCK_MESSAGE_COOLDOWN_MS = 3000;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-40-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-41-    @SubscribeEvent(priority = EventPriority.HIGH)
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:42:    public static void onLivingHurt(LivingHurtEvent event) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-43-        if (!TaxConfig.isBesiegeSystemEnabled()) return;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-44-        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-45-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:46:        Entity sourceEntity = event.getSource().getEntity();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-47-        if (!(sourceEntity instanceof ServerPlayer source)) return;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-48-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-49-        LivingEntity target = event.getEntity();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-50-        UUID sourceUUID = source.getUUID();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-51-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-52-        // If the source player is themselves an active besieger, they can do whatever damage they want.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-53-        BesiegeManager.BesiegeRaidData sourceOwnRaid = BesiegeManager.getRaidForBesieger(sourceUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-54-        if (sourceOwnRaid != null) return;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-55-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java-56-        // Look for any active besiege whose besieger is in the same colony as this source player.
--
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-231-        if (battle == null) {
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-232-            return;
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-233-        }
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-234-
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-235-        // Check for friendly fire in team battles
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-236-        if (TaxConfig.PVP_DISABLE_FRIENDLY_FIRE.get()) {
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-237-            // Get the battle ID and check if it's a team battle in the PvPManager
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-238-            String battleId = battle.getBattleId();
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-239-            TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-240-
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:241:            if (teamBattle != null && event.getSource().getEntity() instanceof ServerPlayer attacker) {
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-242-                // Check if both players are in the battle and on the same team
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-243-                if (teamBattle.arePlayersOnSameTeam(player.getUUID(), attacker.getUUID())) {
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-244-                    // Cancel friendly fire damage
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-245-                    event.setCanceled(true);
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-246-
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-247-                    // Notify the attacker once every 2 seconds to prevent spam
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-248-                    long currentTime = System.currentTimeMillis();
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-249-                    UUID attackerUUID = attacker.getUUID();
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-250-                    Long lastNotifyTime = pvpManager.lastFriendlyFireNotifications.getOrDefault(attackerUUID, 0L);
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java-251-
--
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-52-    public static Map<UUID, Integer> getDisconnectedWarParticipants() {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-53-        return disconnectedWarParticipants;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-54-    }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-55-    
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-56-    @SubscribeEvent(priority = EventPriority.HIGHEST)
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-57-    public static void onPlayerDeath(LivingDeathEvent event) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-58-        if (!(event.getEntity() instanceof ServerPlayer player)) return;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-59-        
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-60-        ActiveRaidData raidData = RaidManager.getActiveRaidForPlayer(player.getUUID());
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-61-        if (raidData != null) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:62:            Entity src = event.getSource().getEntity();
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-63-            if (src instanceof ServerPlayer killer) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-64-                RaidManager.handleRaiderKilled(raidData, killer);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-65-            } else {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-66-                RaidManager.endActiveRaid(raidData, "Raider killed");
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-67-            }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-68-            return;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-69-        }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-70-        
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-71-        if (TaxConfig.isDebugLogging()) LOGGER.debug("WarEventHandler.onPlayerDeath fired for {}", player.getName().getString());
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-72-        WarData war = WarSystem.getActiveWarForPlayer(player);
--
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-85-
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-86-            ItemStack[] inventoryCopy = new ItemStack[player.getInventory().getContainerSize()];
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-87-            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-88-                inventoryCopy[i] = player.getInventory().getItem(i).copy();
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-89-            }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-90-            savedInventories.put(player.getUUID(), inventoryCopy);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-91-            war.getLastLifeInventoryPreservation().add(player.getUUID());
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-92-            event.getEntity().getTags().add("war_keep_inventory");
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-93-        }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-94-
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:95:        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-96-            WarSystem.onPlayerKilledInWar(killer, player, war);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-97-
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-98-            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-99-                if (entry.getKey().equals(player.getUUID())) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-100-                    if (TaxConfig.isDebugLogging()) LOGGER.debug("Raider {} killed during raid", player.getName().getString());
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-101-                    RaidManager.handleRaiderKilled(entry.getValue(), killer);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-102-                    break;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-103-                }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-104-            }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-105-        }
--
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-37- *
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-38- * /wnt treasury withdraw <amount> [tax|wallet|inventory] [colonyId]
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-39- *   Withdraws funds from the treasury to the specified destination:
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-40- *     tax       â€” colony's accumulated tax balance (default)
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-41- *     wallet    â€” player's SDMShop / SDMEconomy balance
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-42- *     inventory â€” gives physical currency items to the player's inventory
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-43- */
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-44-public class TreasuryCommand {
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-45-
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-46-    private static final SuggestionProvider<CommandSourceStack> COLONY_SUGGESTIONS = (ctx, builder) -> {
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java:47:        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-48-            return builder.buildFuture();
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-49-        }
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-50-        List<String> suggestions = new ArrayList<>();
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-51-        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-52-        for (Level world : player.getServer().getAllLevels()) {
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-53-            for (IColony colony : colonyManager.getColonies(world)) {
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-54-                if (colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-55-                    suggestions.add(String.valueOf(colony.getID()));
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-56-                }
src/main/java\net\machiavelli\minecolonytax\commands\TreasuryCommand.java-57-            }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 304ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-85-        try {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-86-            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-87-                Rank sourceRank = colony.getPermissions().getRank(source.getUUID());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-88-                Rank besiegerRank = colony.getPermissions().getRank(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-89-                if (sourceRank == null || besiegerRank == null) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-90-                // Either party being neutral on this colony means they're not "mates" via this colony
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-91-                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-92-                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:93:                if (sourceRank.equals(colony.getPermissions().getRankHostile())) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:94:                if (besiegerRank.equals(colony.getPermissions().getRankHostile())) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-95-                return true;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-96-            }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-97-        } catch (Exception ignored) {}
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-98-        return false;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-99-    }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-100-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-101-    private static boolean isDefenderSideTarget(LivingEntity target, int besiegedColonyId) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-102-        // Citizen of the besieged colony
--
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-114-        if (target instanceof ServerPlayer player) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-115-            try {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-116-                IColony besieged = com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().stream()
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-117-                        .filter(c -> c.getID() == besiegedColonyId)
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-118-                        .findFirst().orElse(null);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-119-                if (besieged == null) return false;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-120-                Rank targetRank = besieged.getPermissions().getRank(player.getUUID());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-121-                if (targetRank == null) return false;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:122:                return !targetRank.equals(besieged.getPermissions().getRankHostile())
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-123-                        && !targetRank.equals(besieged.getPermissions().getRankNeutral());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-124-            } catch (Exception ignored) {}
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-125-        }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-126-        return false;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-127-    }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-128-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-129-    private static void sendBlockedMessage(ServerPlayer source) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java-130-        long now = System.currentTimeMillis();
--
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1313-            perms.setPermission(hostile, action, allowed);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1314-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1315-        
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1316-        // Also update neutral rank attack permissions during raids
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1317-        if (allowed) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1318-            // Grant attack permissions during raid
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1319-            perms.setPermission(neutral, Action.HURT_CITIZEN, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1320-            perms.setPermission(neutral, Action.ATTACK_CITIZEN, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1321:            // GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1322-            perms.setPermission(neutral, Action.HURT_VISITOR, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1323-            perms.setPermission(neutral, Action.ATTACK_ENTITY, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1324-            perms.setPermission(neutral, Action.SHOOT_ARROW, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1325-            perms.setPermission(neutral, Action.THROW_POTION, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1326-            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1327-            perms.setPermission(neutral, Action.FILL_BUCKET, true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1328-        } else {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1329-            // Revoke attack permissions after raid
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1330-            perms.setPermission(neutral, Action.HURT_CITIZEN, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1331-            perms.setPermission(neutral, Action.ATTACK_CITIZEN, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1332:            // GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1333-            perms.setPermission(neutral, Action.HURT_VISITOR, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1334-            perms.setPermission(neutral, Action.ATTACK_ENTITY, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1335-            perms.setPermission(neutral, Action.SHOOT_ARROW, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1336-            perms.setPermission(neutral, Action.THROW_POTION, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1337-            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1338-            perms.setPermission(neutral, Action.FILL_BUCKET, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1339-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1340-        
--
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-229-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-230-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.HURT_CITIZEN, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-231-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.HURT_VISITOR, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-232-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.TELEPORT_TO_COLONY, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-233-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.RECEIVE_MESSAGES, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-234-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.USE_SCAN_TOOL, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-235-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.THROW_POTION, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-236-            permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.SHOOT_ARROW, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:237:            // NOTE: GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-238-            // NOTE: USE_FLY_STICK not available in this API version
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-239-            
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-240-            Rank hostileRank = permissions.getRankHostile();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-241-            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-242-            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-243-            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-244-            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java-245-            permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-71-            return false;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-72-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-73-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-74-        Rank rank = colony.getPermissions().getRank(playerUUID);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-75-        if (rank == null) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-76-            return false;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-77-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-78-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:79:        // isColonyManager checks for officers, and !rank.isHostile() includes any
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-80-        // friendly non-enemy rank.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:81:        return rank.isColonyManager() || !rank.isHostile();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-82-    }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-83-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-84-    private static boolean isFTBTeamsLoaded() {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-85-        try {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-86-            Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-87-            return true;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-88-        } catch (ClassNotFoundException e) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-89-            return false;
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-495-        IPermissions perms = colony.getPermissions();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-496-        Rank neutral = perms.getRankNeutral();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-497-        UUID colonyOwner = perms.getOwner();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-498-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-499-        for (UUID uuid : participants) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-500-            if (uuid.equals(colonyOwner)) continue; // Never demote the colony owner
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-501-            try {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-502-                Rank current = perms.getRank(uuid);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:503:                if (current != null && current.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-504-                    perms.setPlayerRank(uuid, neutral, colony.getWorld());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-505-                    if (TaxConfig.isDebugLogging())
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-506-                        WARSYSTEM_LOGGER.debug("Demoted {} from Hostile to Neutral on colony {} after war end.", uuid, colony.getName());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-507-                }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-508-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-509-                WARSYSTEM_LOGGER.warn("Failed to demote player {} from hostile rank on colony {} at war end", uuid, colony.getName(), e);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-510-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-511-        }
--
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-225-    private static boolean applyGrant(MinecraftServer server, IColony colony, UUID memberUUID, GrantKind kind) {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-226-        IPermissions perms = colony.getPermissions();
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-227-        Level world = colony.getWorld();
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-228-        if (world == null) return false;
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-229-
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-230-        Rank currentRank = perms.getRank(memberUUID);
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-231-
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-232-        // Never override hostile rank â€” owned by WarSystem / RaidManager.
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:233:        if (currentRank != null && currentRank.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-234-            return false;
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-235-        }
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-236-
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-237-        // Owner rank: don't touch.
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-238-        if (currentRank != null && currentRank.equals(perms.getRankOwner())) {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-239-            return false;
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-240-        }
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-241-
--
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-304-        Map<UUID, GrantKind> ours = GRANTS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-305-        if (ours == null) return;
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-306-        GrantKind prevOurs = ours.get(memberUUID);
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-307-
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-308-        try {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-309-            Rank currentRank = perms.getRank(memberUUID);
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-310-            // Don't touch hostile/owner â€” that's not ours to revert.
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-311-            if (currentRank == null
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:312:                    || currentRank.isHostile()
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-313-                    || currentRank.equals(perms.getRankOwner())) {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-314-                return;
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-315-            }
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-316-            // Only revert if their current rank is still what we granted. If admin moved them
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-317-            // to a different rank, they own it now â€” leave it alone.
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-318-            if (prevOurs != null) {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-319-                Rank previouslyGrantedRank = (prevOurs == GrantKind.OFFICER)
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java-320-                        ? perms.getRankOfficer()
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1832-                                .define("KeepInventoryOnLastLife", true);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1833-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1834-                PLAYER_LIVES_IN_WAR = BUILDER.comment("Number of lives each player has during a war.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1835-                                .defineInRange("PlayerLivesInWar", 5, 1, 100); // Default 5 lives
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1836-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1837-                CONFIGURABLE_WAR_ACTIONS = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1838-                                "Actions permitted during a War. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1839-                                                +
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1840:                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1841-                                .defineList("WarActions",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1842-                                                List.of("PLACE_BLOCKS", "TOSS_ITEM", "PICKUP_ITEM",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1843-                                                                "ATTACK_CITIZEN", "FILL_BUCKET",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1844-                                                                "SHOOT_ARROW", "RIGHTCLICK_BLOCK", "RIGHTCLICK_ENTITY",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1845-                                                                "ATTACK_ENTITY", "EXPLODE", "HURT_CITIZEN",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1846-                                                                "HURT_VISITOR", "THROW_POTION"),
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1847-                                                obj -> obj instanceof String);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1848-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1849-                CONFIGURABLE_RAID_ACTIONS = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1850-                                "Actions permitted during a Raid. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1851-                                                +
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1852:                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1853-                                .defineList("RaidActions",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1854-                                                List.of("TOSS_ITEM", "PICKUP_ITEM", "ATTACK_CITIZEN",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1855-                                                                "FILL_BUCKET", "SHOOT_ARROW", "RIGHTCLICK_BLOCK",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1856-                                                                "RIGHTCLICK_ENTITY", "ATTACK_ENTITY", "EXPLODE",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1857-                                                                "HURT_CITIZEN", "HURT_VISITOR", "THROW_POTION"),
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1858-                                                obj -> obj instanceof String);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1859-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1860-                CONFIGURABLE_CLAIMING_ACTIONS = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1861-                                "Actions permitted during Abandoned Colony Claiming raids. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1862-                                                +
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1863:                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1864-                                .defineList("ClaimingActions",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1865-                                                List.of("PLACE_BLOCKS", "BREAK_BLOCKS", "TOSS_ITEM", "PICKUP_ITEM",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1866-                                                                "ATTACK_CITIZEN", "FILL_BUCKET",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1867-                                                                "SHOOT_ARROW", "RIGHTCLICK_BLOCK", "RIGHTCLICK_ENTITY",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1868-                                                                "ATTACK_ENTITY", "EXPLODE", "HURT_CITIZEN",
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1869-                                                                "HURT_VISITOR", "THROW_POTION", "OPEN_CONTAINER"),
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1870-                                                obj -> obj instanceof String);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-1871-
--
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-438-        // Demote the raider from the Hostile rank on the target colony.
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-439-        // The raider was assigned hostile rank at raid start; restore them to Neutral.
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-440-        IColony targetColony = raidData.getColony();
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-441-        if (targetColony != null && targetColony.getWorld() != null) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-442-            try {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-443-                com.minecolonies.api.colony.permissions.IPermissions perms = targetColony.getPermissions();
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-444-                UUID raiderUUID = raidData.getRaider();
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-445-                com.minecolonies.api.colony.permissions.Rank current = perms.getRank(raiderUUID);
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:446:                if (current != null && current.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-447-                    perms.setPlayerRank(raiderUUID, perms.getRankNeutral(), targetColony.getWorld());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-448-                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging())
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-449-                        LOGGER.debug("Demoted raider {} from Hostile to Neutral on colony {} after raid end.", raiderUUID, targetColony.getName());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-450-                }
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-451-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-452-                LOGGER.warn("Failed to demote raider from hostile rank on colony {} after raid end", targetColony.getName(), e);
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-453-            }
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-454-        }
--
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1182-        // Demote the raider from the Hostile rank on the target colony.
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1183-        // The raider was assigned Hostile rank at raid start; restore to Neutral.
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1184-        IColony targetColony = raidData.getColony();
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1185-        if (targetColony != null && targetColony.getWorld() != null) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1186-            try {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1187-                IPermissions perms = targetColony.getPermissions();
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1188-                UUID raiderUUID = raidData.getRaider();
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1189-                Rank current = perms.getRank(raiderUUID);
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1190:                if (current != null && current.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1191-                    perms.setPlayerRank(raiderUUID, perms.getRankNeutral(), targetColony.getWorld());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1192-                    if (TaxConfig.isDebugLogging())
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1193-                        LOGGER.debug("Demoted raider {} from Hostile to Neutral on colony {} after raid end.",
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1194-                                raiderUUID, targetColony.getName());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1195-                }
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1196-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1197-                LOGGER.warn("Failed to demote raider from hostile rank on colony {} after raid end",
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java-1198-                        targetColony.getName(), e);
--
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-201-        if ((responderIsAttacker && proposerIsAttacker) || (responderIsDefender && proposerIsDefender)) {
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-202-            return false;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-203-        }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-204-
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-205-        // Officer rank (id >= 2) or owner may accept/decline on behalf of their colony
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-206-        if (responderIsAttacker && war.getAttackerColony() != null) {
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-207-            IColony colony = war.getAttackerColony();
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-208-            if (colony.getPermissions().getOwner().equals(responderId)) return true;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:209:            if (colony.getPermissions().getRank(responder).isHostile()) return false;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-210-            if (colony.getPermissions().getRank(responder).getId() >= 2) return true;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-211-        }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-212-        if (responderIsDefender && war.getColony() != null) {
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-213-            IColony colony = war.getColony();
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-214-            if (colony.getPermissions().getOwner().equals(responderId)) return true;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:215:            if (colony.getPermissions().getRank(responder).isHostile()) return false;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-216-            if (colony.getPermissions().getRank(responder).getId() >= 2) return true;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-217-        }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-218-        return false;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-219-    }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-220-
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-221-
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-222-    private void finalizePeaceProposal(WarData war, boolean accepted, ServerPlayer responder) {
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-223-        PeaceProposal proposal = war.getActiveProposal();
--
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-161-        // Snapshotting ensures the iterator is stable even if setPlayerRank() causes any
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-162-        // internal structural changes (e.g. adding a GameProfile cache entry).
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-163-        List<UUID> playerIds = new ArrayList<>(perms.getPlayers().keySet());
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-164-
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-165-        for (UUID playerUUID : playerIds) {
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-166-            Rank rank = perms.getRank(playerUUID);
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-167-            // getRank() returns @NotNull (falls back to Neutral for unknowns) but we
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-168-            // defensively null-check, then skip anyone not currently in Hostile rank.
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:169:            if (rank == null || !rank.isHostile()) continue;
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-170-
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-171-            // The colony owner should never be in Hostile rank on their own colony.
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-172-            // If they are, something is seriously wrong â€” log it but do not auto-fix,
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-173-            // since changing the owner's rank could break colony management.
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-174-            if (playerUUID.equals(colonyOwner)) {
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-175-                LOGGER.warn("[PermissionsHealthCheck] Colony owner {} is in Hostile rank on their own colony {} (id={}) â€” investigate manually. Will not auto-demote.",
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-176-                        playerUUID, colony.getName(), colony.getID());
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java-177-                continue;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
One concrete gap is emerging: “defender side” in `BesiegeManager` includes spawned mercenaries in addition to colony citizens, but the new target helper only recognizes citizens and players. I’m checking whether there are other non-player/citizen defender entities before I give the final verdict.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 18 \"private static int spawnMercenaries|spawnedMercenaries\\.add|new .*Entity|Pillager|Raider|Monster\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"getDirectEntity\\(|Projectile|AbstractArrow|ThrownPotion|DamageSource|LivingHurtEvent\" src/main/java/net/machiavelli/minecolonytax -S" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"class Rank|boolean isHostile|isHostile\\(\\)|isColonyManager\\(\" minecolonies/src/main/java/com/minecolonies/api/colony/permissions minecolonies/src/main/java/com/minecolonies/core/colony/permissions -S" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 374ms:
484-            colony.getCitizenManager().getCitizens().stream()
485-                    .filter(c -> c.getId() == citizenId)
486-                    .findFirst()
487-                    .flatMap(ICitizenData::getEntity)
488-                    .ifPresent(entity -> entity.addEffect(
489-                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
490-        }
491-        // Also boost mercenaries
492-        for (Entity merc : raid.spawnedMercenaries) {
493-            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
494-                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
495-            }
496-        }
497-        if (TaxConfig.isDebugLogging())
498-            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
499-                    colony.getName(), extraAmplifier);
500-    }
501-
502:    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
503-        Level world = colony.getWorld();
504-        if (!(world instanceof ServerLevel)) return 0;
505-
506-        int buildingCount = 0;
507-        try {
508-            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
509-                    .getBuildings(colony).size();
510-        } catch (Exception e) {
511-            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
512-        }
513-
514-        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
515-        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
516-        if (count <= 0) return 0;
517-
518-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
519-        int spawned = 0;
520-
521-        for (int i = 0; i < count; i++) {
522-            try {
523-                EntityMercenary merc = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
524-                if (merc == null) continue;
525-
526-                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
527-                merc.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
528-                merc.setTarget(besieger);
529-
530-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
531-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));
532-
533-                world.addFreshEntity(merc);
534:                raid.spawnedMercenaries.add(merc);
535-                spawned++;
536-            } catch (Exception e) {
537-                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
538-            }
539-        }
540-        return spawned;
541-    }
542-
543-
544-    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
545-        cleanupRaid(raid, true);
546-        applyCooldown(raid.besiegingPlayerUUID);
547-
548-        if (attackerWon) {
549-            if (raid.isReclaim) {
550-                completeReclaim(raid, colony);
551-            } else {
552-                completeBesiegeVictory(raid, colony);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 372ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:13:import net.minecraftforge.event.entity.living.LivingHurtEvent;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:42:    public static void onLivingHurt(LivingHurtEvent event) {
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:13:import net.minecraft.world.damagesource.DamageSource;
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:208:    public boolean hurt(DamageSource source, float amount) {
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:218:    private boolean isPlayerOrGuardDamage(DamageSource source) {
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:356:    public void die(DamageSource cause) {
src/main/java/net/machiavelli/minecolonytax\event\PvPKillEconomyHandler.java:10:import net.minecraft.world.damagesource.DamageSource;
src/main/java/net/machiavelli/minecolonytax\event\PvPKillEconomyHandler.java:33:        DamageSource damageSource = event.getSource();
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:16:import net.minecraft.world.damagesource.DamageSource;
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:44:    private static void handleEntityDeath(AbstractEntityCitizen citizen, DamageSource damageSource) {
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:440:            DamageSource damageSource) {
src/main/java/net/machiavelli/minecolonytax\pvp\PvPBattleManager.java:44:            net.minecraft.world.damagesource.DamageSource source) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 369ms:
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-349-                  && !colony.getPermissions().hasPermission(event.getEntity(), Action.ACCESS_HUTS))
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-350-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-351-                cancelEvent(event, event.getEntity(), colony, Action.ACCESS_HUTS, event.getPos());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-352-                return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-353-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-354-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-355-            final Permissions perms = colony.getPermissions();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-356-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java:357:            if (isFreeToInteractWith(block, event.getPos()) && !perms.getRank(event.getEntity()).isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-358-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-359-                return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-360-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-361-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-362-            if ((state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)) && perms.hasPermission(event.getEntity(), Action.ACCESS_TOGGLEABLES))
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-363-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-364-                return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-365-            }
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-425-     * Check, if a player right clicked an entity. Deny if: - If the entity is in colony - player has not permission
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-426-     *
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-427-     * @param event PlayerInteractEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-428-     */
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-429-    @SubscribeEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-430-    public void on(final PlayerInteractEvent.EntityInteract event)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-431-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-432-        if (isFreeToInteractWith(null, event.getPos())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java:433:              && !colony.getPermissions().getRank(event.getEntity()).isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-434-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-435-            return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-436-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-437-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-438-        if (event.getTarget().getType().is(ModTags.freeToInteractWith))
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-439-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-440-            return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-441-        }
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-486-     * <p>
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-487-     * Check, if a player right clicked a entity. Deny if: - If the entity is in colony - player has not permission
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-488-     *
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-489-     * @param event PlayerInteractEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-490-     */
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-491-    @SubscribeEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-492-    public void on(final PlayerInteractEvent.EntityInteractSpecific event)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-493-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java:494:        if (isFreeToInteractWith(null, event.getPos()) && !colony.getPermissions().getRank(event.getEntity()).isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-495-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-496-            return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-497-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-498-        checkEventCancelation(Action.RIGHTCLICK_ENTITY, event.getEntity(), event.getLevel(), event, event.getPos());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-499-    }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-500-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-501-    /**
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-502-     * ItemTossEvent handler.
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-574-     */
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-575-    @SubscribeEvent
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-576-    public void on(final LivingHurtEvent event)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-577-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-578-        if (event.getEntity() instanceof ServerPlayer
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-579-              && event.getSource().getEntity() instanceof EntityCitizen
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-580-              && ((EntityCitizen) event.getSource().getEntity()).getCitizenColonyHandler().getColonyId() == colony.getID()
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-581-              && colony.getRaiderManager().isRaided()
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java:582:              && !colony.getPermissions().getRank((Player) event.getEntity()).isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-583-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-584-            event.setCanceled(true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-585-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-586-    }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-587-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-588-    /**
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-589-     * AttackEntityEvent handler.
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-590-     * <p>
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-604-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-605-        if (MineColonies.getConfig().getServer().enableColonyProtection.get()
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-606-              && colony.isCoordInColony(player.getCommandSenderWorld(), player.blockPosition()))
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-607-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-608-            final Permissions perms = colony.getPermissions();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-609-            if (event.getTarget() instanceof EntityCitizen)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-610-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-611-                final AbstractEntityCitizen citizen = (AbstractEntityCitizen) event.getTarget();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java:612:                if (citizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard && perms.getRank(event.getEntity()).isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-613-                {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-614-                    return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-615-                }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-616-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-617-                if (perms.hasPermission(event.getEntity(), Action.ATTACK_CITIZEN))
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-618-                {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-619-                    return;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\ColonyPermissionEventHandler.java-620-                }
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-226-     * @param rank
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-227-     */
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-228-    private void upgradePermissions(final int version, final Rank rank)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-229-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-230-        // keep this consistent with loadRanks(), as that's still used for new colonies
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-231-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-232-        if (version < 4)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-233-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:234:            if (rank.isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-235-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-236-                this.setPermission(rank, Action.HURT_CITIZEN, true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-237-                this.setPermission(rank, Action.HURT_VISITOR, true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-238-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-239-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:240:            if (rank.isColonyManager())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-241-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-242-                this.setPermission(rank, Action.MAP_DEATHS, true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-243-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-244-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-245-            this.setPermission(rank, Action.MAP_BORDER, true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-246-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-247-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-248-        if (version < 5)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-249-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:250:            if (!rank.isHostile())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-251-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-252-                this.setPermission(rank, Action.ACCESS_TOGGLEABLES, true);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-253-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-254-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-255-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-256-        // Fix bad saved values
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-257-        if (rank == getRankOwner())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-258-        {
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-338-            final ListTag rankTagList = compound.getList(TAG_RANKS, Tag.TAG_COMPOUND);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-339-            for (int i = 0; i < rankTagList.size(); ++i)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-340-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-341-                final CompoundTag rankCompound = rankTagList.getCompound(i);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-342-                final int id = rankCompound.getInt(TAG_ID);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-343-                final String name = rankCompound.getString(TAG_NAME);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-344-                final boolean isInitial = rankCompound.getBoolean(TAG_INITIAL);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-345-                final boolean isColonyManager = rankCompound.getBoolean(TAG_COLONY_MANAGER);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:346:                final boolean isHostile = rankCompound.getBoolean(TAG_HOSTILE);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-347-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-348-                final Rank rank = new Rank(id, 0L, name, isInitial, isColonyManager, isHostile);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-349-                ranks.put(id, rank);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-350-                upgradePermissions(version, rank);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-351-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-352-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-353-            final ListTag permissionsTagList = compound.getList(TAG_PERMISSIONS, Tag.TAG_COMPOUND);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-354-            for (int i = 0; i < permissionsTagList.size(); ++i)
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-562-        //  Ranks
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-563-        @NotNull final ListTag rankTagList = new ListTag();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-564-        for (@NotNull final Rank rank : ranks.values())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-565-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-566-            @NotNull final CompoundTag rankCompound = new CompoundTag();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-567-            rankCompound.putInt(TAG_ID, rank.getId());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-568-            rankCompound.putString(TAG_NAME, rank.getName());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-569-            rankCompound.putBoolean(TAG_INITIAL, rank.isInitial());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:570:            rankCompound.putBoolean(TAG_COLONY_MANAGER, rank.isColonyManager());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:571:            rankCompound.putBoolean(TAG_HOSTILE, rank.isHostile());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-572-            rankTagList.add(rankCompound);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-573-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-574-        compound.put(TAG_RANKS, rankTagList);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-575-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-576-        //  Owners
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-577-        @NotNull final ListTag ownerTagList = new ListTag();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-578-        for (@NotNull final ColonyPlayer player : players.values())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-579-        {
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-725-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-726-        final ColonyPlayer player = getPlayers().get(id);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-727-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-728-        if (player != null)
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-729-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-730-            Rank oldRank = player.getRank();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-731-            player.setRank(rank);
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-732-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:733:            if (rank.isColonyManager())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-734-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-735-                fullyAbandoned = false;
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-736-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-737-            else
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-738-            {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-739-                checkFullyAbandoned();
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-740-            }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-741-
--
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-934-    {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-935-        buf.writeVarInt(ranks.size());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-936-        for (Rank rank : ranks.values())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-937-        {
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-938-            buf.writeVarInt(rank.getId());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-939-            buf.writeLong(rank.getPermissions());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-940-            buf.writeUtf(rank.getName());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-941-            buf.writeBoolean(rank.isInitial());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:942:            buf.writeBoolean(rank.isColonyManager());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java:943:            buf.writeBoolean(rank.isHostile());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-944-        }
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-945-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-946-        buf.writeVarInt(viewerRank.getId());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-947-
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-948-        //  Owners
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-949-        buf.writeVarInt(players.size());
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-950-        for (@NotNull final Map.Entry<UUID, ColonyPlayer> player : players.entrySet())
minecolonies/src/main/java/com/minecolonies/core/colony/permissions\Permissions.java-951-        {
--
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-1-package com.minecolonies.api.colony.permissions;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-2-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-3-import com.minecolonies.api.util.Utils;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-4-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-5-import java.util.Objects;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-6-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java:7:public class Rank
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-8-{
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-9-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-10-     * The name of the rank
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-11-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-12-    private String name;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-13-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-14-     * The id of the rank
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-15-     */
--
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-22-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-23-     * Whether the rank is a colony manager (can perform certain actions)
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-24-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-25-    private boolean isColonyManager;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-26-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-27-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-28-     * Whether the rank is hostile (can attack and be attacked)
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-29-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java:30:    private boolean isHostile;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-31-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-32-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-33-     * Holds all bits indicating given permissions
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-34-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-35-    private long permissionData = 0;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-36-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-37-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-38-     * Rank constructor
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-39-     *
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-40-     * @param id           the id of the rank
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-41-     * @param name         the name of the rank
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-42-     * @param isInitial    whether the rank is an initial rank
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-43-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java:44:    public Rank(int id, long permissionData, String name, boolean isInitial, boolean isColonyManager, boolean isHostile)
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-45-    {
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-46-        this.id = id;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-47-        this.permissionData = permissionData;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-48-        this.name = name;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-49-        this.isInitial = isInitial;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-50-        this.isColonyManager = isColonyManager;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-51-        this.isHostile = isHostile;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-52-    }
--
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-79-     * @return true if so
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-80-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-81-    public boolean isInitial() { return isInitial; }
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-82-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-83-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-84-     * Get whether this rank is a colony manager
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-85-     * @return true if so
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-86-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java:87:    public boolean isColonyManager() { return isColonyManager; }
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-88-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-89-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-90-     * Get whether this rank is hostile
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-91-     * @return true if so
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-92-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java:93:    public boolean isHostile() { return isHostile; }
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-94-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-95-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-96-     * Set whether this rank is a colony manager
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-97-     * @param isColonyManager whether the rank is a colony manager
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-98-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-99-    public void setColonyManager(boolean isColonyManager)
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-100-    {
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-101-        this.isColonyManager = isColonyManager;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-102-    }
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-103-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-104-    /**
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-105-     * Set whether this rank is hostile
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-106-     * @param isHostile whether the rank is hostile
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-107-     */
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java:108:    public void setHostile(boolean isHostile)
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-109-    {
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-110-        this.isHostile = isHostile;
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-111-    }
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-112-
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-113-    @Override
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-114-    public boolean equals(final Object o)
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-115-    {
minecolonies/src/main/java/com/minecolonies/api/colony/permissions\Rank.java-116-        if (this == o)
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"IColonyManager\\.getInstance\\(|IMinecoloniesAPI\\.getInstance\\(\\)\\.getColonyManager\\(\\)\\.getAllColonies\" src/main/java minecolonies/src/main/java -S" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 394ms:
minecolonies/src/main/java\com\minecolonies\core\compatibility\journeymap\ColonyBorderMapping.java:58:        final IColony colony = IColonyManager.getInstance().getIColony(Minecraft.getInstance().level, pos);
minecolonies/src/main/java\com\minecolonies\core\util\TeleportHelper.java:72:        final IColony colony = IColonyManager.getInstance().getIColonyByOwner(player.getCommandSenderWorld(), player);
minecolonies/src/main/java\com\minecolonies\core\util\TeleportHelper.java:112:        final IColony colony = IColonyManager.getInstance().getColonyByDimension(id, dimension);
minecolonies/src/main/java\com\minecolonies\core\util\SortingUtils.java:97:        final int creativeTabId = IColonyManager.getInstance().getCompatibilityManager().getCreativeTabKey(entry.getKey());
minecolonies/src/main/java\com\minecolonies\core\util\SortingUtils.java:145:        final int creativeTabId1 = IColonyManager.getInstance().getCompatibilityManager().getCreativeTabKey(t1.getKey());
minecolonies/src/main/java\com\minecolonies\core\util\SortingUtils.java:146:        final int creativeTabId2 = IColonyManager.getInstance().getCompatibilityManager().getCreativeTabKey(t2.getKey());
minecolonies/src/main/java\com\minecolonies\core\util\SortingUtils.java:187:            final int index = IColonyManager.getInstance().getCompatibilityManager().getCreativeTabKey(entry.getKey());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:441:                for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2207:        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2560:        List<IColony> playerColonies = IColonyManager.getInstance().getColonies(player.level()).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2665:                List<IColony> attackerColonies = IColonyManager.getInstance().getColonies(source.getLevel()).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2692:            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3029:            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3162:        IColony targetColony = IColonyManager.getInstance().getColonyByDimension(colonyId,
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3203:                IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3566:        return IColonyManager.getInstance().getColonies(level).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:4038:                defenderColony = IColonyManager.getInstance().getColonies(level).stream()
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:4042:                attackerColony = IColonyManager.getInstance().getColonies(level).stream()
minecolonies/src/main/java\com\minecolonies\core\entity\visitor\VisitorColonyHandler.java:42:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, citizen.level);
minecolonies/src/main/java\com\minecolonies\core\client\render\worldevent\WorldEventContext.java:100:            nearestColony = IColonyManager.getInstance().getClosestColonyView(level, clientPlayer.blockPosition());
minecolonies/src/main/java\com\minecolonies\core\entity\visitor\VisitorCitizen.java:190:                        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(getCitizenColonyHandler().getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\visitor\VisitorCitizen.java:411:        final IColonyView iColonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), player.level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\visitor\VisitorCitizen.java:478:                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\visitor\VisitorCitizen.java:517:                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\event\FMLEventHandler.java:25:        IColonyManager.getInstance().onServerTick(event);
minecolonies/src/main/java\com\minecolonies\core\event\FMLEventHandler.java:31:        IColonyManager.getInstance().onClientTick(event);
minecolonies/src/main/java\com\minecolonies\core\event\FMLEventHandler.java:40:            IColonyManager.getInstance().getIColonyByOwner(event.getEntity().level, event.getEntity());
minecolonies/src/main/java\com\minecolonies\core\event\FMLEventHandler.java:62:        IColonyManager.getInstance().onWorldTick(event);
minecolonies/src/main/java\com\minecolonies\core\event\FMLEventHandler.java:68:        IColonyManager.getInstance().getRecipeManager().reset();
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:499:        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:312:                final IColony oldColony = IColonyManager.getInstance().getColonyByWorld(owningColony, player.level);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:337:            final IColony newColony = IColonyManager.getInstance().getColonyByWorld(ColonyUtils.getOwningColony(newChunk), player.level);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:390:            final IColony colony = IColonyManager.getInstance().getColonyByWorld(chunkCapData.getOwningColony(), world);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:403:                final IColony newColony = IColonyManager.getInstance().getColonyByWorld(entry.getKey(), world);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:444:        final IColony newColony = IColonyManager.getInstance().getColonyByWorld(owningColony, (Level) event.getLevel());
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:472:            for (final IColony colony : IColonyManager.getInstance().getAllColonies())
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:505:            for (final IColony colony : IColonyManager.getInstance().getAllColonies())
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:535:                    final IColony colony = IColonyManager.getInstance().getColonyByWorld(owningColony, entityCitizen.level);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:565:                final IColony colony = IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:600:                final IColony colony = IColonyManager.getInstance().getIColony(world, event.getPos());
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:612:            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, bedBlockPos);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:641:                final IColony colony = IColonyManager.getInstance().getIColony(world, event.getPos());
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:722:        final IColony colony = IColonyManager.getInstance().getIColony(world, pos);
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:732:            if (IColonyManager.getInstance().getIColonyByOwner(world, player) == null)
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:765:            IColonyManager.getInstance().onWorldLoad((Level) event.getLevel());
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:796:            IColonyManager.getInstance().onWorldUnload((Level) event.getLevel());
minecolonies/src/main/java\com\minecolonies\core\event\EventHandler.java:830:            final IColony colony = IColonyManager.getInstance().getIColony(world, entity.blockPosition());
minecolonies/src/main/java\com\minecolonies\core\blocks\MinecoloniesCropBlock.java:143:                    && IColonyManager.getInstance().getColonyByPosFromWorld(level, pos) instanceof Colony colony && colony.getResearchManager().getResearchEffects().getEffectStrength(
minecolonies/src/main/java\com\minecolonies\core\event\ClientEventHandler.java:108:        IColonyManager.getInstance().resetColonyViews();
minecolonies/src/main/java\com\minecolonies\core\event\ClientEventHandler.java:404:            IColony colony = IColonyManager.getInstance().getIColony(world, pos);
minecolonies/src/main/java\com\minecolonies\core\event\ClientEventHandler.java:407:                if (IColonyManager.getInstance().isFarEnoughFromColonies(world, pos))
minecolonies/src/main/java\com\minecolonies\core\event\ClientEventHandler.java:412:                colony = IColonyManager.getInstance().getClosestIColony(world, pos);
minecolonies/src/main/java\com\minecolonies\core\event\ClientEventHandler.java:422:                        IColonyManager.getInstance().getMinimumDistanceBetweenTownHalls()).getString());
minecolonies/src/main/java\com\minecolonies\apiimp\initializer\InteractionValidatorInitializer.java:149:                for (final ItemStorage storage : IColonyManager.getInstance().getCompatibilityManager().getSmeltableOres())
minecolonies/src/main/java\com\minecolonies\core\util\ChunkDataHelper.java:78:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(closeColony, world.dimension());
minecolonies/src/main/java\com\minecolonies\core\util\ChunkDataHelper.java:97:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(closeColony, world.dimension());
minecolonies/src/main/java\com\minecolonies\core\util\ChunkDataHelper.java:395:                final IColony colony = IColonyManager.getInstance().getColonyByDimension(id, world.dimension());
minecolonies/src/main/java\com\minecolonies\core\util\BuildingUtils.java:203:        IColony colony = IColonyManager.getInstance().getIColony(level, pos);
minecolonies/src/main/java\com\minecolonies\core\client\render\worldevent\ColonyPatrolPointRenderer.java:45:        final IColonyView colony = IColonyManager.getInstance().getColonyView(itemStackNbt.getInt(TAG_ID), ctx.clientLevel.dimension());
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:96:                for (int i = 1; i <= IColonyManager.getInstance().getTopColonyId() + 1; i++)
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:103:                        if (IColonyManager.getInstance().getColonyByDimension(i, dimensionType) == null)
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:184:                IColonyManager.getInstance().read(data);
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:212:                    if (IColonyManager.getInstance().getColonyByDimension(i, dimensionType) == null)
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:337:        IColonyManager.getInstance().write(compound);
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:343:        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:383:            for (int i = 1; i <= IColonyManager.getInstance().getTopColonyId() + 1; i++)
minecolonies/src/main/java\com\minecolonies\core\util\BackUpHelper.java:431:        IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\commands\generalcommands\CommandWhoAmI.java:32:        final IColony colony = IColonyManager.getInstance().getIColonyByOwner(sender.getCommandSenderWorld(), sender.getUUID());
minecolonies/src/main/java\com\minecolonies\core\commands\generalcommands\CommandWhereAmI.java:28:        final IColony colony = IColonyManager.getInstance().getClosestColony(sender.getCommandSenderWorld(), playerPos);
minecolonies/src/main/java\com\minecolonies\core\commands\generalcommands\CommandWhereAmI.java:38:        if (!IColonyManager.getInstance().isCoordinateInAnyColony(sender.getCommandSenderWorld(), playerPos))
minecolonies/src/main/java\com\minecolonies\core\commands\generalcommands\CommandGetRanks.java:78:        final List<IColony> colonies = IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityStash.java:60:            if (level != null && !level.isClientSide && IColonyManager.getInstance().isCoordinateInAnyColony(level, worldPosition))
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityStash.java:62:                final IColony colony = IColonyManager.getInstance().getClosestColony(level, worldPosition);
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityScarecrow.java:73:            this.currentColony = IColonyManager.getInstance().getIColony(level, worldPosition);
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityRack.java:250:            if (IColonyManager.getInstance().getCompatibilityManager().getCreativeTab(checkItem) == IColonyManager.getInstance().getCompatibilityManager().getCreativeTab(storage))
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityPlantationField.java:123:            this.currentColony = IColonyManager.getInstance().getIColony(level, worldPosition);
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonySign.java:193:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, level.dimension());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonySign.java:260:                        final IColony targetColony = IColonyManager.getInstance().getColonyByDimension(targetColonyId, level.dimension());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyFlag.java:59:            IColony colony = IColonyManager.getInstance().getIColony(this.getLevel(), worldPosition);
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyFlag.java:93:            IColonyView colony = IColonyManager.getInstance().getColonyView(this.colonyId, level.dimension());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyBuilding.java:179:                colony = IColonyManager.getInstance().getColonyByPosFromWorld(getLevel(), this.getBlockPos());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyBuilding.java:183:                colony = IColonyManager.getInstance().getColonyView(colonyId, getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyBuilding.java:187:                colony = IColonyManager.getInstance().getColonyByWorld(colonyId, getLevel());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyBuilding.java:357:        final IColonyView c = IColonyManager.getInstance().getColonyView(colonyId, level.dimension());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityColonyBuilding.java:462:            final IColony tempColony = IColonyManager.getInstance().getColonyByPosFromWorld(getLevel(), this.getPosition());
minecolonies/src/main/java\com\minecolonies\core\tileentities\TileEntityBarrel.java:181:        return IColonyManager.getInstance().getCompatibilityManager()
minecolonies/src/main/java\com\minecolonies\core\compatibility\jei\CompostRecipeCategory.java:53:        return IColonyManager.getInstance().getCompatibilityManager().getCopyOfCompostRecipes().entrySet().stream()
minecolonies/src/main/java\com\minecolonies\core\compatibility\CraftingTagAuditor.java:112:        final ICompatibilityManager compatibility = IColonyManager.getInstance().getCompatibilityManager();
minecolonies/src/main/java\com\minecolonies\core\compatibility\CraftingTagAuditor.java:448:        final Map<Item, CompostRecipe> compostRecipes = IColonyManager.getInstance().getCompatibilityManager().getCopyOfCompostRecipes();
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:65:            List<IColony> ownedColonies = IColonyManager.getInstance().getColonies(raider.level()).stream()
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:1320:        return IColonyManager.getInstance().getColonies(level).stream()
minecolonies/src/main/java\com\minecolonies\core\entity\pathfinding\proxy\GeneralEntityWalkToProxy.java:34:        final IColony colony = IColonyManager.getInstance().getClosestColony(living.getCommandSenderWorld(), pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockStash.java:97:            @Nullable final IBuildingView building = IColonyManager.getInstance().getBuildingView(worldIn.dimension(), pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockHutTownHall.java:79:            final IBuilding building = IColonyManager.getInstance().getBuilding(player.level, pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockHutTownHall.java:194:            @Nullable final IBuildingView building = IColonyManager.getInstance().getBuildingView(worldIn.dimension(), pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockHutTownHall.java:221:        IColony colony = IColonyManager.getInstance().getIColony(player.level(), pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockHutTavern.java:52:        IColony colony = IColonyManager.getInstance().getIColony(player.level(), pos);
minecolonies/src/main/java\com\minecolonies\core\entity\other\cavalry\CavalryHorseEntity.java:189:            IColony colony = IColonyManager.getInstance().getClosestColony(level, this.getOnPos());
minecolonies/src/main/java\com\minecolonies\core\entity\other\cavalry\CavalryHorseEntity.java:211:                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(animalColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockHutGateHouse.java:74:            @Nullable final IBuildingView building = IColonyManager.getInstance().getBuildingView(worldIn.dimension(), pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\huts\BlockHutGateHouse.java:85:            final IColony colony = IColonyManager.getInstance().getIColony(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\api\util\ItemStackUtils.java:889:        final Set<ItemStorage> allItems = new HashSet<>(IColonyManager.getInstance().getCompatibilityManager().getSetOfAllItems());
minecolonies/src/main/java\com\minecolonies\core\commands\colonycommands\requestsystem\CommandRSResetAll.java:22:        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
minecolonies/src/main/java\com\minecolonies\core\colony\requestsystem\resolvers\PrivateWorkerCraftingProductionResolver.java:69:        final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(request.getRequest().getRecipeID());
minecolonies/src/main/java\com\minecolonies\core\items\ItemBannerRallyGuards.java:362:            return IColonyManager.getInstance().getBuildingView(worldIn.dimension(), position) instanceof AbstractBuildingGuards.View;
minecolonies/src/main/java\com\minecolonies\core\items\ItemBannerRallyGuards.java:366:            return IColonyManager.getInstance().getBuilding(worldIn, position) instanceof IGuardBuilding;
minecolonies/src/main/java\com\minecolonies\core\items\ItemBannerRallyGuards.java:387:                 ? (AbstractBuildingGuards.View) IColonyManager.getInstance().getBuildingView(worldIn.dimension(), position)
minecolonies/src/main/java\com\minecolonies\core\items\ItemBannerRallyGuards.java:407:        return isGuardBuilding(worldIn, position) ? (IGuardBuilding) IColonyManager.getInstance().getBuilding(worldIn, position) : null;
minecolonies/src/main/java\com\minecolonies\core\items\ItemAssistantHammer.java:85:        final IColonyView view = IColonyManager.getInstance().getColonyView(level, interactPos);
minecolonies/src/main/java\com\minecolonies\core\commands\colonycommands\CommandShowClaim.java:74:                final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, level.dimension());
minecolonies/src/main/java\com\minecolonies\core\commands\colonycommands\CommandShowClaim.java:92:                final IColony colony = IColonyManager.getInstance().getColonyByDimension(entry.getKey(), level.dimension());
src/main/java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:150:                for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
minecolonies/src/main/java\com\minecolonies\core\items\ItemAncientTome.java:34:            final IColony colony = IColonyManager.getInstance().getClosestColony(worldIn, entityIn.blockPosition());
minecolonies/src/main/java\com\minecolonies\api\util\CreativeBuildingStructureHandler.java:87:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(getWorld(), getWorldPos());
minecolonies/src/main/java\com\minecolonies\core\items\AbstractItemScroll.java:171:        return IColonyManager.getInstance().getColonyByDimension(stack.getTag().getInt(TAG_COLONY_ID), ResourceKey.create(Registries.DIMENSION, new ResourceLocation(stack.getTag().getString(TAG_COLONY_DIM))));
minecolonies/src/main/java\com\minecolonies\core\items\AbstractItemScroll.java:187:        return IColonyManager.getInstance().getColonyView(stack.getTag().getInt(TAG_COLONY_ID), ResourceKey.create(Registries.DIMENSION, new ResourceLocation(stack.getTag().getString(TAG_COLONY_DIM))));
src/main/java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:67:            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:84:            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:87:            if (TaxConfig.isNormalLogging()) LOGGER.info("General colony permissions applied to {} colonies", IColonyManager.getInstance().getAllColonies().size());
src/main/java\net\machiavelli\minecolonytax\permissions\GeneralColonyPermissionsManager.java:175:            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\ReactivateBuildingMessage.java:82:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\PlantationFieldBuildRequestMessage.java:152:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(dimension, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemColonySign.java:85:                        final IColony sourceColony = IColonyManager.getInstance().getColonyByDimension(compound.getInt(TAG_COLONY), ctx.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\items\ItemColonySign.java:119:                    final IColony colony = IColonyManager.getInstance().getColonyByDimension(signEntity.getColonyId(), ctx.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\items\ItemColonySign.java:129:                        final IColony sourceColony = IColonyManager.getInstance().getColonyByDimension(compound.getInt(TAG_COLONY), ctx.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\items\ItemColonySign.java:183:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, ctx.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\PickupBlockMessage.java:70:        if (IColonyManager.getInstance().getColonyByPosFromWorld(world, pos) instanceof Colony)
minecolonies/src/main/java\com\minecolonies\core\items\ItemColonyMap.java:116:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_COLONY), world.dimension());
minecolonies/src/main/java\com\minecolonies\core\commands\colonycommands\CommandListColonies.java:64:        final List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
minecolonies/src/main/java\com\minecolonies\core\items\ItemClipboard.java:121:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_COLONY), world.dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\GetColonyInfoMessage.java:79:        if (IColonyManager.getInstance().getColonyByPosFromWorld(world, pos) instanceof Colony)
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\GetColonyInfoMessage.java:85:        if (IColonyManager.getInstance().getIColonyByOwner(world, sender) instanceof Colony colony)
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\GetColonyInfoMessage.java:91:        final IColony nextColony = IColonyManager.getInstance().getClosestColony(world, pos);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\GetColonyInfoMessage.java:92:        if (IColonyManager.getInstance().isFarEnoughFromColonies(world, pos))
minecolonies/src/main/java\com\minecolonies\core\commands\colonycommands\CommandDeleteColony.java:173:        IColonyManager.getInstance().deleteColonyByDimension(colony.getID(), deleteBuildings, context.getSource().getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\DirectPlaceMessage.java:114:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\DecorationBuildRequestMessage.java:144:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromDim(dimension, pos);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\CreateColonyMessage.java:114:        final IColony colony = IColonyManager.getInstance().getClosestColony(world, townHall);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\CreateColonyMessage.java:164:        if (colony != null && !IColonyManager.getInstance().isFarEnoughFromColonies(world, townHall))
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\CreateColonyMessage.java:170:        final IColony ownedColony = IColonyManager.getInstance().getIColonyByOwner(world, sender);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\CreateColonyMessage.java:174:            final IColony createdColony = IColonyManager.getInstance().createColony(world, townHall, sender, colonyName, pack);
src/main/java\net\machiavelli\minecolonytax\event\RaidKillTracker.java:46:        IColony colony = IColonyManager.getInstance().getColonyByWorld(citizen.getCitizenColonyHandler().getColonyId(),
src/main/java\net\machiavelli\minecolonytax\event\EntityRaidEventHandler.java:68:        IColonyManager.getInstance().getAllColonies().forEach(colony -> {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:601:            for (IColony c : IColonyManager.getInstance().getColonies(level)) {
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterBeekeeper.java:68:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(compound.getInt(TAG_ID), useContext.getLevel());
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterBeekeeper.java:115:        final IColonyView colony = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_ID), world.dimension());
minecolonies/src/main/java\com\minecolonies\core\client\render\ColonyMapDecorator.java:41:                colonyView = IColonyManager.getInstance().getColonyView(colonyId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\client\render\ClipBoardDecorator.java:48:                colonyView = IColonyManager.getInstance().getColonyView(colonyId, Minecraft.getInstance().level.dimension());
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:103:        IColony firstColony = IColonyManager.getInstance().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:198:        IColony colony = IColonyManager.getInstance().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:63:        List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
src/main/java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:103:            List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
minecolonies/src/main/java\com\minecolonies\core\items\ItemSupplyChestDeployer.java:249:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:130:        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemSupplyCampDeployer.java:214:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemResourceScroll.java:73:        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(colonyId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\items\ItemResourceScroll.java:151:        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_COLONY_ID), Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\items\ItemResourceScroll.java:321:        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(colonyId, worldIn.dimension());
src/main/java\net\machiavelli\minecolonytax\event\AbandonedColonyProtectionHandler.java:64:                    IColonyManager.getInstance().getColonyByPosFromWorld((Level) event.getLevel(), event.getPos()))) {
src/main/java\net\machiavelli\minecolonytax\event\AbandonedColonyProtectionHandler.java:78:            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemQuestLog.java:122:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(compound.getInt(TAG_COLONY), world.dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\colony\TriggerConnectionEventMessage.java:52:            final IColony targetColony = IColonyManager.getInstance().getColonyByDimension(targetColonyId, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockScarecrow.java:101:        final IColony iColony = IColonyManager.getInstance().getIColony(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockScarecrow.java:182:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockScarecrow.java:223:            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\colony\TeleportToColonyMessage.java:77:        final IColony originColony = IColonyManager.getInstance().getColonyByDimension(originColonyId, ctxIn.getSender().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockPlantationField.java:184:            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockPlantationField.java:242:            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockMinecoloniesRack.java:229:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterPermission.java:132:        final IColonyView iColonyView = IColonyManager.getInstance().getClosestColonyView(ctx.getLevel(), ctx.getClickedPos());
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterPermission.java:195:        final IColonyView colony = IColonyManager.getInstance().getClosestColonyView(world, player.blockPosition());
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockMinecoloniesGrave.java:153:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterLumberjack.java:119:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(compound.getInt(TAG_ID), worldIn);
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterLumberjack.java:169:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, world);
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterLumberjack.java:188:        final IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, world.dimension());
minecolonies/src/main/java\com\minecolonies\core\colony\requestsystem\requests\StandardRequests.java:659:                foodExamples = ImmutableList.copyOf(IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\colony\requestsystem\requests\StandardRequests.java:715:                oreExamples = ImmutableList.copyOf(IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\colony\requestsystem\requests\StandardRequests.java:719:                                                     .filter(IColonyManager.getInstance().getCompatibilityManager()::isOre)
minecolonies/src/main/java\com\minecolonies\core\colony\requestsystem\requests\StandardRequests.java:765:                burnableExamples = ImmutableList.copyOf(IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockDecorationController.java:195:            @Nullable final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\core\items\ItemScepterGuard.java:92:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(compound.getInt(TAG_ID), worldIn);
minecolonies/src/main/java\com\minecolonies\core\colony\requestsystem\requests\AbstractRequest.java:455:              ImmutableList.copyOf(IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems().stream().filter(deliverable::matches).collect(Collectors.toList()));
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:94:            IColonyManager.getInstance().handlePermissionsViewMessage(colonyID, data, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:159:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:187:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:256:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:327:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:401:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:469:            IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:477:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:553:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyID, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:631:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\PermissionsMessage.java:701:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockColonySign.java:106:        final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, worldIn.dimension());
minecolonies/src/main/java\com\minecolonies\core\blocks\BlockColonySign.java:123:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(tileEntityColonySign.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\colony\ColonyDeleteOwnMessage.java:49:        final IColony colony = IColonyManager.getInstance().getIColonyByOwner(player.level, player);
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\colony\ColonyDeleteOwnMessage.java:52:            IColonyManager.getInstance().deleteColonyByDimension(colony.getID(), false, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\colony\ColonyAbandonOwnMessage.java:48:        final IColony colony = IColonyManager.getInstance().getIColonyByOwner(player.level, player);
src/main/java\net\machiavelli\minecolonytax\network\packets\UpdateTaxPermissionPacket.java:38:                IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, player.serverLevel());
src/main/java\net\machiavelli\minecolonytax\economy\RaidPenaltyManager.java:62:        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\MultiColonyIdArgument.java:55:            final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, context.getSource().getLevel());
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\MultiColonyIdArgument.java:98:            return IColonyManager.getInstance().getAllColonies().stream().map(IColony::getID).toList();
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\ColonyIdArgument.java:58:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, context.getSource().getLevel());
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\ColonyIdArgument.java:107:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, context.getSource().getLevel());
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\ColonyIdArgument.java:120:        final IColony colony = IColonyManager.getInstance().getIColonyByOwner(source.getLevel(), id);
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\ColonyIdArgument.java:139:            final IColony colony = IColonyManager.getInstance().getIColony(source.getLevel(), BlockPos.containing(source.getPosition()));
minecolonies/src/main/java\com\minecolonies\core\commands\arguments\ColonyIdArgument.java:200:            IColonyManager.getInstance().getIColonies(world).stream().map(IColony::getID).forEach(builder::suggest);
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:444:                for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestOfficerDataPacket.java:48:                    for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestOfficerDataPacket.java:55:                    IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, player.serverLevel());
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:219:                        : com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
minecolonies/src/main/java\com\minecolonies\core\entity\mobs\EntityMercenary.java:349:                setColony(IColonyManager.getInstance().getColonyByWorld(colonyId, level));
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\colony\building\worker\AddRemoveRecipeMessage.java:176:            final IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
minecolonies/src/main/java\com\minecolonies\core\placementhandlers\WayPointBlockPlacementHandler.java:39:        final IColony colony = IColonyManager.getInstance().getClosestColony(world, pos);
minecolonies/src/main/java\com\minecolonies\core\placementhandlers\main\SurvivalHandler.java:74:            final IColonyView colonyView = IColonyManager.getInstance().getClosestColonyView(clientLevel, blockPos);
minecolonies/src/main/java\com\minecolonies\core\placementhandlers\main\SurvivalHandler.java:103:        final IColony tempColony = IColonyManager.getInstance().getClosestColony(world, blockPos);
minecolonies/src/main/java\com\minecolonies\core\placementhandlers\main\SurvivalHandler.java:115:            if (isInColony || IColonyManager.getInstance().isFarEnoughFromColonies(world, blockPos))
minecolonies/src/main/java\com\minecolonies\core\placementhandlers\main\SurvivalHandler.java:217:                @Nullable final IBuilding building = IColonyManager.getInstance().getBuilding(world, blockPos);
minecolonies/src/main/java\com\minecolonies\core\placementhandlers\main\SurvivalHandler.java:231:                        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, blockPos);
minecolonies/src/main/java\com\minecolonies\api\tileentities\AbstractTileEntityRack.java:128:                if (IColonyManager.getInstance().isCoordinateInAnyColony(level, worldPosition))
minecolonies/src/main/java\com\minecolonies\api\tileentities\AbstractTileEntityRack.java:130:                    final IColony colony = IColonyManager.getInstance().getClosestColony(level, worldPosition);
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:118:        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:131:            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:137:        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:353:        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\compat\ColonyHelper.java:12:        for (IColony c : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:972:        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:86:            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeDamageShieldHandler.java:116:                IColony besieged = com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:925:            return IColonyManager.getInstance().getIColonyByOwner(player.level(), player);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\OpenBuildingUIMessage.java:79:        if (IColonyManager.getInstance().getColonyView(colonyId, dimension) instanceof ColonyView colonyView && colonyView.getClientBuildingManager().getBuilding(buildingId) instanceof AbstractBuildingView buildingView)
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyVisitorViewDataMessage.java:111:        final IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewWorkOrderMessage.java:85:        IColonyManager.getInstance().handleColonyViewWorkOrderMessage(colonyId, workOrderBuffer, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewResearchManagerViewMessage.java:87:        final IColonyView colonyView = IColonyManager.getInstance().getColonyView(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewRemoveWorkOrderMessage.java:68:            IColonyManager.getInstance().handleColonyViewRemoveWorkOrderMessage(colonyId, workOrderId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewRemoveMessage.java:57:        IColonyManager.getInstance().removeColonyView(id, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewRemoveCitizenMessage.java:67:            IColonyManager.getInstance().handleColonyViewRemoveCitizenMessage(colonyId, citizenId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewRemoveBuildingMessage.java:68:            IColonyManager.getInstance().handleColonyViewRemoveBuildingMessage(colonyId, buildingId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewMessage.java:99:            IColonyManager.getInstance().handleColonyViewMessage(colonyId, colonyBuffer, Minecraft.getInstance().level, isNewSubscription, dim);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewCitizenViewMessage.java:85:        IColonyManager.getInstance().handleColonyViewCitizensMessage(colonyId, citizenId, citizenBuffer, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewBuildingViewMessage.java:95:        IColonyManager.getInstance().handleColonyBuildingViewMessage(colonyId, buildingId, buildingData, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewBuildingExtensionsUpdateMessage.java:107:        final IColonyView view = IColonyManager.getInstance().getColonyView(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyViewAnimalViewDataMessage.java:112:        final IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, dimension);
minecolonies/src/main/java\com\minecolonies\core\network\messages\client\colony\ColonyListMessage.java:83:            Network.getNetwork().sendToPlayer(new ColonyListMessage(IColonyManager.getInstance().getColonies(ctxIn.getSender().level)), ctxIn.getSender());
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1384:                        IColony target = IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2338:                IColony target = IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2397:                IColony playerColony = IColonyManager.getInstance().getColonies(level).stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2481:                IColony targetColony = IColonyManager.getInstance().getColonyByDimension(colonyId, level.dimension());
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2622:                        IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
minecolonies/src/main/java\com\minecolonies\core\network\messages\server\AbstractColonyServerMessage.java:119:        final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, dimensionId);
src/main/java\net\machiavelli\minecolonytax\commands\WarCommands.java:129:        IColony attackerColony = IColonyManager.getInstance().getColonies(level).stream()
src/main/java\net\machiavelli\minecolonytax\commands\RandomEventsCommand.java:72:        IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.serverLevel().dimension());
src/main/java\net\machiavelli\minecolonytax\commands\RandomEventsCommand.java:119:        IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.serverLevel().dimension());
src/main/java\net\machiavelli\minecolonytax\commands\RandomEventsCommand.java:162:        IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, player.serverLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\mobs\AnimalColonyHandler.java:72:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, animal.getEntity().level());
minecolonies/src/main/java\com\minecolonies\core\entity\mobs\AnimalColonyHandler.java:118:            colony = IColonyManager.getInstance().getColonyByWorld(colonyId, animal.getEntity().level());
minecolonies/src/main/java\com\minecolonies\core\entity\mobs\AnimalColonyHandler.java:142:            colony = IColonyManager.getInstance().getColonyView(colonyId, animal.getEntity().level().dimension());
src/main/java\net\machiavelli\minecolonytax\commands\GeneralPermissionsCommands.java:69:                    IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
src/main/java\net\machiavelli\minecolonytax\commands\GeneralPermissionsCommands.java:180:            IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
src/main/java\net\machiavelli\minecolonytax\commands\GeneralPermissionsCommands.java:209:            IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
src/main/java\net\machiavelli\minecolonytax\commands\EntityRaidCommands.java:68:                    IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
src/main/java\net\machiavelli\minecolonytax\commands\EntityRaidCommands.java:167:            for (IColony c : IColonyManager.getInstance().getAllColonies()) {
minecolonies/src/main/java\com\minecolonies\core\colony\requestable\SmeltableOre.java:115:        return IColonyManager.getInstance().getCompatibilityManager().isOre(stack);
minecolonies/src/main/java\com\minecolonies\api\loot\ResearchUnlocked.java:99:                .flatMap(pos -> test(lootContext, IColonyManager.getInstance().getIColony(lootContext.getLevel(), pos)));
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:313:                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:365:        final IColonyView iColonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), player.level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:666:                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:733:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:842:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:861:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(citizenColonyHandler.getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\EntityCitizen.java:1340:                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(getCitizenColonyHandler().getColonyId(), level.dimension());
minecolonies/src/main/java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:224:            @Nullable final IBuilding building = IColonyManager.getInstance().getBuilding(event.getPlayer().level, event.getPos());
minecolonies/src/main/java\com\minecolonies\core\colony\permissions\ColonyPermissionEventHandler.java:254:                IColonyManager.getInstance().deleteColonyByWorld(building.getColony().getID(), false, event.getPlayer().level);
minecolonies/src/main/java\com\minecolonies\api\inventory\container\ContainerCitizenInventory.java:89:            colony = IColonyManager.getInstance().getColonyView(colonyId, inv.player.level().dimension());
minecolonies/src/main/java\com\minecolonies\api\inventory\container\ContainerCitizenInventory.java:93:            colony = IColonyManager.getInstance().getColonyByWorld(colonyId, inv.player.level());
minecolonies/src/main/java\com\minecolonies\api\inventory\container\ContainerBuildingInventory.java:92:                                  final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, inv.player.level());
minecolonies/src/main/java\com\minecolonies\core\colony\crafting\ToolsAnalyzer.java:32:        for (final ItemStack stack : IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems())
minecolonies/src/main/java\com\minecolonies\core\colony\crafting\CustomRecipe.java:781:                    final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(recipeToken);
minecolonies/src/main/java\com\minecolonies\core\colony\crafting\CustomRecipe.java:817:            IRecipeManager recipeManager = IColonyManager.getInstance().getRecipeManager();
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\citizenhandlers\CitizenColonyHandler.java:104:        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, citizen.level);
minecolonies/src/main/java\com\minecolonies\core\entity\citizen\citizenhandlers\CitizenColonyHandler.java:142:            colony = IColonyManager.getInstance().getColonyView(colonyId, citizen.level.dimension());
minecolonies/src/main/java\com\minecolonies\core\colony\CitizenDataView.java:389:            final IColonyView colonyView = IColonyManager.getInstance().getColonyView(colonyId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\colony\managers\ColonyConnectionManager.java:259:                                final IColony connectedColony = IColonyManager.getInstance().getColonyByDimension(targetColonyId, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\colony\managers\ColonyConnectionManager.java:338:        final IColony targetColony = IColonyManager.getInstance().getColonyByDimension(targetColonyId, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\colony\managers\ColonyConnectionManager.java:472:            final IColony connectedColony = IColonyManager.getInstance().getColonyByDimension(colonyEntry.id, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\colony\managers\ColonyConnectionManager.java:558:            final IColony connectedColony = IColonyManager.getInstance().getColonyByDimension(connectedColonyData.id, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\colony\managers\ColonyConnectionManager.java:704:        final IColony originColony = IColonyManager.getInstance().getColonyByDimension(originColonyId, colony.getDimension());
minecolonies/src/main/java\com\minecolonies\core\colony\IColonyManagerCapability.java:151:                IColonyManager.getInstance().write(managerCompound);
minecolonies/src/main/java\com\minecolonies\core\colony\IColonyManagerCapability.java:161:            IColonyManager.getInstance().setCapLoaded();
minecolonies/src/main/java\com\minecolonies\core\colony\IColonyManagerCapability.java:211:                    IColonyManager.getInstance().read(compound.getCompound(TAG_COLONY_MANAGER));
minecolonies/src/main/java\com\minecolonies\api\blocks\decorative\AbstractColonyFlagBanner.java:64:            IColony colony = IColonyManager.getInstance().getIColony(worldIn, pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\decorative\AbstractColonyFlagBanner.java:69:                colony = IColonyManager.getInstance().getIColonyByOwner(worldIn, (Player) placer);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractColonyBlock.java:107:        final IBuilding building = IColonyManager.getInstance().getBuilding(player.level(), pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractColonyBlock.java:192:            @Nullable final IBuildingView building = IColonyManager.getInstance().getBuildingView(worldIn.dimension(), pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractColonyBlock.java:205:                    IColonyManager.getInstance().openReactivationWindow(pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractColonyBlock.java:269:            @Nullable final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, hut.getPosition());
minecolonies/src/main/java\com\minecolonies\api\entity\mobs\AbstractEntityMinecoloniesRaider.java:253:                setColony(IColonyManager.getInstance().getColonyByWorld(colonyId, level()));
minecolonies/src/main/java\com\minecolonies\api\entity\mobs\AbstractEntityMinecoloniesRaider.java:362:            final IColony tempColony = IColonyManager.getInstance().getColonyByWorld(owningColonyId, level);
minecolonies/src/main/java\com\minecolonies\core\colony\interactionhandling\RequestBasedInteraction.java:136:        final IColony colony = IColonyManager.getInstance().getColonyView(dataView.getColonyId(), Minecraft.getInstance().player.level.dimension());
minecolonies/src/main/java\com\minecolonies\core\colony\interactionhandling\RequestBasedInteraction.java:158:            final IColony colony = IColonyManager.getInstance().getColonyView(data.getColonyId(), player.level.dimension());
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractBlockHut.java:135:        final IColonyView colonyView = IColonyManager.getInstance().getClosestColonyView(level, pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractBlockHut.java:251:        @Nullable final IBuilding building = IColonyManager.getInstance().getBuilding(world, pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractBlockHut.java:266:                final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractBlockHut.java:307:        final IColony colony = IColonyManager.getInstance().getIColony(player.level(), pos);
minecolonies/src/main/java\com\minecolonies\api\blocks\AbstractBlockHut.java:317:            if (IColonyManager.getInstance().getIColonyByOwner(player.level(), player) == null)
minecolonies/src/main/java\com\minecolonies\core\client\gui\WindowRequestDetail.java:128:        final IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, Minecraft.getInstance().level.dimension());
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\util\Tree.java:273:                    IColonyManager.getInstance().getCompatibilityManager().connectLeafToSapling(block, stack);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\util\Tree.java:280:            return IColonyManager.getInstance().getCompatibilityManager().getSaplingForLeaf(block);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\util\Tree.java:506:        final ItemStack sap = IColonyManager.getInstance().getCompatibilityManager().getSaplingForLeaf(leaf.getBlock());
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\builder\EntityAIStructureBuilder.java:192:        return mineBlock(blockToMine, safeStand, true, !IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(blockToMine)), null);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\service\EntityAIWorkEnchanter.java:181:            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\client\gui\WindowDecorationController.java:61:        final IColonyView view = IColonyManager.getInstance().getClosestColonyView(world, controller.getBlockPos());
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\util\BuildingStructureHandler.java:121:        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(getWorld(), getWorldPos());
minecolonies/src/main/java\com\minecolonies\core\client\gui\WindowBuildDecoration.java:159:        IColonyView colony = (IColonyView) IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIQuarrier.java:651:        return IColonyManager.getInstance().getCompatibilityManager().isOre(worldMetadata);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIQuarrier.java:695:        if (IColonyManager.getInstance().getCompatibilityManager().isOre(blockToMine))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIWorkNether.java:86:    final List<ItemStack> netherEdible = IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIWorkLumberjack.java:245:            < IColonyManager.getInstance().getCompatibilityManager().getNumberOfSaplings())
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIWorkLumberjack.java:628:                    final ItemStack fungus = IColonyManager.getInstance().getCompatibilityManager().getSaplingForLeaf(wartBlock);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:266:        if (!IColonyManager.getInstance().getCompatibilityManager().isOre(blockState))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:283:        if (IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(blockToMine)))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:295:        return IColonyManager.getInstance().getCompatibilityManager().isOre(curBlockState);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:303:            if (IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(offset)))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:764:              || IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(curBlock)))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:985:        return IColonyManager.getInstance().getCompatibilityManager().isOre(worldMetadata);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:993:        if (IColonyManager.getInstance().getCompatibilityManager().isLuckyBlock(blockToMine.getBlock()))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\production\EntityAIStructureMiner.java:1017:        if (IColonyManager.getInstance().getCompatibilityManager().isOre(blockToMine))
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingBeekeeper.java:255:            return IColonyManager.getInstance().getCompatibilityManager().getImmutableFlowers().stream()
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingBaker.java:109:                final IRecipeStorage recipe = IColonyManager.getInstance().getRecipeManager().getRecipe(craftingRequest.getRecipeID());
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingBaker.java:174:                final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\guard\AbstractEntityAIGuard.java:812:        if (IColonyManager.getInstance().getCompatibilityManager().getAllMonsters().contains(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())) && !user.getCitizenData()
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\crafting\EntityAIWorkSmelter.java:188:        if (ItemStackUtils.isEmpty(stack) || !ItemStackUtils.IS_SMELTABLE.and(itemStack -> IColonyManager.getInstance().getCompatibilityManager().isOre(stack)).test(stack))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\crafting\EntityAIWorkSmelter.java:192:        if (IColonyManager.getInstance().getCompatibilityManager().isBreakableOre(stack))
minecolonies/src/main/java\com\minecolonies\core\entity\ai\workers\crafting\EntityAIWorkSmelter.java:214:                final List<ItemStack> requests = IColonyManager.getInstance().getCompatibilityManager().getSmeltableOres().stream()
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingSmeltery.java:74:        keepX.put(IColonyManager.getInstance().getCompatibilityManager()::isOre, new Tuple<>(Integer.MAX_VALUE, true));
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingSmeltery.java:124:            final ICompatibilityManager compatibility = IColonyManager.getInstance().getCompatibilityManager();
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingSmeltery.java:232:                IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(tempRecipe);
minecolonies/src/main/java\com\minecolonies\api\colony\IColonyTagCapability.java:130:            final IColony colony = IColonyManager.getInstance().getColonyByDimension(id, chunk.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\api\colony\IColonyTagCapability.java:137:            if (owningColony == NO_COLONY_ID || IColonyManager.getInstance().getColonyByDimension(owningColony, chunk.getLevel().dimension()) == null)
minecolonies/src/main/java\com\minecolonies\api\colony\IColonyTagCapability.java:189:                final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, chunk.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\api\colony\IColonyTagCapability.java:199:                final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, chunk.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\api\colony\IColonyTagCapability.java:253:                            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyEntry.getKey(), chunk.getLevel().dimension());
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingLumberjack.java:131:        for (final ItemStorage sapling : IColonyManager.getInstance().getCompatibilityManager().getCopyOfSaplings())
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingKitchen.java:92:                final IRecipeStorage recipe = IColonyManager.getInstance().getRecipeManager().getRecipe(craftingRequest.getRecipeID());
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingFlorist.java:174:                return IColonyManager.getInstance().getCompatibilityManager().getCopyOfPlantables().stream()
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingFlorist.java:178:                return IColonyManager.getInstance().getCompatibilityManager().getCopyOfPlantables().stream()
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingFlorist.java:185:                return IColonyManager.getInstance().getCompatibilityManager().getCopyOfPlantables();
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingFarmer.java:382:            for (final ItemStack stack : IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems())
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingFarmer.java:459:            for (final ItemStack stack : IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems())
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingDyer.java:115:            for (final ItemStack item : IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems())
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingDyer.java:169:                recipe = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingDyer.java:182:            final IRecipeStorage recipe = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingDyer.java:207:                    final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\workerbuildings\BuildingDyer.java:246:            return IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(tempRecipe);
minecolonies/src/main/java\com\minecolonies\core\client\gui\modules\building\EntityListModuleWindow.java:71:        groupedItemList = new ArrayList<>(IColonyManager.getInstance().getCompatibilityManager().getAllMonsters());
minecolonies/src/main/java\com\minecolonies\core\client\gui\modules\building\DOCraftingWindow.java:99:                IColonyManager.getInstance()
minecolonies/src/main/java\com\minecolonies\core\client\gui\modules\building\RestaurantMenuModuleWindow.java:92:        groupedItemList = new ArrayList<>(IColonyManager.getInstance().getCompatibilityManager().getEdibles(moduleView.getBuildingView().getBuildingLevel() - 1));
minecolonies/src/main/java\com\minecolonies\core\client\gui\containers\WindowBrewingstandCrafting.java:82:        this.building = (AbstractBuildingView) IColonyManager.getInstance().getBuildingView(playerInventory.player.level.dimension(), container.getPos());
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\AbstractBuildingGuards.java:560:        final IColony colonyAtPosition = IColonyManager.getInstance().getColonyByPosFromDim(rallyLocation.getDimension(), rallyLocation.getInDimensionLocation());
minecolonies/src/main/java\com\minecolonies\core\client\gui\containers\WindowFurnaceCrafting.java:101:        this.building = (AbstractBuildingView) IColonyManager.getInstance().getBuildingView(playerInventory.player.level.dimension(), container.getPos());
minecolonies/src/main/java\com\minecolonies\core\client\gui\containers\WindowCrafting.java:139:        this.building = (AbstractBuildingView) IColonyManager.getInstance().getBuildingView(playerInventory.player.level.dimension(), container.getPos());
minecolonies/src/main/java\com\minecolonies\core\client\gui\containers\WindowField.java:158:            IColonyManager.getInstance().getCompatibilityManager().getListOfMatchingItems(stack -> stack.is(Tags.Items.SEEDS)
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:245:                IColonyManager.getInstance().getRecipeManager().registerUse(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:291:                final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:400:                    final IRecipeStorage recipeStorage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(request.getRequest().getRecipeID());
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:462:        final IRecipeStorage recipeStorage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:488:        final IRecipeManager recipeManager = IColonyManager.getInstance().getRecipeManager();
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:632:                final IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:670:            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:699:            IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(foundRecipe.getClassicForMultiOutput(stackPredicate));
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:700:            foundRecipe = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:719:        final IRecipeStorage storageIn = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:727:            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipe(localToken);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:765:            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:845:            final IRecipeStorage recipeStorage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\AbstractCraftingBuildingModule.java:932:            final IRecipeStorage recipeStorage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(key);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\BuildingModules.java:75:        (buildingView) -> IColonyManager.getInstance().getCompatibilityManager().getFuel()));
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\BuildingModules.java:80:          (buildingView) -> IColonyManager.getInstance().getCompatibilityManager().getCompostInputs()));
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\BuildingModules.java:91:          (buildingView) -> IColonyManager.getInstance().getCompatibilityManager().getCopyOfSaplings()));
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\BuildingModules.java:96:          (buildingView) -> IColonyManager.getInstance().getCompatibilityManager().getSmeltableOres()));
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\BuildingModules.java:101:          (buildingView) -> IColonyManager.getInstance().getCompatibilityManager().getImmutableFlowers()));
minecolonies/src/main/java\com\minecolonies\api\crafting\GenericRecipe.java:367:        final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(recipeToken);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\settings\BlockSetting.java:128:                IColonyManager.getInstance().getCompatibilityManager().getListOfMatchingItems(stack -> {
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\settings\RecipeSetting.java:73:                return IColonyManager.getInstance().getRecipeManager().getRecipe(selectedRecipe);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\settings\RecipeSetting.java:78:        return IColonyManager.getInstance().getRecipeManager().getRecipe(selectedRecipe);
minecolonies/src/main/java\com\minecolonies\core\colony\buildings\modules\settings\RecipeSetting.java:104:            settings.add(IColonyManager.getInstance().getRecipeManager().getRecipe(token).getPrimaryOutput());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS: REWORK

- Major bypass: `sourceOwnRaid != null` returns before checking other raids. A player who starts any besiege can help a colony-mate’s besiege freely.
- `isDefenderSideTarget` misses `raid.spawnedMercenaries`; those are defender-side entities and count toward besiege completion, so allies can help kill mercs.
- Built-in Neutral/Hostile players are mostly handled correctly because `getRank(UUID)` falls back to Neutral, but custom hostile ranks can false-positive; use `Rank.isHostile()`.
- Hot path is expensive: every player damage during any active raid can scan all active raids and all colonies before even proving the target is protected.
- Projectile owner damage should be covered by `DamageSource#getEntity()`, but pets/traps/environmental damage from an attacker ally can bypass because only `ServerPlayer` sources are handled.

Fix suggestions:
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:53` remove the global own-raid return; keep only the per-raid `raid.besiegingPlayerUUID.equals(sourceUUID)` skip.
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:101` pass `raid` into `isDefenderSideTarget` and return true for `raid.spawnedMercenaries.contains(target)`.
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:91` switch hostile checks to `rank.isHostile()` and move/collapse target-side checks before `areColonyMates()` where possible.
tokens used
97.992
STATUS: REWORK

- Major bypass: `sourceOwnRaid != null` returns before checking other raids. A player who starts any besiege can help a colony-mate’s besiege freely.
- `isDefenderSideTarget` misses `raid.spawnedMercenaries`; those are defender-side entities and count toward besiege completion, so allies can help kill mercs.
- Built-in Neutral/Hostile players are mostly handled correctly because `getRank(UUID)` falls back to Neutral, but custom hostile ranks can false-positive; use `Rank.isHostile()`.
- Hot path is expensive: every player damage during any active raid can scan all active raids and all colonies before even proving the target is protected.
- Projectile owner damage should be covered by `DamageSource#getEntity()`, but pets/traps/environmental damage from an attacker ally can bypass because only `ServerPlayer` sources are handled.

Fix suggestions:
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:53` remove the global own-raid return; keep only the per-raid `raid.besiegingPlayerUUID.equals(sourceUUID)` skip.
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:101` pass `raid` into `isDefenderSideTarget` and return true for `raid.spawnedMercenaries.contains(target)`.
- `src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java:91` switch hostile checks to `rank.isHostile()` and move/collapse target-side checks before `areColonyMates()` where possible.
