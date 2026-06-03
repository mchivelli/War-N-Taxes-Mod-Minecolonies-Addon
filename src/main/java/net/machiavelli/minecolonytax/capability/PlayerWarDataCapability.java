package net.machiavelli.minecolonytax.capability;

import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
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
    public static final Capability<PlayerWarData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    public static PlayerWarData getOrCreate(Player player) {
        return player.getCapability(CAPABILITY).orElseGet(PlayerWarData::new);
    }

    public static LazyOptional<PlayerWarData> get(Player player) {
        return player.getCapability(CAPABILITY);
    }

    @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerWarData.class);
        }
    }

    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            Provider provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);

            if (player instanceof ServerPlayer serverPlayer) {
                loadDataFromPersistent(serverPlayer, provider);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoad(net.minecraftforge.event.entity.player.PlayerEvent.LoadFromFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                player.getCapability(CAPABILITY).ifPresent(data -> {
                    CompoundTag persistentData = player.getPersistentData();
                    if (persistentData.contains("ForgeData")) {
                        CompoundTag forgeData = persistentData.getCompound("ForgeData");
                        if (forgeData.contains(MineColonyTax.MOD_ID + "_war_data")) {
                            data.deserializeNBT(forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data"));
                        }
                    }
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error loading player war data: " + e.getMessage());
            }
        }
    }

    private static void loadDataFromPersistent(ServerPlayer player, Provider provider) {
        try {
            CompoundTag persistentData = player.getPersistentData();
            if (persistentData.contains("ForgeData")) {
                CompoundTag forgeData = persistentData.getCompound("ForgeData");
                if (forgeData.contains(MineColonyTax.MOD_ID + "_war_data")) {
                    provider.deserializeNBT(forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data"));
                }
            }
        } catch (Exception e) {
            MineColonyTax.LOGGER.error("Error loading persistent data: " + e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            try {
                event.getOriginal().getCapability(CAPABILITY).ifPresent(oldData -> {
                    try {
                        CompoundTag nbt = oldData.serializeNBT();
                        event.getEntity().getCapability(CAPABILITY).ifPresent(newData -> {
                            try {
                                newData.deserializeNBT(nbt);
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

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Data lives in player persistent data (ForgeData NBT), not in the capability directly;
        // no migration needed when changing dimensions.
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                player.getCapability(CAPABILITY).ifPresent(data -> {
                    CompoundTag persistentData = player.getPersistentData();
                    if (persistentData.contains("ForgeData")) {
                        CompoundTag forgeData = persistentData.getCompound("ForgeData");
                        if (forgeData.contains(MineColonyTax.MOD_ID + "_war_data")) {
                            data.deserializeNBT(forgeData.getCompound(MineColonyTax.MOD_ID + "_war_data"));
                        }
                    }
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error loading war data on player login: " + e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                player.getCapability(CAPABILITY).ifPresent(data -> {
                    CompoundTag nbt = data.serializeNBT();
                    CompoundTag persistentData = player.getPersistentData();
                    if (!persistentData.contains("ForgeData")) {
                        persistentData.put("ForgeData", new CompoundTag());
                    }
                    persistentData.getCompound("ForgeData").put(MineColonyTax.MOD_ID + "_war_data", nbt);
                });
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error saving war data on player logout: " + e.getMessage());
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onPlayerSave(net.minecraftforge.event.entity.player.PlayerEvent.SaveToFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                CompoundTag nbt = getOrCreate(player).serializeNBT();
                CompoundTag persistentData = player.getPersistentData();
                if (!persistentData.contains("ForgeData")) {
                    persistentData.put("ForgeData", new CompoundTag());
                }
                persistentData.getCompound("ForgeData").put(MineColonyTax.MOD_ID + "_war_data", nbt);
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error saving player war data: " + e.getMessage());
            }
        }
    }

    private static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
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

        @Override
        public CompoundTag serializeNBT() {
            try {
                return data.serializeNBT();
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error serializing PlayerWarData: " + e.getMessage());
                return new CompoundTag();
            }
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            try {
                if (nbt != null) {
                    data.deserializeNBT(nbt);
                }
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error deserializing PlayerWarData: " + e.getMessage());
            }
        }
    }
}
