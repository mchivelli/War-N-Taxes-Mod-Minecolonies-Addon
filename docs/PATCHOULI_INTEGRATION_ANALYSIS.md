# Patchouli Integration Analysis for War-N-Taxes (Minecolonies Addon)

This document is a **self-contained briefing** for an AI agent that should continue implementing Patchouli integration for the *War N Taxes* Minecolonies addon.

It assumes:
- Repository root: `War-N-Taxes-Mod---Minecolonies-Addon`
- Java mod ID: `minecolonytax`
- Main mod package: `net.machiavelli.minecolonytax`
- Minecraft Forge-based mod (judging from structure and `build.gradle`)

Use this file as **primary context** when making design or implementation decisions related to Patchouli books.

---

## 1. High-Level Overview of the Mod

### 1.1. Purpose

War N Taxes is a **Minecolonies addon** that adds:
- **War system** (declarations, join phase, combat, victory conditions, vassalization, etc.)
- **Raid system** (player-triggered and entity-triggered raids with tax impacts)
- **Tax system** (periodic tax generation, maintenance, happiness modifiers, guard tower boosts, inactivity pauses, etc.)
- **Economy integrations** (SDMShop, inventory-based currency, raid/war rewards)
- **PvP arena** system
- **Colony management** (auto-abandonment, claiming abandoned colonies)
- **Permissions & protection layers** (block interaction filters, general colony permissions)
- **Web API** (HTTP server exposing war statistics)

The mod is **feature dense** and heavy on configuration. Commands and systems are non-trivial to understand without documentation.

### 1.2. Evidence from Codebase

Key packages in `src/main/java/net/machiavelli/minecolonytax`:

- **Core**
  - `MineColonyTax.java` – main mod class, registration
  - `TaxConfig.java` – large config file (80k+ bytes) with many options
  - `TaxManager.java` – tax generation and calculations
  - `WarSystem.java` – core war logic

- **Systems**
  - `raid/*` – `RaidManager`, `ActiveRaidData`, `EntityRaidManager`, `GuardResistanceHandler`, etc.
  - `pvp/*` – `PvPManager`, `PvPArenaCommand`, `PvPBattleManager`, etc.
  - `abandon/*` – colony abandonment logic
  - `vassalization/*` – vassal relationships
  - `webapi/*` – `WebAPIServer`, `WarStatsAPIData`, `PlayerDataCache`
  - `permissions/*` – colony permission adjustments
  - `integration/*` – SDMShop, other mods

- **Commands** (`commands/*`)
  - `WntCommands.java` – **unified command system** (163kB)
  - And many specific command helpers: `WarCommands`, `RaidHistoryCommand`, `TaxGUICommand`, `TaxDebtCommand`, etc.

- **Other content**
  - `recipes/` – world datapack-like recipes (mainly for Minecolonies huts)
  - `docs/` – internal technical docs (block interaction filter system, build requirements, officer abandonment, etc.)
  - `CHANGELOG.md` – extremely detailed change log with technical references and feature explanations

The mod is clearly mature and complex, with many systems that would benefit from **in-game documentation**.

---

## 2. Patchouli Capabilities (from Bundled Docs)

The repo contains a copy of Patchouli’s web docs in:

- `Patchouli/web/docs/`
  - `patchouli-basics/` – getting started, page types, text formatting, multiblocks, templates, config gating, etc.
  - `patchouli-advanced/` – template nesting, component processors, itemstack format, etc.
  - `reference/` – `book-json`, `category-json`, `entry-json`, overview

Important capabilities:

### 2.1. Book Definition (`book.json`)

Located under either:
- `/data/_MODID_/patchouli_books/_BOOKNAME_/book.json` (declaration)
- Content under `/assets/_NAMESPACE_/patchouli_books/_BOOKNAME_/...` (entries, categories, templates) when `use_resource_pack=true`

Key book options (from `book-json.md`):

- **Identity & layout**
  - `name`, `landing_text` (localizable)
  - `book_texture`, `filler_texture`, `crafting_texture`
  - `model` (item model)

- **Colors & style**
  - `text_color`, `header_color`, `nameplate_color`
  - `link_color`, `link_hover_color`
  - `progress_bar_color`, `progress_bar_background`
  - `use_blocky_font`

