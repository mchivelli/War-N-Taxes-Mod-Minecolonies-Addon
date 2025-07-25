package net.machiavelli.minecolonytax.pvp;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.pvp.model.PvPMap;
import net.machiavelli.minecolonytax.pvp.persistence.ArenaDataCollection;
import net.machiavelli.minecolonytax.pvp.persistence.ArenaMapData;
import net.machiavelli.minecolonytax.pvp.persistence.SpawnPointData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PvPMapManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private final PvPManager pvpManager = PvPManager.INSTANCE;

    public int createMap(CommandContext<CommandSourceStack> context, String mapName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (pvpManager.arenaMapsByName.containsKey(mapName)) {
            context.getSource().sendFailure(Component.literal("Map '" + mapName + "' already exists!"));
            return 0;
        }
        PvPMap newMap = new PvPMap(mapName, player.level().dimension());
        pvpManager.arenaMapsByName.put(mapName, newMap);
        if (pvpManager.defaultMapName == null) {
            pvpManager.defaultMapName = mapName;
        }
        saveArenaData();
        context.getSource().sendSuccess(() -> Component.literal("Created PvP map: " + mapName).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public int deleteMap(CommandContext<CommandSourceStack> context, String mapName) {
        if (!pvpManager.arenaMapsByName.containsKey(mapName)) {
            context.getSource().sendFailure(Component.literal("Map '" + mapName + "' does not exist!"));
            return 0;
        }
        boolean inUse = pvpManager.activeBattles.values().stream().anyMatch(battle -> battle.getMapName().equals(mapName));
        if (inUse) {
            context.getSource().sendFailure(Component.literal("Cannot delete map '" + mapName + "' - it's currently in use!"));
            return 0;
        }
        pvpManager.arenaMapsByName.remove(mapName);
        if (mapName.equals(pvpManager.defaultMapName)) {
            pvpManager.defaultMapName = pvpManager.arenaMapsByName.isEmpty() ? null : pvpManager.arenaMapsByName.keySet().iterator().next();
        }
        saveArenaData();
        context.getSource().sendSuccess(() -> Component.literal("Deleted PvP map: " + mapName).withStyle(ChatFormatting.RED), false);
        return 1;
    }

    public int addSpawnPoint(CommandContext<CommandSourceStack> context, String mapName, int spawnIndex) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PvPMap map = pvpManager.arenaMapsByName.get(mapName);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("Map '" + mapName + "' does not exist!"));
            return 0;
        }
        GlobalPos playerPos = GlobalPos.of(player.level().dimension(), player.blockPosition());
        if (!map.getDimension().equals(player.level().dimension())) {
            context.getSource().sendFailure(Component.literal("You must be in the same dimension as the map!"));
            return 0;
        }
        // Ensure we have enough spawn points in the list
        while (map.getSpawnPoints().size() < spawnIndex) {
            // Add placeholder spawn points that will be replaced
            map.getSpawnPoints().add(GlobalPos.of(player.level().dimension(), BlockPos.ZERO));
        }
        
        // Set the spawn point at the specified index (1-based to 0-based conversion)
        if (spawnIndex <= map.getSpawnPoints().size()) {
            map.getSpawnPoints().set(spawnIndex - 1, playerPos);
        } else {
            map.addSpawnPoint(playerPos);
        }
        saveArenaData();
        context.getSource().sendSuccess(() -> Component.literal("Set spawn point " + spawnIndex + " for map '" + mapName + "'").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public int setDefaultMap(CommandContext<CommandSourceStack> context, String mapName) {
        if (!pvpManager.arenaMapsByName.containsKey(mapName)) {
            context.getSource().sendFailure(Component.literal("Map '" + mapName + "' does not exist!"));
            return 0;
        }
        pvpManager.defaultMapName = mapName;
        saveArenaData();
        context.getSource().sendSuccess(() -> Component.literal("Set default map to: " + mapName).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public int listMaps(CommandContext<CommandSourceStack> context) {
        if (pvpManager.arenaMapsByName.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No PvP maps configured"), false);
            return 0;
        }
        MutableComponent message = Component.literal("PvP Maps:\n").withStyle(ChatFormatting.GOLD);
        for (PvPMap map : pvpManager.arenaMapsByName.values()) {
            String defaultMarker = map.getName().equals(pvpManager.defaultMapName) ? " [DEFAULT]" : "";
            message.append(Component.literal("- " + map.getName() + defaultMarker +
                            " (Spawns: " + map.getSpawnPoints().size() + "/" + map.getMaxPlayers() + ")\n")
                    .withStyle(ChatFormatting.WHITE));
        }
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    public int showMapInfo(CommandContext<CommandSourceStack> context, String mapName) {
        PvPMap map = pvpManager.arenaMapsByName.get(mapName);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("Map '" + mapName + "' does not exist!"));
            return 0;
        }
        MutableComponent info = Component.literal("Map: " + mapName + "\n").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Dimension: " + map.getDimension().location() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Spawn Points: " + map.getSpawnPoints().size() + "/" + map.getMaxPlayers() + "\n").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Max Teams: " + (map.getSpawnPoints().size() / 2) + "v" + (map.getSpawnPoints().size() / 2)).withStyle(ChatFormatting.GREEN));
        context.getSource().sendSuccess(() -> info, false);
        return 1;
    }

    public void saveArenaData() {
        try {
            if (!PvPManager.ARENA_DATA_FILE.getParentFile().exists()) {
                PvPManager.ARENA_DATA_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(PvPManager.ARENA_DATA_FILE)) {
                ArenaDataCollection data = new ArenaDataCollection();
                for (PvPMap map : pvpManager.arenaMapsByName.values()) {
                    ArenaMapData mapData = new ArenaMapData();
                    mapData.name = map.getName();
                    mapData.dimension = map.getDimension().location().toString();
                    mapData.maxPlayers = map.getMaxPlayers();
                    mapData.spawnPoints = new java.util.ArrayList<>();
                    for (GlobalPos pos : map.getSpawnPoints()) {
                        SpawnPointData spawnData = new SpawnPointData();
                        spawnData.x = pos.pos().getX();
                        spawnData.y = pos.pos().getY();
                        spawnData.z = pos.pos().getZ();
                        mapData.spawnPoints.add(spawnData);
                    }
                    data.maps.add(mapData);
                }
                data.defaultMapName = pvpManager.defaultMapName;
                PvPManager.GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save arena data", e);
        }
    }

    public void loadArenaData() {
        if (!PvPManager.ARENA_DATA_FILE.exists()) {
            LOGGER.info("No PvP arena data file found. Skipping load.");
            return;
        }
        try (FileReader reader = new FileReader(PvPManager.ARENA_DATA_FILE)) {
            ArenaDataCollection data = PvPManager.GSON.fromJson(reader, ArenaDataCollection.class);
            if (data == null) {
                LOGGER.warn("PvP arena data file is empty or malformed. No arenas loaded.");
                return;
            }
            pvpManager.arenaMapsByName.clear();
            for (ArenaMapData mapData : data.maps) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(mapData.dimension));
                PvPMap map = new PvPMap(mapData.name, dimension);
                map.setMaxPlayers(mapData.maxPlayers);
                for (SpawnPointData spawnData : mapData.spawnPoints) {
                    GlobalPos pos = GlobalPos.of(dimension, new BlockPos(spawnData.x, spawnData.y, spawnData.z));
                    map.getSpawnPoints().add(pos);
                }
                pvpManager.arenaMapsByName.put(map.getName(), map);
            }
            pvpManager.defaultMapName = data.defaultMapName;
            LOGGER.info("Successfully loaded {} PvP arenas.", pvpManager.arenaMapsByName.size());
        } catch (Exception e) {
            LOGGER.error("FATAL: Failed to load or parse PvP arena data. Arenas will not be available.", e);
        }
    }
} 