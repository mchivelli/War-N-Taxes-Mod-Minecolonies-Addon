# MineColonyTax: War 'N Taxes Addon for MineColonies

A comprehensive Forge mod extension for MineColonies that adds:

- **🏛️ Automated tax collection & management** with debt tracking
- **⚔️ Advanced war system** with declarations, join phases, lives, and strategic combat
- **🏴‍☠️ Dynamic raid mechanics** with penalties and grace periods
- **🕊️ Peace proposal system** for diplomatic resolution
- **📊 Comprehensive statistics tracking** for players and colonies
- **🎮 Unified command interface** with `/wnt` prefix and intelligent suggestions
- **🛡️ Admin tools** for server management and debugging

---

## 📦 Installation

1. Install **Minecraft Forge** (1.XX.X) and the **MineColonies** mod
2. Drop `MineColonyTax-<version>.jar` into your `mods/` folder
3. (Optional) Configure settings in `config/minecolonytax.toml`
4. Launch the game—all systems register automatically

---

## 🎮 Command System

**All commands use the unified `/wnt` prefix** (War 'N Taxes)

### 📚 Getting Help
- **`/wnt`** or **`/wnt help`** - Show overview of all commands
- **`/wnt help <command>`** - Get detailed help for specific commands

### ⚔️ War Commands
| Command | Description | Requirements |
|---------|-------------|--------------|
| `/wnt wagewar "<colony>"`| Declare war on a colony | 5+ guards, grace period met, no active raids |
| `/wnt raid "<colony>"` | Start a raid on a colony | Target owner offline (configurable) |
| `/wnt joinwar` | Join the current war during join phase | Active war with open join phase |
| `/wnt leavewar` | Leave war during join phase | Cannot be colony owner/attacker |
| `/wnt war accept <colonyId>` | Accept a war declaration | Must be colony owner/officer |
| `/wnt war decline <colonyId>` | Decline a war declaration | Must be colony owner/officer |
| `/wnt warinfo` | Show detailed war status | Must be participating in war |

### 🕊️ Peace & Diplomacy
| Command | Description | Requirements |
|---------|-------------|--------------|
| `/wnt peace whitepeace` | Propose peace with no reparations | Must be in active war |
| `/wnt peace reparations <amount>` | Propose peace with payment | Must be in active war |
| `/wnt peace accept` | Accept a peace proposal | Must be authorized to negotiate |
| `/wnt peace decline` | Decline a peace proposal | Must be authorized to negotiate |

### 💰 Tax Management
| Command | Description | Requirements |
|---------|-------------|--------------|
| `/wnt claimtax` | Claim all tax from all your colonies | Must be colony manager |
| `/wnt claimtax "<colony>"` | Claim all tax from specific colony | Must manage that colony |
| `/wnt claimtax "<colony>" <amount>` | Claim specific amount | Must manage that colony |
| `/wnt checktax` | Check tax revenue for your colonies | Must be colony manager |
| `/wnt checktax <player>` | Check another player's tax (Admin) | Permission level 2 |
| `/wnt taxdebt pay <amount> "<colony>"` | Pay colony debt | Must manage colony, have funds |

### 📈 Statistics & History
| Command | Description | Requirements |
|---------|-------------|--------------|
| `/wnt warhistory` | View war history for your colonies | Must be colony manager |
| `/wnt warhistory "<colony>"` | View specific colony's history | Must manage that colony |
| `/wnt warstats` | View your personal war statistics | None |

### 🛡️ Admin Commands
| Command | Description | Permission |
|---------|-------------|-----------|
| `/wnt wardebug` | Show debug info for all wars | Level 2 (OP) |
| `/wnt warstop "<colony>"` | Stop specific war by colony | Level 2 (OP) |
| `/wnt warstopall` | Stop all active wars | Level 2 (OP) |
| `/wnt raidstop` | Stop active raids | Level 2 (OP) |
| `/wnt taxgen disable <colonyId>` | Disable tax generation | Level 2 (OP) |
| `/wnt taxgen enable <colonyId>` | Enable tax generation | Level 2 (OP) |

---

## 🎯 Smart Features

### 🤖 Intelligent Command Suggestions
- **Colony names** automatically suggested with proper quotes for spaces
- **Player names** suggested for admin commands
- **Colony IDs** suggested for war responses
- **Context-aware** suggestions based on your permissions

### 📊 Comprehensive Help System
- **Command-specific help** with `/wnt help <command>`
- **Permission-aware** - only shows commands you can use
- **Detailed explanations** including requirements and examples

### 💳 Flexible Currency Support
- **SDMShop integration** - Uses shop balance when available
- **Item-based currency** - Falls back to emeralds or configured items
- **Automatic detection** and seamless switching

---

## ⚙️ Configuration

Settings in `config/minecolonytax.toml`:

### 💰 Tax Settings
| Option | Default | Description |
|--------|---------|-------------|
| `TaxIntervalMinutes` | `60` | Tax generation frequency |
| `MaxTaxRevenue` | `5000` | Maximum stored tax per colony |
| `EnableSDMShopConversion` | `true` | Use SDMShop API for currency |
| `CurrencyItemName` | `"minecraft:emerald"` | Fallback currency item |

### ⚔️ War Settings
| Option | Default | Description |
|--------|---------|-------------|
| `WarAcceptanceRequired` | `true` | Require manual war acceptance |
| `AttackerGracePeriodMinutes` | `120` | Cooldown between war declarations |
| `JOIN_PHASE_DURATION_MINUTES` | `5` | Time to join declared wars |
| `WAR_DURATION_MINUTES` | `60` | Maximum war duration |
| `MinGuardCountForWar` | `5` | Minimum guards required |

### 🏴‍☠️ Raid Settings
| Option | Default | Description |
|--------|---------|-------------|
| `RaidGracePeriodMinutes` | `1` | Cooldown between raids |
| `MaxRaidDurationMinutes` | `20` | Maximum raid duration |
| `RaidOwnerMustBeOffline` | `true` | Target owner offline requirement |

---

## 🔥 War System Features

### 📋 War Phases
1. **Declaration Phase** - Attacker declares war, defender can accept/decline
2. **Join Phase** - Other players can join either side (configurable duration)
3. **Combat Phase** - Active warfare with lives, guards, and objectives
4. **Resolution** - Victory conditions, reparations, or stalemate

### 🎯 Victory Conditions
- **Total Victory** - Eliminate all enemy lives or guards
- **Strategic Victory** - Superior performance when time expires
- **Stalemate** - Proportional losses or inactivity

### 📊 Live War Tracking
- **Real-time statistics** with `/wnt warinfo`
- **Boss bars** showing war progress and timers
- **Lives tracking** for all participants
- **Guard count** monitoring
- **Penalty reports** for rule violations

---

## 🏴‍☠️ Raid System Features

### ⚡ Dynamic Raids
- **Territory-based** - Must stay in target colony
- **Progressive rewards** - Tax transferred over time
- **Risk/reward** - Death penalties vs. profit potential
- **Automatic termination** - Time limits and death consequences

### 🛡️ Defense Mechanics
- **Colony alerts** - Automatic notifications to defenders
- **Raider tracking** - Know who's attacking
- **Counter-attack opportunities** - Turn the tables on raiders

---

## 📈 Statistics & History

### 📊 Personal Stats (via `/wnt warstats`)
- Players killed in wars
- Colonies raided successfully
- Total amount gained from raids
- Wars won/lost
- War stalemates

### 📚 Colony History (via `/wnt warhistory`)
- Complete war and raid records
- Outcomes and results
- Amounts transferred
- Timestamps and participants

---

## 🔧 Developer Information

### 📁 Project Structure
- **Package:** `net.machiavelli.minecolonytax`
- **Main Class:** `MineColonyTax.java`
- **Command System:** `WntCommands.java` (unified command handler)
- **War Engine:** `WarSystem.java`
- **Raid Engine:** `RaidManager.java`

### 💾 Data Persistence
- **Tax data:** `config/colonyTaxData.json`
- **War history:** Tracked via `HistoryManager`
- **Player stats:** Capability-based storage
- **Crash logs:** `crash_report.log` via `CrashLogger`

### 🔨 Building
```bash
./gradlew build
```

---

## 📋 Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

---

## 🤝 Contributing

Contributions welcome! Please:
1. Fork this repository
2. Create a feature branch
3. Submit a pull request

Bug reports and feature requests can be submitted via Issues.

---

## 📄 License

This project is released under the [MIT License](LICENSE).

---

## Configuration Options

### Console Logging Control

To reduce console spam during initialization, you can now control the logging of tax generation details in the configuration file.

In your `config/minecolonytax-common.toml` file, under the `[General]` section, you'll find:

```toml
#Enable console logging of tax generation details (building upgrades, max warnings, etc.). Set to false to reduce console spam during initialization.
ShowTaxGenerationLogs = true
```

Set this to `false` to hide the following types of console messages:
- Tax generation cycle details for each colony
- Building upgrade tax calculations
- Maximum tax revenue warnings
- Debt limit notifications
- Tax claim/payment logs
- Colony tax freeze/unfreeze notifications

Error messages will still be displayed regardless of this setting to ensure important issues are not missed.
