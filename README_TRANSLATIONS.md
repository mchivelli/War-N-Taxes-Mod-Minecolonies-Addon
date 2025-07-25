# MineColonyTax - Custom Language Guide

## Creating a Language Datapack (Simple Guide)

This guide will help you create a custom language datapack for MineColonyTax. This allows server admins to change any text message in the mod without editing the mod files.

### Quick Start (Step by Step)

1. **Create Your Datapack Folder**
   - Go to your Minecraft server's `world/datapacks` folder
   - Create a new folder for your datapack (e.g., `mct_custom_lang`)

2. **Set Up Basic Structure**
   - Inside your datapack folder, create this exact folder structure:
   ```
   mct_custom_lang/
   ├── pack.mcmeta
   └── data/
       └── minecolonytax/
           └── lang/
   ```

3. **Create the pack.mcmeta File**
   - In the main datapack folder, create `pack.mcmeta` with this content:
   ```json
   {
     "pack": {
       "pack_format": 9,
       "description": "My Custom MineColonyTax Messages"
     }
   }
   ```

4. **Create Your Language File**
   - In the `lang` folder, create a file called `en_us.json` (or your language code)
   - Add only the messages you want to change, for example:
   ```json
   {
     "war.declare.title": "WAR HAS BEEN DECLARED!",
     "war.declare.body": "The faction of %s has declared war on %s!"
   }
   ```

5. **Install the Datapack**
   - Place your datapack folder in the server's `world/datapacks` directory
   - In-game, run `/reload` or restart the server
   - Your custom messages will now be used instead of the default ones

### Example Language Files

Here are some simple examples that you can copy and modify:

#### Basic War Messages (English)
```json
{
  "war.declare.title": "WAR HAS STARTED!",
  "war.declare.body": "%s has declared WAR against %s!",
  "war.defenders.win.title": "DEFENDERS WIN!",
  "war.defenders.win.body": "The defenders of %s have beaten the attackers from %s!"
}
```

#### Tax Messages (English)
```json
{
  "command.checktax.self": "Your colony %s has stored %d tax coins",
  "command.claimtax.success": "You collected %d tax coins from %s colony"
}
```

### Important Tips

1. **Placeholders**: Many messages contain `%s` or `%d` - these are placeholders that get replaced with names or numbers. Keep them in your custom text!

2. **Languages**: Create files for different languages using their codes:
   - `en_us.json` - English
   - `de_de.json` - German
   - `ru_ru.json` - Russian
   - `fr_fr.json` - French
   - `es_es.json` - Spanish

3. **Partial Changes**: You only need to include the messages you want to change - others will use the default text.

4. **Finding Message Keys**: Look at the original language files in the mod or check the example below for message keys.

### Common Message Keys

#### Tax System
- `command.checktax.self` - Message when checking your colony's tax
- `command.checktax.other` - Message when checking another colony's tax
- `command.claimtax.success` - Message when claiming tax

#### War System
- `war.declare.title` - War declaration title
- `war.declare.body` - War declaration message
- `war.defenders.win.title` - Title when defenders win
- `war.attackers.win.title` - Title when attackers win

#### Raid System
- `raid.initiate.message` - Message when raid begins
- `raid.alert.colony` - Alert to colony being raided

For a complete list of message keys, check the language files in the mod.

### Example Full Datapack

For a working example, check the `example_datapack` folder included with the mod.
