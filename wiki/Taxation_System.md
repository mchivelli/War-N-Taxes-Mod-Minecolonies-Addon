# Taxation & Economy System

War 'N Taxes introduces a dynamic economy deeply integrated with Minecolonies' existing features. Every building, citizen, and action directly impacts your colony's financial stability.

To access any of the features below, officers use the base command: `/wnt`

## 1. Passive Tax Generation
Your colony acts as an economic engine, generating revenue passively over a set interval.
- **Generation Interval:** By default, taxes are calculated and deposited into the colony's virtual Treasury every **60 minutes** (`TaxIntervalMinutes`).
- **Revenue Sources:** Your income is not arbitrary. It is mathematically calculated based on the specific **levels and types of buildings** you have constructed. 
  - Every specialized building (Town Hall, Guard Towers, Smeltery, Lumberjack, Combat Academy, etc.) provides a base flat tax amount, plus an upgrade multiplier for each level. Building tall (level 5 buildings) yields exponentially better tax revenue.
  - E.g., The Town Hall is your largest native tax generator.

## 2. The Happiness Multiplier
If enabled (`EnableHappinessTaxModifier`), your Colony's **Citizen Happiness** acts as the ultimate multiplier on your gross tax revenue. *Keeping citizens fed, rested, and safe is no longer just for their health; it is a financial necessity.*
- **Unhappy Citizens:** If citizen happiness drops low, a massive negative multiplier applies to your generation. By default (`HappinessTaxMultiplierMin`), this can crush your revenue down to a mere **10% efficiency** (0.1 multiplier).
- **Happy Citizens:** Alternatively, maintaining perfect happiness rewards you. By default (`HappinessTaxMultiplierMax`), maximum happiness doubles your gross revenue, applying a **200% efficiency** (2.0 multiplier) to your earning cycles.

## 3. Tax Debt, Freezes & Collection
- **The Treasury Cap:** Taxes do not magically appear in your physical inventory; they sit in your colony **Treasury**. By default, a treasury can hold up to **50,000,000** (`MaxTaxRevenue`). If you hit this cap, your colony stops generating revenue entirely.
- **Claiming:** Colony Owners or Officers must manually execute `/wnt claimtax` to extract the virtual treasury funds into their personal server economy balances (e.g., SDMShop) or as physical currency items.
- **Going into Debt:** If your colony is heavily raided, sabotaged, or loses wars, you can sink into negative balances. 
  - If `DebtLimit` is configured (default: **500,000**), your debt will bottom out at this value. You must type `/wnt taxdebt pay <amount> <colony>` to bail your colony out of debt before it can start generating positive numbers again.
- **Inactivity Pauses:** To protect server economies from "ghost inflation", `EnableColonyInactivityTaxPause` forces a complete tax freeze if no Owner or Officer physically visits the colony chunks for **72 hours** (`ColonyInactivityHoursThreshold`). 

## 4. Tax Policies
Colony leaders can actively control output versus stability by enforcing specific **Tax Policies**. 
- **Low Policy:** Designed for recovery. Drastically boosts citizen happiness regeneration (**+20% faster**) at the heavy cost of lowered tax generation (**-25% gross revenue**).
- **High Policy:** Gouges citizens for massive short-term gain. High tax output (**+25% gross revenue**), but steadily crushes citizen happiness (**-15% slower regeneration**).
- **War Economy:** The ultimate desperate lever. Yields the highest potential revenue (**+50%**), used to fund active defensive wars, but severely angers citizens (**-25% happiness penalty**).

## 5. Factions, Shared Pools & Trade Routes
Players can forge official alliances via the `/wnt faction` mechanics.
- **Shared Tax Pools:** Allied member colonies can establish an automated shared fund. When tax is generated, a default percentage (often 10-20%) is instantly diverted into the central Faction pool up to the `MaxPoolBalance`.
- **Trade Routes:** Factions can establish active trade routes between allied colonies. 
  - Routes generate passive income based on the chunk distance between the two colonies. 
  - Larger distances yield significantly higher chunk-based payouts, but they also introduce a massive base `TradeRouteMaintenanceCost` per tax cycle.

---

### Command Reference
* `/wnt checktax` : View your current generation speed, active multipliers, and total treasury.
* `/wnt claimtax [amount]` : Extract money from the colony into your pocket.
* `/wnt taxdebt pay <amount>` : Pay off accumulated negative balances.
* `/wnt taxreport` : Generate an in-game written book detailing your colony's financial history.
