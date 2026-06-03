# Easy Factions Integration

War 'N Taxes has optional integration with the **Easy Factions** mod. When both mods are installed on the server, faction members are automatically granted permissions on each other's colonies so they can work and fight together without each colony owner manually inviting everyone.

If Easy Factions is not installed, the integration silently does nothing - no errors, no setup needed.

## What it does

When a player belongs to a faction:

- Every other faction member is automatically added to that player's colony with the **Friend** rank.
- Optionally, the faction owner and faction officers can be promoted to the **Officer** rank on every faction member's colony. This is **off by default** because Officer rank grants tax-claim rights (see warning below).
- When a player leaves the faction, is kicked, or the faction is disbanded, those automatic grants are reverted back to **Neutral**.

The integration reconciles on player login and again every few seconds, so it catches faction changes even if a player creates or joins a faction while another faction member is offline.

> **Important**: Enabling `EasyFactionsPromoteOfficers` lets your faction officers drain each other's colony treasuries via `/wnt claimtax`. Only turn it on if every officer is tightly trusted.

## What it does **not** touch

- **Active wars, raids, or sieges.** While a war, raid, or siege is underway, the integration skips that colony entirely. It also skips any individual player who is currently a participant in any active war, even on other colonies. This guarantees the integration never races the cleanup the war/raid/siege systems perform when those events end.
- **Colony owners.** The colony owner's own rank is never modified.
- **Abandoned colonies.** The integration reverts any ranks it granted before handing control back to the abandonment system, then stays out of the way.
- **Manual grants by the colony owner.** If you manually re-rank someone the integration previously promoted, the integration detects this on the next sync and releases tracking of that player - it will not downgrade or fight your manual choice.

## Configuration

All keys live in the `Easy Factions Integration` section of `config/warntax/minecolonytax.toml`:

| Key                              | Default  | Description                                                                                          |
|----------------------------------|----------|------------------------------------------------------------------------------------------------------|
| `EnableEasyFactionsIntegration`  | `true`   | Master switch. When `false`, all auto-grants are reverted on the next sync tick.                     |
| `EasyFactionsMemberRank`         | `friend` | Rank for regular faction members. Valid values: `friend`, `officer`.                                 |
| `EasyFactionsPromoteOfficers`    | `false`  | If `true`, the faction owner and faction officers get the **Officer** rank (see warning above).      |
| `EasyFactionsSyncIntervalTicks`  | `200`    | How often to reconcile permissions. 20 ticks = 1 second. Default is every 10 seconds.                |

## Notes for server operators

- The integration uses pure reflection against Easy Factions, so updates to Easy Factions do not require a rebuild of War 'N Taxes. If a future Easy Factions update renames the relevant API methods, the integration will silently disable itself and log a warning rather than crash.
- Grants made by this integration are tracked in memory and rebuilt on every server start. If you disable the integration with the master switch, any existing auto-grants are reverted on the next sync tick.
