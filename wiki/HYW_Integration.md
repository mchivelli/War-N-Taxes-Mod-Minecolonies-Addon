# Hundred Years' War Integration

War 'N Taxes has optional integration with the **Hundred Years' War (HYW)** mod — the large-scale medieval troop-commanding mod. When both mods are installed, War 'N Taxes stops a player's HYW troops from fighting that player's own MineColonies town, while still letting them wage war on enemy colonies.

If HYW is not installed, the integration silently does nothing - no errors, no setup needed.

## What it does

While the two mods are running together:

- An HYW troop can **never** damage or target a colonist belonging to its own commander, to any colony **allied** with that commander, or to any **neutral** colony.
- Protection is **symmetric**: a friendly player's colonists - guards included - will not target or damage that player's HYW troops either. A stray hit can't accidentally start a war between your own army and your own town.
- **Guards are fully covered.** In MineColonies a guard is just a colonist, so your guard towers never turn your marching army hostile.
- It works in two layers. First, a troop refuses the target before it even aims at a friendly colonist. Second, a damage backstop cancels any friendly hit that slips past HYW's own AI - covering both melee and archers/arrows - and tells the troop to drop the target.
- It recognises the **whole HYW roster**: foot troops, workers, every kind of cavalry, all siege engines (cannon, bombard, mangonel, trebuchet, battering ram, siege tower, and the rest), undead units, and more. Every commandable HYW unit is accounted for.

## What it does **not** touch

Some attacks are meant to happen, and the integration stays out of the way for them:

- **Enemy colonies during a sanctioned event.** The instant a War 'N Taxes **war, besiege, raid, or abandoned-colony claiming raid** places an enemy colony on the opposing side, the protection lifts for that colony - your (and the enemy's) HYW troops can kill and pillage enemy colonists exactly as a siege should. When the event ends, protection resumes.
- **Ownerless HYW units** (for example HYW bandits or other unowned spawns). With no "own side" to protect, they stay hostile to everyone - colonists can fight them and they can attack colonists, unchanged.
- **Non-HYW combat.** Vanilla mobs and MineColonies' own natural raiders are never affected.

## Configuration

One key, in the relevant section of `config/warntax/minecolonytax.toml`:

| Key                                | Default | Description                                                                                                       |
|------------------------------------|---------|------------------------------------------------------------------------------------------------------------------|
| `EnableHywFriendlyFireProtection`  | `true`  | Master switch for the whole HYW friendly-fire protection. When `false`, HYW troops use their vanilla (unprotected) targeting. |

## Notes for server operators

- The integration uses pure **reflection** against HYW, so an HYW update does **not** require a rebuild of War 'N Taxes. If a future HYW version renames the API it relies on, the integration disables itself and logs a warning rather than crashing.
- **Offline-safe owner attribution.** A troop's commanding player is identified even while that player is logged out, so a player's standing army keeps respecting their town on a multiplayer server after they log off. This has been verified against HYW builds from 0.3.8r through 0.6.4r on both Forge 1.20.1 and NeoForge 1.21.1.
- Whether a colonist counts as "friendly" is decided by the same war/besiege/raid state the rest of War 'N Taxes uses, so protection always stays consistent with who you are actually at war with.
