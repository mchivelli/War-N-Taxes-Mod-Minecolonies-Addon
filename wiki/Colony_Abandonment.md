# Colony Abandonment & Claiming

In long-running servers, inactive players naturally leave behind ghost-town colonies. The **Colony Abandonment** system allows active players to strategically purge and reclaim these dead plots, redistributing wealth and land back into the active economy.

## 1. Automatic Abandonment (`EnableColonyAutoAbandon`)
The server systematically monitors the activity of all Colony Owners and Officers. 
- **Inactivity Threshold:** If an Owner or Officer has not physically visited their colony chunks for a configurable duration (default: **72 Hours** via `ColonyInactivityHoursThreshold`), the server initiates the abandonment process.
- **Warnings:** If `NotifyOwnersBeforeAbandon` is true, logging into the server during the final warning period (default: **3 Days** before via `AbandonWarningDays`) will blast urgent notifications to the leaders.
- *(Experimental)* `ResetTimerOnOfficerLogin`: Admins can allow the timer to reset purely by an officer logging onto the Minecraft server, regardless of whether they physically step foot inside the colony borders. This is typically disabled to force real map interaction. 

Once the final time expires, all ownership bindings to the land are wiped clean. The town is now legally "Abandoned".

## 2. Listing Abandoned Colonies
Abandoned colonies sit waiting to be claimed or destroyed.
- **OPs vs All Players:** By default, only Admins can run `/wnt listabandoned` to see what towns are decaying. If `EnableListAbandonedForAll` is turned on, the entire server can hunt for free real estate without admin intervention.
- **Admin Protections:** If a colony belongs to a VIP or a specific server build that should never decay, Admins can permanently flag it using `/wnt protectcolony <colony>`. 

## 3. The Claiming Raid (`/wnt claimcolony <colony>`)
Taking over an abandoned colony is an incredibly violent procedure. It is not handed over for free, ensuring players must earn the right to control pre-built infrastructure.

When a claim is initiated, a **Claiming Raid** triggers:
1. The claiming player is locked into the colony borders. 
2. Every surviving citizen, guard, and even children living within the abandoned colony instantly converts into **Hostile Militia**, furious at the prospect of new management.
3. The defending militia will swarm and relentlessly hunt the claiming player for a specified duration (`ClaimingRaidDurationMinutes`).
4. **Mercenaries:** To prevent players from easily claiming entirely dead colonies that lack living citizens, `SpawnMercenariesIfLowDefenders` forces high-tier mercenaries to spawn out of thin air if the town has fewer than **5** remaining defenders. 

### Claim Victory
If the claiming player survives the duration of the raid without dying and successfully executes all hostiles, the massive blood-toll is considered paid! The `ColonyOwnershipHandler` instantly transfers full ownership of the town, all its structures, and any remaining treasury funds over to the victor.
