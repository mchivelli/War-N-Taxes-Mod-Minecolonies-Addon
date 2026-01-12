# 🏰 MineColonies: War 'N Taxes Addon

![War 'N Taxes Banner](https://media.forgecdn.net/attachments/description/1129258/description_b7e3011c-d15f-48e4-a952-7fd8b4f30663.png)

**War 'N Taxes** (formerly MinecolonyTax) is the ultimate addon for MineColonies, transforming your colonies into thriving economic powerhouses and strategic battlegrounds. Designed for modpack developers and server owners, this comprehensive mod introduces:

- 💰 **Dynamic Taxation System** with configurable rates for every building
- ⚔️ **Epic War & Raid Mechanics** with territory control
- 👑 **Vassalization & Tribute Systems** for diplomatic domination
- 🏟️ **PvP Arena Duels & Team Battles**
- 📖 **Complete In-Game Guidebook** powered by Patchouli
- 🔧 **Seamless SDMShop Integration** for economy management

Originally crafted for The Warborn Realms SMP Server, now available for your world!

> ⚠️ **Note:** This is regularly updated with new features. Found a bug? Report it on [Discord](https://discord.gg/BBAFqg9yY8)!

---

![Features Divider](https://media.forgecdn.net/attachments/description/1129258/description_4c27eea7-fc84-4fbb-85be-78b6e369c762.png)

---

## 📖 NEW: War 'N Taxes Codex (v4.0)

A **complete in-game guidebook** powered by Patchouli that teaches players everything about the mod!

| Feature | Description |
|---------|-------------|
| 📚 **9 Content Categories** | Getting Started, Tax System, Raids, Wars, Diplomacy, PvP Arena, Colony Management, Commands, Configuration |
| 🎁 **Auto Distribution** | Players receive the codex on first join |
| 🖱️ **Interactive Commands** | Clickable links that execute commands directly |
| 🌐 **Multi-Language** | German, Russian, French, Spanish translations |
| 🏆 **Advancements** | Track progress for claiming tax, starting raids, declaring war |

---

## 💰 Core Tax System

| Feature | Description |
|---------|-------------|
| **Configurable Tax Rates** | Define base and upgrade tax for every MineColonies building |
| **Maintenance Costs** | Military buildings have upkeep costs deducted from revenue |
| **Tax Revenue Cap** | Taxes stop accumulating at a configurable maximum |
| **Tax Freeze** | Temporarily halt collection as war penalties |
| **Happiness Modifier** | Colony happiness affects tax generation |
| **Guard Tower Boost** | More guards = higher tax revenue |

### 💵 Currency Options
- **SDMShop Integration** - Seamless conversion with null-safe handling
- **Custom Currency** - Set `SDMShopConversion = false` and configure your item
- **Colony Balance** - Built-in virtual economy

---

## ⚔️ War & Raid Mechanics

### 🏴 War System
| Phase | Description |
|-------|-------------|
| **Declaration** | `/wnt wagewar "<colony>"` triggers join phase |
| **Preparation** | Allies and officers join with `/wnt joinwar` |
| **Active Combat** | 2-hour default duration, 5 lives per player |
| **Resolution** | Total Victory, Strategic Victory, Stalemate, or Vassalization |

### ⚡ Raid System
- **Tax Theft** - Steal accumulated tax from enemy colonies
- **Militia Defense** - Citizens become armed defenders
- **Mercenary Spawns** - Additional defenders if garrison is small
- **Configurable Duration** - Set raid length and cooldowns

### 🎯 Combat Features
- **1:1 Ratio Balance** - Teams differ by at most ±1 player
- **Normalized Lives** - Fair outcome calculation including guards
- **Death Handling** - Spectator mode when out of lives
- **Disconnect Protection** - Individual pauses, war continues

---

## 👑 Vassalization System

Create a **network of tribute-paying colonies** under your dominion!

| Command | Description |
|---------|-------------|
| `/wnt vassalize <percent> "<colony>"` | Offer vassalization |
| `/wnt vassalaccept <colonyId>` | Accept a proposal |
| `/wnt vassaldecline <colonyId>` | Decline a proposal |
| `/wnt revoke <player>` | End a vassal relationship |
| `/wnt vassals` | View your vassals and tribute |

### 🏛️ War Vassalization (v3.2.11)
When colony transfer is disabled, **winning a war vassalizes the enemy** instead:
- Configurable duration (default: 1 week)
- 25% tribute rate (configurable)
- Automatic expiration with notifications

---

## 🏚️ Colony Abandonment & Claiming (v3.2)

### Automatic Abandonment
Colonies become **abandoned** after owner/officer inactivity (default: 14 days)

### Colony Claiming Raids
| Feature | Description |
|---------|-------------|
| **5-Minute Raid** | Kill ALL defenders to claim |
| **Hostile Militia** | All citizens become armed defenders |
| **Mercenary Spawns** | If fewer than 5 defenders exist |
| **Victory = Ownership** | Successful claimers become Officers |

### Commands
```
/wnt listabandoned          - View abandoned colonies
/wnt claimcolony <colony>   - Initiate claiming raid
/wnt forceabandon <colony>  - Admin forced abandonment
```

### Configuration
- `AutoAbandonmentEnabled` (default: true)
- `ColonyInactivityDays` (default: 14)
- `ClaimingRaidDurationMinutes` (default: 5)
- `EnableListAbandonedForAll` - Allow all players to view abandoned list

---

## 🏟️ PvP Arena System

Host **fair duels and team battles** that don't affect progression!

| Feature | Description |
|---------|-------------|
| **Arena Setup** | `/pvparena p1` and `/pvparena p2` |
| **Duel Challenges** | `/pvp` with clickable accept/decline |
| **Team Battles** | Organized multi-player combat |
| **Spectator Mode** | `/pvp spectate [player]` |
| **Inventory Safety** | Gear saved during duels, restored after |

---

## 🔧 Advanced Configuration

### Building Requirements
Smart `building:level:amount` syntax for raids, wars, and claiming:
```
townhall:2:1,guardtower:1:3
```

### Configurable Actions
Control what players can do during conflicts:
- `PLACE_BLOCK`, `BREAK_BLOCK`
- `ATTACK_ENTITIES`, `USE_ITEMS`
- Default / Raid / War action sets

### Recipe Control
Disable building hut recipes directly in config!

---

## 📋 Essential Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `/wnt help` | Show all commands |
| `/wnt claimtax` | Collect accumulated tax |
| `/wnt taxinfo` | View tax breakdown |
| `/wnt raid "<colony>"` | Start a raid |
| `/wnt wagewar "<colony>"` | Declare war |
| `/wnt peace` | Propose peace terms |

### Admin Commands
| Command | Description |
|---------|-------------|
| `/wnt forceabandon` | Force colony abandonment |
| `/wnt debugtax` | Debug tax calculation |
| `/wnt endwar` | Force end active war |

---

## 📦 Requirements & Compatibility

| Mod | Status |
|-----|--------|
| **MineColonies** | Required |
| **FTB Teams** | Required |
| **SDMShop** | Recommended (for currency) |
| **Recruits Mod** | Recommended |
| **Patchouli** | Recommended (for guidebook) |

---

## 🌐 Community & Support

### 🔥 The Warborn Realms Community
| Resource | Link |
|----------|------|
| 📦 **Modpack** | [EpicWarsCvC on CurseForge](https://www.curseforge.com/minecraft/modpacks/epicwars-colony-vs-colony-smp-modpack) |
| 🌍 **Website** | [warbornrealms.com](https://warbornrealms.com/) |
| 💬 **Discord** | [Join our server](https://discord.gg/BBAFqg9yY8) |
| 🎮 **Server IP** | `play.warbornrealms.com` |

### 🖥️ Premium Server Hosting
We offer **extreme performance** hosting for heavy modded servers:
- **Ryzen 9 7950x3D** with dedicated cores
- **16-32 GB RAM** with ultra-fast I/O
- *Limited availability* - Contact on Discord

---

## 🚀 Transform Your MineColonies Experience!

With **War 'N Taxes**, your colonies become living, breathing economies where:
- 💰 **Taxes fund your ambitions**
- ⚔️ **Wars determine dominance**
- 👑 **Vassals pay tribute to the strong**
- 🏆 **Strategy trumps brute force**

*Build your empire. Crush your rivals. Rule the server.*

---

**Your feedback shapes this mod!** Report bugs or suggest features on Discord or in the comments.
