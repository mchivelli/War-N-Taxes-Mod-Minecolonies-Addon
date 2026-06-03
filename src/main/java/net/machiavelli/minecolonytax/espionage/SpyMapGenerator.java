package net.machiavelli.minecolonytax.espionage;

import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates a pre-filled Minecraft map of a target colony area.
 *
 * Unloaded chunks appear as neutral gray — the player can fill them in by
 * visiting the area, which is an intentional gameplay hook.
 */
public class SpyMapGenerator {

    private static final Logger LOGGER = LogManager.getLogger(SpyMapGenerator.class);

    public static ItemStack generateColonyMap(ServerLevel level, int centerX, int centerZ, String colonyName) {
        try {
            byte scale = (byte) TaxConfig.getSpyMapScale();
            int scaleFactor = 1 << scale;

            // MapItem.create handles getFreeMapId + setMapData internally.
            ItemStack mapStack = MapItem.create(level, centerX, centerZ, scale, false, false);

            MapItemSavedData mapData = MapItem.getSavedData(mapStack, level);
            if (mapData == null) {
                LOGGER.warn("SpyMapGenerator: getSavedData returned null for map of {}", colonyName);
                return mapStack;
            }

            // Vanilla formula aligns the map origin to the scale grid, matching how vanilla renders maps.
            int mapStartX = (centerX / scaleFactor - 64) * scaleFactor;
            int mapStartZ = (centerZ / scaleFactor - 64) * scaleFactor;

            for (int pz = 0; pz < 128; pz++) {
                int worldZ = mapStartZ + pz * scaleFactor;
                for (int px = 0; px < 128; px++) {
                    int worldX = mapStartX + px * scaleFactor;

                    int curHeight  = getTerrainHeight(level, worldX, worldZ);
                    int northHeight = (pz > 0)
                            ? getTerrainHeight(level, worldX, worldZ - scaleFactor)
                            : curHeight;

                    // Topographic shade: brighter uphill, darker downhill
                    int shade = (curHeight > northHeight) ? 2 : (curHeight < northHeight) ? 0 : 1;

                    MapColor mapColor = getTopBlockColor(level, worldX, worldZ, curHeight);
                    mapData.colors[px + pz * 128] = (byte) (mapColor.id * 4 + shade);
                }
            }

            mapData.setDirty();

            mapStack.setHoverName(
                    Component.literal("Spy Map: " + colonyName)
                            .withStyle(ChatFormatting.DARK_PURPLE));

            return mapStack;

        } catch (Exception e) {
            LOGGER.warn("SpyMapGenerator: failed to generate map for {} at ({}, {}): {}",
                    colonyName, centerX, centerZ, e.getMessage());
            return null;
        }
    }

    // Returns sea level (64) for unloaded chunks so shading remains neutral
    private static int getTerrainHeight(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return 64;
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    private static MapColor getTopBlockColor(ServerLevel level, int x, int z, int surfaceY) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return MapColor.COLOR_GRAY;

        int y = Math.max(surfaceY - 1, level.getMinBuildHeight());
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        MapColor color = state.getMapColor(level, pos);

        if (color == MapColor.NONE) {
            // Transparent/air — scan downward for a solid color
            for (int dy = 1; dy <= 4; dy++) {
                int scanY = y - dy;
                if (scanY < level.getMinBuildHeight()) break;
                BlockPos below = new BlockPos(x, scanY, z);
                MapColor below_color = level.getBlockState(below).getMapColor(level, below);
                if (below_color != MapColor.NONE) return below_color;
            }
            return MapColor.COLOR_GRAY;
        }

        return color;
    }
}
