package net.machiavelli.minecolonytax.capability;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.data.PlayerWarData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID)
public class PlayerWarDataCapability {

    public static final ResourceLocation ID = new ResourceLocation(MineColonyTax.MOD_ID, "player_war_data");
    public static final Capability<PlayerWarData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    // Get player war data, creating if necessary
    public static PlayerWarData getOrCreate(Player player) {
        return player.getCapability(CAPABILITY).orElseGet(PlayerWarData::new);
    }

    // Get player war data if present
    public static LazyOptional<PlayerWarData> get(Player player) {
        return player.getCapability(CAPABILITY);
    }

    // Mod event bus subscriber for capability registration
    @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            MineColonyTax.LOGGER.info("REGISTERING PlayerWarData capability on MOD event bus");
            event.register(PlayerWarData.class);
            MineColonyTax.LOGGER.info("PlayerWarData capability registration complete");
        }
    }

    // Attach capabilities to player
    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            // Safely log without trying to access the player's name which might not be initialized yet
            MineColonyTax.LOGGER.debug("Attaching PlayerWarData capability to player entity");
            Provider provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
            
            // If this player has saved war data in their persistent data, try to load it
            if (player instanceof ServerPlayer serverPlayer) {
                loadDataFromPersistent(serverPlayer, provider);
            }
        }
    }
    
    // Add player load event handler to load data from persistent storage
    @SubscribeEvent
    public static void onPlayerLoad(net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                String playerIdStr = player.getStringUUID();
                MineColonyTax.LOGGER.debug("Loading PlayerWarData for player " + playerIdStr);
                
                // Get the capability
                player.getCapability(CAPABILITY).ifPresent(data -> {
                    // Try to load from persistent data
                    CompoundTag persistentData = player.getPersistentData();
                    if (persistentData.contains("ForgeData")) {
                        CompoundTag forgeData = persistentData.getCompound("ForgeData");
                        if (forgeData.contains(MineColonyTax.MOD_ID + "_war_data")) {
                            CompoundTag warDataNbt = forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data");
                            data.deserializeNBT(warDataNbt);
                            MineColonyTax.LOGGER.debug("Loaded war data from persistent storage: " + warDataNbt);
                            
                            // Log individual stat values for debugging
                            MineColonyTax.LOGGER.info("Loaded war stats - PlayersKilled: " + data.getPlayersKilledInWar() + 
                                ", RaidedColonies: " + data.getRaidedColonies() + 
                                ", AmountRaided: " + data.getAmountRaided() + 
                                ", WarsWon: " + data.getWarsWon() + 
                                ", WarStalemates: " + data.getWarStalemates());
                        } else {
                            MineColonyTax.LOGGER.info("No war data found in ForgeData for player " + playerIdStr);
                        }
                    } else {
                        MineColonyTax.LOGGER.info("No ForgeData found for player " + playerIdStr);
                    }
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error loading player war data: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Helper method to load data from persistent storage
    private static void loadDataFromPersistent(ServerPlayer player, Provider provider) {
        try {
            String playerIdStr = player.getStringUUID();
            MineColonyTax.LOGGER.info("Attempting to load persistent war data for player " + playerIdStr);
            
            // Access the player's persistent data
            CompoundTag persistentData = player.getPersistentData();
            if (persistentData.contains("ForgeData")) {
                CompoundTag forgeData = persistentData.getCompound("ForgeData");
                if (forgeData.contains(MineColonyTax.MOD_ID + "_war_data")) {
                    CompoundTag warDataNbt = forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data");
                    provider.deserializeNBT(warDataNbt);
                    MineColonyTax.LOGGER.info("Successfully loaded war data from persistent storage: " + warDataNbt);
                } else {
                    MineColonyTax.LOGGER.info("No war data found in persistent storage for player " + playerIdStr);
                }
            }
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error loading persistent data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Copy from dead player to respawned player
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            try {
                // Get the NBT data from the original player's capability
                event.getOriginal().getCapability(CAPABILITY).ifPresent(oldData -> {
                    try {
                        CompoundTag nbt = oldData.serializeNBT(); // Directly serialize the PlayerWarData
                        MineColonyTax.LOGGER.info("PlayerWarData on death: " + nbt);
                        
                        // Deserialize the NBT data into the new player's capability
                        event.getEntity().getCapability(CAPABILITY).ifPresent(newData -> {
                            try {
                                newData.deserializeNBT(nbt); // Directly deserialize into the PlayerWarData
                                MineColonyTax.LOGGER.info("PlayerWarData after respawn: " + newData.serializeNBT());
                            } catch (Exception e) {
                                MineColonyTax.LOGGER.error("Error deserializing player clone data: " + e.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        MineColonyTax.LOGGER.error("Error serializing original player data: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error during player clone: " + e.getMessage());
            }
        }
    }

    // Sync data when changing dimensions
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // This isn't needed with our implementation as it's stored in player data
            // but including for completeness to ensure dimension changes don't affect data
            String playerIdStr = player.getStringUUID();
            MineColonyTax.LOGGER.info("Player " + playerIdStr + " changed dimension, verifying war data persistence");
            
            // Just log current values to verify they're intact
            player.getCapability(CAPABILITY).ifPresent(data -> {
                MineColonyTax.LOGGER.info("War stats after dimension change - PlayersKilled: " + data.getPlayersKilledInWar() + 
                    ", RaidedColonies: " + data.getRaidedColonies() + 
                    ", AmountRaided: " + data.getAmountRaided() + 
                    ", WarsWon: " + data.getWarsWon() + 
                    ", WarStalemates: " + data.getWarStalemates());
            });
        }
    }
    
    // Player login event handler to ensure data is loaded when player joins
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                String playerIdStr = player.getStringUUID();
                MineColonyTax.LOGGER.info("Player " + playerIdStr + " logged in, ensuring war data is loaded");
                
                // Get the capability
                player.getCapability(CAPABILITY).ifPresent(data -> {
                    // Try to load from persistent data if not already loaded
                    CompoundTag persistentData = player.getPersistentData();
                    if (persistentData.contains("ForgeData")) {
                        CompoundTag forgeData = persistentData.getCompound("ForgeData");
                        if (forgeData.contains(MineColonyTax.MOD_ID + "_war_data")) {
                            CompoundTag warDataNbt = forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data");
                            data.deserializeNBT(warDataNbt);
                            MineColonyTax.LOGGER.info("War data loaded on player login: " + warDataNbt);
                            
                            // Log individual stat values for verification
                            MineColonyTax.LOGGER.info("War stats on login - PlayersKilled: " + data.getPlayersKilledInWar() + 
                                ", RaidedColonies: " + data.getRaidedColonies() + 
                                ", AmountRaided: " + data.getAmountRaided() + 
                                ", WarsWon: " + data.getWarsWon() + 
                                ", WarStalemates: " + data.getWarStalemates());
                        } else {
                            MineColonyTax.LOGGER.info("No war data found for player " + playerIdStr + " on login");
                        }
                    }
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error loading war data on player login: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Player logout event handler to ensure data is saved when player leaves
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                String playerIdStr = player.getStringUUID();
                MineColonyTax.LOGGER.info("Player " + playerIdStr + " logged out, ensuring war data is saved");
                
                // Get the capability
                player.getCapability(CAPABILITY).ifPresent(data -> {
                    CompoundTag nbt = data.serializeNBT();
                    
                    // Log war stats before saving
                    MineColonyTax.LOGGER.info("War stats on logout - PlayersKilled: " + data.getPlayersKilledInWar() + 
                        ", RaidedColonies: " + data.getRaidedColonies() + 
                        ", AmountRaided: " + data.getAmountRaided() + 
                        ", WarsWon: " + data.getWarsWon() + 
                        ", WarStalemates: " + data.getWarStalemates());
                    
                    // Save to persistent data
                    CompoundTag persistentData = player.getPersistentData();
                    if (!persistentData.contains("ForgeData")) {
                        persistentData.put("ForgeData", new CompoundTag());
                    }
                    CompoundTag forgeData = persistentData.getCompound("ForgeData");
                    forgeData.put(MineColonyTax.MOD_ID + "_war_data", nbt);
                    
                    MineColonyTax.LOGGER.info("War data saved on player logout: " + nbt);
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error saving war data on player logout: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Add player save event handler
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onPlayerSave(net.minecraftforge.event.entity.player.PlayerEvent.SaveToFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                // Get the player's war data capability
                PlayerWarData data = getOrCreate(player);
                CompoundTag nbt = data.serializeNBT();
                
                // Safely log with player ID instead of potentially unavailable name
                String playerIdStr = player.getStringUUID();
                MineColonyTax.LOGGER.debug("Saving PlayerWarData for player " + playerIdStr);
                
                // Log individual stat values for debugging at DEBUG level only
                if (MineColonyTax.LOGGER.isDebugEnabled()) {
                    MineColonyTax.LOGGER.debug("Saving war stats - PlayersKilled: " + data.getPlayersKilledInWar() + 
                        ", RaidedColonies: " + data.getRaidedColonies() + 
                        ", AmountRaided: " + data.getAmountRaided() + 
                        ", WarsWon: " + data.getWarsWon() + 
                        ", WarStalemates: " + data.getWarStalemates());
                }
                
                // IMPORTANT: Save the NBT data directly to the player's persistent data
                // This ensures it gets written to the player's UUID.dat file
                CompoundTag persistentData = player.getPersistentData();
                if (!persistentData.contains("ForgeData")) {
                    persistentData.put("ForgeData", new CompoundTag());
                }
                CompoundTag forgeData = persistentData.getCompound("ForgeData");
                forgeData.put(MineColonyTax.MOD_ID + "_war_data", nbt);
                
                // Force mark the player's persistence as dirty to ensure it gets saved
                // This is crucial for making sure data is written to disk
                // No direct API for this in vanilla, but the change to persistent data should trigger it
                
                MineColonyTax.LOGGER.debug("PlayerWarData successfully saved to persistent storage for player " + playerIdStr);
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error saving player war data: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Implementation of provider
    private static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        // Directly hold PlayerWarData and create a new instance if needed.
        private final PlayerWarData data = new PlayerWarData();
        private final LazyOptional<PlayerWarData> instance = LazyOptional.of(() -> data);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> requiredCapability, @Nullable Direction side) {
            return CAPABILITY.orEmpty(requiredCapability, instance);
        }

        void invalidate() {
            instance.invalidate();
        }

        // Directly use PlayerWarData's serializeNBT
        @Override
        public CompoundTag serializeNBT() {
            try {
                CompoundTag tag = data.serializeNBT();
                // Remove excessive logging that causes console spam
                return tag;
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error serializing PlayerWarData: " + e.getMessage());
                return new CompoundTag();
            }
        }

        // Directly use PlayerWarData's deserializeNBT
        @Override
        public void deserializeNBT(CompoundTag nbt) {
            try {
                if (nbt != null) {
                    // Remove excessive logging that causes console spam
                    data.deserializeNBT(nbt);
                } else {
                    MineColonyTax.LOGGER.warn("Received null NBT data for PlayerWarData");
                }
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error deserializing PlayerWarData: " + e.getMessage());
            }
        }
    }
} 