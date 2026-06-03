package net.machiavelli.minecolonytax.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.pvp.model.PlayerPvPStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Atomic JSON persistence for PvPManager player stats, original game-modes,
 * and original world positions. Splits stats off from arena-map data so
 * the existing pvp_arena_data.json file stays unchanged.
 *
 * Persisted state survives server crashes and disconnect-during-defeat
 * windows so players stranded in SPECTATOR can be restored on next login.
 */
public final class PvPStatsPersistence {

    private static final Logger LOGGER = LogManager.getLogger(PvPStatsPersistence.class);

    private PvPStatsPersistence() {}

    public static synchronized void save() {
        PvPManager mgr = PvPManager.INSTANCE;
        try {
            if (PvPManager.PVP_STATS_FILE.getParentFile() != null
                    && !PvPManager.PVP_STATS_FILE.getParentFile().exists()) {
                PvPManager.PVP_STATS_FILE.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();

            // Player stats
            JsonObject statsObj = new JsonObject();
            for (Map.Entry<UUID, PlayerPvPStats> e : mgr.playerStats.entrySet()) {
                PlayerPvPStats s = e.getValue();
                if (s == null) continue;
                JsonObject o = new JsonObject();
                // Use reflection-free direct getters where available; for hidden fields,
                // fall back to reflection so we don't have to mutate the model class.
                try {
                    o.addProperty("duelsWon", getIntField(s, "duelsWon"));
                    o.addProperty("duelsLost", getIntField(s, "duelsLost"));
                    o.addProperty("teamBattlesWon", getIntField(s, "teamBattlesWon"));
                    o.addProperty("teamBattlesLost", getIntField(s, "teamBattlesLost"));
                    o.addProperty("arenaKills", s.getArenaKills());
                    o.addProperty("arenaDeaths", s.getArenaDeaths());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to serialize PvP stats for {}: {}", e.getKey(), ex.toString());
                    continue;
                }
                statsObj.add(e.getKey().toString(), o);
            }
            root.add("playerStats", statsObj);

            // Original game-modes
            JsonObject gmObj = new JsonObject();
            for (Map.Entry<UUID, GameType> e : mgr.playerOriginalGameModes.entrySet()) {
                if (e.getValue() == null) continue;
                gmObj.addProperty(e.getKey().toString(), e.getValue().getName());
            }
            root.add("originalGameModes", gmObj);

            // Original positions
            JsonObject posObj = new JsonObject();
            for (Map.Entry<UUID, GlobalPos> e : mgr.playerOriginalPositions.entrySet()) {
                GlobalPos gp = e.getValue();
                if (gp == null) continue;
                JsonObject p = new JsonObject();
                p.addProperty("dimension", gp.dimension().location().toString());
                p.addProperty("x", gp.pos().getX());
                p.addProperty("y", gp.pos().getY());
                p.addProperty("z", gp.pos().getZ());
                posObj.add(e.getKey().toString(), p);
            }
            root.add("originalPositions", posObj);

            // Atomic write: temp file + Files.move with ATOMIC_MOVE
            Path target = PvPManager.PVP_STATS_FILE.toPath();
            Path tmp = target.resolveSibling(PvPManager.PVP_STATS_FILE.getName() + ".tmp");
            Files.writeString(tmp, PvPManager.GSON.toJson(root));
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                // Some Windows configurations refuse ATOMIC_MOVE across handles — fall back to plain move
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ioe) {
            LOGGER.error("Failed to save PvP stats", ioe);
        } catch (Throwable t) {
            LOGGER.error("Unexpected error saving PvP stats", t);
        }
    }

    public static synchronized void load() {
        PvPManager mgr = PvPManager.INSTANCE;
        if (!PvPManager.PVP_STATS_FILE.exists()) {
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("No PvP stats file found. Starting fresh.");
            }
            return;
        }
        try (Reader r = Files.newBufferedReader(PvPManager.PVP_STATS_FILE.toPath())) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root == null) return;

            if (root.has("playerStats")) {
                JsonObject statsObj = root.getAsJsonObject("playerStats");
                for (String key : statsObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        JsonObject o = statsObj.getAsJsonObject(key);
                        PlayerPvPStats s = new PlayerPvPStats();
                        setIntField(s, "duelsWon", o.has("duelsWon") ? o.get("duelsWon").getAsInt() : 0);
                        setIntField(s, "duelsLost", o.has("duelsLost") ? o.get("duelsLost").getAsInt() : 0);
                        setIntField(s, "teamBattlesWon", o.has("teamBattlesWon") ? o.get("teamBattlesWon").getAsInt() : 0);
                        setIntField(s, "teamBattlesLost", o.has("teamBattlesLost") ? o.get("teamBattlesLost").getAsInt() : 0);
                        setIntField(s, "arenaKills", o.has("arenaKills") ? o.get("arenaKills").getAsInt() : 0);
                        setIntField(s, "arenaDeaths", o.has("arenaDeaths") ? o.get("arenaDeaths").getAsInt() : 0);
                        mgr.playerStats.put(uuid, s);
                    } catch (Exception perEntry) {
                        LOGGER.warn("Skipping malformed PvP stats entry '{}': {}", key, perEntry.toString());
                    }
                }
            }

            if (root.has("originalGameModes")) {
                JsonObject gmObj = root.getAsJsonObject("originalGameModes");
                for (String key : gmObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        String name = gmObj.get(key).getAsString();
                        GameType gt = GameType.byName(name, GameType.SURVIVAL);
                        mgr.playerOriginalGameModes.put(uuid, gt);
                    } catch (Exception perEntry) {
                        LOGGER.warn("Skipping malformed gamemode entry '{}': {}", key, perEntry.toString());
                    }
                }
            }

            if (root.has("originalPositions")) {
                JsonObject posObj = root.getAsJsonObject("originalPositions");
                for (String key : posObj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        JsonObject p = posObj.getAsJsonObject(key);
                        ResourceLocation dimLoc = new ResourceLocation(p.get("dimension").getAsString());
                        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);
                        BlockPos bp = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
                        mgr.playerOriginalPositions.put(uuid, GlobalPos.of(dim, bp));
                    } catch (Exception perEntry) {
                        LOGGER.warn("Skipping malformed original-position entry '{}': {}", key, perEntry.toString());
                    }
                }
            }

            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Loaded PvP stats: {} player records, {} stashed gamemodes, {} stashed positions",
                        mgr.playerStats.size(), mgr.playerOriginalGameModes.size(), mgr.playerOriginalPositions.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load PvP stats", e);
        }
    }

    private static int getIntField(Object obj, String field) throws Exception {
        Field f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getInt(obj);
    }

    private static void setIntField(Object obj, String field, int value) throws Exception {
        Field f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(obj, value);
    }

    /**
     * Build the JsonArray representation of the read array - exposed for testing/debugging.
     * Kept package-private so reflection-callers in tests can introspect; production code
     * does not call this.
     */
    @SuppressWarnings("unused")
    static JsonArray emptyArray() {
        return new JsonArray();
    }
}