- **Behavior**
  - `creative_tab` – where book item appears
  - `version`, `subtitle`
  - `show_progress`, `advancements_tab`
  - `dont_generate_book`, `custom_book_item`
  - `show_toasts`, `pause_game`
  - `macros` – formatting macros for text
  - `text_overflow_mode` – `overflow`, `resize`, `truncate`
  - `extend`, `allow_extensions` (for older versions; extension books)

### 2.2. Page Types (from `page-types.md`)

Key builtin page types:

- `patchouli:text` – basic text page (rich formatting)
- `patchouli:image` – images (e.g. diagrams)
- `patchouli:crafting` – crafting recipes
- `patchouli:smelting` – smelting recipes
- `patchouli:multiblock` – 3D structure visualization with optional visualization button
- `patchouli:entity` – 3D entity display
- `patchouli:spotlight` – highlight an item / tag with description
- `patchouli:link` – text page + external link button
- `patchouli:relations` – link list to related entries
- `patchouli:quest` – advancement-based quest page
- `patchouli:empty` – blank page / filler

Common fields for all pages: `type`, `advancement`, `flag`, `anchor`.

### 2.3. Text Formatting & Macros (`text-formatting.md`)

- Inline codes: `$(#rrggbb)`, `$(6)` color codes, `$(l)` bold, `$(o)` italic, etc.
- Line breaks: `$(br)`, `$(br2)`
- Lists: `$(li)`, `$(li2)` ...
- Links: `$(l:entry_id)`, `$(l:https://...)`, `$(/l)`
- Tooltips: `$(t:tooltip) ... $(/t)`
- Commands: `$(c:/command) ... $(/c)`
- Player-specific: `$(playername)`, keybinds via `$(k:bindingId)`
- Custom macros defined per book in `book.json`.

### 2.4. Advancements & Gating (`advancement-locking.md`)

- Entries or pages can be **locked behind advancements**.
- Simply add `"advancement": "modid:path"` to entry or page.
- Good for **progressive tutorials**.

### 2.5. Multiblocks (`multiblocks.md`)

- JSON-based schemas of multiblock structures.
- Use mapping from characters to block predicates.
- Patterns as a 3D array of strings.
- Can be used with `patchouli:multiblock` pages and optional `Visualize` button.

---

## 3. Why Patchouli Fits War N Taxes

The War N Taxes mod:

- Has **complex multi-stage systems** (war, raids, vassalization, taxes, PvP, permissions).
- Has **lots of commands** (unified via `/wnt`, but still numerous and parameter-heavy).
- Has **many configuration options** in `TaxConfig.java`, some interacting in non-obvious ways (e.g. building requirements vs guard count requirements).
- Already ships **internal markdown docs** under `docs/` for specific technical features.
- Already has **detailed, technical CHANGELOG** entries referencing class names and fields.

Patchouli books would turn these into:

- **Player-facing tutorials** (Getting Started, War 101, How to raid, How taxes are calculated, etc.).
- **Interactive command reference** with styled examples.
- **Progressive guide** that unlocks more advanced pages as players experience mechanics.
- **Visual aids**: e.g. entity pages for militia, multiblock pages for recommended guard tower layouts (optional), diagrams via images.

This significantly boosts **QoL, discoverability, and onboarding**.

---

## 4. Target Patchouli Book Design

### 4.1. Book Identity

Proposed book:

- **Namespace**: `minecolonytax`
- **Book folder**: `war_taxes_codex`
- **Full book ID**: `minecolonytax:war_taxes_codex`
- **Display name**: "War & Taxes Codex" or similar.
- **Audience**: regular players; advanced admins get a separate configuration section.

### 4.2. File Structure

Recommended resource layout (per Patchouli 1.20+ guidelines):

```text
src/main/resources/
├── data/minecolonytax/patchouli_books/
│   └── war_taxes_codex/
│       └── book.json              # Book definition (declaration)
└── assets/minecolonytax/patchouli_books/
    └── war_taxes_codex/
        ├── en_us/
        │   ├── categories/
        │   │   ├── getting_started.json
        │   │   ├── tax_system.json
        │   │   ├── raid_system.json
        │   │   ├── war_system.json
        │   │   ├── diplomacy.json
        │   │   ├── pvp_arena.json
        │   │   ├── colony_management.json
        │   │   ├── economy.json
        │   │   ├── commands.json
        │   │   └── configuration.json
        │   ├── entries/
        │   │   ├── getting_started/
        │   │   ├── tax_system/
        │   │   ├── raid_system/
        │   │   ├── war_system/
        │   │   ├── diplomacy/
        │   │   ├── pvp_arena/
        │   │   ├── colony_management/
        │   │   ├── economy/
        │   │   ├── commands/
        │   │   └── configuration/
        │   └── templates/
        └── textures/
            └── gui/                # Optional custom images/textures
```

