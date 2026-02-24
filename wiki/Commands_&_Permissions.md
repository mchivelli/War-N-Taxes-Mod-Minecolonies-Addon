# Commands & Permissions

War 'N Taxes offers a robust set of commands for both end-users (Colony managers and officers) and Server Administrators (Operation Level 2+). Below is the comprehensive list of commands provided by the mod.

All commands share the root `/wnt`.

## Player Commands (Colony Owners / Officers)

These commands can be executed by players who hold sufficient rank in their respective colonies.

| Command Syntax | Description |
|---|---|
| `/wnt help [command]` | Shows detailed help for a specific command, including syntax and requirements. |
| **War Commands** | |
| `/wnt wagewar <colony> [extortion%]` | Declares war on a specified colony. Requires certain building levels or guards. Optionally demand extortion. |
| `/wnt raid <colony>` | Starts a quick raid on a target colony. |
| `/wnt joinwar` | Join an active war. |
| `/wnt leavewar` | Leave your current war engagement. |
| `/wnt payextortion <colonyId> <amount>`| Pay requested extortion to avoid an incoming war. |
| `/wnt war accept <colonyId>` | Accept a pending war declaration. |
| `/wnt war decline <colonyId>` | Decline a pending war declaration. |
| `/wnt peace whitepeace` | Propose a white peace (status quo) with no reparations. |
| `/wnt peace reparations <amount>` | Propose peace but demand reparations from your enemies. |
| `/wnt peace accept` | Accept an incoming peace proposal. |
| `/wnt peace decline` | Decline an incoming peace proposal. |
| `/wnt warinfo` | Show detailed information about your current active war. |
| **Tax Commands** | |
| `/wnt claimtax [colony] [amount]` | Claim accumulated tax revenue from your colony's treasury. |
| `/wnt checktax` | Check your current tax generation statistics and total revenue. |
| `/wnt taxdebt pay <amount> <colony>` | Pay off your colony's debt. |
| **Info Commands** | |
| `/wnt warhistory [colony]` | View your colony's war history. |
| `/wnt raidhistory [colony]` | View your colony's raid history. |
| `/wnt warstats` | View your personal PvP and war statistics. |
| **Vassal Commands** | |
| `/wnt vasalize <percent> <colony>` | Offer a vassalization treaty to a colony, taking a percentage of their taxes. |
| `/wnt vasalaccept <colonyId>` | Accept a vassalization proposal. |
| `/wnt vasaldecline <colonyId>` | Decline a vassalization proposal. |
| `/wnt revoke <player>` | Revoke a vassalization relationship. |
| `/wnt vasals` | List your current vassals. |
| **Colony Claiming Commands** | |
| `/wnt claimcolony [colony]` | Claim an abandoned colony by starting a claiming raid against it. |
| `/wnt claimstatus` | Check your eligibility and cooldown for claiming abandoned colonies. |
| `/wnt listabandoned` | List all abandoned colonies (Enabled by default only for Admins unless configured otherwise). |

---

## Server Administrator Commands

These commands require OP Permission Level 2 or higher and are meant for server moderation, economy balancing, and debugging.

| Command Syntax | Description |
|---|---|
| `/wnt checktax <player>` | Check the tax revenue and stats of an arbitrary player's colony. |
| `/wnt wardebug` | Show deeply technical debug information for active wars. |
| `/wnt warstop <colony>` | Force-stop a specific war involving the given colony. |
| `/wnt warstopall` | Force-stop all currently active wars on the server. |
| `/wnt raidstop` | Force-stop any active raids currently executing. |
| `/wnt debugguards [colony]` | Debug the guard and guard tower counting mechanisms for a given colony. |
| `/wnt forceabandon <colony>` | Forcefully abandon a player's colony. |
| `/wnt protectcolony <colony>` | Protect an abandoned colony from being claimed by other players. |
| `/wnt unprotectcolony <colony>` | Remove claiming protection from an abandoned colony. |
| `/wnt listprotected` | List all abandoned colonies that are protected from claiming. |
| `/wnt claimraidstatus <colony>` | Check the status of a claiming raid on a colony. |
| `/wnt taxgen disable <colonyId>` | Completely halt tax generation for a specific colony. |
| `/wnt taxgen enable <colonyId>` | Resume tax generation for a specific colony. |
| `/wnt entityraid status` | Show the status of active entity (PvE) raids. |
| `/wnt entityraid config` | Show the current configuration limits and rules for entity raids. |
| `/wnt entityraid end <colonyId>` | End an active entity raid for a colony. |
| `/wnt entityraid test <colony>` | Force-trigger an entity raid to test mechanics. |
| `/wnt entityraid reload` | Reload the entity raid configuration dynamically. |
| `/wnt permissions status` | Show the status of customized general permissions. |
| `/wnt permissions config` | Show the customized permission configuration values. |
| `/wnt permissions apply [colonyId]` | Apply overridden general permissions to all colonies or a specific one. |
| `/wnt permissions remove [colonyId]` | Remove overridden general permissions from all colonies or a specific one. |
| `/wnt permissions reload` | Reload the general permission modifiers. |
