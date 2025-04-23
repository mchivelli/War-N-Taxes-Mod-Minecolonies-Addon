# MineColonyTax: War ’N Taxes Addon for MineColonies

A Forge mod extension for MineColonies that adds:

- **Automated tax collection & management**  
- **Debt tracking & repayment**  
- **Raid mechanics** (hit other colonies, grace periods, penalties)  
- **Full-fledged war system** (declarations, join phase, lives, guards, boss bars, victory conditions)  
- **Peace proposals** (white peace or coin reparations)  
- **PvP arena duels** (configurable arena, wagers, invites, spectating)  
- **Crash logging** for easier debugging

---

## 📦 Installation

1. Install Minecraft Forge (1.XX.X) and the MineColonies mod.  
2. Drop the `MineColonyTax-<version>.jar` into your `mods/` folder.  
3. (Optional) Configure settings in `config/minecolonytax.toml`.  
4. Launch the game—commands and systems register automatically.

---

## ⚙️ Configuration

All settings live in `config/minecolonytax.toml`. Key options:

| Option                         | Default        | Description                                             |
|------------------------------- |--------------- |-------------------------------------------------------- |
| `TaxIntervalMinutes`           | `60`           | How often (minutes) each colony generates taxes.       |
| `MaxTaxRevenue`                | `5000`         | Cap on stored tax before generation pauses.            |
| `EnableSDMShopConversion`      | `true`         | Use SDMShopR API instead of emerald items.             |
| `CurrencyItemName`             | `"minecraft:emerald"` | Item used for on-hand taxes & reparations.    |
| **War Settings**               |                |                                                        |
| `WarAcceptanceRequired`        | `true`         | Require manual acceptance or start automatically.      |
| `AttackerGracePeriodMinutes`   | `120`          | Cooldown before same player can declare another war.   |
| `RaidGracePeriodMinutes`       | `1`            | Cooldown between raids.                                |
| `MaxRaidDurationMinutes`       | `20`           | How long a raid may last.                              |
| `JOIN_PHASE_DURATION_MINUTES`  | `5`            | Time window to join a declared war.                    |
| **PvP Arena Settings**         |                |                                                        |
| `AllowPvPArenaCommands`        | `false`        | Allow commands while standing in the PvP arena bounds. |

> See `TaxConfig.java` for the full list of building taxes, maintenance costs, and more.

---

## 🛠️ Commands

### Tax System
| Command                                            | Permission | Description                                                   |
|--------------------------------------------------- |----------- |-------------------------------------------------------------- |
| `/claimtax [<colony>] [<amount>]`                  | 0          | Claim all (or `<amount>`) of accumulated tax from your colony. |
| `/checktax [<player>]`                             | 0 (other: 2)| View stored tax for your colonies (or `<player>`’s if you’re an operator). |
| `/taxdebt pay <amount> <colony>`                   | 0          | Repay up to `<amount>` of your colony’s debt.                |

### Raid System
| Command         | Permission | Description                                                                                |
|---------------- |----------- |-------------------------------------------------------------------------------------------|
| `/raid <colony>`| 0          | Start a raid on `<colony>` (must meet guard count, owner offline checks, grace period).   |
| `/raidstop`     | 2          | Force-stop the active raid.                                                               |

### War System
| Command                                              | Permission | Description                                                               |
|----------------------------------------------------- |----------- |--------------------------------------------------------------------------|
| `/wagewar <colony>`                                  | 0          | Declare war on `<colony>` (checks guard counts, offline status, grace).  |
| `/joinwar`                                           | 0          | Join the current war’s join phase (clickable prompts also available).    |
| `/leavewar`                                          | 0          | Leave during join phase.                                                 |
| `/war accept <colonyId>` / `/war decline <colonyId>` | 0          | Owner/officer accepts or declines a pending war request.                 |
| `/peace whitepeace`                                  | 0          | Propose “white peace” (no reparations).                                   |
| `/peace reparations <amount>`                        | 0          | Propose peace with `<amount>` coin reparations.                           |
| `/warinfo`                                           | 0          | Show live war details: lives, guards, timer, pending penalties.           |
| `/wardebug`                                          | 2          | Operator debug output for every active war.                               |
| `/warstop`                                           | 2          | End all wars immediately.                                                 |

### PvP Arena Duels
| Command                               | Permission | Description                                                                                   |
|-------------------------------------- |----------- |----------------------------------------------------------------------------------------------|
| `/pvparena p1` / `/pvparena p2`        | 2          | Define the two corners of your duel arena.                                                  |
| `/pvp <target> [<wager>]`             | 0          | Challenge another player (optional `<wager>` in coins).                                      |
| `/pvp accept` / `/pvp decline`        | 0          | Respond to a pending duel request.                                                           |
| `/pvp spectate <player>` / `/pvp spectate stop` | 0 | Spectate or stop spectating an active duel.                                                  |

---

## 🔧 Developer & Build

- **Package:** `net.machiavelli.minecolonytax`  
- **Main Mod Class:** `MineColonyTax.java`  
- **Build:** Standard ForgeGradle setup.  
- **Crash Logging:** All uncaught exceptions are logged to `crash_report.log` via `CrashLogger.java`.  
- **Persistent Data:**  
  - Tax data in `config/colonyTaxData.json`  
  - PvP arena positions in `config/pvp_arena_data.json`

> Contributions, bug reports, and pull requests are welcome! Please fork this repo, create a feature branch, and submit a PR.

---

## 📄 License

This project is released under the [MIT License](LICENSE).