Key point: `book.json` lives under `data/`, while content lives under `assets/` with `use_resource_pack=true`.

---

## 5. Category & Entry Mapping (Detailed)

Below is a **proposed mapping** of systems → Patchouli categories and entries. This is a design; the actual JSON still needs to be implemented.

### 5.1. Category: Getting Started (`getting_started.json`)

**Purpose**: High-level introduction and onboarding.

Suggested entries (folder `entries/getting_started/`):

- `welcome.json`
  - `patchouli:text` page: What War N Taxes is, short summary of war/raids/taxes.

- `first_steps.json`
  - Text + `patchouli:quest` page guiding:
    - How to open the book
    - Run `/wnt help`
    - Check your tax with `/wnt checktax`
    - Claim tax with `/wnt claimtax`

- `core_concepts.json`
  - Text: brief overview of **Tax**, **Raid**, **War**, **Diplomacy**, **PvP**, with cross-links to detailed categories using `$(l:...)`.

### 5.2. Category: Tax System (`tax_system.json`)

**Purpose**: Explain how taxes are generated, modified and claimed.

Entries (folder `entries/tax_system/`):

- `tax_generation.json`
  - Explain the interval system; refer to `lastTaxGeneration.json` and real-time vs uptime fix.
  - Mention `TaxManager.generateTaxesForAllColonies()` conceptually.

- `tax_calculation.json`
  - Explain per-building revenue & maintenance.
  - Explain **max cap** and intervals.
  - Might reflect examples from `TAX_GUI_AND_DEBUG_FIXES.md`.

- `tax_gui.json`
  - Detail the Tax GUI, refresh button fix and its meaning.
  - Guidance on reading the GUI.

- `happiness_modifier.json`
  - Explain how average happiness (0–10) gives up to ±50% tax.
  - Tie into config options controlling multipliers.

- `guard_tower_boost.json`
  - Explain guard tower boost (default +50% when `RequiredGuardTowersForBoost` met).
  - Possibly use `patchouli:entity` or `patchouli:spotlight` to highlight guard towers or relevant items.

- `inactivity_pause.json`
  - Explain inactivity-based tax pause (`EnableColonyInactivityTaxPause`, `ColonyInactivityDays`, etc.).

- `claiming_tax.json`
  - Explain `/wnt claimtax` behavior.
  - Note integration with war tax protection (tax claim blocked during wars).

### 5.3. Category: Raid System (`raid_system.json`)

Entries (folder `entries/raid_system/`):

- `raid_overview.json`
  - High-level overview of **raids** vs **wars**.
  - Requirements and consequences.

- `player_raids.json`
  - Explain `/wnt raid` command flow.
  - Use quest page to encourage players to perform a first raid.

- `raid_mechanics.json`
  - Boss bar, kill count, timer, leaving colony boundaries → disqualification.

- `entity_raids.json`
  - Use text pages to explain entity-triggered raids from the changelog section:
    - whitelisted mobs
    - entity threshold
    - bossbar display
    - penalties (tax deductions per minute)

- `raid_rewards.json`
  - Explain kill-based tax stealing system from 3.1.0.
  - How rewards and penalties integrate into main tax pool.

- `raid_guard_protection.json`
  - Document `EnableRaidGuardProtection`, `MinGuardsToBeRaided`, `MinGuardTowersToBeRaided` and related debugging command `/wnt debugguards`.

- `guard_resistance.json`
  - Explain guard resistance effects during raids/wars (`GuardResistanceLevel`, `EnableGuardResistanceDuringRaids`).

### 5.4. Category: War System (`war_system.json`)

Entries (folder `entries/war_system/`):

- `war_overview.json`
  - High-level explanation of war compared to raids.

- `declaring_war.json`
  - Explain `/wnt wagewar` and extortion parameters.
  - Building requirements for war (from `BUILD_REQUIREMENTS_FEATURE.md` and config keys).

- `war_phases.json`
  - Explain declaration, join phase, active combat, and conclusion.

