# Recipe Disable Feature

This feature allows server administrators to disable all Minecolonies building hut recipes for buildings that accumulate taxes or maintenance costs. When enabled, players must obtain these building huts through SDMShop or Admin Shop instead of crafting them.

## Configuration

The feature is controlled by a configuration option in the mod's config file:

```toml
[Recipe Disabling]
# Disable all Minecolonies building hut recipes. When enabled, players must obtain building huts through SDMShop or Admin Shop instead of crafting them.
# This affects all buildings that accumulate taxes or maintenance costs. Disabled by default.
DisableHutRecipes = false
```

## How It Works

When `DisableHutRecipes` is set to `true`, the mod will:

1. **Identify Affected Buildings**: All buildings that have taxes or maintenance costs defined in the mod's configuration are identified
2. **Generate Disabled Recipes**: Custom "disabled" recipe files are generated that override the original Minecolonies recipes
3. **Prevent Crafting**: The disabled recipes cannot be crafted, effectively removing the ability to craft these building huts

## Affected Buildings

The following building types will have their recipes disabled when the feature is enabled:

### Buildings with Taxes
- Alchemist Hut
- Concrete Mixer Hut
- Fletcher Hut
- Lumberjack Hut
- Rabbit Hutch
- Shepherd Hut
- Smeltery Hut
- Swine Herder Hut
- Town Hall
- Warehouse
- Baker Hut
- Blacksmith Hut
- Builder Hut
- Chicken Herder Hut
- Composter Hut
- Cook Hut
- Cowboy Hut
- Crusher Hut
- Deliveryman Hut
- Dyer Hut
- Enchanter Hut
- Farmer Hut
- Fisherman Hut
- Florist Hut
- Glassblower Hut
- Hospital Hut
- Library Hut
- Mechanic Hut
- Miner Hut
- Plantation Hut
- Sawmill Hut
- Stonemason Hut
- Tavern Hut
- Nether Worker Hut
- Graveyard Hut
- Beekeeper Hut
- University Hut
- Residence (Home)

### Buildings with Maintenance Costs
- Barracks
- Guard Tower
- Barracks Tower
- Archery Hut
- Combat Academy

## Testing Commands

The mod includes test commands to verify the feature is working correctly:

- `/testrecipedisable status` - Shows whether recipe disabling is enabled or disabled
- `/testrecipedisable list` - Lists all building huts that will have recipes disabled
- `/testrecipedisable check <block_id>` - Checks if a specific block's recipe should be disabled

Example usage:
```
/testrecipedisable status
/testrecipedisable list
/testrecipedisable check minecolonies:blockhutbarracks
```

## Implementation Details

### Recipe Replacement System
The mod uses a recipe replacement system that generates custom "disabled" recipe files. These recipes:
- Have the same output as the original recipes
- Cannot be crafted (always return false for matches)
- Override the original Minecolonies recipes when loaded

### Data Generation
The disabled recipes are generated during the data generation phase when the mod is built. This ensures that:
- The recipes are properly registered
- The feature works in both development and production environments
- No runtime recipe manipulation is required

### Configuration Integration
The feature integrates seamlessly with the existing mod configuration system:
- Uses the same configuration file as other mod features
- Follows the same configuration patterns
- Can be changed without restarting the server (though a restart is recommended)

## Usage Recommendations

1. **Enable the Feature**: Set `DisableHutRecipes = true` in the config file
2. **Restart the Server**: Restart the server to ensure the disabled recipes are loaded
3. **Test the Feature**: Use the test commands to verify the feature is working
4. **Configure Shops**: Ensure SDMShop or Admin Shop is properly configured to sell the building huts
5. **Inform Players**: Let players know that building huts must be purchased from shops

## Troubleshooting

### Recipes Still Appearing
- Ensure the configuration is set to `true`
- Restart the server after changing the configuration
- Check the server logs for any error messages

### Missing Building Huts
- Verify that SDMShop or Admin Shop is properly configured
- Check that the shop has the building huts available for purchase
- Ensure players have the necessary permissions to use the shop

### Performance Issues
- The feature has minimal performance impact
- Recipe generation only occurs during server startup
- No runtime performance overhead

## Technical Notes

- The feature uses Forge's data generation system
- Custom recipe serializers are registered for disabled recipes
- The implementation is compatible with Forge 1.19.2+
- No modifications to Minecolonies code are required







