![WarNTaxes](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b01_hero.jpg)

**WarNTaxes** turns your MineColonies world into a living strategy game: a full economy of **taxes and treasuries**, **wars and sieges** between colonies, **espionage**, **occupation**, **vassal tribute**, and **colony investments** — built for SMP servers and modpacks.

- ◆ **Dynamic Taxation** — configurable rates for every building, with maintenance, caps and happiness modifiers
- ◆ **Wars & Multiplayer Sieges** — besiege rival colonies together and split the spoils
- ◆ **Espionage** — deploy spies that travel, infiltrate, gather tiered intel, and flee when caught
- ◆ **Occupation, Vassalization & Tribute** — dominate rivals and siphon their income
- ◆ **Colony Investments** — spend your treasury on militia, defense, spies and more
- ◆ **PvP Arena Duels & Team Battles** — fair fights that never touch progression
- ◆ **In-Game Codex** (Patchouli) + multi-language support

> » Actively developed. Found a bug or have an idea? Tell us on [Discord](https://discord.gg/BBAFqg9yY8)!

---

![Taxation](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b02_taxation.jpg)

Every colony generates tax from its buildings — the economic engine behind everything else.

| Feature | Description |
|---------|-------------|
| **Per-Building Rates** | Set base and per-level tax for every MineColonies building |
| **Maintenance Costs** | Military buildings cost upkeep, deducted from revenue |
| **Revenue Cap** | Tax stops accumulating at a configurable maximum |
| **Happiness Modifier** | Happier colonies generate more tax |
| **Guard Tower Boost** | More guards generate higher revenue |
| **Tax Freeze** | Halt collection as a war penalty |

**Currency:** seamless **SDMShop** integration, a built-in colony balance, or your own custom currency item (`SDMShopConversion = false`).

---

![War System](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b03_war.jpg)

Declare war on rival colonies and fight for dominance.

| Phase | Description |
|-------|-------------|
| **Declaration** | `/wnt wagewar "<colony>"` opens a join phase |
| **Preparation** | Allies and officers join with `/wnt joinwar` |
| **Active Combat** | Configurable duration, lives per player, 1:1 team balancing |
| **Resolution** | Total Victory, Strategic Victory, Stalemate, or **Vassalization** |

**Colony tiers:** your **first** colony is a **Primary** (capital) — it can be tax-occupied but its ownership can't be permanently taken unless the server enables `EnablePrimaryColonyTransfer`. Additional **Secondary** colonies can be captured outright.

**Vassalize-only "huge money" grab:** when colony transfer is off, winning a war **vassalizes** the loser and takes a one-time cut of their **colony treasury** (`WarVassalizationTreasuryGrabPercent`) and **player wallet** (`WarVassalizationPlayerBalanceGrabPercent`).

---

![Siege SMP](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b04_besiege.jpg)

Lay siege to a colony with `/wnt besiege` — designed for real SMP play:

- ► **Band together** — several players can besiege the same colony at once and **share the spoils** (`BesiegeShareSpoils`); a minimum-attacker gate (`BesiegeMinAttackers`) can force "not solo" raids
- ► **Online & fair** — besiegers must stay online (`BesiegeRequireOnline` + an offline grace period); one player logging off doesn't collapse the siege for the rest
- ► **Owner keeps access while vassalized** — by default the original owner still uses their colony (only tax tribute is siphoned); full lockout is opt-in via `VassalLockOutFormerOwner`
- ► **Configurable looting** — `BesiegeAllowChestAccess` decides whether attackers can open containers mid-siege
- ► **Persistent siege damage** — war explosions are recorded and **fully restored when the war ends** (blocks and chest contents intact), and the ledger survives server restarts

### ◆ Experimental Siege Objectives
Gated behind `EnableExperimentalSiegeObjectives`:
- ● **Plant the Banner** — hold a planted siege banner inside the town-hall borders to capture
- ● **Demolish the Town Hall** — destroy it with explosives; each hit makes the attacker glow and pings the defenders

---

![Espionage](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b05_espionage.jpg)

Deploy spies to learn your rivals' secrets before you strike.

| Stage | What happens |
|-------|--------------|
| **Travel** | Spies physically travel to the target — no instant intel |
| **Infiltrate** | Intel accumulates over time in tiers (early / mid / late) |
| **Flee** | When detected, the spy tries to escape the colony border |
| **Escape / Recall** | Intel is preserved; a killed spy loses everything |

Optional **JourneyMap** integration plots spy positions on your map.

---

![Raids & Militia](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b06_raids.jpg)

Faster than war, raids let you **steal accumulated tax** from a colony.

- ► **Militia defense** — citizens take up arms to defend
- ► **Mercenary spawns** — extra defenders when the garrison is small
- ► **Militia investment** — buy extra defenders that scale with guard count (wars, sieges and raids); they extend a fight but never count as a victory objective
- ► **Configurable** — raid length, cooldowns, and required buildings

---

![Occupation & Vassalization](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b07_occupation.jpg)

Winning isn't just destruction — it's **domination**.

- ► **Occupation** — after a war, collect the loser's taxes for a set period; they wage a reclamation war to win it back
- ► **Vassal tribute** — bind colonies into a tribute network that pays you every cycle
- ► **Commands** — `/wnt vassalize <percent> "<colony>"`, `/wnt vassalaccept <id>`, `/wnt vassaldecline <id>`, `/wnt revoke <player>`, `/wnt vassals`
- ► **War vassalization** auto-expires after a configurable duration with notifications

---

## ◆ Colony Investments

Reinvest your treasury into lasting upgrades from the in-game **Investments** tab:

| Investment | Effect |
|------------|--------|
| **Militia** | More defenders in conflicts |
| **Defense** | Tougher colony defense |
| **Raid Force** | Stronger raids |
| **Spy Capacity / Speed / Evasion** | Better espionage |

---

![PvP Arena](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b08_pvp.jpg)

Host **fair duels and team battles** that don't affect progression.

| Feature | Description |
|---------|-------------|
| **Arena Setup** | `/pvparena p1` and `/pvparena p2` |
| **Duels** | `/pvp` with clickable accept / decline |
| **Team Battles** | Organized multi-player combat |
| **Spectator Mode** | `/pvp spectate [player]` |
| **Inventory Safety** | Gear saved during duels, restored after |

---

## ◆ Random Events

Tax cycles can trigger **random events** — bandit raids, festivals, shortages and more — driven by your colony's composition. Every event type is individually toggleable, and a per-colony **event history** is viewable in the colony book.

---

## ◆ The War 'N Taxes Codex

A complete in-game guidebook (Patchouli) that teaches players everything:

| Feature | Description |
|---------|-------------|
| ▸ **Content Categories** | Getting Started, Tax, Raids, Wars, Diplomacy, PvP, Colony Management, Commands, Configuration |
| ▸ **Auto Distribution** | Players receive the codex on first join |
| ▸ **Clickable Commands** | Run commands straight from the book |
| ▸ **Multi-Language** | German, Russian, French, Spanish |
| ▸ **Advancements** | Track claiming tax, raiding, declaring war |

---

## ◆ Essential Commands

| Command | Description |
|---------|-------------|
| `/wnt help` | Show all commands |
| `/wnt claimtax` | Collect accumulated tax |
| `/wnt taxinfo` | View tax breakdown |
| `/wnt raid "<colony>"` | Start a raid |
| `/wnt wagewar "<colony>"` | Declare war |
| `/wnt besiege "<colony>"` | Begin / join a siege |
| `/wnt vassalize <percent> "<colony>"` | Offer vassalization |
| `/wnt peace` | Propose peace terms |

Admin: `/wnt endwar`, `/wnt forceabandon`, `/wnt debug …`

---

## ◆ Requirements & Compatibility

| Mod | Status |
|-----|--------|
| **MineColonies** | Required |
| **SDMShop** | Recommended (currency) |
| **FTB Teams** | Optional (team integration) |
| **Recruits** | Optional |
| **Patchouli** | Recommended (Codex) |
| **JourneyMap** | Optional (spy map markers) |

Available for **Forge 1.20.1** and **NeoForge 1.21.1**.

---

![Join the War](https://raw.githubusercontent.com/mchivelli/War-N-Taxes-Mod-Minecolonies-Addon/1.20.1/curseforge/images/b09_community.jpg)

## ◆ Community & Support

| Resource | Link |
|----------|------|
| ▸ **Modpack** | [EpicWars: Colony vs Colony SMP](https://www.curseforge.com/minecraft/modpacks/epicwars-colony-vs-colony-smp-modpack) |
| ▸ **Website** | [warbornrealms.com](https://warbornrealms.com/) |
| ▸ **Discord** | [Join our server](https://discord.gg/BBAFqg9yY8) |
| ▸ **Server IP** | `play.warbornrealms.com` |

---

### Build your empire. Crush your rivals. Rule the server.

With **WarNTaxes**, your colonies become economies where **taxes fund your ambitions**, **sieges decide dominance**, and **vassals pay tribute to the strong**.

*Your feedback shapes this mod — report bugs and suggest features on Discord or in the comments!*