- `war_victory_and_rewards.json`
  - Single-winner reward system: owner > officers > any participant fallback.
  - Multi-economy handling: SDMShop, item-based currency, tax pool.

- `war_vassalization.json`
  - Explain **War Vassalization** system from 3.2.11+:
    - When colony transfer disabled, win → vassalization.
    - Configurable duration, tribute percentage.
    - Integration with tax system.

- `war_notifications.json`
  - Summarize war notification system improvements and targeted vs server-wide messages.

### 5.5. Category: Diplomacy (`diplomacy.json`)

Entries:

- `peace_proposals.json`
  - White peace, reparations, commands under `/wnt peace`.

- `extortion.json`
  - Detailed explanation of extortion system, timers, immunity, commands `/wnt wagewar`, `/wnt payextortion`.

- `vassalization_details.json`
  - More on long-term tribute, `/wnt vasals` command, display, statuses.

### 5.6. Category: PvP Arena (`pvp_arena.json`)

Entries:

- `pvp_overview.json`
  - High-level: duels and team PvP.

- `duels.json`
  - Duel mechanics, no inventory duplication after 3.2.4 fix.

- `team_pvp.json`
  - `/teampvp` commands and config timers.

### 5.7. Category: Colony Management (`colony_management.json`)

Entries:

- `abandonment_system.json`
  - Auto-abandonment after inactivity, configuration, and affected flows.

- `claiming_abandoned_colonies.json`
  - `/wnt claimcolony`, raid mechanics to claim, configuration.

- `officer_visit_tracking.json`
  - Officer visit tracking to prevent unfair abandonment.

### 5.8. Category: Economy (`economy.json`)

Entries:

- `currency_sources.json`
  - SDMShop, physical item currencies, tax pool.

- `pvp_kill_economy.json`
  - Configurable PvP kill rewards.

- `raid_defense_rewards.json`
  - Raid defense economic rewards and integration into tax.

- `war_economy.json`
  - War reward system, transfers and penalties.

### 5.9. Category: Commands (`commands.json`)

Entries (split by audience):

- `player_commands.json`
  - Summarize player-available `/wnt` commands, with formatted examples.

- `admin_commands.json`
  - Summarize OP-only commands (`/wnt taxgen`, `/wnt wardebug`, `/wnt warstop`, `/wnt raidstop`, etc.).

- `debug_commands.json`
  - Document debug utilities like `/wnt debugtax`, `/wnt debugguards`, officer tracking debug, etc.

### 5.10. Category: Configuration (`configuration.json`)

Admin-focused; it can be collapsed to fewer entries if necessary.

Entries:

- `tax_config.json`
- `raid_config.json`
- `war_config.json`
- `pvp_config.json`
- `block_filter_system.json` (from `BLOCK_INTERACTION_FILTER_SYSTEM.md`)
- `permissions_system.json` (General Colony Permissions system)
- `web_api.json` (REST API, endpoints & security)

---

## 6. Recommended Macros & Style (book.json)

Define macros in `book.json` to standardize formatting:

```jsonc
"macros": {
  "$(cmd)": "$(#5FF)$(l)",
  "$(/cmd)": "$(/l)$()",
  "$(gold)": "$(#FFD700)",
  "$(warning)": "$(#FF5555)",
  "$(success)": "$(#55FF55)",
  "$(tax)": "$(#00FF00)",
  "$(war)": "$(#FF0000)",
  "$(raid)": "$(#FFA500)"
}
```

Usage examples:

- Commands: `$(cmd)/wnt claimtax$(/cmd)`
- Highlights: `$(war)This action can start a war!$()`
- Warnings: `$(warning)You cannot claim taxes during an active war.$()`

The AI agent implementing JSON should **copy this idea** but adapt actual keys as needed.

---

## 7. Implementation Plan for an AI Agent

This section outlines **concrete steps** an AI agent should perform in the repo to implement the Patchouli integration.

### 7.1. Phase 0 – Verify Patchouli Dependency Strategy

1. Open `build.gradle` and see if Patchouli is already referenced.
2. If not, add a **soft dependency**:
   - Use `compileOnly` / `runtimeOnly` Forge-style deps for Patchouli, referencing the correct MC + Patchouli version used in the pack.
   - If the version is unknown, leave TODO comments rather than guessing versions.
3. Ensure **no hard runtime dependency** is introduced where the mod would crash if Patchouli is missing; the book content is purely data.

### 7.2. Phase 1 – Create Book Skeleton

