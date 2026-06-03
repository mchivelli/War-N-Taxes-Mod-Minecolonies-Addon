# Random Events

Running a colony shouldn't just be an AFK math equation. To prevent the economy from stagnating and to keep colony rulers on their toes, War 'N Taxes implements a **Random Event System**.

If `EnableRandomEvents` is true, the system checks each colony on every tax cycle and rolls for a chance to trigger one of 16 events based on the colony's current conditions. Each event applies a **tax multiplier** and an **additive happiness modifier** (on the 0-10 scale) for a set number of tax cycles.

---

## 1. Positive Events

Rulers are occasionally blessed by unforeseen fortune. These events reward investment in infrastructure, high happiness, and stable policy.

*   **Merchant Caravan** (`ENABLE_MERCHANT_CARAVAN`)
    *   *Effect:* A trading company arrives at your borders. **+15% tax** and **+0.3 happiness** for 2 cycles.
    *   *Conditions:* 3 or more restaurants or taverns. Cannot trigger during war.

*   **Bountiful Harvest** (`ENABLE_BOUNTIFUL_HARVEST`)
    *   *Effect:* Exceptional crop yields bless the colony. **+20% tax** and **+0.4 happiness** for 1 cycle.
    *   *Conditions:* 20 or more citizens.

*   **Cultural Festival** (`ENABLE_CULTURAL_FESTIVAL`)
    *   *Effect:* Citizens throw a spontaneous celebration. Slight tax cost (**-5%**) but a major **+0.5 happiness** boost for 1 cycle.
    *   *Conditions:* Colony happiness above 6.0.

*   **Successful Recruitment** (`ENABLE_SUCCESSFUL_RECRUITMENT`)
    *   *Effect:* Skilled workers join the colony. **+10% tax** and **+0.2 happiness** for 3 cycles.
    *   *Conditions:* Happiness above 5.5 and more than 15 citizens.

*   **Royal Feast** (`ENABLE_ROYAL_FEAST`)
    *   *Effect:* A grand feast is held for all citizens. **+10% tax** and **+0.6 happiness** for 1 cycle. All citizens are set to maximum saturation.
    *   *Conditions:* Happiness above 7.0. Cannot trigger during war.

*   **Wandering Trader Offer** (`ENABLE_WANDERING_TRADER_OFFER`)
    *   *Effect:* A wandering trader buys colony goods at premium prices. **+20% tax** and **+0.1 happiness** for 1 cycle.
    *   *Conditions:* Colony has more than 500 stored taxes. Cannot trigger during war.

*   **Neighboring Alliance** (`ENABLE_NEIGHBORING_ALLIANCE`)
    *   *Effect:* A nearby settlement forms a trade pact. **+5% tax** and **+0.3 happiness** for 2 cycles.
    *   *Conditions:* Happiness above 6.0 and more than 30 citizens. Cannot trigger during war.

---

## 2. Negative Events

Prosperity invites disaster. A colony pushed too hard, mired in debt, or neglected in its defenses will attract chaos.

*   **Food Shortage** (`ENABLE_FOOD_SHORTAGE`)
    *   *Effect:* Dwindling supplies reduce morale. **-15% tax** and **-0.3 happiness** for 2 cycles.
    *   *Conditions:* Happiness below 5.0.

*   **Disease Outbreak** (`ENABLE_DISEASE_OUTBREAK`)
    *   *Effect:* Sickness spreads through the colony. **-20% tax** and **-0.4 happiness** for 3 cycles.
    *   *Conditions:* 40 or more citizens, happiness below 5.0, and no hospital at level 3 or higher.

*   **Bandit Harassment** (`ENABLE_BANDIT_HARASSMENT`)
    *   *Effect:* Bandits harass trade routes and workers. **-10% tax** and **-0.2 happiness** for 2 cycles.
    *   *Conditions:* Fewer than 3 guard towers built.

*   **Corrupt Official** (`ENABLE_CORRUPT_OFFICIAL`)
    *   *Effect:* Embezzlement is discovered in the administration. **-25% tax** and **-0.1 happiness** for 1 cycle.
    *   *Conditions:* Colony is on HIGH or WAR_ECONOMY tax policy.

*   **Guard Desertion** (`ENABLE_GUARD_DESERTION`)
    *   *Effect:* Guards abandon their posts. **-20% tax** and **-0.4 happiness** for 2 cycles.
    *   *Conditions:* Happiness below 3.0 under HIGH or WAR_ECONOMY policy.

*   **Labor Strike** (`ENABLE_LABOR_STRIKE`)
    *   *Effect:* Workers refuse to report to their jobs. **-30% tax** and **-0.5 happiness** for 2 cycles. 30-50% of employed citizens stop working entirely for the event duration.
    *   *Conditions:* Happiness below 3.5, more than 30 citizens, under HIGH or WAR_ECONOMY policy.

*   **Plague Outbreak** (`ENABLE_PLAGUE_OUTBREAK`)
    *   *Effect:* Deadly plague sweeps the colony. **-25% tax** and **-0.6 happiness** for 3 cycles. 20-40% of citizens are directly infected with disease.
    *   *Conditions:* More than 50 citizens and no hospital at level 3 or higher.

