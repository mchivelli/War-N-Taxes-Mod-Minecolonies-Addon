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
| `/wnt payextortion <colonyId> <extortion%>` | Pay requested extortion to avoid an incoming war. The percent must match the attacker's demand. |
| `/wnt war accept <colonyId>` | Accept a pending war declaration. |
| `/wnt war decline <colonyId>` | Decline a pending war declaration. |
| `/wnt peace whitepeace` | Propose a white peace (status quo) with no reparations. |
| `/wnt peace reparations <amount>` | Propose peace but demand reparations from your enemies. |
| `/wnt peace surrender` | Unconditionally surrender the war, accepting full defeat penalties immediately. |
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
| **Besiege Commands** | |
| `/wnt besiege <colony>` | Besiege a target non-primary colony. On victory, siphons a portion of their taxes. Former owner can use this same command to reclaim their besieged colony. |
| **Colony Claiming Commands** | |
| `/wnt claimcolony [colony]` | Claim an abandoned colony by starting a claiming raid against it. |
| `/wnt claimstatus` | Check your eligibility and cooldown for claiming abandoned colonies. |
| `/wnt listabandoned` | List all abandoned colonies (Enabled by default only for Admins unless configured otherwise). |
| **Espionage Commands** | |
| `/wnt spy deploy <targetColonyId>` | Deploy a spy to a target colony. The spy travels there in real time before arriving. |
| `/wnt spy recall <missionId>` | Recall an active or traveling spy. Traveling spies cancel immediately; active spies begin a return journey. |
| `/wnt spy status` | List all your active and recently completed spy missions with status and gathered intel summary. |
| **Treasury Commands** | |
| `/wnt treasury status [colonyId]` | View treasury balance, drain rate, war sustainability, and available deposit sources. |
| `/wnt treasury deposit <amount> [tax\|wallet\|inventory] [colonyId]` | Deposit into the treasury. Source: `tax` (colony tax balance, default), `wallet` (SDMShop/SDMEconomy balance), `inventory` (currency items from your inventory). |
| `/wnt treasury withdraw <amount> [tax\|wallet\|inventory] [colonyId]` | Withdraw from the treasury. Destination: `tax` (colony tax balance, default), `wallet` (SDMShop/SDMEconomy balance), `inventory` (gives currency items to your inventory). |
| **Investment Commands** | |
| `/wnt invest list` | List all available upgrade types and their current costs for your colony. |
| `/wnt invest status` | View your colony's current upgrade levels for each type. |
| `/wnt invest buy <type>` | Purchase the next level of the specified upgrade (e.g., MILITIA, SPY_CAPACITY, DEFENSE). Funded from treasury. |
| **Events Commands** | |
| `/wnt events <colonyId>` | View all active random events for a colony, including their remaining duration and effects. |

---

## Server Administrator Commands

These commands require OP Permission Level 2 or higher and are meant for server moderation, economy balancing, and debugging.

| Command Syntax | Description |
|---|---|
| `/wnt checktax <player>` | Check the tax revenue and stats of an arbitrary player's colony. |
| `/wnt warstop <colony>` | Force-stop a specific war involving the given colony. |
| `/wnt warstopall` | Force-stop all currently active wars on the server. |
| `/wnt raidstop` | Force-stop any active raids currently executing. |
| `/wnt forceabandon <colony>` | Forcefully abandon a player's colony. |
| `/wnt protectcolony <colony>` | Protect an abandoned colony from being claimed by other players. |
| `/wnt unprotectcolony <colony>` | Remove claiming protection from an abandoned colony. |
| `/wnt listprotected` | List all abandoned colonies that are protected from claiming. |
| `/wnt claimraidstatus <colony>` | Check the status of a claiming raid on a colony. |
| `/wnt taxgen disable <colonyId>` | Completely halt tax generation for a specific colony. |
| `/wnt taxgen enable <colonyId>` | Resume tax generation for a specific colony. |
| `/wnt cleanupabandonedentries` | Remove stale abandonment tracking records from memory. |
| `/wnt debugbossbar <colony>` | Debug the boss bar state for the war involving a colony. |
| `/wnt forcecleanupcolony <colony>` | Force a full cleanup of a colony's war/raid state. |
| `/wnt emergencyfix` | Emergency fix for broken war state on the server. |
| `/wnt fixnullowners` | Scan and fix colonies with null owner records. |
| **Debug Commands (Admin)** | |
| `/wnt debug war` | Show deeply technical debug information for all active wars. |
| `/wnt debug guards [colony]` | Debug the guard and guard tower counting mechanisms for a given colony. |
| `/wnt debug tax [colony]` | Show a per-building tax and maintenance breakdown for a colony. |
| `/wnt debug officertracking [colonyId]` | Show officer visit tracking state for a colony (used for inactivity detection). |
| `/wnt debug besiege` | Show active besiege and occupation status across all colonies. |
| `/wnt debug entityraid status` | Show the status of active entity (PvE) raids. |
| `/wnt debug entityraid config` | Show the current configuration limits and rules for entity raids. |
| `/wnt debug entityraid end <colonyId>` | End an active entity raid for a colony. |
| `/wnt debug entityraid test <colony>` | Force-trigger an entity raid to test mechanics. |
| `/wnt debug entityraid reload` | Reload the entity raid configuration dynamically. |
| **Permissions Commands (Admin)** | |
| `/wnt permissions status` | Show the status of customized general permissions. |
| `/wnt permissions config` | Show the customized permission configuration values. |
| `/wnt permissions apply [colonyId]` | Apply overridden general permissions to all colonies or a specific one. |
| `/wnt permissions remove [colonyId]` | Remove overridden general permissions from all colonies or a specific one. |
| `/wnt permissions reload` | Reload the general permission modifiers. |
