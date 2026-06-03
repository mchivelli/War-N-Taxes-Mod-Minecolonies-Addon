## STEP 9 — Vassals tab data feed (data class only; UI render + network packet deferred)

Adds VassalKind enum (VASSAL/TAX_OCCUPIED/PROVISIONAL) to VassalIncomeData with backward-compat constructor. UI integration in VassalsPage and packet wiring in ColonyDataResponsePacket are NOT done yet — those need to populate the kind field from BesiegeManager.OCCUPATIONS and OccupationManager.ACTIVE_OCCUPATIONS, then VassalsPage needs to render the badge.

```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java b/src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java
index a5a13b4..5813313 100644
--- a/src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java
+++ b/src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java
@@ -1,6 +1,16 @@
 package net.machiavelli.minecolonytax.gui.data;
 
 public class VassalIncomeData {
+
+    /**
+     * The kind of tributary relationship represented by this row. Lets the
+     * Vassals tab render different badges:
+     *  - VASSAL: classic war-vassalage (gold badge)
+     *  - TAX_OCCUPIED: primary colony under besiege tax-occupation (red badge)
+     *  - PROVISIONAL: secondary colony mid-conversion to permanent claim (orange badge)
+     */
+    public enum VassalKind { VASSAL, TAX_OCCUPIED, PROVISIONAL }
+
     private final int vassalColonyId;
     private final String vassalColonyName;
     private final int tributeRate;
@@ -8,12 +18,19 @@ public class VassalIncomeData {
     private final int lastTribute;
     private final long lastPayment;
     private final boolean canClaim;
-    
+    private final VassalKind kind;
+
     // UI state for claim button
     private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
-    
-    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate, 
+
+    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate,
                            int tributeOwed, int lastTribute, long lastPayment, boolean canClaim) {
+        this(vassalColonyId, vassalColonyName, tributeRate, tributeOwed, lastTribute, lastPayment, canClaim, VassalKind.VASSAL);
+    }
+
+    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate,
+                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim,
+                           VassalKind kind) {
         this.vassalColonyId = vassalColonyId;
         this.vassalColonyName = vassalColonyName;
         this.tributeRate = tributeRate;
@@ -21,6 +38,11 @@ public class VassalIncomeData {
         this.lastTribute = lastTribute;
         this.lastPayment = lastPayment;
         this.canClaim = canClaim;
+        this.kind = kind != null ? kind : VassalKind.VASSAL;
+    }
+
+    public VassalKind getKind() {
+        return kind;
     }
     
     public int getVassalColonyId() { return vassalColonyId; }
```