1. Create directories:
   - `src/main/resources/data/minecolonytax/patchouli_books/war_taxes_codex/`
   - `src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/categories/`
   - `src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/`
   - `src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/templates/`

2. Add `book.json` with at minimum:
   - `name`, `landing_text`, `use_resource_pack`, `model` (custom or default), `creative_tab`, `text_color`, `header_color`, `macros`.

3. Decide on a base texture:
   - Start with `patchouli:textures/gui/book_brown.png` or custom.

### 7.3. Phase 2 – Add Categories

For each high-level category in **Section 5**, create a category JSON such as:

```jsonc
{
  "name": "Getting Started",
  "description": "Overview of War N Taxes and first steps.",
  "icon": "minecraft:writable_book",
  "sortnum": 0
}
```

Agent tasks:

- Create 9–10 category JSON files under `categories/`.
- Give them reasonable `sortnum` values to group similar topics.

### 7.4. Phase 3 – Implement Minimal Viable Content

**Goal**: Provide a usable book quickly, then iterate.

Priority entries (minimum set):

1. `getting_started/welcome.json`
2. `getting_started/first_steps.json`
3. `commands/player_commands.json`
4. `tax_system/tax_generation.json`
5. `raid_system/raid_overview.json`
6. `war_system/war_overview.json`

Each entry should:

- Have at least **one** `patchouli:text` page.
- Use macros and formatting consistently.
- Reference commands **accurately** from `WntCommands.java` and `CHANGELOG.md`.

### 7.5. Phase 4 – Expand Content (Systems)

Gradually add entries for:

- Full tax system details (happiness, guard boost, inactivity, debug tax command, etc.).
- Raid mechanics, rewards, protection, and guard resistance.
- War phases, rewards, vassalization, notifications, team selection features.
- Diplomacy (peace & extortion systems).
- PvP arena (1v1 and team PvP).
- Colony abandonment and claiming.
- Economy (PvP kill economy, raid defense rewards, war rewards).

Use the **internal docs** in `docs/` and `CHANGELOG.md` sections as raw material for text pages.

### 7.6. Phase 5 – Advanced Features

If time and complexity allow:

1. **Advancement Gating**
   - Create Minecolonies / War N Taxes advancements in `data/minecolonytax/advancements/`.
   - Link them to quest/tutorial entries to only show advanced content after certain milestones.

2. **Quest Pages**
   - For example, a quest that asks the player to successfully complete a raid, or start and win a war.

3. **Relations Pages**
   - Use `patchouli:relations` to link related entries like `war_vassalization` ↔ `vassalization_details`.

4. **Images & Diagrams**
   - Optional: Export diagrams from existing docs or draw simplified ones and place in `assets/minecolonytax/textures/gui/warntax_*.png` for `patchouli:image` pages.

---

## 8. Integration Notes & Safeguards

For any AI agent performing code or data changes:

- **Do not remove or modify existing contents** of `Patchouli/web/docs/`; they are reference only.
- When creating new resources:
  - Do not overwrite user files; check for existence first.
  - Ensure file paths match case and naming conventions exactly.
- Keep **book language structure** flexible for future translations:
  - Put English content in `en_us/`.
  - Avoid hardcoding text into `book.json` that should later be localizable where appropriate; but for first pass, direct strings are acceptable.
- Prefer **Patchouli hot-reload flow** for testing:
  - In-game, open book → shift-click pencil icon to reload content after resource edits.

---

## 9. Summary for Next AI Agent

You should:

1. **Confirm** or add Patchouli as a soft dependency in `build.gradle`.
2. **Create** the Patchouli book structure under `data/minecolonytax/patchouli_books/war_taxes_codex` and `assets/minecolonytax/patchouli_books/war_taxes_codex`.
3. **Implement** `book.json` with sensible defaults and macros.
4. **Add** the categories defined in Section 5 as JSON files.
5. **Populate** minimum viable entries (Getting Started, core systems overview, basic commands).
6. **Iterate** to cover the full system set using the mapping above and existing docs (`docs/*.md`, `CHANGELOG.md`).
7. Optionally **enhance** with advancements, quest pages, relations, and images for higher polish.

This document is intentionally **high-level but concrete**. When in doubt, treat `CHANGELOG.md` and `docs/*.md` as authoritative sources for behavior descriptions, and reflect that in Patchouli text pages in a simplified, player-facing way.