*   **Crop Blight** (`ENABLE_CROP_BLIGHT`)
    *   *Effect:* Crops fail across the colony. **-25% tax** and **-0.5 happiness** for 2 cycles. All citizens are reduced to near-starvation saturation.
    *   *Conditions:* 5 or more farms built.

---

## 3. War Events

One event only triggers during active wars.

*   **War Profiteering** (`ENABLE_WAR_PROFITEERING`)
    *   *Effect:* War drives demand for colony goods at a moral cost. **+35% tax** but **-0.5 happiness** for 3 cycles.
    *   *Conditions:* Colony is on WAR_ECONOMY policy and is actively at war.

---

## 4. Deep Integration

Several events go beyond modifiers and directly affect the game world or individual citizens.

| Event | What Happens | Restored on Expiry? |
|---|---|---|
| Labor Strike | 30-50% of employed citizens stop working (STUCK status) | Yes — citizens resume work |
| Plague Outbreak | 20-40% of citizens infected with disease | Yes — all citizens cured |
| Disease Outbreak | 10-15% of citizens infected with disease (lighter variant) | Yes — all citizens cured |
| Royal Feast | All citizens set to maximum saturation | No — normalizes naturally |
| Bountiful Harvest | All citizens set to maximum saturation | No — normalizes naturally |
| Food Shortage | All citizens reduced to low saturation (6/20) | No — normalizes naturally |
| Crop Blight | All citizens reduced to near-starvation saturation (3/20) | No — normalizes naturally |
| Bandit Harassment | 2-4 Pillagers spawn near the colony center | No — mobs despawn or are killed |
| Guard Desertion | The highest-experienced guard citizen permanently leaves the colony | No — desertion is permanent |

---

## 5. Probability, Size, and Context

### Colony Size

Event probability scales with colony population:

| Colony Population | Probability Modifier |
|---|---|
| Under 20 citizens | 0.5x (less likely) |
| 20-49 citizens | 1.0x (normal) |
| 50-99 citizens | 1.2x (more likely) |
| 100+ citizens | 1.3x (most likely) |

### Tax Policy

The colony's active tax policy shifts the odds of positive and negative events:

| Policy | Positive Events | Negative Events |
|---|---|---|
| LOW | +20% more likely | -20% less likely |
| NORMAL | No change | No change |
| HIGH | -10% less likely | +20% more likely |
| WAR_ECONOMY | -20% less likely | +40% more likely |

### War and Raids

*   **During an active raid:** No events fire at all.
*   **During an active war:** All events are 50% less likely, except War Profiteering which fires at normal rate.
*   **Post-war recovery:** While war exhaustion is active, negative events are 50% less likely.

---

## 6. Mitigation and Control

The base probability for each event is scaled by `BaseChanceMultiplier` (configurable, range 0.5-2.0). A global cooldown prevents events from clustering, and each event type has its own cooldown to prevent repeats. `MaxSimultaneousEvents` caps how many events can be active at once per colony. `ProtectNewColoniesHours` provides a grace period before events can target a newly founded colony.

Every event can be individually disabled in `minecolonytax.toml` using the config keys listed above.

As a player, this isn't purely RNG. Investing in infrastructure (hospitals, guard towers, restaurants), maintaining high happiness, and choosing careful tax policies heavily skews the probability tables in your favor. A well-run colony ensures Plagues and Labor Strikes seek out your weaker neighbors instead of your thriving metropolis.

---

## 7. Commands

### Player Commands

| Command | What it does |
|---|---|
| `/wnt events <colonyId>` | Lists all active events, remaining cycles, and effects. Requires officer rank or above. |

### Admin Commands (OP required)

| Command | What it does |
|---|---|
| `/wnt triggerevent <colonyId> <EVENT_TYPE>` | Force-triggers a specific event immediately. Bypasses conditions, probability, and cooldowns. |
| `/wnt events reset <colonyId>` | Resets all event state for a colony: clears new-colony protection, all cooldowns, and active events. Use this to start a fresh testing session without waiting for the 24-hour protection window. |

**All valid event type names** for `/wnt triggerevent`:

```
MERCHANT_CARAVAN  BOUNTIFUL_HARVEST  CULTURAL_FESTIVAL  SUCCESSFUL_RECRUITMENT
FOOD_SHORTAGE  DISEASE_OUTBREAK  BANDIT_HARASSMENT  CORRUPT_OFFICIAL
WANDERING_TRADER_OFFER  NEIGHBORING_ALLIANCE  WAR_PROFITEERING
GUARD_DESERTION  LABOR_STRIKE  PLAGUE_OUTBREAK  ROYAL_FEAST  CROP_BLIGHT
```

**Example testing workflow:**
1. `/wnt events reset 1` — clear protection and cooldowns for colony 1
2. `/wnt triggerevent 1 BANDIT_HARASSMENT` — immediately trigger a bandit raid
3. `/wnt events 1` — verify the event is active and check remaining cycles
