# Taxation & Economy System

War 'N Taxes introduces a dynamic economy deeply integrated with Minecolonies' existing features. Every building, citizen, and action directly impacts your colony's financial stability.

To access any of the features below, officers use the base command: `/wnt`

> **Screenshot placeholder:** *The colony management GUI open on the tax overview — showing the colony list on the left with balances, and the main panel with the Claim button and current treasury total*

## 1. Passive Tax Generation
Your colony acts as an economic engine, generating revenue passively over a set interval.
- **Generation Interval:** By default, taxes are calculated and deposited into the colony's virtual Treasury every **60 minutes** (`TaxIntervalMinutes`).
- **Revenue Sources:** Your income is calculated from the specific levels and types of buildings you have constructed. Every specialized building (Town Hall, Guard Towers, Smeltery, Lumberjack, Combat Academy, etc.) provides a base tax amount plus an upgrade bonus per level. Building to higher levels yields meaningfully better revenue.
- **Guard Tower Scaling:** Guard towers provide a bonus to overall tax output. The bonus starts from your **first guard tower** and scales linearly at **+8% per tower**, up to a hard cap of **80%**. Every tower you build contributes — there is no minimum threshold to clear before the boost kicks in.
- **Tax Floor:** No matter how many penalties are stacked against your colony (war exhaustion, raid penalties, labor strikes, etc.), tax generation will never fall below **30%** of its base value. This prevents permanent economic collapse from compounding debts.

## 2. The Happiness Multiplier
If enabled (`EnableHappinessTaxModifier`), your Colony's **real Minecolonies Citizen Happiness** acts as the ultimate multiplier on your gross tax revenue. The mod reads the actual per-citizen happiness scores from Minecolonies (housing, food, security, health, employment, etc.), averages them across all adult citizens, and applies any active Tax Policy or Random Event modifiers on top.

*Keeping citizens fed, housed, employed, and safe is no longer just for their health — it is a financial necessity.*

- **How it works:** A colony happiness of **5.0** (neutral) produces a **1.0x** tax multiplier — no bonus, no penalty. Below 5 penalizes revenue; above 5 rewards it.
- **Unhappy Citizens:** If citizen happiness drops low, a negative multiplier applies to your generation. By default (`HappinessTaxMultiplierMin`), this can reduce your revenue down to **50% efficiency** (0.5x multiplier) at happiness 0.
- **Happy Citizens:** Maintaining perfect happiness rewards you. By default (`HappinessTaxMultiplierMax`), maximum happiness boosts your gross revenue to **150% efficiency** (1.5x multiplier) at happiness 10.
- **What affects happiness:** Everything in Minecolonies matters — food diversity, housing, employment, guard-to-citizen ratio, health, sleep, education, and more. On top of that, your active Tax Policy and any Random Events apply additive modifiers to the colony average.

## 3. Tax Debt, Freezes & Collection
- **The Treasury Cap:** Taxes do not magically appear in your physical inventory; they sit in your colony **Treasury**. By default, a treasury can hold up to **10,000 coins** (`MaxTaxRevenue`). If you hit this cap, your colony stops generating revenue entirely.
- **Claiming:** Colony Owners or Officers must manually execute `/wnt claimtax` to extract the virtual treasury funds into their personal server economy balances (e.g., SDMShop) or as physical currency items.
- **Going into Debt:** If your colony is heavily raided, sabotaged, or loses wars, you can sink into negative balances.
  - The debt limit is set to **2,000 coins** by default (`DebtLimit`). Your debt bottoms out at this value and cannot grow further. Use `/wnt taxdebt pay <amount> <colony>` to clear the debt before the colony resumes generating positive revenue.
- **Inactivity Pauses:** To protect server economies from "ghost inflation", `EnableColonyInactivityTaxPause` forces a complete tax freeze if no Owner or Officer physically visits the colony chunks for **72 hours** (`ColonyInactivityHoursThreshold`). 

## 4. Tax Policies
Colony leaders can actively control output versus stability by enforcing specific **Tax Policies**. Policy happiness modifiers are **additive** to the colony's average happiness on a 0–10 scale — the effect is fixed and predictable.
- **Normal Policy:** No modifiers. Standard tax generation, standard happiness.
- **Low Policy:** Designed for recovery. Adds **+0.20 happiness** to the colony average, at the cost of **-25% gross revenue**.
- **High Policy:** Gouges citizens for massive short-term gain. **+25% gross revenue**, but subtracts **0.15 happiness** from the colony average.
- **War Economy:** The ultimate desperate lever. **+50% revenue**, used to fund active wars, but subtracts **0.25 happiness** from the colony average.

## 5. Factions, Shared Pools & Trade Routes
Players can forge official alliances via the `/wnt faction` mechanics.
- **Shared Tax Pools:** Allied member colonies can establish an automated shared fund. When tax is generated, a default percentage (often 10-20%) is instantly diverted into the central Faction pool up to the `MaxPoolBalance`.
- **Trade Routes:** Factions can establish active trade routes between allied colonies.
  - Routes generate passive income based on the chunk distance between the two colonies at **1 coin per chunk** (default).
  - Maximum route distance is **100 chunks**; a maximum of **2 routes** can be active at once per colony.
  - Each route has a maintenance cost per tax cycle. At long distances the maintenance will exceed the income — plan routes carefully.
- **Investments:** Colonies can spend tax on permanent or temporary bonuses (infrastructure, guard training, festivals). Each successive purchase of the same investment is less effective than the last due to diminishing returns, and most investment types have a cap on how many stacks can be purchased.

---

### Command Reference
* `/wnt checktax` : View your current generation speed, active multipliers, and total treasury.
* `/wnt claimtax [amount]` : Extract money from the colony into your pocket.
* `/wnt taxdebt pay <amount>` : Pay off accumulated negative balances.
* `/wnt taxreport` : Generate an in-game written book detailing your colony's financial history.
